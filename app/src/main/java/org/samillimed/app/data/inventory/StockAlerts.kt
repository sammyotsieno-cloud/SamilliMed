package org.samillimed.app.data.inventory

import org.samillimed.app.data.catalog.Product
import java.time.LocalDate

/** Why a product needs attention. A product shows once, under its most urgent reason. */
enum class AlertKind { EXPIRED, OUT, LOW, EXPIRING }

class StockAlert(
    val product: Product,
    val kind: AlertKind,
    /** Unexpired stock, in the smallest unit. */
    val sellableBase: Long,
    /** Expired stock still on the shelf, in the smallest unit. */
    val expiredBase: Long,
    val nearestExpiryMillis: Long?,
)

class StockAlerts(
    /** One entry per product, most urgent first. */
    val items: List<StockAlert>,
    val expiredProducts: Int,
    /** Products with expired stock or stock expiring within the window. */
    val expiringProducts: Int,
    /** Products at or below their reorder level (out of stock included). */
    val lowProducts: Int,
    val stockValueCents: Long,
) {
    val total: Int get() = items.size
}

/**
 * Works out what needs attention. Low and out-of-stock alerts apply only to products that have a
 * reorder level, because without one there is no way to know what "low" means.
 */
fun buildStockAlerts(
    products: List<Product>,
    batches: List<StockBatch>,
    today: LocalDate = LocalDate.now(),
    expiringWithinDays: Int = 30,
): StockAlerts {
    val byProduct = batches.groupBy { it.productId }
    val items = ArrayList<StockAlert>()
    var expiredCount = 0
    var expiringCount = 0
    var lowCount = 0

    products.forEach { p ->
        val own = byProduct[p.id].orEmpty()
        if (!p.active && own.isEmpty()) return@forEach

        val sellable = own.filter { !isExpired(it.expiryDateMillis, today) }
        val sellableBase = sellable.sumOf { it.remainingQuantity }
        val expiredBase = own.sumOf { it.remainingQuantity } - sellableBase
        val nearest = sellable.mapNotNull { it.expiryDateMillis }.minOrNull()
        val reorder = p.reorderLevel.toLong()

        val hasExpired = expiredBase > 0L
        val expiringSoon = nearest != null && daysUntilExpiry(nearest, today) <= expiringWithinDays.toLong()
        val needsReorder = p.active && reorder > 0L && sellableBase <= reorder

        if (hasExpired) expiredCount++
        if (hasExpired || expiringSoon) expiringCount++
        if (needsReorder) lowCount++

        val kind = when {
            hasExpired -> AlertKind.EXPIRED
            needsReorder && sellableBase <= 0L -> AlertKind.OUT
            needsReorder -> AlertKind.LOW
            expiringSoon -> AlertKind.EXPIRING
            else -> return@forEach
        }
        items.add(StockAlert(p, kind, sellableBase, expiredBase, nearest))
    }

    return StockAlerts(
        items = items.sortedWith(
            compareBy<StockAlert> { it.kind.ordinal }
                .thenBy { it.nearestExpiryMillis ?: Long.MAX_VALUE }
                .thenBy { it.product.name.lowercase() },
        ),
        expiredProducts = expiredCount,
        expiringProducts = expiringCount,
        lowProducts = lowCount,
        stockValueCents = batches.sumOf { batchValueCents(it) },
    )
}
