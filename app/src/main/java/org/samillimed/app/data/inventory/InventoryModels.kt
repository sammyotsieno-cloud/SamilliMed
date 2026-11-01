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
 * The receiving unit and its conversion factor are snapshots.
 * Historical receipts therefore remain accurate even if the
 * product's unit configuration changes later.
 */
data class GoodsReceiptLine(
    val id: String = "",
    val receiptId: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",
    val productTypeSnapshot: String = "",

    // Receiving quantity and unit
    val quantityReceived: Long = 0,
    val receivingUnitName: String = "",
    val baseUnitsPerReceivingUnit: Long = 1,
    val baseQuantityReceived: Long = 0,

    // Batch information
    val batchNumber: String = "",
    val expiryDateMillis: Long? = null,

    // Financial values stored in cents
    val purchaseCostPerReceivingUnitCents: Long = 0,
    val lineTotalCostCents: Long = 0,
    val sellingPricePerReceivingUnitCents: Long = 0,
)

/**
 * An individual received batch.
 *
 * Quantities are maintained in the product's smallest registered
 * unit, independently of the unit used when receiving stock.
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

    // Original receiving-unit snapshot
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
 * This is the authoritative quantity used for stock calculations.
 * It is always expressed in the product's smallest registered unit.
 */
data class StockBalance(
    val productId: String = "",
    val productNameSnapshot: String = "",
    val quantityOnHand: Long = 0,
    val updatedAtMillis: Long = 0,
)

/**
 * A snapshot of the unit selected for a stock transaction.
 *
 * Example:
 * unitName = "Strip"
 * baseUnitsPerUnit = 10
 *
 * One strip therefore represents 10 smallest registered units.
 */
data class TransactionUnitSnapshot(
    val unitName: String = "",
    val baseUnitsPerUnit: Long = 1,
)

/**
 * A dispensing transaction line.
 *
 * The user may dispense in any supported product unit,
 * regardless of the unit originally used to receive stock.
 */
data class DispensingLine(
    val id: String = "",
    val dispensingId: String = "",
    val productId: String = "",
    val productNameSnapshot: String = "",

    // Unit selected during dispensing
    val quantityDispensed: Long = 0,
    val dispensingUnit: TransactionUnitSnapshot = TransactionUnitSnapshot(),
    val baseQuantityDispensed: Long = 0,

    // Batch from which the stock was deducted
    val batchId: String = "",
    val batchNumberSnapshot: String = "",

    val sellingPricePerUnitCents: Long = 0,
    val lineTotalCents: Long = 0,
    val createdAtMillis: Long = 0,
)

/**
 * A dispensing transaction header.
 *
 * One dispensing transaction can contain multiple products.
 */
data class DispensingRecord(
    val id: String = "",
    val referenceNumber: String = "",
    val dispensedAtMillis: Long = 0,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val totalAmountCents: Long = 0,
    val lineCount: Int = 0,
)

/**
 * An auditable record of every stock change.
 *
 * Movement quantities are always expressed in the smallest
 * registered unit, while the selected transaction unit is
 * retained separately for historical display.
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

    // Positive for incoming stock; negative for outgoing stock
    val quantityChange: Long = 0,
    val resultingBatchBalance: Long = 0,
    val resultingProductBalance: Long = 0,

    // Unit selected for the original transaction
    val transactionUnit: TransactionUnitSnapshot =
        TransactionUnitSnapshot(),

    val transactionQuantity: Long = 0,

    val referenceNumber: String = "",
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
)
