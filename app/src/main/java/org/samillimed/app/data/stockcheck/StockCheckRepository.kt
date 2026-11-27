package org.samillimed.app.data.stockcheck

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.FacilityScope
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.Permissions
import org.samillimed.app.data.inventory.StockMovementType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

private val CHECKS: String get() = FacilityScope.path("stockChecks")
private val CHECK_LINES: String get() = FacilityScope.path("stockCheckLines")
private val MOVEMENTS: String get() = FacilityScope.path("stockMovements")
private val BATCHES: String get() = FacilityScope.path("stockBatches")
private val BALANCES: String get() = FacilityScope.path("stockBalances")
private val SUMMARIES: String get() = FacilityScope.path("dailySummaries")
private val SETTINGS: String get() = FacilityScope.path("settings")

class CountedInput(val productId: String, val productName: String, val countedBase: Long, val countedAtMillis: Long)

/** One change to one batch: below zero takes stock away, above zero adds it. */
class CorrectionOp(val batchId: String, val delta: Long)

class Correction(
    val lineId: String,
    val productId: String,
    val countedBase: Long,
    val expectedBase: Long,
    val ops: List<CorrectionOp>,
)

class LineUpdate(val lineId: String, val status: String, val expectedBase: Long, val differenceBase: Long, val valueCents: Long)

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "There is no internet connection. Try again when you are back online."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do this. An administrator can allow it in Staff Access."
        FirebaseFirestoreException.Code.ABORTED -> "Something changed at the same moment. Please try again."
        else -> error.message ?: fallback
    }

private fun ratio(amount: Long, numerator: Long, denominator: Long): Long =
    BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(numerator))
        .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).toLong()

class StockCheckRepository {

    private val db get() = FacilityScope.firestore()

