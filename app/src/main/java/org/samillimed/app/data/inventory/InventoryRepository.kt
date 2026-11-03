package org.samillimed.app.data.inventory

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val RECEIPTS = "goodsReceipts"
private const val RECEIPT_LINES = "goodsReceiptLines"
private const val STOCK_BATCHES = "stockBatches"
private const val STOCK_BALANCES = "stockBalances"
private const val STOCK_MOVEMENTS = "stockMovements"
private const val STOCK_RECONCILIATIONS = "stockReconciliations"
private const val PRODUCTS = "products"

private const val FIRESTORE_MAX_WRITES = 500

/** Turns a Firestore failure into a message a shop assistant can act on. */
private fun friendlyMessage(error: Exception, fallback: String): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "No internet connection. Stock changes need a connection so that every device " +
                "shows the same stock. Try again when you are back online."
        FirebaseFirestoreException.Code.ABORTED ->
            "Someone changed this stock at the same moment. Please try again."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do this. Sign in again."
        else -> error.message ?: fallback
    }

/**
 * Handles stock receiving, batch records, aggregate balances, reconciliation and
 * inventory observation.
 *
 * Individual batch records are the source of truth for stock quantities.
 * StockBalance is a maintained aggregate for fast product-level display. Every stock change
 * goes through a transaction on the balance document, so two devices can never both
 * change the same balance from an out-of-date value.
 *
 * Note: the Android Firestore SDK can only read single documents inside a transaction, not
 * queries. Receiving therefore updates the balance incrementally, and batches are compared
 * with the balance in reconcileProductBalance, outside the transaction.
 */
class InventoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun currentUser(): String =
        FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    /** Sums batch quantities. Returns null if one is invalid or the total overflows. */
    private fun sumBatchQuantities(quantities: List<Long>): Long? {
        if (quantities.any { it < 0L }) return null
        return try {
            quantities.fold(0L) { total, quantity -> Math.addExact(total, quantity) }
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Saves a complete receipt atomically: the receipt, its lines, one batch and one stock
     * movement per line, and the new balance of each product, all in one transaction.
     *
     * Needs an internet connection. Pass a receipt.id that stays the same for one draft: if the
     * receipt was already saved (for example the reply was lost and the user tried again), this
     * reports success instead of receiving the stock twice.
     */
    fun saveReceipt(
        receipt: GoodsReceipt,
        lines: List<GoodsReceiptLine>,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = currentUser()

        if (user.isBlank()) {
            onFailure("Sign in before receiving stock.")
            return
        }
        if (lines.isEmpty()) {
            onFailure("A receipt must contain at least one product.")
            return
        }
        if (receipt.supplierName.isBlank()) {
            onFailure("Enter the supplier name.")
            return
        }

        data class PreparedLine(
            val line: GoodsReceiptLine,
            val lineRefId: String,
            val batchRefId: String,
            val baseQuantity: Long,
            val totalCostCents: Long,
        )

        val receiptRef = if (receipt.id.isBlank()) {
            db.collection(RECEIPTS).document()
        } else {
            db.collection(RECEIPTS).document(receipt.id)
        }

        val now = System.currentTimeMillis()
        val preparedLines = mutableListOf<PreparedLine>()

        try {
            lines.forEach { line ->
                if (line.productId.isBlank()) {
                    throw IllegalArgumentException("Every receipt line must reference a registered product.")
                }
                if (line.quantityReceived <= 0L) {
                    throw IllegalArgumentException("Received quantities must be greater than zero.")
                }
                if (line.receivingUnitName.isBlank()) {
                    throw IllegalArgumentException("Select a receiving unit for every product.")
                }
                if (line.purchaseCostPerReceivingUnitCents < 0L ||
                    line.sellingPricePerReceivingUnitCents < 0L
                ) {
                    throw IllegalArgumentException("Prices cannot be negative.")
                }

                val baseQuantity = InventoryCalculations.toBaseQuantity(
                    quantity = line.quantityReceived,
                    baseUnitsPerUnit = line.baseUnitsPerReceivingUnit,
                ) ?: throw IllegalArgumentException("Invalid receiving quantity or unit conversion.")

                val totalCost = InventoryCalculations.calculateLineCostCents(
                    quantity = line.quantityReceived,
                    costPerUnitCents = line.purchaseCostPerReceivingUnitCents,
                ) ?: throw IllegalArgumentException("Invalid purchase cost.")

                if (line.expiryDateMillis != null && line.expiryDateMillis <= 0L) {
                    throw IllegalArgumentException("Invalid expiry date.")
                }

                preparedLines.add(
                    PreparedLine(
                        line = line,
                        lineRefId = line.id.ifBlank { db.collection(RECEIPT_LINES).document().id },
                        batchRefId = db.collection(STOCK_BATCHES).document().id,
                        baseQuantity = baseQuantity,
                        totalCostCents = totalCost,
                    ),
                )
            }
        } catch (error: IllegalArgumentException) {
            onFailure(error.message ?: "The receipt contains invalid information.")
            return
        }

        if (preparedLines.map { it.lineRefId }.distinct().size != preparedLines.size) {
            onFailure("Receipt line IDs must be unique.")
            return
        }

        val productIds = preparedLines.map { it.line.productId }.distinct()

        // 1 receipt + (line + batch + movement) per line + 1 balance per product
        val requiredWrites = 1 + (preparedLines.size * 3) + productIds.size
        if (requiredWrites > FIRESTORE_MAX_WRITES) {
            onFailure(
                "This receipt contains too many lines to save atomically. " +
                    "Split it into smaller receipts.",
            )
            return
        }

        val receiptTotal = InventoryCalculations.calculateReceiptTotalCents(
            preparedLines.map { it.totalCostCents },
        )
        if (receiptTotal == null) {
            onFailure("The receipt total could not be calculated.")
            return
        }

        db.runTransaction { transaction ->

            // ---- reads (single documents only), all before any write -----------------------
            val existingReceipt = transaction.get(receiptRef)
            if (existingReceipt.exists()) {
                // The same draft was saved before: treat it as done, never receive twice.
                if (receipt.id.isNotBlank()) return@runTransaction receiptRef.id
                throw IllegalArgumentException("This receipt already exists.")
            }

            val productSnapshots = productIds.associateWith { productId ->
                transaction.get(db.collection(PRODUCTS).document(productId))
            }
            val balanceSnapshots = productIds.associateWith { productId ->
                transaction.get(db.collection(STOCK_BALANCES).document(productId))
            }

            // ---- checks against the live catalogue -------------------------------------------
            val productUnits = HashMap<String, List<Map<*, *>>>()
            val productTracksExpiry = HashMap<String, Boolean>()

            productIds.forEach { productId ->
                val snapshot = productSnapshots[productId]
                    ?: throw IllegalArgumentException("Product not found.")

                if (!snapshot.exists()) {
                    throw IllegalArgumentException("A product in this receipt is no longer registered.")
                }
                if (!(snapshot.getBoolean("active") ?: true)) {
                    throw IllegalArgumentException("Inactive products cannot receive new stock.")
                }

                productTracksExpiry[productId] = snapshot.getBoolean("trackExpiry") ?: true
                productUnits[productId] =
                    (snapshot.get("units") as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
            }

            preparedLines.forEach { prepared ->
                val line = prepared.line

                val matchingUnit = productUnits[line.productId].orEmpty().firstOrNull {
                    (it["name"] as? String)?.equals(line.receivingUnitName, ignoreCase = true) == true
                } ?: throw IllegalArgumentException(
                    "${line.productNameSnapshot} does not have ${line.receivingUnitName} as a registered unit.",
                )

                val catalogueBaseQty = (matchingUnit["baseQty"] as? Number)?.toLong()
                if (catalogueBaseQty == null || catalogueBaseQty != line.baseUnitsPerReceivingUnit) {
                    throw IllegalArgumentException(
                        "The unit conversion for ${line.productNameSnapshot} has changed. " +
                            "Refresh the product and try again.",
                    )
                }

                if (productTracksExpiry[line.productId] == true && line.expiryDateMillis == null) {
                    throw IllegalArgumentException("${line.productNameSnapshot} requires an expiry date.")
                }
            }

            // ---- balances: start from the recorded balance of each product --------------------
            val runningBalances = HashMap<String, Long>()
            productIds.forEach { productId ->
                val recorded = balanceSnapshots[productId]?.getLong("quantityOnHand") ?: 0L
                if (recorded < 0L) {
                    throw IllegalArgumentException(
                        "The recorded stock balance of a product is invalid. Run a stock reconciliation first.",
                    )
                }
                runningBalances[productId] = recorded
            }

            // ---- writes ---------------------------------------------------------------------
            transaction.set(
                receiptRef,
                mapOf(
                    "id" to receiptRef.id,
                    "supplierName" to receipt.supplierName.trim(),
                    "referenceNumber" to receipt.referenceNumber.trim(),
                    "receivedAtMillis" to receipt.receivedAtMillis,
                    "createdAtMillis" to now,
                    "createdBy" to user,
                    "status" to ReceiptStatus.COMPLETED,
                    "totalCostCents" to receiptTotal,
                    "lineCount" to preparedLines.size,
                ),
            )

            preparedLines.forEach { prepared ->
                val line = prepared.line
                val productId = line.productId

                val lineRef = db.collection(RECEIPT_LINES).document(prepared.lineRefId)
                val batchRef = db.collection(STOCK_BATCHES).document(prepared.batchRefId)
                val movementRef = db.collection(STOCK_MOVEMENTS).document()

                val newBalance = InventoryCalculations.addReceivedStock(
                    currentBalance = runningBalances[productId] ?: 0L,
                    receivedBaseQuantity = prepared.baseQuantity,
                ) ?: throw IllegalArgumentException(
                    "Stock balance overflow for ${line.productNameSnapshot}.",
                )
                runningBalances[productId] = newBalance

                transaction.set(
                    lineRef,
                    mapOf(
                        "id" to lineRef.id,
                        "receiptId" to receiptRef.id,
                        "productId" to productId,
                        "productNameSnapshot" to line.productNameSnapshot,
                        "productTypeSnapshot" to line.productTypeSnapshot,
                        "quantityReceived" to line.quantityReceived,
                        "receivingUnitName" to line.receivingUnitName,
                        "baseUnitsPerReceivingUnit" to line.baseUnitsPerReceivingUnit,
                        "baseQuantityReceived" to prepared.baseQuantity,
                        "batchNumber" to line.batchNumber.trim(),
                        "expiryDateMillis" to line.expiryDateMillis,
                        "purchaseCostPerReceivingUnitCents" to line.purchaseCostPerReceivingUnitCents,
                        "lineTotalCostCents" to prepared.totalCostCents,
                        "sellingPricePerReceivingUnitCents" to line.sellingPricePerReceivingUnitCents,
                    ),
                )

                transaction.set(
                    batchRef,
                    mapOf(
                        "id" to batchRef.id,
                        "productId" to productId,
                        "productNameSnapshot" to line.productNameSnapshot,
                        "receiptId" to receiptRef.id,
                        "receiptLineId" to lineRef.id,
                        "batchNumber" to line.batchNumber.trim(),
                        "expiryDateMillis" to line.expiryDateMillis,
                        "receivedQuantity" to prepared.baseQuantity,
                        "remainingQuantity" to prepared.baseQuantity,
                        "receivingUnitName" to line.receivingUnitName,
                        "baseUnitsPerReceivingUnit" to line.baseUnitsPerReceivingUnit,
                        "purchaseCostPerReceivingUnitCents" to line.purchaseCostPerReceivingUnitCents,
                        "sellingPricePerReceivingUnitCents" to line.sellingPricePerReceivingUnitCents,
                        "receivedAtMillis" to receipt.receivedAtMillis,
                        "createdAtMillis" to now,
                    ),
                )

                transaction.set(
                    movementRef,
                    mapOf(
                        "id" to movementRef.id,
                        "productId" to productId,
                        "batchId" to batchRef.id,
                        "receiptId" to receiptRef.id,
                        "receiptLineId" to lineRef.id,
                        "dispensingId" to "",
                        "dispensingLineId" to "",
                        "movementType" to StockMovementType.RECEIPT,
                        "quantityChange" to prepared.baseQuantity,
                        "resultingBatchBalance" to prepared.baseQuantity,
                        "resultingProductBalance" to newBalance,
                        "transactionUnit" to mapOf(
                            "unitName" to line.receivingUnitName,
                            "baseUnitsPerUnit" to line.baseUnitsPerReceivingUnit,
                        ),
                        "transactionQuantity" to line.quantityReceived,
                        "referenceNumber" to receipt.referenceNumber.trim(),
                        "createdAtMillis" to now,
                        "createdBy" to user,
                    ),
                )
            }

            productIds.forEach { productId ->
                transaction.set(
                    db.collection(STOCK_BALANCES).document(productId),
                    mapOf(
                        "productId" to productId,
                        "productNameSnapshot" to (productSnapshots[productId]?.getString("name") ?: ""),
                        "quantityOnHand" to (runningBalances[productId] ?: 0L),
                        "updatedAtMillis" to now,
                    ),
                )
            }

            receiptRef.id
        }.addOnSuccessListener { savedReceiptId ->
            onSuccess(savedReceiptId)
        }.addOnFailureListener { error ->
            onFailure(friendlyMessage(error, "The receipt could not be saved."))
        }
    }

    /**
     * Compares one product's aggregate balance with the sum of its batches and, if they differ,
     * records the discrepancy and corrects the aggregate. Batch records are never changed.
     *
     * The batches are read first (a transaction cannot run a query), so run this when nobody is
     * receiving or dispensing that product at the same moment.
     */
    fun reconcileProductBalance(
        productId: String,
        onSuccess: (Long) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val user = currentUser()

        if (user.isBlank()) {
            onFailure("Sign in before reconciling stock.")
            return
        }
        if (productId.isBlank()) {
            onFailure("A valid product ID is required.")
            return
        }

        val balanceRef = db.collection(STOCK_BALANCES).document(productId)
        val now = System.currentTimeMillis()

        db.collection(STOCK_BATCHES).whereEqualTo("productId", productId).get()
            .addOnSuccessListener { batchSnapshot ->
                val actualBalance = sumBatchQuantities(
                    batchSnapshot.documents.map { it.getLong("remainingQuantity") ?: 0L },
                )
                if (actualBalance == null) {
                    onFailure("Invalid batch quantity or stock total overflow.")
                    return@addOnSuccessListener
                }

                db.runTransaction { transaction ->
                    val balanceSnapshot = transaction.get(balanceRef)
                    val recordedBalance = balanceSnapshot.getLong("quantityOnHand") ?: 0L

                    if (recordedBalance != actualBalance) {
                        val auditRef = db.collection(STOCK_RECONCILIATIONS).document()
                        transaction.set(
                            auditRef,
                            mapOf(
                                "id" to auditRef.id,
                                "productId" to productId,
                                "recordedBalance" to recordedBalance,
                                "batchDerivedBalance" to actualBalance,
                                "correctedBalance" to actualBalance,
                                "difference" to actualBalance - recordedBalance,
                                "reason" to "MANUAL_BALANCE_RECONCILIATION",
                                "createdAtMillis" to now,
                                "createdBy" to user,
                            ),
                        )
                        transaction.set(
                            balanceRef,
                            mapOf(
                                "productId" to productId,
                                "productNameSnapshot" to (balanceSnapshot.getString("productNameSnapshot") ?: ""),
                                "quantityOnHand" to actualBalance,
                                "updatedAtMillis" to now,
                            ),
                        )
                    }
                    actualBalance
                }.addOnSuccessListener { balance ->
                    onSuccess(balance)
                }.addOnFailureListener { error ->
                    onFailure(friendlyMessage(error, "Stock reconciliation failed."))
                }
            }
            .addOnFailureListener { error ->
                onFailure(friendlyMessage(error, "Stock reconciliation failed."))
            }
    }

    // ---- live data ----------------------------------------------------------------------------
    // Listener errors are ignored on purpose (as in the catalogue): closing the flow with an
    // error would crash the screen that collects it, for example when someone signs out.

    /** Observes aggregate stock balances for all registered products. */
    fun stockBalancesFlow(): Flow<List<StockBalance>> = callbackFlow {
        val registration = db.collection(STOCK_BALANCES)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val balances = snapshot.documents
                    .mapNotNull { it.toObject(StockBalance::class.java) }
                    .sortedBy { it.productNameSnapshot.lowercase() }
                trySend(balances)
            }
        awaitClose { registration.remove() }
    }

    /** Observes every batch of one product, soonest expiry first. Batches are never merged. */
    fun batchesFlow(productId: String): Flow<List<StockBatch>> = callbackFlow {
        val registration = db.collection(STOCK_BATCHES)
            .whereEqualTo("productId", productId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val batches = snapshot.documents
                    .mapNotNull { it.toObject(StockBatch::class.java) }
                    .sortedWith(
                        compareBy<StockBatch> { it.expiryDateMillis ?: Long.MAX_VALUE }
                            .thenBy { it.receivedAtMillis },
                    )
                trySend(batches)
            }
        awaitClose { registration.remove() }
    }

    /** Observes all completed goods receipts, newest first. */
    fun receiptsFlow(): Flow<List<GoodsReceipt>> = callbackFlow {
        val registration = db.collection(RECEIPTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val receipts = snapshot.documents
                    .mapNotNull { it.toObject(GoodsReceipt::class.java) }
                    .sortedByDescending { it.receivedAtMillis }
                trySend(receipts)
            }
        awaitClose { registration.remove() }
    }

    /** Observes the product lines belonging to one receipt. */
    fun receiptLinesFlow(receiptId: String): Flow<List<GoodsReceiptLine>> = callbackFlow {
        val registration = db.collection(RECEIPT_LINES)
            .whereEqualTo("receiptId", receiptId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toObject(GoodsReceiptLine::class.java) })
            }
        awaitClose { registration.remove() }
    }
}
