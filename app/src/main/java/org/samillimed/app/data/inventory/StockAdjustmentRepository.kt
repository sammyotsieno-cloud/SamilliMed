package org.samillimed.app.data.inventory

import org.samillimed.app.data.facility.FacilityScope

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val STOCK_BATCHES: String get() = FacilityScope.path("stockBatches")
private val STOCK_BALANCES: String get() = FacilityScope.path("stockBalances")
private val STOCK_MOVEMENTS: String get() = FacilityScope.path("stockMovements")
private val STOCK_RECONCILIATIONS: String get() = FacilityScope.path("stockReconciliations")
private val DAILY_SUMMARIES: String get() = FacilityScope.path("dailySummaries")

/** Why stock was changed by hand. Every adjustment needs one. */
object AdjustmentReason {
    const val EXPIRED = "EXPIRED"
    const val DAMAGED = "DAMAGED"
    const val LOST = "LOST"
    const val SAMPLE = "SAMPLE"
    const val RETURNED_TO_SUPPLIER = "RETURNED_TO_SUPPLIER"
    const val COUNT_SHORT = "COUNT_SHORT"
    const val COUNT_OVER = "COUNT_OVER"

    /** Reasons that take stock out. */
    val decreases = listOf(EXPIRED, DAMAGED, LOST, SAMPLE, RETURNED_TO_SUPPLIER, COUNT_SHORT)

    /** Reasons that put stock back (corrections of an earlier count, never above what was received). */
    val increases = listOf(COUNT_OVER)

    fun label(code: String): String = when (code) {
        EXPIRED -> "Expired: written off"
        DAMAGED -> "Damaged"
        LOST -> "Lost or stolen"
        SAMPLE -> "Given as a free sample"
        RETURNED_TO_SUPPLIER -> "Returned to the supplier"
        COUNT_SHORT -> "Stock count: shortfall"
        COUNT_OVER -> "Stock count: found extra"
        else -> code
    }

    /** Counted as a loss in reports. A return to the supplier is credited back, so it is not a loss. */
    fun isLoss(code: String): Boolean = code in listOf(EXPIRED, DAMAGED, LOST, SAMPLE, COUNT_SHORT)

    /** Reasons where the person must explain what happened. */
    fun needsNote(code: String): Boolean = code !in listOf(EXPIRED, RETURNED_TO_SUPPLIER)
}

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No internet connection. Stock changes need a connection so that every device shows " +
                "the same stock. Try again when you are back online."
        FirebaseFirestoreException.Code.ABORTED ->
            "Someone changed this stock at the same moment. Please try again."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do this. Sign in again."
        else -> error.message ?: fallback
    }

private fun ratioCents(amountCents: Long, numerator: Long, denominator: Long): Long =
    BigDecimal.valueOf(amountCents)
        .multiply(BigDecimal.valueOf(numerator))
        .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
        .toLong()

private fun DocumentSnapshot.longMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
        ?: emptyMap()

