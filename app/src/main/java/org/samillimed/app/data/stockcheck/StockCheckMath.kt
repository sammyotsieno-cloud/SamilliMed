package org.samillimed.app.data.stockcheck

import org.samillimed.app.data.catalog.ProductUnit
import org.samillimed.app.data.inventory.StockBatch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

fun unitsLargestFirst(units: List<ProductUnit>): List<ProductUnit> =
    units.sortedByDescending { it.baseQty.toLong() }

/** How many of each unit make up a quantity, biggest pack first: 25 tablets with strips of 10 is 2 strips and 5 tablets. */
fun partsFromBase(base: Long, units: List<ProductUnit>): List<Pair<ProductUnit, Long>> {
    var left = base
    return unitsLargestFirst(units).map { u ->
        val size = u.baseQty.toLong().coerceAtLeast(1L)
        val n = left / size
        left -= n * size
        u to n
    }
}

fun baseFromParts(parts: List<Pair<ProductUnit, Long>>): Long =
    parts.sumOf { it.first.baseQty.toLong() * it.second }

/** Which batches to change to correct a difference. leftover is what could not be placed. */
class OpsPlan(val ops: List<CorrectionOp>, val leftover: Long)

/**
 * A shortage comes off the batches that expire first. An extra goes onto the batches that expire last,
 * but only up to what each batch received, because a batch can never hold more than it received.
 */
fun planCorrection(difference: Long, batches: List<StockBatch>): OpsPlan {
    if (difference == 0L) return OpsPlan(emptyList(), 0L)
    val ops = ArrayList<CorrectionOp>()
    var need = abs(difference)
    if (difference < 0L) {
        val ordered = batches.filter { it.remainingQuantity > 0L }
            .sortedWith(compareBy<StockBatch>({ it.expiryDateMillis ?: Long.MAX_VALUE }, { it.receivedAtMillis }))
        for (b in ordered) {
            if (need <= 0L) break
            val take = minOf(b.remainingQuantity, need)
            ops.add(CorrectionOp(b.id, -take))
            need -= take
        }
    } else {
        val ordered = batches
            .sortedWith(compareByDescending<StockBatch> { it.expiryDateMillis ?: Long.MAX_VALUE }.thenByDescending { it.receivedAtMillis })
        for (b in ordered) {
            if (need <= 0L) break
            val room = b.receivedQuantity - b.remainingQuantity
            if (room <= 0L) continue
            val take = minOf(room, need)
            ops.add(CorrectionOp(b.id, take))
            need -= take
        }
    }
    return OpsPlan(ops, need)
}

/** What the stock in these changes cost, in cents. */
fun opsValueCents(ops: List<CorrectionOp>, batches: List<StockBatch>): Long = ops.sumOf { op ->
    val b = batches.firstOrNull { it.id == op.batchId } ?: return@sumOf 0L
    if (b.baseUnitsPerReceivingUnit <= 0L) {
        0L
    } else {
        BigDecimal.valueOf(b.purchaseCostPerReceivingUnitCents)
            .multiply(BigDecimal.valueOf(abs(op.delta)))
            .divide(BigDecimal.valueOf(b.baseUnitsPerReceivingUnit), 0, RoundingMode.HALF_UP)
            .toLong()
    }
}
