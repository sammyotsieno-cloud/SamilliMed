package org.samillimed.app.ui.reports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.writeTextToUri
import org.samillimed.app.data.inventory.BillStatus
import org.samillimed.app.data.reports.SalesReturn
import org.samillimed.app.data.inventory.DispensingLine
import org.samillimed.app.data.inventory.DispensingRecord
import org.samillimed.app.data.inventory.DispensingServiceLine
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.shortLabel
import org.samillimed.app.data.reports.ReportsReader
import org.samillimed.app.data.reports.buildBillsCsv
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.home.StatusPill
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvChip
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.inventory.fmtNumber
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

private fun timeOf(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

/** The bills of one day as summaries. A bill opens into its details only when tapped. */
@Composable
fun BillsScreen(
    catalog: Catalog,
    reader: ReportsReader,
    initialDateKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var dateKey by rememberSaveable { mutableStateOf(initialDateKey) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val date = runCatching { LocalDate.parse(dateKey) }.getOrDefault(today)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bills by produceState<List<DispensingRecord>?>(null, dateKey) {
        value = null
        reader.billsFlow(dateKey).collect { value = it }
    }
    val open = bills?.firstOrNull { it.id == openId }
    BackHandler(enabled = openId != null) { openId = null }

    val latestBills by rememberUpdatedState(bills.orEmpty())
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val ok = writeTextToUri(context, uri, buildBillsCsv(latestBills))
            Toast.makeText(context, if (ok) "Bills sheet saved" else "Could not save the file", Toast.LENGTH_LONG).show()
        }
    }

    if (open != null) {
        BillDetailScreen(bill = open, reader = reader, onBack = { openId = null }, modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Bills",
            subtitle = date.format(dayFormat),
            onBack = onBack,
            actions = {
                InvHeaderPill("Export", enabled = !bills.isNullOrEmpty() && CurrentUser.isAdmin) {
                    exportLauncher.launch("samillimed-bills-$dateKey.csv")
                }
            },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InvChip("◀ Previous day", false) { dateKey = date.minusDays(1).toString() }
            InvChip("Today", date == today) { dateKey = today.toString() }
            if (date.isBefore(today)) InvChip("Next day ▶", false) { dateKey = date.plusDays(1).toString() }
        }

        val list = bills
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                icon = SamilliIcons.Box,
                title = "No bills on this day",
                message = "Bills saved from Dispense will appear here.",
            )
            else -> {
                val total = list.sumOf { it.totalAmountCents }
                SamilliCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total for the day", color = c.textSecondary, fontSize = 12.sp)
                            Text(formatKsh(total), color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${fmtNumber(list.size.toLong())} bills", color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("average ${formatKsh(total / list.size)}", color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { bill -> BillRow(bill, onClick = { openId = bill.id }) }
                }
            }
        }
    }
}

@Composable
private fun BillRow(bill: DispensingRecord, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(74.dp)) {
                Text(bill.shortLabel, color = c.accentText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(timeOf(bill.dispensedAtMillis), color = c.textSecondary, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f)) {
                val items = buildList {
                    if (bill.productLineCount > 0) add("${bill.productLineCount} item${if (bill.productLineCount == 1) "" else "s"}")
                    if (bill.serviceLineCount > 0) add("${bill.serviceLineCount} service${if (bill.serviceLineCount == 1) "" else "s"}")
                }.joinToString(" · ")
                Text(items, color = c.textPrimary, fontSize = 13.sp)
                val extra = listOf(PaymentMethod.label(bill.paymentMethod), bill.label).filter { it.isNotBlank() }.joinToString(" · ")
                Text(extra, color = c.textSecondary, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatKsh(bill.totalAmountCents), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (bill.afterShift) StatusPill("AFTER SHIFT", c.reorder, c.onReorder)
            }
        }
    }
}

