package org.samillimed.app.data.inventory

import org.samillimed.app.data.catalog.ProductUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a product's stock stands. The first matching status in this order wins. */
enum class StockStatus { EXPIRED_STOCK, OUT, LOW, EXPIRING_SOON, OK }

private fun count(n: Long): String = String.format(Locale.US, "%,d", n)

/**
 * Shows a quantity in the units people actually use: 1,247 tablets of a product sold by strip
 * (10) and box (100) becomes "12 Box · 4 Strip · 7 Tablet". A zero quantity shows "0 Tablet".
 */
fun formatMixedUnits(baseQuantity: Long, units: List<ProductUnit>): String {
    val baseName = units.firstOrNull()?.name ?: return count(baseQuantity)
    if (baseQuantity <= 0L) return "0 $baseName"
    var left = baseQuantity
    val parts = mutableListOf<String>()
    units.drop(1)
        .filter { it.baseQty > 1 }
        .sortedByDescending { it.baseQty }
        .forEach { unit ->
            val n = left / unit.baseQty
            if (n > 0L) {
                parts.add("${count(n)} ${unit.name}")
                left -= n * unit.baseQty
            }
        }
    if (left > 0L || parts.isEmpty()) parts.add("${count(left)} $baseName")
    return parts.joinToString(" · ")
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Days from today until the LAST valid day of a batch. Negative once it has expired. */
fun daysUntilExpiry(expiryMillis: Long, today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(today, expiryMillis.toLocalDate())

/** A batch counts as expired from the day after its last valid day. */
fun isExpired(expiryMillis: Long?, today: LocalDate = LocalDate.now()): Boolean =
    expiryMillis != null && daysUntilExpiry(expiryMillis, today) < 0L

/**
 * The status of one product.
 * sellableBase: unexpired stock. hasExpiredStock: expired batches still on the shelf.
 * reorderLevelBase: in the smallest unit (0 = no reorder level set).
 */
fun stockStatus(
    sellableBase: Long,
    hasExpiredStock: Boolean,
    reorderLevelBase: Long,
    nearestExpiryMillis: Long?,
    expiringWithinDays: Int = 90,
    today: LocalDate = LocalDate.now(),
): StockStatus = when {
    hasExpiredStock -> StockStatus.EXPIRED_STOCK
    sellableBase <= 0L -> StockStatus.OUT
    reorderLevelBase > 0L && sellableBase <= reorderLevelBase -> StockStatus.LOW
    nearestExpiryMillis != null && daysUntilExpiry(nearestExpiryMillis, today) <= expiringWithinDays ->
        StockStatus.EXPIRING_SOON
    else -> StockStatus.OK
}

/** What the stock still in one batch cost, in cents. */
fun batchValueCents(batch: StockBatch): Long {
    if (batch.remainingQuantity <= 0L || batch.baseUnitsPerReceivingUnit <= 0L) return 0L
    return BigDecimal.valueOf(batch.purchaseCostPerReceivingUnitCents)
        .multiply(BigDecimal.valueOf(batch.remainingQuantity))
        .divide(BigDecimal.valueOf(batch.baseUnitsPerReceivingUnit), 0, RoundingMode.HALF_UP)
        .toLong()
}

/**
 * How much to order, in the smallest unit.
 * soldBase / daysObserved is the average daily sales; the order tops stock up to targetDays of
 * cover, and never to less than the reorder level. Returns 0 when stock is already enough.
 */
fun suggestedReorderBase(
    soldBase: Long,
    daysObserved: Int,
    onHandBase: Long,
    targetDays: Int,
    reorderLevelBase: Long,
): Long {
    val perDay = if (daysObserved > 0) soldBase.toDouble() / daysObserved else 0.0
    val target = maxOf(Math.ceil(perDay * targetDays).toLong(), reorderLevelBase)
    return (target - onHandBase).coerceAtLeast(0L)
}
