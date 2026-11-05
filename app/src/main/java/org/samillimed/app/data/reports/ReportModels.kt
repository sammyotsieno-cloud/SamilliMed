package org.samillimed.app.data.reports

import com.google.firebase.firestore.DocumentSnapshot

/** The totals of one day, kept up to date by every bill and stock adjustment. */
data class DailySummary(
    val dateKey: String = "",
    val billCount: Long = 0,
    val totalSalesCents: Long = 0,
    val productSalesCents: Long = 0,
    val serviceSalesCents: Long = 0,
    /** Purchase cost of the stock that was sold. */
    val costCents: Long = 0,
    val profitCents: Long = 0,
    /** Payment method code -> amount. */
    val payments: Map<String, Long> = emptyMap(),
    val shiftBills: Map<String, Long> = emptyMap(),
    val shiftSales: Map<String, Long> = emptyMap(),
    val shiftNames: Map<String, String> = emptyMap(),
    val shiftCodes: Map<String, String> = emptyMap(),
    val afterShiftBills: Long = 0,
    /** Cost of stock lost: expired, damaged, lost, samples, count shortfalls. */
    val writeOffCostCents: Long = 0,
    val lossByReason: Map<String, Long> = emptyMap(),
    val returnedToSupplierCents: Long = 0,
    val adjustmentCount: Long = 0,
)

private fun DocumentSnapshot.longMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
        ?: emptyMap()

private fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
    (get(field) as? Map<*, *>)?.entries
        ?.associate { it.key.toString() to it.value.toString() }
        ?: emptyMap()

fun DocumentSnapshot.toDailySummary(): DailySummary = DailySummary(
    dateKey = getString("dateKey") ?: id,
    billCount = getLong("dayCount") ?: 0L,
    totalSalesCents = getLong("totalSalesCents") ?: 0L,
    productSalesCents = getLong("productSalesCents") ?: 0L,
    serviceSalesCents = getLong("serviceSalesCents") ?: 0L,
    costCents = getLong("costCents") ?: 0L,
    profitCents = getLong("profitCents") ?: 0L,
    payments = longMap("payments"),
    shiftBills = longMap("shiftCounts"),
    shiftSales = longMap("shiftSales"),
    shiftNames = stringMap("shiftNames"),
    shiftCodes = stringMap("shiftCodes"),
    afterShiftBills = getLong("afterShiftBills") ?: 0L,
    writeOffCostCents = getLong("writeOffCostCents") ?: 0L,
    lossByReason = longMap("lossByReason"),
    returnedToSupplierCents = getLong("returnedToSupplierCents") ?: 0L,
    adjustmentCount = getLong("adjustmentCount") ?: 0L,
)

/** Profit as a share of sales, in percent. 0 when nothing was sold. */
val DailySummary.marginPercent: Double
    get() = if (totalSalesCents > 0L) profitCents * 100.0 / totalSalesCents else 0.0

/** What one product sold on one day, or in one month. Quantities are in the smallest unit. */
data class ProductStat(
    val productId: String = "",
    val productName: String = "",
    val dateKey: String = "",
    val monthKey: String = "",
    val unitsSoldBase: Long = 0,
    val saleCents: Long = 0,
    val costCents: Long = 0,
    val profitCents: Long = 0,
    /** Number of bills the product appeared on. */
    val lineCount: Long = 0,
)

/** Adds several days (or months) of one product together. */
fun List<ProductStat>.combinedByProduct(): List<ProductStat> =
    groupBy { it.productId }.map { (id, stats) ->
        ProductStat(
            productId = id,
            productName = stats.first().productName,
            unitsSoldBase = stats.sumOf { it.unitsSoldBase },
            saleCents = stats.sumOf { it.saleCents },
            costCents = stats.sumOf { it.costCents },
            profitCents = stats.sumOf { it.profitCents },
            lineCount = stats.sumOf { it.lineCount },
        )
    }
