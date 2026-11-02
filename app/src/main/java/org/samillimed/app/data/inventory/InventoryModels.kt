package org.samillimed.app.data.inventory

/**
 * Status values for a goods receipt.
 */
object ReceiptStatus {
    const val COMPLETED = "COMPLETED"
}

/**
 * Status values for stock movements.
 */
object StockMovementType {
    const val RECEIPT = "RECEIPT"
    const val DISPENSING = "DISPENSING"
    const val ADJUSTMENT = "ADJUSTMENT"
}

/**
 * A single supplier delivery or invoice.
 *
 * One receipt can contain multiple products and batches.
 */
data class GoodsReceipt(
    val id: String = "",
    val supplierName: String = "",
    val referenceNumber: String = "",
    val receivedAtMillis: Long = 0,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val status: String = ReceiptStatus.COMPLETED,
    val totalCostCents: Long = 0,
    val lineCount: Int = 0,
)

/**
 * One product entry within a goods receipt.
 *
 * Receiving-unit details and prices are historical snapshots.
 */
data class GoodsReceiptLine(
    val id: String = "",
    val receiptId: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",
    val productTypeSnapshot: String = "",

    val quantityReceived: Long = 0,
    val receivingUnitName: String = "",
    val baseUnitsPerReceivingUnit: Long = 1,
    val baseQuantityReceived: Long = 0,

    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,

    val purchaseCostPerReceivingUnitCents: Long = 0,
    val lineTotalCostCents: Long = 0,
    val sellingPricePerReceivingUnitCents: Long = 0,
)

/**
 * An individual received batch.
 *
 * Stock quantities are maintained in the product's smallest unit.
 * Purchase and reference selling prices belong to this batch.
 */
data class StockBatch(
    val id: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",
    val receiptId: String = "",
    val receiptLineId: String = "",

    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,

    val receivedQuantity: Long = 0,
    val remainingQuantity: Long = 0,

    val receivingUnitName: String = "",
    val baseUnitsPerReceivingUnit: Long = 1,

    val purchaseCostPerReceivingUnitCents: Long = 0,
    val sellingPricePerReceivingUnitCents: Long = 0,

    val receivedAtMillis: Long = 0,
    val createdAtMillis: Long = 0,
)

/**
 * Aggregate stock balance for a registered product.
 *
 * The quantity is the sum of remaining quantities across its batches,
 * expressed in the product's smallest registered unit.
 */
data class StockBalance(
    val productId: String = "",
    val productNameSnapshot: String = "",
    val quantityOnHand: Long = 0,
    val updatedAtMillis: Long = 0,
)

/**
 * A snapshot of the unit selected for a stock transaction.
 */
data class TransactionUnitSnapshot(
    val unitName: String = "",
    val baseUnitsPerUnit: Long = 1,
)

/**
 * A product-level dispensing request.
 *
 * The actual selling price is entered for this particular sale.
 * It is not required to match the registered selling price of any batch.
 *
 * Purchase cost and gross profit are calculated from the actual
 * batch allocations and retained as historical transaction values.
 */
data class DispensingLine(
    val id: String = "",
    val dispensingId: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",

    val quantityDispensed: Long = 0,
    val dispensingUnit: TransactionUnitSnapshot =
        TransactionUnitSnapshot(),
    val baseQuantityDispensed: Long = 0,

    val actualSellingPricePerUnitCents: Long = 0,
    val totalSaleValueCents: Long = 0,

    val totalPurchaseCostCents: Long = 0,
    val grossProfitCents: Long = 0,

    val createdAtMillis: Long = 0,
)

/**
 * A batch-level allocation belonging to a dispensing line.
 *
 * One dispensing line can have multiple allocation records when
 * the requested quantity is fulfilled from several batches.
 *
 * Quantities are expressed in the product's smallest registered unit.
 * Monetary amounts are stored in cents.
 */
data class DispensingBatchAllocation(
    val id: String = "",
    val dispensingId: String = "",
    val dispensingLineId: String = "",
    val productId: String = "",

    val batchId: String = "",
    val batchNumberSnapshot: String = "",
    val expiryDateMillis: Long? = null,

    val quantityDeducted: Long = 0,

    val purchaseCostPerReceivingUnitCents: Long = 0,
    val baseUnitsPerReceivingUnit: Long = 1,

    val allocatedPurchaseCostCents: Long = 0,
    val allocatedSaleValueCents: Long = 0,
    val allocatedProfitCents: Long = 0,

    val resultingBatchBalance: Long = 0,
    val createdAtMillis: Long = 0,
)

/**
 * A dispensing transaction header.
 *
 * One transaction can contain multiple product lines.
 */
data class DispensingRecord(
    val id: String = "",
    val referenceNumber: String = "",
    val dispensedAtMillis: Long = 0,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val totalAmountCents: Long = 0,
    val totalPurchaseCostCents: Long = 0,
    val grossProfitCents: Long = 0,
    val lineCount: Int = 0,
)

/**
 * An auditable record of every stock change.
 *
 * Movement quantities are expressed in the smallest registered unit.
 */
data class StockMovement(
    val id: String = "",
    val productId: String = "",
    val batchId: String = "",
    val receiptId: String = "",
    val receiptLineId: String = "",
    val dispensingId: String = "",
    val dispensingLineId: String = "",

    val movementType: String = StockMovementType.RECEIPT,

    val quantityChange: Long = 0,
    val resultingBatchBalance: Long = 0,
    val resultingProductBalance: Long = 0,

    val transactionUnit: TransactionUnitSnapshot =
        TransactionUnitSnapshot(),

    val transactionQuantity: Long = 0,

    val referenceNumber: String = "",
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
)
