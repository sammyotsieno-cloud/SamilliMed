package org.samillimed.app.data.inventory

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import org.samillimed.app.data.shifts.ShiftContext
import org.samillimed.app.data.shifts.ShiftResolver
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

private const val DISPENSING_RECORDS = "dispensingRecords"
private const val DISPENSING_LINES = "dispensingLines"
private const val DISPENSING_SERVICE_LINES = "dispensingServiceLines"
private const val DISPENSING_ALLOCATIONS = "dispensingBatchAllocations"
private const val DAILY_SUMMARIES = "dailySummaries"
private const val PRODUCT_DAY_STATS = "productDayStats"
private const val PRODUCT_MONTH_STATS = "productMonthStats"
private const val PRODUCTS = "products"
private const val SERVICES = "services"
private const val STOCK_BATCHES = "stockBatches"
private const val STOCK_BALANCES = "stockBalances"
private const val STOCK_MOVEMENTS = "stockMovements"
private const val STOCK_RECONCILIATIONS = "stockReconciliations"

private const val MAX_WRITES = 480

// ---- what the screen sends ----------------------------------------------------------------

/** A medicine or item on the bill. priceCents is per the unit that was chosen. */
class DispenseProductInput(
    val productId: String,
    val productName: String,
    val productType: String,
    val quantity: Long,
    val unitName: String,
    val baseUnitsPerUnit: Long,
    val priceCents: Long,
)

class DispenseServiceInput(
    val serviceId: String,
    val serviceName: String,
    val category: String,
    val department: String,
    val quantity: Long,
    val priceCents: Long,
)

/** id stays the same for one bill, so saving twice can never create two bills. */
class DispenseRequest(
    val id: String,
    val products: List<DispenseProductInput>,
    val services: List<DispenseServiceInput>,
    val paymentMethod: String,
    val amountReceivedCents: Long,
    val label: String,
)

class DispenseResult(
    val id: String,
    val reference: String,
    val shortLabel: String,
    val dayNumber: Int,
    val shiftNumber: Int,
    val totalCents: Long,
    val changeCents: Long,
)

// ---- helpers ------------------------------------------------------------------------------

private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No internet connection. Bills change stock, so they need a connection to keep every " +
                "device in step. Try again when you are back online."
        FirebaseFirestoreException.Code.ABORTED ->
            "Someone changed this stock at the same moment. Please save again."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do this. Sign in again."
        else -> error.message ?: fallback
    }

/** amount x numerator / denominator, rounded to the nearest cent. */
private fun ratioCents(amountCents: Long, numerator: Long, denominator: Long): Long =
    BigDecimal.valueOf(amountCents)
        .multiply(BigDecimal.valueOf(numerator))
        .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
        .toLong()

/** A map field of numbers (for example payments) read from a document. */
private fun DocumentSnapshot.longMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
        ?: emptyMap()

/** A map field of text (for example shift names) read from a document. */
private fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to it.value.toString() }
        ?: emptyMap()

private class Prepared(
    val productLineTotals: List<Long>,
    val serviceLineTotals: List<Long>,
    val totalCents: Long,
    val receivedCents: Long,
    val changeCents: Long,
)

