package org.samillimed.app.ui.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.writeTextToUri
import org.samillimed.app.data.inventory.AdjustmentReason
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.reports.DailySummary
import org.samillimed.app.data.reports.Period
import org.samillimed.app.data.reports.PeriodKind
import org.samillimed.app.data.reports.ProductStat
import org.samillimed.app.data.reports.ReportTotals
import org.samillimed.app.data.reports.ReportsReader
import org.samillimed.app.data.reports.buildDailyCsv
import org.samillimed.app.data.reports.buildInsights
import org.samillimed.app.data.reports.changePercent
import org.samillimed.app.data.reports.combinedByProduct
import org.samillimed.app.data.reports.comparisonLabel
import org.samillimed.app.data.reports.dailySeries
import org.samillimed.app.data.reports.parseDay
import org.samillimed.app.data.reports.periodFor
import org.samillimed.app.data.reports.previous
import org.samillimed.app.data.reports.totals
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvChip
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtNumber
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayInput: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun String.toDayOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

/**
 * The whole Reports area: the overview, the bill list (with bill details) and the full top sellers.
 */
@Composable
fun ReportsHost(
    catalog: Catalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reader = remember { ReportsReader() }
    val today = remember { LocalDate.now() }

    var kindName by rememberSaveable { mutableStateOf(PeriodKind.TODAY.name) }
    var customFrom by rememberSaveable { mutableStateOf("") }
    var customTo by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf(0) } // 0 overview, 1 bills, 2 top sellers
    var billsDate by rememberSaveable { mutableStateOf(today.toString()) }
    var showCustom by rememberSaveable { mutableStateOf(false) }

    val kind = PeriodKind.values().firstOrNull { it.name == kindName } ?: PeriodKind.TODAY
    val period = remember(kind, customFrom, customTo) {
        periodFor(kind, today, customFrom.toDayOrNull(), customTo.toDayOrNull())
    }
    val previousPeriod = remember(period) { period.previous() }
    val monthKey = remember(period) { period.monthKey(today) }

    val summaries by produceState<List<DailySummary>?>(null, period.fromKey, period.toKey) {
        value = null
        reader.summariesFlow(period.fromKey, period.toKey).collect { value = it }
    }
    val previousSummaries by produceState<List<DailySummary>?>(null, previousPeriod.fromKey, previousPeriod.toKey) {
        value = null
        reader.summariesFlow(previousPeriod.fromKey, previousPeriod.toKey).collect { value = it }
    }
    val stats by produceState<List<ProductStat>?>(null, period.fromKey, period.toKey, monthKey) {
        value = null
        when {
            monthKey != null -> reader.monthStatsFlow(monthKey).collect { value = it.combinedByProduct() }
            period.days <= 31 -> reader.dayStatsFlow(period.fromKey, period.toKey).collect { value = it.combinedByProduct() }
            else -> value = emptyList()
        }
    }

    BackHandler(enabled = page != 0) { page = 0 }

    when (page) {
        1 -> BillsScreen(
            catalog = catalog,
            reader = reader,
            initialDateKey = billsDate,
            onBack = { page = 0 },
            modifier = modifier,
        )
        2 -> TopSellersScreen(
            catalog = catalog,
            stats = stats.orEmpty(),
            periodTitle = period.title(),
            onBack = { page = 0 },
            modifier = modifier,
        )
        else -> ReportsHub(
            catalog = catalog,
            period = period,
            kind = kind,
            summaries = summaries,
            previousSummaries = previousSummaries,
            stats = stats,
            onKind = { kindName = it.name },
            onCustom = { showCustom = true },
            onBills = {
                billsDate = period.toKey
                page = 1
            },
            onTopSellers = { page = 2 },
            onBack = onBack,
            modifier = modifier,
        )
    }

    if (showCustom) {
        CustomPeriodDialog(
            today = today,
            initialFrom = if (kind == PeriodKind.CUSTOM) period.from else today.minusDays(6),
            initialTo = if (kind == PeriodKind.CUSTOM) period.to else today,
            onConfirm = { from, to ->
                customFrom = from.toString()
                customTo = to.toString()
                kindName = PeriodKind.CUSTOM.name
                showCustom = false
            },
            onDismiss = { showCustom = false },
        )
    }
}