    /** Sends a finished count for review. id stays the same for one count, so sending twice is safe. */
    fun submit(
        id: String,
        type: String,
        counted: List<CountedInput>,
        note: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        when {
            user.isBlank() -> return onFailure("Sign in first.")
            !Permissions.can(Permission.COUNT_STOCK) -> return onFailure("You are not allowed to count stock.")
            counted.isEmpty() -> return onFailure("Count at least one product first.")
        }
        val database = db
        val now = System.currentTimeMillis()
        val chunks = counted.chunked(400)
        fun writeChunk(index: Int) {
            if (index >= chunks.size) {
                database.collection(CHECKS).document(id).set(
                    mapOf(
                        "id" to id,
                        "type" to type,
                        "status" to StockCheckStatus.SUBMITTED,
                        "createdAtMillis" to now,
                        "createdBy" to user,
                        "lineCount" to counted.size,
                        "note" to note.trim(),
                    ),
                ).addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(friendlyMessage(it, "The count could not be sent.")) }
                return
            }
            val batch = database.batch()
            chunks[index].forEach { c ->
                batch.set(
                    database.collection(CHECK_LINES).document("${id}_${c.productId}"),
                    mapOf(
                        "id" to "${id}_${c.productId}",
                        "checkId" to id,
                        "productId" to c.productId,
                        "productName" to c.productName,
                        "countedBase" to c.countedBase,
                        "countedAtMillis" to c.countedAtMillis,
                        "status" to CheckLineStatus.PENDING,
                    ),
                )
            }
            batch.commit().addOnSuccessListener { writeChunk(index + 1) }
                .addOnFailureListener { onFailure(friendlyMessage(it, "The count could not be sent.")) }
        }
        writeChunk(0)
    }

    /** The latest checks, newest first. */
    fun checksFlow(): Flow<List<StockCheck>> = callbackFlow {
        val registration = db.collection(CHECKS)
            .orderBy("createdAtMillis", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(StockCheck::class.java) })
            }
        awaitClose { registration.remove() }
    }

    /** The counted lines of one check. Administrators only. */
    fun loadLines(checkId: String, onResult: (List<StockCheckLine>) -> Unit, onFailure: (String) -> Unit) {
        db.collection(CHECK_LINES).whereEqualTo("checkId", checkId).get()
            .addOnSuccessListener { s -> onResult(s.documents.mapNotNull { it.toObject(StockCheckLine::class.java) }) }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The check could not be opened.")) }
    }

    /** When each product was last checked. */
    fun loadLastChecked(onResult: (Map<String, Long>) -> Unit, onFailure: (String) -> Unit) {
        db.collection(SETTINGS).document("stockCheckState").get()
            .addOnSuccessListener { snap ->
                val map = (snap.get("lastChecked") as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
                    ?: emptyMap()
                onResult(map)
            }
            .addOnFailureListener { onFailure(friendlyMessage(it, "Could not read the last check dates.")) }
    }

    /**
     * How much each product's stock changed after the moment it was counted (sales, deliveries, returns).
     * requests: product id to the time it was counted.
     */
    fun loadChangesSince(
        requests: List<Pair<String, Long>>,
        onResult: (Map<String, Long>) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (requests.isEmpty()) {
            onResult(emptyMap())
            return
        }
        val database = db
        Tasks.whenAllSuccess<QuerySnapshot>(
            requests.map { (productId, since) ->
                database.collection(MOVEMENTS)
                    .whereEqualTo("productId", productId)
                    .whereGreaterThan("createdAtMillis", since)
                    .get()
            },
        ).addOnSuccessListener { results ->
            val out = HashMap<String, Long>()
            requests.forEachIndexed { i, (productId, _) ->
                out[productId] = results[i].documents.sumOf { it.getLong("quantityChange") ?: 0L }
            }
            onResult(out)
        }.addOnFailureListener { onFailure(friendlyMessage(it, "Could not work out what was expected.")) }
    }

    /** Fixes the stock for each item, one at a time. An item already fixed is skipped, so retrying is safe. */
    fun applyAll(
        reference: String,
        items: List<Correction>,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        fun next(i: Int) {
            if (i >= items.size) {
                onSuccess()
                return
            }
            onProgress(i + 1)
            applyOne(reference, items[i], onSuccess = { next(i + 1) }, onFailure = onFailure)
        }
        next(0)
    }

    private fun applyOne(reference: String, c: Correction, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        if (!CurrentUser.isAdmin) return onFailure("Only an administrator can correct stock.")
        val database = db
        val now = System.currentTimeMillis()
        val dateKey = LocalDate.now().toString()
        val lineRef = database.collection(CHECK_LINES).document(c.lineId)
        val balanceRef = database.collection(BALANCES).document(c.productId)
        val summaryRef = database.collection(SUMMARIES).document(dateKey)
        val difference = c.countedBase - c.expectedBase
        database.runTransaction { tx ->
            val line = tx.get(lineRef)
            if (!line.exists()) throw IllegalArgumentException("This line of the check no longer exists.")
            if (line.getString("status") != CheckLineStatus.PENDING) return@runTransaction true
            val batchSnaps = c.ops.map { it.batchId }.distinct()
                .associateWith { tx.get(database.collection(BATCHES).document(it)) }
            val balanceSnap = tx.get(balanceRef)
            var balance = balanceSnap.getLong("quantityOnHand") ?: 0L
            val running = HashMap<String, Long>()
            batchSnaps.forEach { (id, s) ->
                if (!s.exists()) throw IllegalArgumentException("Stock changed while you were reviewing. Open the check again.")
                running[id] = s.getLong("remainingQuantity") ?: 0L
            }
            var totalValue = 0L
            var lossValue = 0L
            c.ops.forEach { op ->
                val s = batchSnaps.getValue(op.batchId)
                val after = running.getValue(op.batchId) + op.delta
                if (after < 0L) throw IllegalArgumentException("Stock changed while you were reviewing. Open the check again.")
                if (after > (s.getLong("receivedQuantity") ?: 0L)) {
                    throw IllegalArgumentException("A batch cannot hold more than was received.")
                }
                running[op.batchId] = after
                balance = (balance + op.delta).coerceAtLeast(0L)
                val perUnit = (s.getLong("baseUnitsPerReceivingUnit") ?: 1L).coerceAtLeast(1L)
                val value = ratio(s.getLong("purchaseCostPerReceivingUnitCents") ?: 0L, abs(op.delta), perUnit)
                totalValue += value
                if (op.delta < 0L) lossValue += value
                val movementRef = database.collection(MOVEMENTS).document()
                tx.set(
                    movementRef,
                    mapOf(
                        "id" to movementRef.id,
                        "productId" to c.productId,
                        "batchId" to op.batchId,
                        "receiptId" to "",
                        "receiptLineId" to "",
                        "dispensingId" to "",
                        "dispensingLineId" to "",
                        "movementType" to StockMovementType.ADJUSTMENT,
                        "quantityChange" to op.delta,
                        "resultingBatchBalance" to after,
                        "resultingProductBalance" to balance,
                        "transactionUnit" to mapOf("unitName" to "", "baseUnitsPerUnit" to 1L),
                        "transactionQuantity" to abs(op.delta),
                        "referenceNumber" to reference,
                        "reason" to (if (op.delta < 0L) "COUNT_SHORT" else "COUNT_OVER"),
                        "note" to "Stock check: counted ${c.countedBase}, expected ${c.expectedBase} (smallest unit)",
                        "valueCents" to value,
                        "createdAtMillis" to now,
                        "createdBy" to user,
                    ),
                )
            }
            running.forEach { (id, remaining) ->
                tx.update(database.collection(BATCHES).document(id), mapOf("remainingQuantity" to remaining))
            }
            tx.set(
                balanceRef,
                mapOf(
                    "productId" to c.productId,
                    "productNameSnapshot" to line.getString("productName").orEmpty(),
                    "quantityOnHand" to balance,
                    "updatedAtMillis" to now,
                ),
            )
            if (lossValue > 0L) {
                tx.set(
                    summaryRef,
                    mapOf(
                        "dateKey" to dateKey,
                        "writeOffCostCents" to FieldValue.increment(lossValue),
                        "lossByReason" to mapOf("COUNT_SHORT" to FieldValue.increment(lossValue)),
                        "adjustmentCount" to FieldValue.increment(1L),
                        "updatedAtMillis" to now,
                    ),
                    SetOptions.merge(),
                )
            } else {
                tx.set(
                    summaryRef,
                    mapOf("dateKey" to dateKey, "adjustmentCount" to FieldValue.increment(1L), "updatedAtMillis" to now),
                    SetOptions.merge(),
                )
            }
            tx.update(
                lineRef,
                mapOf(
                    "status" to CheckLineStatus.APPLIED,
                    "expectedBase" to c.expectedBase,
                    "differenceBase" to difference,
                    "valueCents" to totalValue,
                    "appliedAtMillis" to now,
                ),
            )
            true
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(friendlyMessage(it, "The stock could not be corrected.")) }
    }

    /** Closes a check: records what happened to the lines not fixed, and updates each product's last-checked date. */
    fun finishReview(
        checkId: String,
        updates: List<LineUpdate>,
        appliedLines: Int,
        skippedLines: Int,
        checkedAt: Map<String, Long>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (!CurrentUser.isAdmin) return onFailure("Only an administrator can review a stock check.")
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        val database = db
        val now = System.currentTimeMillis()
        val chunks = updates.chunked(400)
        fun write(index: Int) {
            if (index >= chunks.size) {
                val batch = database.batch()
                batch.update(
                    database.collection(CHECKS).document(checkId),
                    mapOf(
                        "status" to StockCheckStatus.REVIEWED,
                        "reviewedAtMillis" to now,
                        "reviewedBy" to user,
                        "appliedLines" to appliedLines,
                        "skippedLines" to skippedLines,
                    ),
                )
                batch.set(
                    database.collection(SETTINGS).document("stockCheckState"),
                    mapOf("lastChecked" to checkedAt, "updatedAtMillis" to now),
                    SetOptions.merge(),
                )
                batch.commit().addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(friendlyMessage(it, "The check could not be closed.")) }
                return
            }
            val batch = database.batch()
            chunks[index].forEach { u ->
                batch.update(
                    database.collection(CHECK_LINES).document(u.lineId),
                    mapOf(
                        "status" to u.status,
                        "expectedBase" to u.expectedBase,
                        "differenceBase" to u.differenceBase,
                        "valueCents" to u.valueCents,
                    ),
                )
            }
            batch.commit().addOnSuccessListener { write(index + 1) }
                .addOnFailureListener { onFailure(friendlyMessage(it, "The check could not be closed.")) }
        }
        write(0)
    }
}