/** Checks the bill and works out its totals. Throws IllegalArgumentException with a message. */
private fun prepare(request: DispenseRequest): Prepared {
    request.products.forEach { p ->
        if (p.productId.isBlank()) throw IllegalArgumentException("Every item must be a registered product.")
        if (p.unitName.isBlank() || p.baseUnitsPerUnit <= 0L) {
            throw IllegalArgumentException("Choose a unit for ${p.productName}.")
        }
        if (p.quantity <= 0L) throw IllegalArgumentException("Quantities must be above zero.")
        if (p.priceCents <= 0L) throw IllegalArgumentException("Enter a selling price for ${p.productName}.")
    }
    request.services.forEach { s ->
        if (s.serviceId.isBlank()) throw IllegalArgumentException("Every service must be a registered service.")
        if (s.quantity <= 0L) throw IllegalArgumentException("Quantities must be above zero.")
        if (s.priceCents <= 0L) throw IllegalArgumentException("Enter a price for ${s.serviceName}.")
    }

    val productTotals = request.products.map {
        InventoryCalculations.calculateDispensingLineTotalCents(it.quantity, it.priceCents)
            ?: throw IllegalArgumentException("The price or quantity of ${it.productName} is too large.")
    }
    val serviceTotals = request.services.map {
        InventoryCalculations.calculateDispensingLineTotalCents(it.quantity, it.priceCents)
            ?: throw IllegalArgumentException("The price or quantity of ${it.serviceName} is too large.")
    }
    val total = InventoryCalculations.calculateDispensingTotalCents(productTotals + serviceTotals)
        ?: throw IllegalArgumentException("The bill total could not be calculated.")

    if (request.paymentMethod !in PaymentMethod.all) {
        throw IllegalArgumentException("Choose how the bill was paid.")
    }
    val received: Long
    val change: Long
    if (request.paymentMethod == PaymentMethod.CASH) {
        if (request.amountReceivedCents < total) {
            throw IllegalArgumentException("The amount received is less than the bill total.")
        }
        received = request.amountReceivedCents
        change = received - total
    } else {
        received = total
        change = 0L
    }
    return Prepared(productTotals, serviceTotals, total, received, change)
}

/** A batch as read before the transaction (the transaction re-reads it to be sure). */
private class BatchInfo(
    val id: String,
    val batchNumber: String,
    val expiryMillis: Long?,
    val remaining: Long,
    val receivedAtMillis: Long,
    val receivingUnitName: String,
    val baseUnitsPerReceivingUnit: Long,
    val costCents: Long,
    val sellingCents: Long,
)

private fun DocumentSnapshot.toBatchInfo(): BatchInfo = BatchInfo(
    id = id,
    batchNumber = getString("batchNumber").orEmpty(),
    expiryMillis = getLong("expiryDateMillis"),
    remaining = getLong("remainingQuantity") ?: 0L,
    receivedAtMillis = getLong("receivedAtMillis") ?: 0L,
    receivingUnitName = getString("receivingUnitName").orEmpty(),
    baseUnitsPerReceivingUnit = getLong("baseUnitsPerReceivingUnit") ?: 1L,
    costCents = getLong("purchaseCostPerReceivingUnitCents") ?: 0L,
    sellingCents = getLong("sellingPricePerReceivingUnitCents") ?: 0L,
)

/** Part of one bill line that is taken from one batch. */
private class PlannedAllocation(
    val lineIndex: Int,
    val batch: BatchInfo,
    val quantity: Long,
)

/**
 * Decides which batches each line is taken from: the batch that expires soonest first, expired
 * batches skipped, several batches if needed. Lines of the same product share the same stock.
 */
private fun planAllocations(
    request: DispenseRequest,
    batchesByProduct: Map<String, List<BatchInfo>>,
    todayStartMillis: Long,
): List<PlannedAllocation> {
    val remaining = HashMap<String, Long>()
    val plan = mutableListOf<PlannedAllocation>()

    request.products.forEachIndexed { index, input ->
        val baseQuantity = InventoryCalculations.toBaseQuantity(input.quantity, input.baseUnitsPerUnit)
            ?: throw IllegalArgumentException("Invalid quantity for ${input.productName}.")
        var need = baseQuantity

        val candidates = (batchesByProduct[input.productId] ?: emptyList())
            .filter { (it.expiryMillis ?: Long.MAX_VALUE) >= todayStartMillis }
            .sortedWith(compareBy<BatchInfo> { it.expiryMillis ?: Long.MAX_VALUE }.thenBy { it.receivedAtMillis })

        for (batch in candidates) {
            if (need == 0L) break
            val available = remaining.getOrPut(batch.id) { batch.remaining }
            if (available <= 0L) continue
            val take = minOf(available, need)
            remaining[batch.id] = available - take
            need -= take
            plan.add(PlannedAllocation(index, batch, take))
        }

        if (need > 0L) {
            throw IllegalArgumentException(
                "Not enough unexpired stock for ${input.productName} " +
                    "(short by $need in the smallest unit).",
            )
        }
    }
    return plan
}

