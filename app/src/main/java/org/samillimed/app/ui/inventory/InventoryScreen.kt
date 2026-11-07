package org.samillimed.app.ui.inventory

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.catalog.writeTextToUri
import org.samillimed.app.data.inventory.InventoryReader
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.StockStatus
import org.samillimed.app.data.inventory.batchValueCents
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.inventory.isExpired
import org.samillimed.app.data.inventory.stockStatus
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The whole Inventory area: the stock list, the product page and the reorder list. One live
 * feed of batches is shared by all three, so moving between them costs no extra reads.
 */
@Composable
fun InventoryHost(
    catalog: Catalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reader = remember { InventoryReader() }
    val batches by produceState<List<StockBatch>?>(null, reader) {
        reader.activeBatchesFlow().collect { value = it }
    }
    var openProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReorder by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showReorder) { showReorder = false }
    BackHandler(enabled = openProductId != null && !showReorder) { openProductId = null }

    val open = catalog.products.firstOrNull { it.id == openProductId }
    when {
        showReorder -> ReorderScreen(
            catalog = catalog,
            batches = batches,
            reader = reader,
            onBack = { showReorder = false },
            modifier = modifier,
        )
        open != null -> ProductStockScreen(
            product = open,
            batches = batches,
            reader = reader,
            onBack = { openProductId = null },
            modifier = modifier,
        )
        else -> InventoryScreen(
            catalog = catalog,
            batches = batches,
            onBack = onBack,
            onOpenProduct = { openProductId = it.id },
            onReorder = { showReorder = true },
            modifier = modifier,
        )
    }
}

// ---- stock rows ------------------------------------------------------------------------------

private class StockRow(
    val product: Product,
    val sellableBase: Long,
    val expiredBase: Long,
    val nearestExpiry: Long?,
    val valueCents: Long,
    val status: StockStatus,
)

private fun severity(status: StockStatus): Int = when (status) {
    StockStatus.EXPIRED_STOCK -> 0
    StockStatus.OUT -> 1
    StockStatus.LOW -> 2
    StockStatus.EXPIRING_SOON -> 3
    StockStatus.OK -> 4
}

private fun statusText(status: StockStatus): String = when (status) {
    StockStatus.EXPIRED_STOCK -> "Expired stock"
    StockStatus.OUT -> "Out of stock"
    StockStatus.LOW -> "Low stock"
    StockStatus.EXPIRING_SOON -> "Expiring soon"
    StockStatus.OK -> "OK"
}

/** One row per product: its unexpired stock, expired stock, nearest expiry, value and status. */
private fun buildRows(products: List<Product>, batches: List<StockBatch>, today: LocalDate): List<StockRow> {
    val byProduct = batches.groupBy { it.productId }
    return products.mapNotNull { p ->
        val own = byProduct[p.id].orEmpty()
        if (p.units.isEmpty() && own.isEmpty()) return@mapNotNull null
        if (!p.active && own.isEmpty()) return@mapNotNull null
        val sellable = own.filter { !isExpired(it.expiryDateMillis, today) }
        val expired = own.filter { isExpired(it.expiryDateMillis, today) }
        val sellableBase = sellable.sumOf { it.remainingQuantity }
        val expiredBase = expired.sumOf { it.remainingQuantity }
        val nearest = sellable.mapNotNull { it.expiryDateMillis }.minOrNull()
        StockRow(
            product = p,
            sellableBase = sellableBase,
            expiredBase = expiredBase,
            nearestExpiry = nearest,
            valueCents = own.sumOf { batchValueCents(it) },
            status = stockStatus(sellableBase, expiredBase > 0L, p.reorderLevel.toLong(), nearest, 90, today),
        )
    }
}

/** A stocktake sheet: what the app says is on the shelf, with a blank column to write the count in. */
private fun buildStockCsv(rows: List<StockRow>, today: LocalDate): String {
    val header = listOf(
        "Product", "Type", "Generic names", "Strengths", "Dosage form", "Stock on hand",
        "On hand (smallest unit)", "Smallest unit", "Expired (smallest unit)", "Nearest expiry",
        "Value at cost (KSh)", "Reorder level (smallest unit)", "Status", "Counted (stocktake)", "Notes",
    )
    val out = StringBuilder()
    out.append(header.joinToString(",") { csvEscape(it) }).append("\r\n")
    rows.sortedBy { it.product.name.lowercase() }.forEach { row ->
        val p = row.product
        val line = listOf(
            p.name,
            p.type,
            p.genericNames,
            p.strengthText,
            p.dosageForm,
            formatMixedUnits(row.sellableBase, p.units),
            row.sellableBase.toString(),
            p.units.firstOrNull()?.name.orEmpty(),
            row.expiredBase.toString(),
            row.nearestExpiry?.let { fmtDay(it) }.orEmpty(),
            BigDecimal.valueOf(row.valueCents, 2).toPlainString(),
            if (p.reorderLevel > 0) p.reorderLevel.toString() else "",
            statusText(row.status),
            "",
            "",
        )
        out.append(line.joinToString(",") { csvEscape(it) }).append("\r\n")
    }
    return out.toString()
}

// ---- the stock list ---------------------------------------------------------------------------

