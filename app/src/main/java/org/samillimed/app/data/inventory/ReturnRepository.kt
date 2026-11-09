package org.samillimed.app.data.inventory

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.FacilityScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val DISPENSING_RECORDS: String get() = FacilityScope.path("dispensingRecords")
private val DISPENSING_LINES: String get() = FacilityScope.path("dispensingLines")
private val DISPENSING_SERVICE_LINES: String get() = FacilityScope.path("dispensingServiceLines")
private val DISPENSING_ALLOCATIONS: String get() = FacilityScope.path("dispensingBatchAllocations")
private val SALES_RETURNS: String get() = FacilityScope.path("salesReturns")
private val DAILY_SUMMARIES: String get() = FacilityScope.path("dailySummaries")
private val PRODUCT_DAY_STATS: String get() = FacilityScope.path("productDayStats")
private val PRODUCT_MONTH_STATS: String get() = FacilityScope.path("productMonthStats")
private val STOCK_BATCHES: String get() = FacilityScope.path("stockBatches")
private val STOCK_BALANCES: String get() = FacilityScope.path("stockBalances")
private val STOCK_MOVEMENTS: String get() = FacilityScope.path("stockMovements")

private const val MAX_LINES = 40

/** Where a bill stands after returns. */
object BillStatus {
    const val ACTIVE = "ACTIVE"
    const val PARTIALLY_RETURNED = "PARTIALLY_RETURNED"
    const val VOIDED = "VOIDED"
}

/** One line to return: quantity is in the unit the line was sold in (for a service, its quantity). */
class ReturnLineInput(
    val isService: Boolean,
    val lineId: String,
    val quantity: Long,
)

/** id stays the same for one return, so saving twice can never refund twice. */
class ReturnRequest(
    val id: String,
    val billId: String,
    val lines: List<ReturnLineInput>,
    /** Put the returned medicines back on the shelf (never into an expired batch). */
    val restock: Boolean,
    val refundMethod: String,
    val reason: String,
)

class ReturnResult(
    val refundCents: Long,
    /** True when every line of the bill has now been returned, so the bill is void. */
    val fullyReturned: Boolean,
)

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No internet connection. Returns change stock and totals, so they need a connection. Try again when you are back online."
        FirebaseFirestoreException.Code.ABORTED ->
            "Someone changed this at the same moment. Please try again."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Only an administrator can record returns."
        else -> error.message ?: fallback
    }

private fun ratio(amount: Long, numerator: Long, denominator: Long): Long =
    if (denominator <= 0L) {
        0L
    } else {
        BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(numerator))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .toLong()
    }

private fun DocumentSnapshot.longMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
        ?: emptyMap()

/** Returns and voids: reverses stock, cost and the day's totals in one step. Admin only, needs internet. */
class ReturnRepository {

    private val db: FirebaseFirestore get() = FacilityScope.firestore()