@Composable
private fun CustomPeriodDialog(
    today: LocalDate,
    initialFrom: LocalDate,
    initialTo: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SamilliTheme.colors
    var fromText by remember { mutableStateOf(initialFrom.format(dayInput)) }
    var toText by remember { mutableStateOf(initialTo.format(dayInput)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose dates", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter { ch -> ch.isDigit() || ch == '/' } },
                    label = "From (DD/MM/YYYY)",
                    keyboardType = KeyboardType.Number,
                )
                FormTextField(
                    value = toText,
                    onValueChange = { toText = it.filter { ch -> ch.isDigit() || ch == '/' } },
                    label = "To (DD/MM/YYYY)",
                    keyboardType = KeyboardType.Number,
                )
                Text("Up to 31 days at a time.", color = c.textSecondary, fontSize = 12.sp)
                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val from = parseDay(fromText)
                val to = parseDay(toText)
                if (from == null || to == null) {
                    error = "Type both dates like 01/11/2026."
                    return@TextButton
                }
                if (from.isAfter(to)) {
                    error = "The first date must not be after the second."
                    return@TextButton
                }
                if (to.isAfter(today)) {
                    error = "The last date cannot be in the future."
                    return@TextButton
                }
                if (ChronoUnit.DAYS.between(from, to) > 30L) {
                    error = "Choose at most 31 days."
                    return@TextButton
                }
                onConfirm(from, to)
            }) { Text("Show", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReportsHub(
    catalog: Catalog,
    period: Period,
    kind: PeriodKind,
    summaries: List<DailySummary>?,
    previousSummaries: List<DailySummary>?,
    stats: List<ProductStat>?,
    onKind: (PeriodKind) -> Unit,
    onCustom: () -> Unit,
    onBills: () -> Unit,
    onTopSellers: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val totals = remember(summaries) { summaries?.totals() }
    val previousTotals = remember(previousSummaries) { previousSummaries?.totals() }
    val comparison = remember(period) { period.comparisonLabel() }

    val latestSummaries by rememberUpdatedState(summaries.orEmpty())
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val ok = writeTextToUri(context, uri, buildDailyCsv(latestSummaries))
            Toast.makeText(context, if (ok) "Sales sheet saved" else "Could not save the file", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Reports",
            subtitle = period.title(),
            onBack = onBack,
            actions = {
                InvHeaderPill("Bills", onClick = onBills)
                InvHeaderPill("Export", enabled = totals != null && totals.bills > 0L) {
                    exportLauncher.launch("samillimed-sales-${period.fromKey}_${period.toKey}.csv")
                }
            },
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodKind.values().forEach { k ->
                InvChip(k.label, k == kind) { if (k == PeriodKind.CUSTOM) onCustom() else onKind(k) }
            }
        }

        when {
            totals == null -> LoadingState()
            totals.bills == 0L && totals.writeOffCents == 0L -> EmptyState(
                icon = SamilliIcons.Box,
                title = "No sales in this period",
                message = "Bills and stock losses will appear here as soon as there are some.",
            )
            else -> Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeadlineSection(totals, previousTotals, comparison)

                if (period.days > 1) {
                    SamilliCard(Modifier.fillMaxWidth()) {
                        Text("Sales by day", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        SalesBars(dailySeries(summaries.orEmpty(), period.from, period.to))
                    }
                }

                val insights = remember(totals, summaries) { buildInsights(totals, summaries.orEmpty()) }
                if (insights.isNotEmpty()) {
                    SamilliCard(Modifier.fillMaxWidth()) {
                        Text("Highlights", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        insights.forEach {
                            Text("•  $it", color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }

                MixSection(totals)
                PaymentsSection(totals)
                if (totals.shifts.size > 1) ShiftsSection(totals)
                TopSection(catalog, stats, onTopSellers)
                LossSection(totals)
            }
        }
    }
}

@Composable
private fun HeadlineSection(totals: ReportTotals, previous: ReportTotals?, comparison: String) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("Sales", color = c.textSecondary, fontSize = 12.sp)
        Text(formatKsh(totals.salesCents), color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChangeChip(previous?.let { changePercent(totals.salesCents, it.salesCents) }, comparison)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            title = "Profit",
            value = formatKsh(totals.profitCents),
            sub = if (totals.salesCents > 0L) String.format(Locale.US, "%.0f%% margin", totals.marginPercent) else null,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            title = "Bills",
            value = fmtNumber(totals.bills),
            sub = previous?.takeIf { it.bills > 0L }?.let { "was ${fmtNumber(it.bills)}" },
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            title = "Average bill",
            value = formatKsh(totals.averageBillCents),
            sub = previous?.takeIf { it.bills > 0L }?.let { "was ${formatKsh(it.averageBillCents)}" },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MixSection(totals: ReportTotals) {
    val c = SamilliTheme.colors
    val sales = totals.salesCents.coerceAtLeast(1L)
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("Where sales come from", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StackedBar(listOf(totals.productSalesCents to c.gold, totals.serviceSalesCents to c.deltaText))
        LegendRow(c.gold, "Medicines and products", formatKsh(totals.productSalesCents), "${Math.round(totals.productSalesCents * 100.0 / sales)}%")
        LegendRow(c.deltaText, "Services", formatKsh(totals.serviceSalesCents), "${Math.round(totals.serviceSalesCents * 100.0 / sales)}%")
    }
}

@Composable
private fun PaymentsSection(totals: ReportTotals) {
    val c = SamilliTheme.colors
    val methods = (PaymentMethod.all + totals.payments.keys).distinct().filter { (totals.payments[it] ?: 0L) > 0L }
    if (methods.isEmpty()) return
    val colors = mapOf(
        PaymentMethod.CASH to c.deltaText,
        PaymentMethod.MPESA to c.gold,
        PaymentMethod.CARD to Color(0xFF5B8DB8),
    )
    val total = totals.payments.values.sum().coerceAtLeast(1L)
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("How customers paid", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        StackedBar(methods.map { (totals.payments[it] ?: 0L) to (colors[it] ?: c.textSecondary) })
        methods.forEach { code ->
            val amount = totals.payments[code] ?: 0L
            LegendRow(colors[code] ?: c.textSecondary, PaymentMethod.label(code), formatKsh(amount), "${Math.round(amount * 100.0 / total)}%")
        }
    }
}

@Composable
private fun ShiftsSection(totals: ReportTotals) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("By shift", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        totals.shifts.sortedByDescending { it.salesCents }.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${fmtNumber(s.bills)} bills", color = c.textSecondary, fontSize = 11.sp)
                }
                Text(formatKsh(s.salesCents), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TopSection(catalog: Catalog, stats: List<ProductStat>?, onSeeAll: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Top sellers", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!stats.isNullOrEmpty()) {
                Text(
                    text = "See all",
                    color = c.accentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onSeeAll).padding(4.dp),
                )
            }
        }
        when {
            stats == null -> Text("Loading…", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            stats.isEmpty() -> Text("No product sales in this period.", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            else -> {
                val top = stats.sortedByDescending { it.saleCents }.take(5)
                val peak = top.first().saleCents.coerceAtLeast(1L)
                top.forEachIndexed { index, s ->
                    val units = catalog.products.firstOrNull { it.id == s.productId }
                        ?.let { formatMixedUnits(s.unitsSoldBase, it.units) } ?: fmtNumber(s.unitsSoldBase)
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. ${s.productName}", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(formatKsh(s.saleCents), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(units, color = c.textSecondary, fontSize = 11.sp)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth((s.saleCents.toFloat() / peak.toFloat()).coerceIn(0.03f, 1f))
                                .height(4.dp)
                                .background(c.gold, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LossSection(totals: ReportTotals) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("Stock losses", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (totals.writeOffCents <= 0L && totals.returnedToSupplierCents <= 0L) {
            Text("No stock was written off in this period.", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            return@SamilliCard
        }
        if (totals.writeOffCents > 0L) {
            Text(
                text = formatKsh(totals.writeOffCents),
                color = c.urgent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (totals.salesCents > 0L) {
                Text(
                    text = String.format(Locale.US, "%.1f%% of sales, at cost", totals.writeOffCents * 100.0 / totals.salesCents),
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }
            totals.lossByReason.entries.filter { it.value > 0L }.sortedByDescending { it.value }.forEach { (code, amount) ->
                LegendRow(c.urgent, AdjustmentReason.label(code), formatKsh(amount), "")
            }
        }
        if (totals.returnedToSupplierCents > 0L) {
            Text(
                text = "Returned to suppliers (credited back): ${formatKsh(totals.returnedToSupplierCents)}",
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
