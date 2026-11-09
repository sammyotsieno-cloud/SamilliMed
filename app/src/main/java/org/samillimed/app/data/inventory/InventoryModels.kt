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
    const val RETURN = "RETURN"
}

/** How a bill was paid. */
object PaymentMethod {
    const val CASH = "CASH"
    const val MPESA = "MPESA"
    const val CARD = "CARD"
    const val OTHER = "OTHER"

    val all = listOf(CASH, MPESA, CARD, OTHER)

    fun label(code: String): String = when (code) {
        CASH -> "Cash"
        MPESA -> "M-Pesa"
        CARD -> "Card"
        else -> "Other"
    }
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
 * A product-level dispensing line on a bill.
 *
 * The actual selling price is typed for this particular sale.
 * It is not required to match the reference price of any batch.
 *
 * Purchase cost and gross profit are calculated from the actual
 * batch allocations and kept as historical transaction values.
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
 * A service line on a bill (a consultation, a laboratory test and so on).
 * Names are snapshots, so later renames never change old bills.
 */
data class DispensingServiceLine(
    val id: String = "",
    val dispensingId: String = "",
    val serviceId: String = "",
    val serviceNameSnapshot: String = "",
    val categorySnapshot: String = "",
    val departmentSnapshot: String = "",
    val quantity: Long = 1,
    val priceCents: Long = 0,
    val totalCents: Long = 0,
    val createdAtMillis: Long = 0,
)

/**
 * A batch-level allocation belonging to a dispensing line.
 *
 * One dispensing line can have multiple allocation records when
 * the requested quantity is fulfilled from several batches.
 *
 * Original batch pricing and receiving-unit details are retained
 * so historical price comparisons remain accurate.
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

    val receivingUnitName: String = "",
    val purchaseCostPerReceivingUnitCents: Long = 0,
    val sellingPricePerReceivingUnitCents: Long = 0,
    val baseUnitsPerReceivingUnit: Long = 1,

    val allocatedPurchaseCostCents: Long = 0,
    val allocatedSaleValueCents: Long = 0,
    val allocatedProfitCents: Long = 0,

    val resultingBatchBalance: Long = 0,
    val createdAtMillis: Long = 0,
)

/**
 * A bill (one visit): the header of one dispensing transaction.
 *
 * A bill can hold medicines and services together. It is shown as a summary at a glance;
 * the lines are read only when the bill is opened.
 */
data class DispensingRecord(
    val id: String = "",

    /** Full reference, e.g. 20261104-G-014. Unique and sortable. */
    val referenceNumber: String = "",
    /** Date the shift (or day) belongs to, yyyy-MM-dd. */
    val dateKey: String = "",
    val shiftId: String = "",
    val shiftCode: String = "",
    val shiftName: String = "",
    /** 1, 2, 3... within this shift; restarts at 1 for every shift and every day. */
    val shiftNumber: Int = 0,
    /** 1, 2, 3... across the whole day; restarts at 1 every day. */
    val dayNumber: Int = 0,
    /** True when the cashier kept dispensing after their shift had ended. */
    val afterShift: Boolean = false,

    val paymentMethod: String = PaymentMethod.CASH,
    val amountReceivedCents: Long = 0,
    val changeGivenCents: Long = 0,
    /** Optional short label typed by the cashier (initials, for example). Never required. */
    val label: String = "",

    val dispensedAtMillis: Long = 0,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",

    val productTotalCents: Long = 0,
    val serviceTotalCents: Long = 0,
    val totalAmountCents: Long = 0,
    val totalPurchaseCostCents: Long = 0,
    /** Total amount minus the purchase cost of the stock sold. Service income counts in full. */
    val grossProfitCents: Long = 0,

    val productLineCount: Int = 0,
    val serviceLineCount: Int = 0,
    val lineCount: Int = 0,

    /** ACTIVE, PARTIALLY_RETURNED or VOIDED. Bills saved before returns existed have no status, which means ACTIVE. */
    val status: String = BillStatus.ACTIVE,
    val returnedCents: Long = 0,
    val voidedAtMillis: Long = 0,
    val voidedBy: String = "",
    val voidReason: String = "",
)

/** "G-014": the short form shown in lists. */
fun visitShortLabel(shiftCode: String, shiftNumber: Int): String =
    "${shiftCode.ifBlank { "G" }}-${shiftNumber.toString().padStart(3, '0')}"

/** "20261104-G-014": the full form kept in records. */
fun visitReference(dateKey: String, shiftCode: String, shiftNumber: Int): String =
    dateKey.replace("-", "") + "-" + visitShortLabel(shiftCode, shiftNumber)

val DispensingRecord.shortLabel: String
    get() = visitShortLabel(shiftCode, shiftNumber)

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
    /** For adjustments: why the stock changed (see AdjustmentReason) and an optional note. */
    val reason: String = "",
    val note: String = "",
    /** For adjustments: the cost value of the quantity changed, in cents. */
    val valueCents: Long = 0,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
)
