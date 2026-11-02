package org.samillimed.app.data.inventory

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Handles stock receiving, batch records, aggregate balances,
 * reconciliation and inventory observation.
 *
 * Individual batch records are the source of truth for stock quantities.
 * StockBalance is a maintained aggregate for fast product-level display.
 */
class InventoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun currentUser(): String =
        FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    /**
     * Calculates the total remaining stock across individual batches.
     *
     * Returns null if a batch has an invalid quantity or the total overflows.
     */
    private fun sumBatchQuantities(
        quantities: List<Long>,
    ): Long? {
        if (quantities.any { it < 0L }) return null

        return try {
            quantities.fold(0L) { total, quantity ->
                Math.addExact(total, quantity)
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    /**
     * Saves a complete receipt atomically.
     *
     * Existing stock is calculated from batch records, not from the
     * previously stored aggregate balance.
     *
     * If the stored aggregate disagrees with existing batches, the
     * aggregate is corrected and an audit record is created.
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
                    throw IllegalArgumentException(
                        "Every receipt line must reference a registered product."
                    )
                }

                if (line.quantityReceived <= 0L) {
                    throw IllegalArgumentException(
                        "Received quantities must be greater than zero."
                    )
                }

                if (line.receivingUnitName.isBlank()) {
                    throw IllegalArgumentException(
                        "Select a receiving unit for every product."
                    )
                }

                if (line.purchaseCostPerReceivingUnitCents < 0L ||
                    line.sellingPricePerReceivingUnitCents < 0L
                ) {
                    throw IllegalArgumentException(
                        "Prices cannot be negative."
                    )
                }

                val baseQuantity = InventoryCalculations.toBaseQuantity(
                    quantity = line.quantityReceived,
                    baseUnitsPerUnit = line.baseUnitsPerReceivingUnit,
                ) ?: throw IllegalArgumentException(
                    "Invalid receiving quantity or unit conversion."
                )

                val totalCost = InventoryCalculations.calculateLineCostCents(
                    quantity = line.quantityReceived,
                    costPerUnitCents =
                        line.purchaseCostPerReceivingUnitCents,
                ) ?: throw IllegalArgumentException(
                    "Invalid purchase cost."
                )

                if (line.expiryDateMillis != null &&
                    line.expiryDateMillis <= 0L
                ) {
                    throw IllegalArgumentException("Invalid expiry date.")
                }

                preparedLines.add(
                    PreparedLine(
                        line = line,
                        lineRefId = line.id.ifBlank {
                            db.collection(RECEIPT_LINES).document().id
                        },
                        batchRefId =
                            db.collection(STOCK_BATCHES).document().id,
                        baseQuantity = baseQuantity,
                        totalCostCents = totalCost,
                    )
                )
            }
        } catch (error: IllegalArgumentException) {
            onFailure(
                error.message ?: "The receipt contains invalid information."
            )
            return
        }

        if (preparedLines.map { it.lineRefId }.distinct().size !=
            preparedLines.size
        ) {
            onFailure("Receipt line IDs must be unique.")
            return
        }

        val productIds = preparedLines
            .map { it.line.productId }
            .distinct()

        /*
         * Maximum possible writes:
         * 1 receipt + 3 per line + 1 balance per product
         * + 1 possible reconciliation audit per product.
         */
        val requiredWrites =
            1 + (preparedLines.size * 3) + (productIds.size * 2)

        if (requiredWrites > FIRESTORE_MAX_WRITES) {
            onFailure(
                "This receipt contains too many lines to save atomically. " +
                    "Split it into smaller receipts."
            )
            return
        }

        val receiptTotal = InventoryCalculations.calculateReceiptTotalCents(
            preparedLines.map { it.totalCostCents }
        )

        if (receiptTotal == null) {
            onFailure("The receipt total could not be calculated.")
            return
        }

        db.runTransaction { transaction ->

            // All transaction reads happen before any writes.
            val existingReceipt = transaction.get(receiptRef)

            if (existingReceipt.exists()) {
                throw IllegalArgumentException(
                    "This receipt ID already exists. Start a new receipt."
                )
            }

            val productSnapshots = productIds.associateWith { productId ->
                transaction.get(
                    db.collection(PRODUCTS).document(productId)
                )
            }

            val balanceSnapshots = productIds.associateWith { productId ->
                transaction.get(
                    db.collection(STOCK_BALANCES).document(productId)
                )
            }

            val batchSnapshots = productIds.associateWith { productId ->
                transaction.get(
                    db.collection(STOCK_BATCHES)
                        .whereEqualTo("productId", productId)
                )
            }

            val productDetails = productIds.associateWith { productId ->
                val snapshot = productSnapshots[productId]
                    ?: throw IllegalArgumentException("Product not found.")

                if (!snapshot.exists()) {
                    throw IllegalArgumentException(
                        "A product in this receipt is no longer registered."
                    )
                }

                val active = snapshot.getBoolean("active") ?: true

                if (!active) {
                    throw IllegalArgumentException(
                        "Inactive products cannot receive new stock."
                    )
                }

                val trackExpiry =
                    snapshot.getBoolean("trackExpiry") ?: true

                val units = snapshot.get("units") as? List<*>
                    ?: emptyList<Any>()

                Triple(snapshot, trackExpiry, units)
            }

            // Validate every receiving unit against the live catalogue.
            preparedLines.forEach { prepared ->
                val line = prepared.line
                val details = productDetails[line.productId]
                    ?: throw IllegalArgumentException("Product not found.")

                val matchingUnit = details.third
                    .filterIsInstance<Map<*, *>>()
                    .firstOrNull {
                        (it["name"] as? String)?.equals(
                            line.receivingUnitName,
                            ignoreCase = true
                        ) == true
                    }

                if (matchingUnit == null) {
                    throw IllegalArgumentException(
                        "${line.productNameSnapshot} does not have " +
                            "${line.receivingUnitName} as a registered unit."
                    )
                }

                val catalogueBaseQty =
                    (matchingUnit["baseQty"] as? Number)?.toLong()

                if (catalogueBaseQty == null ||
                    catalogueBaseQty != line.baseUnitsPerReceivingUnit
                ) {
                    throw IllegalArgumentException(
                        "The unit conversion for " +
                            "${line.productNameSnapshot} has changed. " +
                            "Refresh the product and try again."
                    )
                }

                if (details.second && line.expiryDateMillis == null) {
                    throw IllegalArgumentException(
                        "${line.productNameSnapshot} requires an expiry date."
                    )
                }
            }

            // Derive existing balances directly from individual batches.
            val batchDerivedBalances = mutableMapOf<String, Long>()

            productIds.forEach { productId ->
                val quantities = batchSnapshots[productId]
                    ?.documents
                    ?.map { it.getLong("remainingQuantity") ?: 0L }
                    ?: emptyList()

                val derived = sumBatchQuantities(quantities)
                    ?: throw IllegalArgumentException(
                        "Invalid batch quantity or stock total overflow " +
                            "for product $productId."
                    )

                batchDerivedBalances[productId] = derived
            }

            // Calculate resulting balances after adding the receipt lines.
            val resultingBalances =
                batchDerivedBalances.toMutableMap()

            preparedLines.forEach { prepared ->
                val productId = prepared.line.productId
                val current = resultingBalances[productId] ?: 0L

                val updated = InventoryCalculations.addReceivedStock(
                    currentBalance = current,
                    receivedBaseQuantity = prepared.baseQuantity,
                ) ?: throw IllegalArgumentException(
                    "Stock balance overflow for " +
                        "${prepared.line.productNameSnapshot}."
                )

                resultingBalances[productId] = updated
            }

            // All reads and calculations are complete. Begin writes.
            val receiptData = mapOf(
                "id" to receiptRef.id,
                "supplierName" to receipt.supplierName.trim(),
                "referenceNumber" to receipt.referenceNumber.trim(),
                "receivedAtMillis" to receipt.receivedAtMillis,
                "createdAtMillis" to now,
                "createdBy" to user,
                "status" to ReceiptStatus.COMPLETED,
                "totalCostCents" to receiptTotal,
                "lineCount" to preparedLines.size,
            )

            transaction.set(receiptRef, receiptData)

            // Maintain the running product balance for each movement.
            val movementBalances =
                batchDerivedBalances.toMutableMap()

            preparedLines.forEach { prepared ->
                val line = prepared.line
                val productId = line.productId

                val lineRef = db.collection(RECEIPT_LINES)
                    .document(prepared.lineRefId)

                val batchRef = db.collection(STOCK_BATCHES)
                    .document(prepared.batchRefId)

                val movementRef = db.collection(STOCK_MOVEMENTS).document()

                val lineData = mapOf(
                    "id" to lineRef.id,
                    "receiptId" to receiptRef.id,
                    "productId" to productId,
                    "productNameSnapshot" to line.productNameSnapshot,
                    "productTypeSnapshot" to line.productTypeSnapshot,
                    "quantityReceived" to line.quantityReceived,
                    "receivingUnitName" to line.receivingUnitName,
                    "baseUnitsPerReceivingUnit" to
                        line.baseUnitsPerReceivingUnit,
                    "baseQuantityReceived" to prepared.baseQuantity,
                    "batchNumber" to line.batchNumber.trim(),
                    "expiryDateMillis" to line.expiryDateMillis,
                    "purchaseCostPerReceivingUnitCents" to
                        line.purchaseCostPerReceivingUnitCents,
                    "lineTotalCostCents" to prepared.totalCostCents,
                    "sellingPricePerReceivingUnitCents" to
                        line.sellingPricePerReceivingUnitCents,
                )

                val batchData = mapOf(
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
                    "baseUnitsPerReceivingUnit" to
                        line.baseUnitsPerReceivingUnit,
                    "purchaseCostPerReceivingUnitCents" to
                        line.purchaseCostPerReceivingUnitCents,
                    "sellingPricePerReceivingUnitCents" to
                        line.sellingPricePerReceivingUnitCents,
                    "receivedAtMillis" to receipt.receivedAtMillis,
                    "createdAtMillis" to now,
                )

                val previousBalance =
                    movementBalances[productId] ?: 0L

                val newBalance = InventoryCalculations.addReceivedStock(
                    currentBalance = previousBalance,
                    receivedBaseQuantity = prepared.baseQuantity,
                ) ?: throw IllegalArgumentException(
                    "Could not calculate the resulting product balance."
                )

                movementBalances[productId] = newBalance

                val movementData = mapOf(
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
                        "baseUnitsPerUnit" to
                            line.baseUnitsPerReceivingUnit,
                    ),
                    "transactionQuantity" to line.quantityReceived,
                    "referenceNumber" to receipt.referenceNumber.trim(),
                    "createdAtMillis" to now,
                    "createdBy" to user,
                )

                transaction.set(lineRef, lineData)
                transaction.set(batchRef, batchData)
                transaction.set(movementRef, movementData)
            }

            // Correct aggregate balances and record any pre-existing mismatch.
            productIds.forEach { productId ->
                val balanceRef = db.collection(STOCK_BALANCES)
                    .document(productId)

                val recordedBalance =
                    balanceSnapshots[productId]
                        ?.getLong("quantityOnHand") ?: 0L

                val batchDerivedBalance =
                    batchDerivedBalances[productId] ?: 0L

                if (recordedBalance != batchDerivedBalance) {
                    val auditRef =
                        db.collection(STOCK_RECONCILIATIONS).document()

                    transaction.set(
                        auditRef,
                        mapOf(
                            "id" to auditRef.id,
                            "productId" to productId,
                            "recordedBalance" to recordedBalance,
                            "batchDerivedBalance" to batchDerivedBalance,
                            "correctedBalance" to
                                (resultingBalances[productId] ?: 0L),
                            "difference" to
                                (batchDerivedBalance - recordedBalance),
                            "reason" to "RECEIPT_BALANCE_RECONCILIATION",
                            "createdAtMillis" to now,
                            "createdBy" to user,
                        )
                    )
                }

                val productSnapshot =
                    productSnapshots[productId]
                        ?: throw IllegalArgumentException("Product not found.")

                transaction.set(
                    balanceRef,
                    mapOf(
                        "productId" to productId,
                        "productNameSnapshot" to
                            (productSnapshot.getString("name") ?: ""),
                        "quantityOnHand" to
                            (resultingBalances[productId] ?: 0L),
                        "updatedAtMillis" to now,
                    )
                )
            }

            receiptRef.id
        }.addOnSuccessListener { savedReceiptId ->
            onSuccess(savedReceiptId)
        }.addOnFailureListener { error ->
            onFailure(
                error.message ?: "The receipt could not be saved."
            )
        }
    }

    /**
     * Reconciles one product's aggregate balance against its batches.
     *
     * Batch records are never modified by reconciliation.
     * A discrepancy is recorded before correcting the aggregate.
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

        val balanceRef = db.collection(STOCK_BALANCES)
            .document(productId)

        val batchQuery = db.collection(STOCK_BATCHES)
            .whereEqualTo("productId", productId)

        val now = System.currentTimeMillis()

        db.runTransaction { transaction ->

            // Read all relevant records before making any changes.
            val balanceSnapshot = transaction.get(balanceRef)
            val batchSnapshot = transaction.get(batchQuery)

            val quantities = batchSnapshot.documents.map {
                it.getLong("remainingQuantity") ?: 0L
            }

            val actualBalance = sumBatchQuantities(quantities)
                ?: throw IllegalArgumentException(
                    "Invalid batch quantity or stock total overflow."
                )

            val recordedBalance =
                balanceSnapshot.getLong("quantityOnHand") ?: 0L

            if (recordedBalance < 0L) {
                throw IllegalArgumentException(
                    "The recorded aggregate balance is invalid."
                )
            }

            if (recordedBalance != actualBalance) {
                val auditRef =
                    db.collection(STOCK_RECONCILIATIONS).document()

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
                    )
                )

                transaction.set(
                    balanceRef,
                    mapOf(
                        "productId" to productId,
                        "quantityOnHand" to actualBalance,
                        "updatedAtMillis" to now,
                    )
                )
            }

            actualBalance
        }.addOnSuccessListener { actualBalance ->
            onSuccess(actualBalance)
        }.addOnFailureListener { error ->
            onFailure(
                error.message ?: "Stock reconciliation failed."
            )
        }
    }

    /**
     * Observes aggregate stock balances for all registered products.
     */
    fun stockBalancesFlow(): Flow<List<StockBalance>> = callbackFlow {
        val registration = db.collection(STOCK_BALANCES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val balances = snapshot.documents
                    .mapNotNull {
                        it.toObject(StockBalance::class.java)
                    }
                    .sortedBy {
                        it.productNameSnapshot.lowercase()
                    }

                trySend(balances)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Observes every batch belonging to one product.
     * Batches remain separate and are never collapsed.
     */
    fun batchesFlow(productId: String): Flow<List<StockBatch>> =
        callbackFlow {
            val registration = db.collection(STOCK_BATCHES)
                .whereEqualTo("productId", productId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    val batches = snapshot.documents
                        .mapNotNull {
                            it.toObject(StockBatch::class.java)
                        }
                        .sortedWith(
                            compareBy<StockBatch> {
                                it.expiryDateMillis ?: Long.MAX_VALUE
                            }.thenBy {
                                it.receivedAtMillis
                            }
                        )

                    trySend(batches)
                }

            awaitClose { registration.remove() }
        }

    /**
     * Observes all completed goods receipts, newest first.
     */
    fun receiptsFlow(): Flow<List<GoodsReceipt>> = callbackFlow {
        val registration = db.collection(RECEIPTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val receipts = snapshot.documents
                    .mapNotNull {
                        it.toObject(GoodsReceipt::class.java)
                    }
                    .sortedByDescending {
                        it.receivedAtMillis
                    }

                trySend(receipts)
            }

        awaitClose { registration.remove() }
    }

    /**
     * Observes the product lines belonging to one receipt.
     */
    fun receiptLinesFlow(
        receiptId: String
    ): Flow<List<GoodsReceiptLine>> = callbackFlow {
        val registration = db.collection(RECEIPT_LINES)
            .whereEqualTo("receiptId", receiptId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val lines = snapshot.documents
                    .mapNotNull {
                        it.toObject(GoodsReceiptLine::class.java)
                    }

                trySend(lines)
            }

        awaitClose { registration.remove() }
    }
}