/** Saves bills: medicines and services together, with stock, cost and profit, in one step. */
class DispensingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun currentUser(): String =
        FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    /**
     * Saves one bill atomically: the bill, its lines and batch allocations, the stock taken from
     * each batch, the stock movements, the product balances, the visit numbers, and the totals
     * that reports read (the day summary and the product day and month totals).
     * Needs an internet connection.
     */
    fun saveDispensing(
        request: DispenseRequest,
        onSuccess: (DispenseResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = currentUser()
        if (user.isBlank()) {
            onFailure("Sign in before dispensing.")
            return
        }
        if (request.id.isBlank()) {
            onFailure("The bill has no ID.")
            return
        }
        if (request.products.isEmpty() && request.services.isEmpty()) {
            onFailure("Add at least one item or service to the bill.")
            return
        }

        val prepared = try {
            prepare(request)
        } catch (e: IllegalArgumentException) {
            onFailure(e.message ?: "The bill contains invalid information.")
            return
        }

        val shiftContext = ShiftResolver.current()
        val todayStartMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val productIds = request.products.map { it.productId }.distinct()

        // A transaction cannot run a query, so the batches are read first. The transaction then
        // re-reads every batch it will change and refuses to continue if the stock moved.
        val loadBatches = if (productIds.isEmpty()) {
            Tasks.forResult<List<QuerySnapshot>>(emptyList())
        } else {
            Tasks.whenAllSuccess<QuerySnapshot>(
                productIds.map { id -> db.collection(STOCK_BATCHES).whereEqualTo("productId", id).get() },
            )
        }

        loadBatches
            .addOnSuccessListener { snapshots ->
                val batchesByProduct = productIds.indices.associate { i ->
                    productIds[i] to snapshots[i].documents.map { it.toBatchInfo() }
                }
                val plan = try {
                    planAllocations(request, batchesByProduct, todayStartMillis)
                } catch (e: IllegalArgumentException) {
                    onFailure(e.message ?: "The stock could not be allocated.")
                    return@addOnSuccessListener
                }
                commit(request, prepared, plan, shiftContext, user, onSuccess, onFailure)
            }
            .addOnFailureListener { error ->
                onFailure(friendlyMessage(error, "The stock could not be checked."))
            }
    }

    private fun commit(
        request: DispenseRequest,
        prepared: Prepared,
        plan: List<PlannedAllocation>,
        shiftContext: ShiftContext,
        user: String,
        onSuccess: (DispenseResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val productIds = request.products.map { it.productId }.distinct()
        val serviceIds = request.services.map { it.serviceId }.distinct()
        val batchIds = plan.map { it.batch.id }.distinct()

        // record + day summary, every line, 2 per allocation (allocation + movement), every batch,
        // and per product: balance + audit + day stats + month stats
        val writes = 2 + request.products.size + request.services.size + plan.size * 2 +
            batchIds.size + productIds.size * 4
        if (writes > MAX_WRITES) {
            onFailure("This bill has too many lines to save in one step. Split it into two bills.")
            return
        }

        val now = System.currentTimeMillis()
        val dateKey = shiftContext.dateKey
        val monthKey = dateKey.take(7)
        val recordRef = db.collection(DISPENSING_RECORDS).document(request.id)
        val summaryRef = db.collection(DAILY_SUMMARIES).document(dateKey)

        db.runTransaction { tx ->

            // ---- reads: single documents only, all before any write --------------------------
            val existing = tx.get(recordRef)
            if (existing.exists()) {
                // This bill was already saved (for example the reply was lost): report it as done.
                return@runTransaction DispenseResult(
                    id = existing.id,
                    reference = existing.getString("referenceNumber").orEmpty(),
                    shortLabel = visitShortLabel(
                        existing.getString("shiftCode").orEmpty(),
                        (existing.getLong("shiftNumber") ?: 0L).toInt(),
                    ),
                    dayNumber = (existing.getLong("dayNumber") ?: 0L).toInt(),
                    shiftNumber = (existing.getLong("shiftNumber") ?: 0L).toInt(),
                    totalCents = existing.getLong("totalAmountCents") ?: 0L,
                    changeCents = existing.getLong("changeGivenCents") ?: 0L,
                )
            }

            val summarySnap = tx.get(summaryRef)
            val productSnaps = productIds.associateWith { tx.get(db.collection(PRODUCTS).document(it)) }
            val balanceSnaps = productIds.associateWith { tx.get(db.collection(STOCK_BALANCES).document(it)) }
            val serviceSnaps = serviceIds.associateWith { tx.get(db.collection(SERVICES).document(it)) }
            val batchSnaps = batchIds.associateWith { tx.get(db.collection(STOCK_BATCHES).document(it)) }

            // ---- checks against the live catalogue --------------------------------------------
            productIds.forEach { id ->
                val snap = productSnaps.getValue(id)
                if (!snap.exists()) throw IllegalArgumentException("A product on this bill is no longer registered.")
                if (!(snap.getBoolean("active") ?: true)) {
                    throw IllegalArgumentException("${snap.getString("name").orEmpty()} is inactive and cannot be sold.")
                }
            }
            request.products.forEach { p ->
                val units = (productSnaps.getValue(p.productId).get("units") as? List<*>)
                    ?.filterIsInstance<Map<*, *>>().orEmpty()
                val unit = units.firstOrNull { (it["name"] as? String)?.equals(p.unitName, ignoreCase = true) == true }
                    ?: throw IllegalArgumentException("${p.productName} does not have ${p.unitName} as a registered unit.")
                val catalogueBase = (unit["baseQty"] as? Number)?.toLong()
                if (catalogueBase == null || catalogueBase != p.baseUnitsPerUnit) {
                    throw IllegalArgumentException(
                        "The unit conversion for ${p.productName} has changed. Refresh and try again.",
                    )
                }
            }
            request.services.forEach { s ->
                val snap = serviceSnaps.getValue(s.serviceId)
                if (!snap.exists()) throw IllegalArgumentException("A service on this bill is no longer registered.")
                if (!(snap.getBoolean("active") ?: true)) {
                    throw IllegalArgumentException("${s.serviceName} is inactive and cannot be billed.")
                }
            }

            // The stock each batch really holds right now
            val batchRunning = HashMap<String, Long>()
            batchIds.forEach { id ->
                val snap = batchSnaps.getValue(id)
                if (!snap.exists()) throw IllegalArgumentException("Stock changed while you were billing. Please try again.")
                batchRunning[id] = snap.getLong("remainingQuantity") ?: 0L
            }

            // ---- visit numbers and the day's running totals --------------------------------------
            val shiftId = shiftContext.shift.id
            val dayNumber = (summarySnap.getLong("dayCount") ?: 0L) + 1L
            val shiftCounts = summarySnap.longMap("shiftCounts")
            val shiftSales = summarySnap.longMap("shiftSales")
            val shiftNames = summarySnap.stringMap("shiftNames")
            val shiftCodes = summarySnap.stringMap("shiftCodes")
            val payments = summarySnap.longMap("payments")
            val shiftNumber = (shiftCounts[shiftId] ?: 0L) + 1L
            val reference = visitReference(dateKey, shiftContext.shift.code, shiftNumber.toInt())
            val shortLabel = visitShortLabel(shiftContext.shift.code, shiftNumber.toInt())

            // ---- writes ---------------------------------------------------------------------------
            val balanceRunning = HashMap<String, Long>()
            productIds.forEach { id ->
                balanceRunning[id] = balanceSnaps.getValue(id).getLong("quantityOnHand") ?: 0L
            }
            val balanceMismatch = HashMap<String, Long>() // product -> recorded balance that was too low
            var totalCostCents = 0L

            // per product: what this bill adds to the product totals that reports read
            val statBase = HashMap<String, Long>()
            val statSale = HashMap<String, Long>()
            val statCost = HashMap<String, Long>()

            request.products.forEachIndexed { index, p ->
                val lineTotal = prepared.productLineTotals[index]
                val baseQuantity = InventoryCalculations.toBaseQuantity(p.quantity, p.baseUnitsPerUnit)
                    ?: throw IllegalArgumentException("Invalid quantity for ${p.productName}.")
                val allocations = plan.filter { it.lineIndex == index }
                val lineRef = db.collection(DISPENSING_LINES).document()

                var lineCost = 0L
                var saleAssigned = 0L

                allocations.forEachIndexed { k, allocation ->
                    val batch = allocation.batch
                    val allocationRef = db.collection(DISPENSING_ALLOCATIONS).document()
                    val movementRef = db.collection(STOCK_MOVEMENTS).document()

                    val newBatchBalance = InventoryCalculations.deductDispensedStock(
                        currentBalance = batchRunning.getValue(batch.id),
                        dispensedBaseQuantity = allocation.quantity,
                    ) ?: throw IllegalArgumentException("Stock changed while you were billing. Please try again.")
                    batchRunning[batch.id] = newBatchBalance

                    val recorded = balanceRunning.getValue(p.productId)
                    val newProductBalance = if (recorded >= allocation.quantity) {
                        recorded - allocation.quantity
                    } else {
                        balanceMismatch.putIfAbsent(p.productId, recorded)
                        0L
                    }
                    balanceRunning[p.productId] = newProductBalance

                    val costPart = ratioCents(
                        batch.costCents,
                        allocation.quantity,
                        batch.baseUnitsPerReceivingUnit,
                    )
                    val salePart = if (k == allocations.lastIndex) {
                        lineTotal - saleAssigned
                    } else {
                        ratioCents(lineTotal, allocation.quantity, baseQuantity)
                    }
                    saleAssigned += salePart
                    lineCost += costPart

                    tx.set(
                        allocationRef,
                        mapOf(
                            "id" to allocationRef.id,
                            "dispensingId" to recordRef.id,
                            "dispensingLineId" to lineRef.id,
                            "productId" to p.productId,
                            "batchId" to batch.id,
                            "batchNumberSnapshot" to batch.batchNumber,
                            "expiryDateMillis" to batch.expiryMillis,
                            "quantityDeducted" to allocation.quantity,
                            "receivingUnitName" to batch.receivingUnitName,
                            "purchaseCostPerReceivingUnitCents" to batch.costCents,
                            "sellingPricePerReceivingUnitCents" to batch.sellingCents,
                            "baseUnitsPerReceivingUnit" to batch.baseUnitsPerReceivingUnit,
                            "allocatedPurchaseCostCents" to costPart,
                            "allocatedSaleValueCents" to salePart,
                            "allocatedProfitCents" to (salePart - costPart),
                            "resultingBatchBalance" to newBatchBalance,
                            "createdAtMillis" to now,
                        ),
                    )
                    tx.set(
                        movementRef,
                        mapOf(
                            "id" to movementRef.id,
                            "productId" to p.productId,
                            "batchId" to batch.id,
                            "receiptId" to "",
                            "receiptLineId" to "",
                            "dispensingId" to recordRef.id,
                            "dispensingLineId" to lineRef.id,
                            "movementType" to StockMovementType.DISPENSING,
                            "quantityChange" to -allocation.quantity,
                            "resultingBatchBalance" to newBatchBalance,
                            "resultingProductBalance" to newProductBalance,
                            "transactionUnit" to mapOf(
                                "unitName" to p.unitName,
                                "baseUnitsPerUnit" to p.baseUnitsPerUnit,
                            ),
                            "transactionQuantity" to p.quantity,
                            "referenceNumber" to reference,
                            "createdAtMillis" to now,
                            "createdBy" to user,
                        ),
                    )
                }

                totalCostCents += lineCost
                statBase[p.productId] = (statBase[p.productId] ?: 0L) + baseQuantity
                statSale[p.productId] = (statSale[p.productId] ?: 0L) + lineTotal
                statCost[p.productId] = (statCost[p.productId] ?: 0L) + lineCost

                tx.set(
                    lineRef,
                    mapOf(
                        "id" to lineRef.id,
                        "dispensingId" to recordRef.id,
                        "productId" to p.productId,
                        "productNameSnapshot" to p.productName,
                        "quantityDispensed" to p.quantity,
                        "dispensingUnit" to mapOf(
                            "unitName" to p.unitName,
                            "baseUnitsPerUnit" to p.baseUnitsPerUnit,
                        ),
                        "baseQuantityDispensed" to baseQuantity,
                        "actualSellingPricePerUnitCents" to p.priceCents,
                        "totalSaleValueCents" to lineTotal,
                        "totalPurchaseCostCents" to lineCost,
                        "grossProfitCents" to (lineTotal - lineCost),
                        "createdAtMillis" to now,
                    ),
                )
            }

            request.services.forEachIndexed { index, s ->
                val serviceLineRef = db.collection(DISPENSING_SERVICE_LINES).document()
                tx.set(
                    serviceLineRef,
                    mapOf(
                        "id" to serviceLineRef.id,
                        "dispensingId" to recordRef.id,
                        "serviceId" to s.serviceId,
                        "serviceNameSnapshot" to s.serviceName,
                        "categorySnapshot" to s.category,
                        "departmentSnapshot" to s.department,
                        "quantity" to s.quantity,
                        "priceCents" to s.priceCents,
                        "totalCents" to prepared.serviceLineTotals[index],
                        "createdAtMillis" to now,
                    ),
                )
            }

            batchRunning.forEach { (id, remaining) ->
                tx.update(db.collection(STOCK_BATCHES).document(id), mapOf("remainingQuantity" to remaining))
            }

            productIds.forEach { id ->
                tx.set(
                    db.collection(STOCK_BALANCES).document(id),
                    mapOf(
                        "productId" to id,
                        "productNameSnapshot" to (productSnaps.getValue(id).getString("name") ?: ""),
                        "quantityOnHand" to balanceRunning.getValue(id),
                        "updatedAtMillis" to now,
                    ),
                )
            }
            // A recorded balance that was lower than the stock sold is clamped to 0 and noted,
            // so a bill is never blocked by a drifted counter; the batches stay the source of truth.
            balanceMismatch.forEach { (id, recorded) ->
                val auditRef = db.collection(STOCK_RECONCILIATIONS).document()
                tx.set(
                    auditRef,
                    mapOf(
                        "id" to auditRef.id,
                        "productId" to id,
                        "recordedBalance" to recorded,
                        "batchDerivedBalance" to 0L,
                        "correctedBalance" to 0L,
                        "difference" to -recorded,
                        "reason" to "DISPENSING_BALANCE_MISMATCH",
                        "createdAtMillis" to now,
                        "createdBy" to user,
                    ),
                )
            }

            // ---- totals that reports read (increments, so bills never overwrite each other) ----
            productIds.forEach { id ->
                val sale = statSale[id] ?: 0L
                val cost = statCost[id] ?: 0L
                val stats = mapOf<String, Any>(
                    "productId" to id,
                    "productName" to (productSnaps.getValue(id).getString("name") ?: ""),
                    "unitsSoldBase" to FieldValue.increment(statBase[id] ?: 0L),
                    "saleCents" to FieldValue.increment(sale),
                    "costCents" to FieldValue.increment(cost),
                    "profitCents" to FieldValue.increment(sale - cost),
                    "lineCount" to FieldValue.increment(1L),
                )
                tx.set(
                    db.collection(PRODUCT_DAY_STATS).document("${dateKey}_$id"),
                    stats + ("dateKey" to dateKey),
                    SetOptions.merge(),
                )
                tx.set(
                    db.collection(PRODUCT_MONTH_STATS).document("${monthKey}_$id"),
                    stats + ("monthKey" to monthKey),
                    SetOptions.merge(),
                )
            }

            val productTotal = prepared.productLineTotals.sum()
            val serviceTotal = prepared.serviceLineTotals.sum()
            val method = request.paymentMethod
            tx.set(
                summaryRef,
                mapOf(
                    "dateKey" to dateKey,
                    "dayCount" to dayNumber,
                    "shiftCounts" to (shiftCounts + (shiftId to shiftNumber)),
                    "shiftSales" to (shiftSales + (shiftId to ((shiftSales[shiftId] ?: 0L) + prepared.totalCents))),
                    "shiftNames" to (shiftNames + (shiftId to shiftContext.shift.name)),
                    "shiftCodes" to (shiftCodes + (shiftId to shiftContext.shift.code)),
                    "payments" to (payments + (method to ((payments[method] ?: 0L) + prepared.totalCents))),
                    "totalSalesCents" to ((summarySnap.getLong("totalSalesCents") ?: 0L) + prepared.totalCents),
                    "productSalesCents" to ((summarySnap.getLong("productSalesCents") ?: 0L) + productTotal),
                    "serviceSalesCents" to ((summarySnap.getLong("serviceSalesCents") ?: 0L) + serviceTotal),
                    "costCents" to ((summarySnap.getLong("costCents") ?: 0L) + totalCostCents),
                    "profitCents" to ((summarySnap.getLong("profitCents") ?: 0L) + (prepared.totalCents - totalCostCents)),
                    "afterShiftBills" to ((summarySnap.getLong("afterShiftBills") ?: 0L) + if (shiftContext.afterShift) 1L else 0L),
                    "updatedAtMillis" to now,
                ),
                SetOptions.merge(),
            )

            tx.set(
                recordRef,
                mapOf(
                    "id" to recordRef.id,
                    "referenceNumber" to reference,
                    "dateKey" to dateKey,
                    "shiftId" to shiftId,
                    "shiftCode" to shiftContext.shift.code,
                    "shiftName" to shiftContext.shift.name,
                    "shiftNumber" to shiftNumber,
                    "dayNumber" to dayNumber,
                    "afterShift" to shiftContext.afterShift,
                    "paymentMethod" to request.paymentMethod,
                    "amountReceivedCents" to prepared.receivedCents,
                    "changeGivenCents" to prepared.changeCents,
                    "label" to request.label.trim(),
                    "dispensedAtMillis" to now,
                    "createdAtMillis" to now,
                    "createdBy" to user,
                    "productTotalCents" to productTotal,
                    "serviceTotalCents" to serviceTotal,
                    "totalAmountCents" to prepared.totalCents,
                    "totalPurchaseCostCents" to totalCostCents,
                    "grossProfitCents" to (prepared.totalCents - totalCostCents),
                    "productLineCount" to request.products.size,
                    "serviceLineCount" to request.services.size,
                    "lineCount" to (request.products.size + request.services.size),
                ),
            )

            DispenseResult(
                id = recordRef.id,
                reference = reference,
                shortLabel = shortLabel,
                dayNumber = dayNumber.toInt(),
                shiftNumber = shiftNumber.toInt(),
                totalCents = prepared.totalCents,
                changeCents = prepared.changeCents,
            )
        }.addOnSuccessListener { result ->
            onSuccess(result)
        }.addOnFailureListener { error ->
            onFailure(friendlyMessage(error, "The bill could not be saved."))
        }
    }
}
