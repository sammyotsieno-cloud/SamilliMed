package org.samillimed.app.ui.stockcheck

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.stockcheck.CheckLineStatus
import org.samillimed.app.data.stockcheck.Correction
import org.samillimed.app.data.stockcheck.CorrectionOp
import org.samillimed.app.data.stockcheck.LineUpdate
import org.samillimed.app.data.stockcheck.StockCheck
import org.samillimed.app.data.stockcheck.StockCheckLine
import org.samillimed.app.data.stockcheck.StockCheckRepository
import org.samillimed.app.data.stockcheck.StockCheckStatus
import org.samillimed.app.data.stockcheck.StockCheckType
import org.samillimed.app.data.stockcheck.opsValueCents
import org.samillimed.app.data.stockcheck.planCorrection
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.theme.SamilliTheme

private class ReviewRow(
    val line: StockCheckLine,
    val product: Product?,
    val expected: Long,
    val difference: Long,
    val ops: List<CorrectionOp>,
    val leftover: Long,
    val valueCents: Long,
    val settled: Boolean,
)

private fun amountText(base: Long, product: Product?): String =
    if (product != null && product.units.isNotEmpty()) formatMixedUnits(base, product.units) else base.toString()

private fun differenceText(d: Long, product: Product?): String = when {
    d == 0L -> "Matches"
    d < 0L -> "Short by " + amountText(-d, product)
    else -> "Extra: " + amountText(d, product)
}

@Composable
private fun differenceColor(d: Long): Color {
    val c = SamilliTheme.colors
    return if (d == 0L) c.deltaText else if (d < 0L) c.urgent else c.accentText
}