@Composable
private fun InventoryScreen(
    catalog: Catalog,
    batches: List<StockBatch>?,
    onBack: () -> Unit,
    onOpenProduct: (Product) -> Unit,
    onReorder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }

    val rows = remember(catalog.products, batches) {
        if (batches == null) emptyList() else buildRows(catalog.products, batches, today)
    }
    val counts = remember(rows) { StockStatus.values().associateWith { s -> rows.count { it.status == s } } }
    val totalValue = rows.sumOf { it.valueCents }
    val filter = filterName?.let { name -> StockStatus.values().firstOrNull { it.name == name } }

    val visible = remember(rows, query, filter) {
        val q = query.trim().lowercase()
        rows.filter { row ->
            (filter == null || row.status == filter) &&
                (q.isEmpty() || listOf(
                    row.product.name, row.product.brandName, row.product.genericNames,
                    row.product.strengthText, row.product.type,
                ).any { it.lowercase().contains(q) })
        }.sortedWith(compareBy<StockRow> { severity(it.status) }.thenBy { it.product.name.lowercase() })
    }

    // Stocktake export: the phone's "Save as" screen asks where to put the file.
    val latestRows by rememberUpdatedState(rows)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val list = latestRows
            val ok = writeTextToUri(context, uri, buildStockCsv(list, today))
            Toast.makeText(
                context,
                if (ok) "Saved ${list.size} products" else "Could not save the file",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun toggle(s: StockStatus): String? = if (filter == s) null else s.name

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Inventory",
            subtitle = if (batches != null && CurrentUser.isAdmin) "Stock at cost ${formatKsh(totalValue)}" else null,
            onBack = onBack,
            actions = {
                InvHeaderPill("Reorder", onClick = onReorder)
                InvHeaderPill("Export", enabled = rows.isNotEmpty() && CurrentUser.isAdmin) {
                    exportLauncher.launch("samillimed-stock-$today.csv")
                }
            },
        )

        when {
            batches == null || !catalog.loaded -> LoadingState()
            rows.isEmpty() -> EmptyState(
                icon = SamilliIcons.Box,
                title = "No stock yet",
                message = "Register products and receive stock. They will appear here.",
            )
            else -> Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HealthTile(counts.getValue(StockStatus.OUT), "Out", c.urgent, filter == StockStatus.OUT, { filterName = toggle(StockStatus.OUT) }, Modifier.weight(1f))
                    HealthTile(counts.getValue(StockStatus.LOW), "Low", c.accentText, filter == StockStatus.LOW, { filterName = toggle(StockStatus.LOW) }, Modifier.weight(1f))
                    HealthTile(counts.getValue(StockStatus.EXPIRING_SOON), "Expiring", c.accentText, filter == StockStatus.EXPIRING_SOON, { filterName = toggle(StockStatus.EXPIRING_SOON) }, Modifier.weight(1f))
                    HealthTile(counts.getValue(StockStatus.EXPIRED_STOCK), "Expired", c.urgent, filter == StockStatus.EXPIRED_STOCK, { filterName = toggle(StockStatus.EXPIRED_STOCK) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search stock",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InvChip("All (${rows.size})", filter == null) { filterName = null }
                    InvChip("Out of stock (${counts.getValue(StockStatus.OUT)})", filter == StockStatus.OUT) { filterName = toggle(StockStatus.OUT) }
                    InvChip("Low (${counts.getValue(StockStatus.LOW)})", filter == StockStatus.LOW) { filterName = toggle(StockStatus.LOW) }
                    InvChip("Expiring (${counts.getValue(StockStatus.EXPIRING_SOON)})", filter == StockStatus.EXPIRING_SOON) { filterName = toggle(StockStatus.EXPIRING_SOON) }
                    InvChip("Expired (${counts.getValue(StockStatus.EXPIRED_STOCK)})", filter == StockStatus.EXPIRED_STOCK) { filterName = toggle(StockStatus.EXPIRED_STOCK) }
                }
                if (visible.isEmpty()) {
                    EmptyState(
                        icon = SamilliIcons.Box,
                        title = "Nothing here",
                        message = "No products match this search or filter.",
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.product.id }) { row ->
                            StockCard(row, today, onClick = { onOpenProduct(row.product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthTile(
    count: Int,
    label: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else c.card, shape)
            .border(1.dp, if (selected) accent else c.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            color = if (count > 0) accent else c.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = label, color = c.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun StockCard(row: StockRow, today: LocalDate, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    val p = row.product
    SamilliCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (p.active) 1f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(text = p.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val detail = if (p.isMedicine) {
                    listOf(p.genericNames, p.strengthText, p.dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
                } else {
                    p.type
                }
                if (detail.isNotBlank()) {
                    Text(text = detail, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            StockStatusPill(row.status)
        }

        Text(
            text = formatMixedUnits(row.sellableBase, p.units),
            color = c.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        val expiryLine = when {
            row.nearestExpiry != null -> "Next expiry " + expiryLabel(row.nearestExpiry, today)
            row.sellableBase > 0L && p.trackExpiry -> "No expiry date recorded"
            else -> ""
        }
        if (expiryLine.isNotEmpty()) {
            Text(text = expiryLine, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (row.expiredBase > 0L) {
            Text(
                text = "${formatMixedUnits(row.expiredBase, p.units)} expired and still on the shelf",
                color = c.urgent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                text = if (CurrentUser.isAdmin) "At cost ${formatKsh(row.valueCents)}" else "",
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (p.reorderLevel > 0) {
                Text(
                    text = "Reorder at ${formatMixedUnits(p.reorderLevel.toLong(), p.units)}",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