    fun recordReturn(
        request: ReturnRequest,
        onSuccess: (ReturnResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = FacilityScope.auth().currentUser?.email.orEmpty()
        val chosen = request.lines.filter { it.quantity > 0L }
        when {
            user.isBlank() -> return onFailure("Sign in before recording a return.")
            !CurrentUser.isAdmin -> return onFailure("Only an administrator can record returns.")
            request.id.isBlank() || request.billId.isBlank() -> return onFailure("The return has no ID.")
            chosen.isEmpty() -> return onFailure("Choose at least one item to return.")
            chosen.size > MAX_LINES -> return onFailure("Too many lines in one return. Split it into two.")
            request.refundMethod !in PaymentMethod.all -> return onFailure("Choose how the refund was paid.")
            request.reason.isBlank() -> return onFailure("Write the reason for the return.")
        }

        // A transaction cannot run a query, so the bill's lines are listed first; the transaction
        // then re-reads each one to be sure nothing changed.
        val database = db
        Tasks.whenAllSuccess<QuerySnapshot>(
            listOf(
                database.collection(DISPENSING_LINES).whereEqualTo("dispensingId", request.billId).get(),
                database.collection(DISPENSING_SERVICE_LINES).whereEqualTo("dispensingId", request.billId).get(),
                database.collection(DISPENSING_ALLOCATIONS).whereEqualTo("dispensingId", request.billId).get(),
            ),
        ).addOnSuccessListener { results ->
            commit(
                request = request,
                chosen = chosen,
                lineIds = results[0].documents.map { it.id },
                serviceLineIds = results[1].documents.map { it.id },
                allocIds = results[2].documents.map { it.id },
                user = user,
                onSuccess = onSuccess,
                onFailure = onFailure,
            )
        }.addOnFailureListener { error ->
            onFailure(friendlyMessage(error, "The bill could not be read."))
        }
    }

    private fun commit(
        request: ReturnRequest,
        chosen: List<ReturnLineInput>,
        lineIds: List<String>,
        serviceLineIds: List<String>,
        allocIds: List<String>,
        user: String,
        onSuccess: (ReturnResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val database = db
        val today = LocalDate.now()
        val todayKey = today.toString()
        val monthKey = todayKey.take(7)
        val now = System.currentTimeMillis()
        val billRef = database.collection(DISPENSING_RECORDS).document(request.billId)
        val returnRef = database.collection(SALES_RETURNS).document(request.id)
        val summaryRef = database.collection(DAILY_SUMMARIES).document(todayKey)

        database.runTransaction { tx ->

            // ---- reads: single documents only, all before any write ----------------------------
            val existing = tx.get(returnRef)
            if (existing.exists()) {
                // Already saved (for example the reply was lost): report it as done.
                return@runTransaction ReturnResult(
                    refundCents = existing.getLong("refundCents") ?: 0L,
                    fullyReturned = existing.getBoolean("fullVoid") ?: false,
                )
            }

            val bill = tx.get(billRef)
            if (!bill.exists()) throw IllegalArgumentException("This bill no longer exists.")
            val reference = bill.getString("referenceNumber").orEmpty()

            val lineSnaps = lineIds.associateWith { tx.get(database.collection(DISPENSING_LINES).document(it)) }
            val serviceSnaps = serviceLineIds.associateWith { tx.get(database.collection(DISPENSING_SERVICE_LINES).document(it)) }
            val allocSnaps = allocIds.associateWith { tx.get(database.collection(DISPENSING_ALLOCATIONS).document(it)) }
            val summarySnap = tx.get(summaryRef)

            val chosenProduct = chosen.filter { !it.isService }
            val chosenService = chosen.filter { it.isService }
            val chosenLineIds = chosenProduct.map { it.lineId }.toSet()
            val neededAllocs = allocSnaps.values.filter { it.getString("dispensingLineId") in chosenLineIds }

            val productIds = chosenProduct.mapNotNull { lineSnaps[it.lineId]?.getString("productId") }.distinct()
            val batchSnaps = neededAllocs.mapNotNull { it.getString("batchId") }.distinct()
                .associateWith { tx.get(database.collection(STOCK_BATCHES).document(it)) }
            val balanceSnaps = productIds.associateWith { tx.get(database.collection(STOCK_BALANCES).document(it)) }

            // ---- work out the return -----------------------------------------------------------
            val batchRunning = HashMap<String, Long>()
            batchSnaps.forEach { (id, snap) -> batchRunning[id] = snap.getLong("remainingQuantity") ?: 0L }
            val balanceRunning = HashMap<String, Long>()
            balanceSnaps.forEach { (id, snap) -> balanceRunning[id] = snap.getLong("quantityOnHand") ?: 0L }

            val touchedBatches = HashSet<String>()
            val touchedProducts = HashSet<String>()
            val productNames = HashMap<String, String>()
            val statBase = HashMap<String, Long>()
            val statSale = HashMap<String, Long>()
            val statCost = HashMap<String, Long>()
            val returnLines = ArrayList<Map<String, Any>>()

            var refundTotal = 0L
            var productRefund = 0L
            var serviceRefund = 0L
            var restockedCost = 0L

            chosenProduct.forEach { c ->
                val line = lineSnaps[c.lineId]
                if (line == null || !line.exists() || line.getString("dispensingId") != request.billId) {
                    throw IllegalArgumentException("A chosen line does not belong to this bill.")
                }
                val name = line.getString("productNameSnapshot").orEmpty()
                val productId = line.getString("productId").orEmpty()
                val original = line.getLong("quantityDispensed") ?: 0L
                val left = original - (line.getLong("returnedQuantity") ?: 0L)
                if (c.quantity > left) throw IllegalArgumentException("Only $left of $name can still be returned.")

                val unit = line.get("dispensingUnit") as? Map<*, *>
                val unitName = (unit?.get("unitName") as? String).orEmpty()
                val unitBase = (unit?.get("baseUnitsPerUnit") as? Number)?.toLong() ?: 1L
                val returnBase = c.quantity * unitBase

                val lineTotal = line.getLong("totalSaleValueCents") ?: 0L
                val alreadyRefunded = line.getLong("returnedCents") ?: 0L
                val refund = if (c.quantity == left) lineTotal - alreadyRefunded else ratio(lineTotal, c.quantity, original)

                // Give the stock back to the batches it came from, latest expiry first.
                var need = returnBase
                var lineRestockedBase = 0L
                var lineRestockedCost = 0L
                val allocations = neededAllocs
                    .filter { it.getString("dispensingLineId") == c.lineId }
                    .sortedByDescending { it.getLong("expiryDateMillis") ?: Long.MAX_VALUE }
                for (a in allocations) {
                    if (need == 0L) break
                    val deducted = a.getLong("quantityDeducted") ?: 0L
                    val returnable = deducted - (a.getLong("returnedQuantity") ?: 0L)
                    if (returnable <= 0L) continue
                    val take = minOf(returnable, need)
                    val allocCost = a.getLong("allocatedPurchaseCostCents") ?: 0L
                    val returnedCost = a.getLong("returnedCostCents") ?: 0L
                    val costPart = if (take == returnable) allocCost - returnedCost else ratio(allocCost, take, deducted)
                    need -= take

                    tx.update(
                        a.reference,
                        mapOf(
                            "returnedQuantity" to FieldValue.increment(take),
                            "returnedCostCents" to FieldValue.increment(costPart),
                        ),
                    )

                    val batchId = a.getString("batchId").orEmpty()
                    val canRestock = request.restock &&
                        batchRunning.containsKey(batchId) &&
                        !isExpired(a.getLong("expiryDateMillis"), today)
                    if (canRestock) {
                        val newBatch = batchRunning.getValue(batchId) + take
                        batchRunning[batchId] = newBatch
                        touchedBatches.add(batchId)
                        val newProduct = (balanceRunning[productId] ?: 0L) + take
                        balanceRunning[productId] = newProduct
                        touchedProducts.add(productId)
                        lineRestockedBase += take
                        lineRestockedCost += costPart

                        val movementRef = database.collection(STOCK_MOVEMENTS).document()
                        tx.set(
                            movementRef,
                            mapOf(
                                "id" to movementRef.id,
                                "productId" to productId,
                                "batchId" to batchId,
                                "receiptId" to "",
                                "receiptLineId" to "",
                                "dispensingId" to request.billId,
                                "dispensingLineId" to c.lineId,
                                "movementType" to StockMovementType.RETURN,
                                "quantityChange" to take,
                                "resultingBatchBalance" to newBatch,
                                "resultingProductBalance" to newProduct,
                                "transactionUnit" to mapOf("unitName" to unitName, "baseUnitsPerUnit" to unitBase),
                                "transactionQuantity" to c.quantity,
                                "referenceNumber" to reference,
                                "reason" to "RETURN",
                                "note" to request.reason.trim(),
                                "valueCents" to costPart,
                                "createdAtMillis" to now,
                                "createdBy" to user,
                            ),
                        )
                    }
                }
                if (need > 0L) {
                    throw IllegalArgumentException("The stock records of $name are incomplete, so it cannot be returned.")
                }

                tx.update(
                    line.reference,
                    mapOf(
                        "returnedQuantity" to FieldValue.increment(c.quantity),
                        "returnedCents" to FieldValue.increment(refund),
                    ),
                )
                refundTotal += refund
                productRefund += refund
                restockedCost += lineRestockedCost
                productNames[productId] = name
                statBase[productId] = (statBase[productId] ?: 0L) + returnBase
                statSale[productId] = (statSale[productId] ?: 0L) + refund
                statCost[productId] = (statCost[productId] ?: 0L) + lineRestockedCost
                returnLines.add(
                    mapOf(
                        "kind" to "PRODUCT",
                        "lineId" to c.lineId,
                        "name" to name,
                        "quantity" to c.quantity,
                        "unitName" to unitName,
                        "refundCents" to refund,
                        "restockedBase" to lineRestockedBase,
                        "restockedCostCents" to lineRestockedCost,
                    ),
                )
            }

            chosenService.forEach { c ->
                val line = serviceSnaps[c.lineId]
                if (line == null || !line.exists() || line.getString("dispensingId") != request.billId) {
                    throw IllegalArgumentException("A chosen service does not belong to this bill.")
                }
                val name = line.getString("serviceNameSnapshot").orEmpty()
                val original = line.getLong("quantity") ?: 0L
                val left = original - (line.getLong("returnedQuantity") ?: 0L)
                if (c.quantity > left) throw IllegalArgumentException("Only $left of $name can still be returned.")
                val lineTotal = line.getLong("totalCents") ?: 0L
                val alreadyRefunded = line.getLong("returnedCents") ?: 0L
                val refund = if (c.quantity == left) lineTotal - alreadyRefunded else ratio(lineTotal, c.quantity, original)

                tx.update(
                    line.reference,
                    mapOf(
                        "returnedQuantity" to FieldValue.increment(c.quantity),
                        "returnedCents" to FieldValue.increment(refund),
                    ),
                )
                refundTotal += refund
                serviceRefund += refund
                returnLines.add(
                    mapOf(
                        "kind" to "SERVICE",
                        "lineId" to c.lineId,
                        "name" to name,
                        "quantity" to c.quantity,
                        "unitName" to "",
                        "refundCents" to refund,
                        "restockedBase" to 0L,
                        "restockedCostCents" to 0L,
                    ),
                )
            }

            // Is every line of the bill now fully returned? Then the bill is void.
            val productsDone = lineSnaps.all { (id, s) ->
                (s.getLong("returnedQuantity") ?: 0L) + chosenProduct.filter { it.lineId == id }.sumOf { it.quantity } ==
                    (s.getLong("quantityDispensed") ?: 0L)
            }
            val servicesDone = serviceSnaps.all { (id, s) ->
                (s.getLong("returnedQuantity") ?: 0L) + chosenService.filter { it.lineId == id }.sumOf { it.quantity } ==
                    (s.getLong("quantity") ?: 0L)
            }
            val fullyReturned = productsDone && servicesDone

            // ---- writes ------------------------------------------------------------------------
            touchedBatches.forEach { id ->
                tx.update(
                    database.collection(STOCK_BATCHES).document(id),
                    mapOf("remainingQuantity" to batchRunning.getValue(id)),
                )
            }
            touchedProducts.forEach { id ->
                tx.set(
                    database.collection(STOCK_BALANCES).document(id),
                    mapOf(
                        "productId" to id,
                        "productNameSnapshot" to (productNames[id] ?: ""),
                        "quantityOnHand" to balanceRunning.getValue(id),
                        "updatedAtMillis" to now,
                    ),
                )
            }

            // Product totals that reports read: units and sales fall; cost falls only for restocked goods.
            statBase.keys.forEach { id ->
                val sale = statSale[id] ?: 0L
                val cost = statCost[id] ?: 0L
                val stats = mapOf<String, Any>(
                    "productId" to id,
                    "productName" to (productNames[id] ?: ""),
                    "unitsSoldBase" to FieldValue.increment(-(statBase[id] ?: 0L)),
                    "saleCents" to FieldValue.increment(-sale),
                    "costCents" to FieldValue.increment(-cost),
                    "profitCents" to FieldValue.increment(-(sale - cost)),
                )
                tx.set(
                    database.collection(PRODUCT_DAY_STATS).document("${todayKey}_$id"),
                    stats + ("dateKey" to todayKey),
                    SetOptions.merge(),
                )
                tx.set(
                    database.collection(PRODUCT_MONTH_STATS).document("${monthKey}_$id"),
                    stats + ("monthKey" to monthKey),
                    SetOptions.merge(),
                )
            }

            // The day's totals: the refund counts on the day it is paid out.
            val payments = summarySnap.longMap("payments")
            tx.set(
                summaryRef,
                mapOf(
                    "dateKey" to todayKey,
                    "totalSalesCents" to ((summarySnap.getLong("totalSalesCents") ?: 0L) - refundTotal),
                    "productSalesCents" to ((summarySnap.getLong("productSalesCents") ?: 0L) - productRefund),
                    "serviceSalesCents" to ((summarySnap.getLong("serviceSalesCents") ?: 0L) - serviceRefund),
                    "costCents" to ((summarySnap.getLong("costCents") ?: 0L) - restockedCost),
                    "profitCents" to ((summarySnap.getLong("profitCents") ?: 0L) - (refundTotal - restockedCost)),
                    "payments" to (payments + (request.refundMethod to ((payments[request.refundMethod] ?: 0L) - refundTotal))),
                    "refundCents" to ((summarySnap.getLong("refundCents") ?: 0L) + refundTotal),
                    "refundCount" to ((summarySnap.getLong("refundCount") ?: 0L) + 1L),
                    "updatedAtMillis" to now,
                ),
                SetOptions.merge(),
            )

            val billUpdate = hashMapOf<String, Any>(
                "status" to (if (fullyReturned) BillStatus.VOIDED else BillStatus.PARTIALLY_RETURNED),
                "returnedCents" to ((bill.getLong("returnedCents") ?: 0L) + refundTotal),
                "lastReturnAtMillis" to now,
            )
            if (fullyReturned) {
                billUpdate["voidedAtMillis"] = now
                billUpdate["voidedBy"] = user
                billUpdate["voidReason"] = request.reason.trim()
            }
            tx.update(billRef, billUpdate)

            tx.set(
                returnRef,
                mapOf(
                    "id" to request.id,
                    "billId" to request.billId,
                    "billReference" to reference,
                    "dateKey" to todayKey,
                    "createdAtMillis" to now,
                    "createdBy" to user,
                    "reason" to request.reason.trim(),
                    "refundMethod" to request.refundMethod,
                    "refundCents" to refundTotal,
                    "restocked" to request.restock,
                    "restockedCostCents" to restockedCost,
                    "fullVoid" to fullyReturned,
                    "lines" to returnLines,
                ),
            )

            ReturnResult(refundCents = refundTotal, fullyReturned = fullyReturned)
        }.addOnSuccessListener { result ->
            onSuccess(result)
        }.addOnFailureListener { error ->
            onFailure(friendlyMessage(error, "The return could not be saved."))
        }
    }
}