/** Counts waiting for review, and past ones. Administrators only. */
@Composable
internal fun ReviewList(repository: StockCheckRepository, onOpen: (String) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    val c = SamilliTheme.colors
    val checks by produceState<List<StockCheck>?>(null) { repository.checksFlow().collect { value = it } }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = checks
    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Review stock checks", subtitle = "Compare counts with the records", onBack = onBack)
        when {
            !CurrentUser.isAdmin -> EmptyState(SamilliIcons.Box, "Administrators only", "Only an administrator can review a stock check.")
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(SamilliIcons.Box, "Nothing to review", "Counts that are sent for review appear here.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { ch ->
                    SamilliCard(Modifier.fillMaxWidth().clickable { onOpen(ch.id) }, contentPadding = PaddingValues(12.dp)) {
                        Text("${StockCheckType.label(ch.type)} · ${ch.lineCount} products", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${fmtDayTime(ch.createdAtMillis)} · counted by ${ch.createdBy.substringBefore('@')}", color = c.textSecondary, fontSize = 11.sp)
                        Text(
                            if (ch.status == StockCheckStatus.REVIEWED) "Reviewed" else "Waiting for your review",
                            color = if (ch.status == StockCheckStatus.REVIEWED) c.deltaText else c.accentText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** One check: what was counted against what the records say, and which differences to fix. */
@Composable
internal fun ReviewDetail(
    catalog: Catalog,
    batches: List<StockBatch>?,
    repository: StockCheckRepository,
    checkId: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var lines by remember { mutableStateOf<List<StockCheckLine>?>(null) }
    var changes by remember { mutableStateOf<Map<String, Long>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var applying by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val fix = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(checkId, reload) {
        lines = null
        changes = null
        error = null
        repository.loadLines(
            checkId,
            onResult = { l ->
                lines = l.sortedBy { it.productName.lowercase() }
                repository.loadChangesSince(
                    l.filter { it.status == CheckLineStatus.PENDING }.map { it.productId to it.countedAtMillis },
                    onResult = { changes = it },
                    onFailure = { error = it },
                )
            },
            onFailure = { error = it },
        )
    }

    val l = lines
    val ch = changes
    val b = batches
    val rows = remember(l, ch, b, catalog.products) {
        if (l == null || ch == null || b == null) {
            null
        } else {
            val byProduct = b.groupBy { it.productId }
            l.map { line ->
                val product = catalog.products.firstOrNull { it.id == line.productId }
                if (line.status != CheckLineStatus.PENDING) {
                    ReviewRow(line, product, line.expectedBase, line.differenceBase, emptyList(), 0L, line.valueCents, true)
                } else {
                    val own = byProduct[line.productId].orEmpty()
                    val onHand = own.sumOf { it.remainingQuantity }
                    val expected = onHand - (ch[line.productId] ?: 0L)
                    val diff = line.countedBase - expected
                    val plan = planCorrection(diff, own)
                    ReviewRow(line, product, expected, diff, plan.ops, plan.leftover, opsValueCents(plan.ops, own), false)
                }
            }
        }
    }
    val pending = rows.orEmpty().filter { !it.settled }
    val mismatches = pending.filter { it.difference != 0L }
    val toFix = mismatches.filter { fix[it.line.id] ?: (it.leftover == 0L && it.ops.isNotEmpty()) }
    val shortValue = mismatches.filter { it.difference < 0L }.sumOf { it.valueCents }
    val extraValue = mismatches.filter { it.difference > 0L }.sumOf { it.valueCents }

    fun finish() {
        val all = rows.orEmpty()
        val fixedIds = toFix.map { it.line.id }.toSet()
        val updates = pending.filter { it.line.id !in fixedIds }.map {
            LineUpdate(
                it.line.id,
                if (it.difference == 0L) CheckLineStatus.MATCHED else CheckLineStatus.SKIPPED,
                it.expected,
                it.difference,
                it.valueCents,
            )
        }
        repository.finishReview(
            checkId = checkId,
            updates = updates,
            appliedLines = all.count { it.line.status == CheckLineStatus.APPLIED } + toFix.size,
            skippedLines = all.count { it.line.status == CheckLineStatus.SKIPPED } + updates.count { it.status == CheckLineStatus.SKIPPED },
            checkedAt = all.associate { it.line.productId to it.line.countedAtMillis },
            onSuccess = {
                applying = 0
                Toast.makeText(context, "Check reviewed", Toast.LENGTH_SHORT).show()
                onBack()
            },
            onFailure = { message ->
                applying = 0
                error = message
            },
        )
    }

    fun run() {
        applying = 1
        error = null
        val items = toFix.map { Correction(it.line.id, it.line.productId, it.line.countedBase, it.expected, it.ops) }
        repository.applyAll(
            reference = "SC-" + checkId.take(6).uppercase(),
            items = items,
            onProgress = { applying = it },
            onSuccess = { finish() },
            onFailure = { message ->
                applying = 0
                error = message
                reload += 1
            },
        )
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Review", subtitle = "Counted against the records", onBack = onBack)
        val failed = error
        when {
            !CurrentUser.isAdmin -> EmptyState(SamilliIcons.Box, "Administrators only", "Only an administrator can review a stock check.")
            failed != null && rows == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(failed, color = c.urgent, fontSize = 14.sp)
                Button(
                    onClick = { reload += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                ) { Text("Try again", fontWeight = FontWeight.Bold) }
            }
            rows == null -> LoadingState()
            else -> {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SamilliCard(Modifier.fillMaxWidth()) {
                            Text("${rows.size} products counted", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${pending.count { it.difference == 0L }} match, ${mismatches.size} differ",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                            )
                            if (shortValue > 0L) Text("Short, at cost: " + formatKsh(shortValue), color = c.urgent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (extraValue > 0L) Text("Extra, at cost: " + formatKsh(extraValue), color = c.accentText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Stock is only changed for the differences you switch on below.",
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    items(rows, key = { it.line.id }) { r ->
                        SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.line.productName, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Counted: " + amountText(r.line.countedBase, r.product), color = c.textSecondary, fontSize = 12.sp)
                                    Text("Records said: " + amountText(r.expected, r.product), color = c.textSecondary, fontSize = 12.sp)
                                    Text(
                                        differenceText(r.difference, r.product) + if (r.difference != 0L) " (" + formatKsh(r.valueCents) + " at cost)" else "",
                                        color = differenceColor(r.difference),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (r.settled) {
                                        Text(
                                            when (r.line.status) {
                                                CheckLineStatus.APPLIED -> "Stock was corrected"
                                                CheckLineStatus.SKIPPED -> "Left as it was"
                                                else -> "Matched"
                                            },
                                            color = c.textMuted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    if (!r.settled && r.leftover > 0L) {
                                        Text(
                                            "Only part of this can be added back to the batches. Record the rest with Receive Stock as opening stock.",
                                            color = c.accentText,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                                if (!r.settled && r.difference != 0L && r.ops.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Fix stock", color = c.textSecondary, fontSize = 10.sp)
                                        Switch(
                                            checked = fix[r.line.id] ?: (r.leftover == 0L),
                                            onCheckedChange = { fix[r.line.id] = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp + navBottom)) {
                    failed?.let { Text(it, color = c.urgent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
                    if (pending.isEmpty()) {
                        Text("This check has been reviewed.", color = c.textSecondary, fontSize = 13.sp)
                    } else {
                        Button(
                            enabled = applying == 0,
                            onClick = { confirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (applying > 0) "Working… $applying of ${toFix.size.coerceAtLeast(1)}"
                                else if (toFix.isEmpty()) "Close this check without changing stock"
                                else "Fix stock for ${toFix.size} product${if (toFix.size == 1) "" else "s"}",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (toFix.isEmpty()) "Close this check?" else "Fix the stock?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (toFix.isEmpty()) {
                        "No stock will be changed. The differences are kept on record and the check is closed."
                    } else {
                        "The stock of ${toFix.size} product${if (toFix.size == 1) "" else "s"} will be changed to match the count. " +
                            "Each change is recorded in the stock card. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (toFix.isEmpty()) {
                        applying = 1
                        finish()
                    } else {
                        run()
                    }
                }) { Text("Yes", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Go back") } },
        )
    }
}