/** One bill with every line, the payment and its cost and profit. */
@Composable
private fun BillDetailScreen(
    bill: DispensingRecord,
    reader: ReportsReader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val lines by produceState<List<DispensingLine>?>(null, bill.id) {
        reader.billLinesFlow(bill.id).collect { value = it }
    }
    val serviceLines by produceState<List<DispensingServiceLine>?>(null, bill.id) {
        reader.billServiceLinesFlow(bill.id).collect { value = it }
    }
    val returns by produceState<List<SalesReturn>?>(null, bill.id) {
        reader.billReturnsFlow(bill.id).collect { value = it }
    }
    var showReturn by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showReturn) { showReturn = false }
    if (showReturn) {
        ReturnScreen(
            bill = bill,
            lines = lines.orEmpty(),
            serviceLines = serviceLines.orEmpty(),
            onBack = { showReturn = false },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = bill.shortLabel, subtitle = bill.referenceNumber, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SamilliCard(Modifier.fillMaxWidth()) {
                Text("Total", color = c.textSecondary, fontSize = 12.sp)
                Text(formatKsh(bill.totalAmountCents), color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                DetailLine("Paid by", PaymentMethod.label(bill.paymentMethod))
                if (bill.paymentMethod == PaymentMethod.CASH) {
                    DetailLine("Cash received", formatKsh(bill.amountReceivedCents))
                    DetailLine("Change given", formatKsh(bill.changeGivenCents))
                }
                DetailLine("Time", fmtDayTime(bill.dispensedAtMillis))
                DetailLine("Shift", "${bill.shiftName} · bill ${bill.shiftNumber} of the shift, ${bill.dayNumber} of the day")
                DetailLine("Served by", bill.createdBy.substringBefore('@').ifBlank { "Unknown" })
                if (bill.label.isNotBlank()) DetailLine("Label", bill.label)
                if (bill.afterShift) DetailLine("Note", "Made after the shift ended")
                if (bill.status == BillStatus.VOIDED) {
                    DetailLine("Status", "VOIDED: ${bill.voidReason}")
                } else if (bill.status == BillStatus.PARTIALLY_RETURNED) {
                    DetailLine("Status", "Partly returned: ${formatKsh(bill.returnedCents)} refunded")
                }
            }

            SamilliCard(Modifier.fillMaxWidth()) {
                Text("Items", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val medicineLines = lines
                val serviceList = serviceLines
                if (medicineLines == null || serviceList == null) {
                    Text("Loading…", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    medicineLines.sortedBy { it.productNameSnapshot.lowercase() }.forEach { line ->
                        ItemRow(
                            name = line.productNameSnapshot,
                            detail = "${fmtNumber(line.quantityDispensed)} ${line.dispensingUnit.unitName} × ${formatKsh(line.actualSellingPricePerUnitCents)}",
                            total = formatKsh(line.totalSaleValueCents),
                        )
                    }
                    serviceList.sortedBy { it.serviceNameSnapshot.lowercase() }.forEach { line ->
                        ItemRow(
                            name = line.serviceNameSnapshot,
                            detail = "Service · ${fmtNumber(line.quantity)} × ${formatKsh(line.priceCents)}",
                            total = formatKsh(line.totalCents),
                        )
                    }
                    if (medicineLines.isEmpty() && serviceList.isEmpty()) {
                        Text("No lines found.", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            if (CurrentUser.isAdmin) SamilliCard(Modifier.fillMaxWidth()) {
                Text("Cost and profit", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                DetailLine("Cost of stock sold", formatKsh(bill.totalPurchaseCostCents))
                DetailLine("Gross profit", formatKsh(bill.grossProfitCents))
                if (bill.totalAmountCents > 0L) {
                    DetailLine("Margin", String.format(Locale.US, "%.0f%%", bill.grossProfitCents * 100.0 / bill.totalAmountCents))
                }
            }
            ReturnsSection(
                bill = bill,
                returns = returns.orEmpty(),
                ready = lines != null && serviceLines != null,
                onStart = { showReturn = true },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val c = SamilliTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ItemRow(name: String, detail: String, total: String) {
    val c = SamilliTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(name, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = c.textSecondary, fontSize = 11.sp)
        }
        Text(total, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
