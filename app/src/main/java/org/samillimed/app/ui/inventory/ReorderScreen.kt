package org.samillimed.app.ui.inventory

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.ProductUnit
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.inventory.InventoryReader
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.inventory.isExpired
import org.samillimed.app.data.inventory.suggestedReorderBase
import org.samillimed.app.data.reports.ProductStat
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate

private class ReorderRow(
    val product: Product,
    val onHandBase: Long,
    val perDay: Double,
    val suggestedBase: Long,
    val orderUnit: ProductUnit,
    val orderCount: Long,
    val daysCover: Double?,
)

/**
 * What to order, worked out from how fast each product really sells.
 * Average daily sales come from the last month and the part of this month so far; the order tops
 * stock up to the chosen number of days of cover, and never below the reorder level. Quantities are
 * rounded up to the biggest pack the product has.
 */
@Composable
fun ReorderScreen(
    catalog: Catalog,
    batches: List<StockBatch>?,
    reader: InventoryReader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val thisMonth = remember { today.toString().take(7) }
    val lastMonth = remember { today.minusMonths(1).toString().take(7) }
    var coverDays by rememberSaveable { mutableStateOf(14) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val thisStats by produceState<List<ProductStat>?>(null, reader) {
        reader.monthStatsFlow(thisMonth).collect { value = it }
    }
    val lastStats by produceState<List<ProductStat>?>(null, reader) {
        reader.monthStatsFlow(lastMonth).collect { value = it }
    }

    val ready = batches != null && thisStats != null && lastStats != null && catalog.loaded
    val rows = remember(catalog.products, batches, thisStats, lastStats, coverDays) {
        if (!ready) {
            emptyList()
        } else {
            val prevHasData = lastStats.orEmpty().isNotEmpty()
            val daysObserved = today.dayOfMonth + if (prevHasData) today.minusMonths(1).lengthOfMonth() else 0
            val sold = (thisStats.orEmpty() + lastStats.orEmpty())
                .groupBy { it.productId }
                .mapValues { (_, stats) -> stats.sumOf { it.unitsSoldBase } }
            val sellable = batches.orEmpty()
                .filter { !isExpired(it.expiryDateMillis, today) }
                .groupBy { it.productId }
                .mapValues { (_, list) -> list.sumOf { it.remainingQuantity } }

            catalog.products.filter { it.active && it.units.isNotEmpty() }.mapNotNull { p ->
                val soldBase = sold[p.id] ?: 0L
                val onHand = sellable[p.id] ?: 0L
                val suggested = suggestedReorderBase(soldBase, daysObserved, onHand, coverDays, p.reorderLevel.toLong())
                if (suggested <= 0L) return@mapNotNull null
                val orderUnit = p.units.maxByOrNull { it.baseQty } ?: p.units.first()
                val perPack = orderUnit.baseQty.toLong().coerceAtLeast(1L)
                val packs = (suggested + perPack - 1L) / perPack
                val perDay = if (daysObserved > 0) soldBase.toDouble() / daysObserved else 0.0
                ReorderRow(
                    product = p,
                    onHandBase = onHand,
                    perDay = perDay,
                    suggestedBase = suggested,
                    orderUnit = orderUnit,
                    orderCount = packs,
                    daysCover = if (perDay > 0.0) onHand / perDay else null,
                )
            }.sortedWith(
                compareBy<ReorderRow> { if (it.onHandBase <= 0L) 0 else 1 }
                    .thenBy { it.daysCover ?: Double.MAX_VALUE }
                    .thenBy { it.product.name.lowercase() },
            )
        }
    }

    fun share() {
        val text = buildString {
            append("Order list - SamilliMed, ${today}\n")
            rows.forEach { r ->
                append("• ${r.product.name}: ${r.orderCount} ${r.orderUnit.name}\n")
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Order list")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share order list"))
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Reorder list",
            subtitle = if (ready) "${rows.size} to order" else null,
            onBack = onBack,
            actions = { InvHeaderPill("Share", enabled = rows.isNotEmpty()) { share() } },
        )

        if (!ready) {
            LoadingState()
        } else {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Stock to cover",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(7, 14, 30).forEach { d ->
                        InvChip("$d days", coverDays == d) { coverDays = d }
                    }
                }
                Text(
                    text = "Based on what each product actually sold in the last month or so. " +
                        "Products with no sales yet are suggested only when below their reorder level.",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )

                if (rows.isEmpty()) {
                    EmptyState(
                        icon = SamilliIcons.Box,
                        title = "Nothing to order",
                        message = "Stock covers the next $coverDays days for everything with sales history.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rows, key = { it.product.id }) { row -> ReorderCard(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderCard(row: ReorderRow) {
    val c = SamilliTheme.colors
    val p = row.product
    val baseName = p.units.firstOrNull()?.name.orEmpty()
    SamilliCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val detail = if (p.isMedicine) {
                    listOf(p.genericNames, p.strengthText, p.dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    p.type
                }
                if (detail.isNotBlank()) {
                    Text(detail, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Order ${row.orderCount} ${row.orderUnit.name}",
                    color = c.accentText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (row.orderUnit.baseQty > 1) {
                    Text(
                        text = "= ${formatMixedUnits(row.orderCount * row.orderUnit.baseQty, p.units)}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Text(
            text = "On hand ${formatMixedUnits(row.onHandBase, p.units)}",
            color = if (row.onHandBase <= 0L) c.urgent else c.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        val speed = if (row.perDay > 0.0) {
            "Sells about ${String.format("%.1f", row.perDay)} $baseName a day" +
                (row.daysCover?.let { "  ·  lasts about ${it.toInt()} days" } ?: "")
        } else {
            "No recent sales: below its reorder level"
        }
        Text(text = speed, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
