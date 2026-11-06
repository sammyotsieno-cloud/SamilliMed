package org.samillimed.app.ui.reports

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.writeTextToUri
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.reports.ProductStat
import org.samillimed.app.data.reports.buildProductsCsv
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvChip
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtNumber
import org.samillimed.app.ui.theme.SamilliTheme
import java.util.Locale

private enum class SortBy { REVENUE, UNITS, PROFIT }

/** Every product sold in the period, ranked by revenue, units or profit. */
@Composable
fun TopSellersScreen(
    catalog: Catalog,
    stats: List<ProductStat>,
    periodTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var sortName by rememberSaveable { mutableStateOf(SortBy.REVENUE.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val sort = SortBy.values().firstOrNull { it.name == sortName } ?: SortBy.REVENUE

    fun unitsText(s: ProductStat): String =
        catalog.products.firstOrNull { it.id == s.productId }
            ?.let { formatMixedUnits(s.unitsSoldBase, it.units) } ?: fmtNumber(s.unitsSoldBase)

    val ranked = remember(stats, sort, query) {
        val q = query.trim().lowercase()
        stats.filter { q.isEmpty() || it.productName.lowercase().contains(q) }
            .sortedByDescending {
                when (sort) {
                    SortBy.REVENUE -> it.saleCents
                    SortBy.UNITS -> it.unitsSoldBase
                    SortBy.PROFIT -> it.profitCents
                }
            }
    }

    val latestRanked by rememberUpdatedState(ranked)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val ok = writeTextToUri(context, uri, buildProductsCsv(latestRanked) { unitsText(it) })
            Toast.makeText(context, if (ok) "Product sheet saved" else "Could not save the file", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Top sellers",
            subtitle = periodTitle,
            onBack = onBack,
            actions = {
                InvHeaderPill("Export", enabled = stats.isNotEmpty()) {
                    exportLauncher.launch("samillimed-products.csv")
                }
            },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InvChip("By revenue", sort == SortBy.REVENUE) { sortName = SortBy.REVENUE.name }
            InvChip("By units", sort == SortBy.UNITS) { sortName = SortBy.UNITS.name }
            InvChip("By profit", sort == SortBy.PROFIT) { sortName = SortBy.PROFIT.name }
        }
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search products",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (sort == SortBy.UNITS) {
            Text(
                text = "Units are counted in each product's smallest unit, so compare only similar products.",
                color = c.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }

        if (ranked.isEmpty()) {
            EmptyState(
                icon = SamilliIcons.Box,
                title = "Nothing to show",
                message = "No product sales match this period or search.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(ranked, key = { _, s -> s.productId }) { index, s ->
                    SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${index + 1}", color = c.accentText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.productName, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(unitsText(s), color = c.textSecondary, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatKsh(s.saleCents), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                val margin = if (s.saleCents > 0L) String.format(Locale.US, " (%.0f%%)", s.profitCents * 100.0 / s.saleCents) else ""
                                Text("profit ${formatKsh(s.profitCents)}$margin", color = c.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