/** Changes the quantity of one batch by hand, with a reason, in one step. */
class StockAdjustmentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun currentUser(): String =
        FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    /**
     * Takes stock out of a batch (or puts a count correction back) and records it in the stock
     * card, the product balance and the day's loss totals. quantityBase is in the product's
     * smallest unit. onSuccess receives what the batch now holds.
     */
    fun adjustBatch(
        batchId: String,
        quantityBase: Long,
        reason: String,
        note: String,
        onSuccess: (Long) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = currentUser()
        if (user.isBlank()) {
            onFailure("Sign in before adjusting stock.")
            return
        }
        val increase = reason in AdjustmentReason.increases
        if (!increase && reason !in AdjustmentReason.decreases) {
            onFailure("Choose a reason for the adjustment.")
            return
        }
        if (batchId.isBlank() || quantityBase <= 0L) {
            onFailure("Enter a quantity above zero.")
            return
        }
        if (AdjustmentReason.needsNote(reason) && note.isBlank()) {
            onFailure("Write a short note explaining what happened.")
            return
        }

        val now = System.currentTimeMillis()
        val dateKey = LocalDate.now().toString()
        val batchRef = db.collection(STOCK_BATCHES).document(batchId)
        val summaryRef = db.collection(DAILY_SUMMARIES).document(dateKey)

        db.runTransaction { tx ->
            val batchSnap = tx.get(batchRef)
            if (!batchSnap.exists()) throw IllegalArgumentException("This batch no longer exists.")

            val productId = batchSnap.getString("productId").orEmpty()
            val balanceRef = db.collection(STOCK_BALANCES).document(productId)
            val balanceSnap = tx.get(balanceRef)
            val summarySnap = tx.get(summaryRef)

            val remaining = batchSnap.getLong("remainingQuantity") ?: 0L
            val received = batchSnap.getLong("receivedQuantity") ?: 0L

            val newRemaining: Long
            if (increase) {
                newRemaining = remaining + quantityBase
                if (newRemaining > received) {
                    throw IllegalArgumentException("A batch cannot hold more than the ${received} that was received.")
                }
            } else {
                if (quantityBase > remaining) {
                    throw IllegalArgumentException("The batch only holds $remaining.")
                }
                newRemaining = remaining - quantityBase
            }

            val recorded = balanceSnap.getLong("quantityOnHand") ?: 0L
            val change = if (increase) quantityBase else -quantityBase
            val newBalance = (recorded + change).coerceAtLeast(0L)

            val valueCents = ratioCents(
                batchSnap.getLong("purchaseCostPerReceivingUnitCents") ?: 0L,
                quantityBase,
                (batchSnap.getLong("baseUnitsPerReceivingUnit") ?: 1L).coerceAtLeast(1L),
            )

            val movementRef = db.collection(STOCK_MOVEMENTS).document()
            tx.update(batchRef, mapOf("remainingQuantity" to newRemaining))
            tx.set(
                balanceRef,
                mapOf(
                    "productId" to productId,
                    "productNameSnapshot" to (balanceSnap.getString("productNameSnapshot")
                        ?: batchSnap.getString("productNameSnapshot").orEmpty()),
                    "quantityOnHand" to newBalance,
                    "updatedAtMillis" to now,
                ),
            )
            tx.set(
                movementRef,
                mapOf(
                    "id" to movementRef.id,
                    "productId" to productId,
                    "batchId" to batchId,
                    "receiptId" to "",
                    "receiptLineId" to "",
                    "dispensingId" to "",
                    "dispensingLineId" to "",
                    "movementType" to StockMovementType.ADJUSTMENT,
                    "quantityChange" to change,
                    "resultingBatchBalance" to newRemaining,
                    "resultingProductBalance" to newBalance,
                    "transactionUnit" to mapOf("unitName" to "", "baseUnitsPerUnit" to 1L),
                    "transactionQuantity" to quantityBase,
                    "referenceNumber" to "ADJ-$dateKey",
                    "reason" to reason,
                    "note" to note.trim(),
                    "valueCents" to valueCents,
                    "createdAtMillis" to now,
                    "createdBy" to user,
                ),
            )

            // A counter that was lower than the change is corrected and the difference noted
            if (!increase && recorded < quantityBase) {
                val auditRef = db.collection(STOCK_RECONCILIATIONS).document()
                tx.set(
                    auditRef,
                    mapOf(
                        "id" to auditRef.id,
                        "productId" to productId,
                        "recordedBalance" to recorded,
                        "batchDerivedBalance" to 0L,
                        "correctedBalance" to newBalance,
                        "difference" to -recorded,
                        "reason" to "ADJUSTMENT_BALANCE_MISMATCH",
                        "createdAtMillis" to now,
                        "createdBy" to user,
                    ),
                )
            }

            // The day's loss totals that the loss report reads
            if (AdjustmentReason.isLoss(reason) && !increase) {
                val lossByReason = summarySnap.longMap("lossByReason")
                tx.set(
                    summaryRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "writeOffCostCents" to ((summarySnap.getLong("writeOffCostCents") ?: 0L) + valueCents),
                        "lossByReason" to (lossByReason + (reason to ((lossByReason[reason] ?: 0L) + valueCents))),
                        "adjustmentCount" to ((summarySnap.getLong("adjustmentCount") ?: 0L) + 1L),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            } else if (reason == AdjustmentReason.RETURNED_TO_SUPPLIER) {
                tx.set(
                    summaryRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "returnedToSupplierCents" to ((summarySnap.getLong("returnedToSupplierCents") ?: 0L) + valueCents),
                        "adjustmentCount" to ((summarySnap.getLong("adjustmentCount") ?: 0L) + 1L),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            } else {
                tx.set(
                    summaryRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "adjustmentCount" to ((summarySnap.getLong("adjustmentCount") ?: 0L) + 1L),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            }

            newRemaining
        }.addOnSuccessListener { remaining ->
            onSuccess(remaining)
        }.addOnFailureListener { error ->
            onFailure(friendlyMessage(error, "The adjustment could not be saved."))
        }
    }
}
