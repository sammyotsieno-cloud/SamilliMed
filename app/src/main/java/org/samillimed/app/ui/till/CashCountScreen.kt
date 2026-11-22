package org.samillimed.app.ui.till

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.till.TillCount
import org.samillimed.app.data.till.TillRepository
import org.samillimed.app.data.till.TillSummary
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.theme.SamilliTheme
import java.util.UUID
import kotlin.math.abs

/** Count the cash in the till and compare it with what the bills say should be there. */
@Composable
fun CashCountScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showHistory) { showHistory = false }
    if (showHistory) {
        TillHistoryScreen(onBack = { showHistory = false }, modifier = modifier)
    } else {
        CountForm(onBack = onBack, onHistory = { showHistory = true }, modifier = modifier)
    }
}

private fun differenceLabel(d: Long): String = when {
    d == 0L -> "Balanced"
    d < 0L -> "Short by " + formatKsh(-d)
    else -> "Over by " + formatKsh(d)
}

@Composable
private fun differenceColor(d: Long): Color {
    val c = SamilliTheme.colors
    return when {
        d == 0L -> c.deltaText
        abs(d) <= 5000L -> c.accentText
        else -> c.urgent
    }
}

private fun summaryText(t: TillCount): String {
    val lines = mutableListOf(
        "CASH COUNT",
        fmtDayTime(t.createdAtMillis),
        "Counted by: ${t.createdBy.substringBefore('@')}",
        "",
        "Cash at the start: ${formatKsh(t.openingCents)}",
        "Cash sales (${t.cashBills} bills): ${formatKsh(t.cashSalesCents)}",
        "Refunds paid in cash: ${formatKsh(t.cashRefundsCents)}",
        "Expenses paid in cash: ${formatKsh(t.cashExpensesCents)}",
        "Cash you should have: ${formatKsh(t.expectedCents)}",
        "Cash you counted: ${formatKsh(t.countedCents)}",
        "Result: ${differenceLabel(t.differenceCents)}",
        "Taken out: ${formatKsh(t.withdrawnCents)}",
        "Left in the till: ${formatKsh(t.leftInTillCents)}",
    )
    if (t.note.isNotBlank()) lines.add("Reason: ${t.note}")
    return lines.joinToString("\n")
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, valueColor: Color? = null) {
    val c = SamilliTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            label,
            color = if (bold) c.textPrimary else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = valueColor ?: c.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CountDetails(t: TillCount) {
    Line("Cash at the start", formatKsh(t.openingCents))
    Line("Cash sales (${t.cashBills} bills)", "+ " + formatKsh(t.cashSalesCents))
    Line("Refunds paid in cash", "− " + formatKsh(t.cashRefundsCents))
    Line("Expenses paid in cash", "− " + formatKsh(t.cashExpensesCents))
    Line("Cash you should have", formatKsh(t.expectedCents), bold = true)
    Line("Cash you counted", formatKsh(t.countedCents), bold = true)
    Line("Result", differenceLabel(t.differenceCents), bold = true, valueColor = differenceColor(t.differenceCents))
    Line("Taken out afterwards", formatKsh(t.withdrawnCents))
    Line("Left in the till", formatKsh(t.leftInTillCents))
}

@Composable
private fun CountForm(onBack: () -> Unit, onHistory: () -> Unit, modifier: Modifier) {
    val c = SamilliTheme.colors
    val repository = remember { TillRepository() }
    val countId = rememberSaveable { UUID.randomUUID().toString() }
    var summary by remember { mutableStateOf<TillSummary?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var openingText by rememberSaveable { mutableStateOf("") }
    var countedText by rememberSaveable { mutableStateOf("") }
    var takenText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<TillCount?>(null) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(reload) {
        summary = null
        loadError = null
        repository.loadSummary(onResult = { summary = it }, onFailure = { loadError = it })
    }

    val s = summary
    val failed = loadError
    val done = saved

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Cash count",
            subtitle = "Check the cash in your till",
            onBack = onBack,
            actions = { if (CurrentUser.isAdmin) InvHeaderPill("Past counts", onClick = onHistory) },
        )
        when {
            done != null -> CountSaved(done, onDone = onBack)
            failed != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(failed, color = c.urgent, fontSize = 14.sp)
                Button(
                    onClick = { reload += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                ) { Text("Try again", fontWeight = FontWeight.Bold) }
            }
            s == null -> LoadingState()
            else -> {
                val opening = s.last?.leftInTillCents ?: (parseKshToCents(openingText) ?: 0L)
                val shouldHave = opening + s.cashSalesCents - s.cashRefundsCents - s.cashExpensesCents
                val counted = parseKshToCents(countedText)
                val countedEntered = countedText.isNotBlank() && counted != null
                val difference = if (countedEntered) (counted ?: 0L) - shouldHave else 0L
                val taken = parseKshToCents(takenText)
                val left = if (countedEntered && taken != null) (counted ?: 0L) - taken else null

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SamilliCard(Modifier.fillMaxWidth()) {
                        Text("What the bills say", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.last != null) "Since the last count, ${fmtDayTime(s.last.createdAtMillis)}"
                            else "Since the start of today",
                            color = c.textSecondary,
                            fontSize = 11.sp,
                        )
                        if (s.last != null) {
                            Line("Cash at the start (left in the till last time)", formatKsh(opening))
                        } else {
                            FormTextField(
                                value = openingText,
                                onValueChange = { openingText = it },
                                label = "Cash in the till when you started (KSh) *",
                                keyboardType = KeyboardType.Decimal,
                                supporting = "Count it now, or type 0 if the till started empty.",
                            )
                            Line("Cash at the start", formatKsh(opening))
                        }
                        Line("Cash sales (${s.cashBills} bills)", "+ " + formatKsh(s.cashSalesCents))
                        Line("Refunds paid in cash", "− " + formatKsh(s.cashRefundsCents))
                        Line("Expenses paid in cash", "− " + formatKsh(s.cashExpensesCents))
                        Line("Cash you should have", formatKsh(shouldHave), bold = true)
                    }

                    FormTextField(
                        value = countedText,
                        onValueChange = { countedText = it },
                        label = "Cash you counted (KSh) *",
                        keyboardType = KeyboardType.Decimal,
                        supporting = "Count every note and coin in the till.",
                    )
                    if (countedEntered) {
                        Text(
                            text = if (difference == 0L) "Balanced. The cash matches the bills." else differenceLabel(difference),
                            color = differenceColor(difference),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    FormTextField(
                        value = takenText,
                        onValueChange = { takenText = it },
                        label = "Cash you are taking out (optional)",
                        keyboardType = KeyboardType.Decimal,
                        supporting = "Money banked, or handed to the owner, after counting.",
                    )
                    if (left != null) {
                        Text("Left in the till: " + formatKsh(left), color = c.textSecondary, fontSize = 13.sp)
                    }

                    FormTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = if (difference != 0L) "Why is there a difference? *" else "Note (optional)",
                        singleLine = false,
                        minLines = 2,
                    )

                    error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }

                    Button(
                        enabled = !saving,
                        onClick = {
                            error = null
                            when {
                                s.last == null && openingText.isBlank() ->
                                    error = "Type the cash that was in the till when you started."
                                parseKshToCents(openingText) == null && s.last == null ->
                                    error = "The cash at the start is not a valid amount."
                                !countedEntered -> error = "Type the cash you counted."
                                taken == null -> error = "The cash you are taking out is not a valid amount."
                                (taken) > (counted ?: 0L) -> error = "You cannot take out more than you counted."
                                difference != 0L && note.isBlank() -> error = "Write a short reason for the difference."
                                else -> confirm = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (saving) "Saving…" else "Save cash count", fontWeight = FontWeight.Bold) }
                }

                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { if (!saving) confirm = false },
                        title = { Text("Save this count?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "You counted ${formatKsh(counted ?: 0L)}. The bills say you should have ${formatKsh(shouldHave)}. " +
                                    "Result: ${differenceLabel(difference)}. A saved count cannot be changed.",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !saving,
                                onClick = {
                                    confirm = false
                                    saving = true
                                    repository.saveCount(
                                        id = countId,
                                        summary = s,
                                        openingCents = opening,
                                        countedCents = counted ?: 0L,
                                        withdrawnCents = taken ?: 0L,
                                        note = note,
                                        onSuccess = { saved = it },
                                        onFailure = { message ->
                                            saving = false
                                            error = message
                                        },
                                    )
                                },
                            ) { Text("Yes, save", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Go back") } },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountSaved(t: TillCount, onDone: () -> Unit) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SamilliCard(Modifier.fillMaxWidth()) {
            Text("Count saved", color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                differenceLabel(t.differenceCents),
                color = differenceColor(t.differenceCents),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            CountDetails(t)
            if (t.note.isNotBlank()) {
                Text("Reason: ${t.note}", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summaryText(t))
                    }
                    context.startActivity(Intent.createChooser(send, "Share cash count"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
            ) { Text("Share summary", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

/** Every past count, newest first. Administrators only. */
@Composable
private fun TillHistoryScreen(onBack: () -> Unit, modifier: Modifier) {
    val c = SamilliTheme.colors
    val repository = remember { TillRepository() }
    val counts by produceState<List<TillCount>?>(null) {
        repository.historyFlow().collect { value = it }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = counts

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Past cash counts", subtitle = "The latest 60 counts", onBack = onBack)
        when {
            !CurrentUser.isAdmin -> EmptyState(
                icon = SamilliIcons.Wallet,
                title = "Administrators only",
                message = "Only an administrator can see past cash counts.",
            )
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                icon = SamilliIcons.Wallet,
                title = "No counts yet",
                message = "Counts you save will appear here.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    SamilliCard(
                        Modifier.fillMaxWidth().clickable { openId = t.id },
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(fmtDayTime(t.createdAtMillis), color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Counted by ${t.createdBy.substringBefore('@')}",
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    "Should have ${formatKsh(t.expectedCents)} · counted ${formatKsh(t.countedCents)}",
                                    color = c.textSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                differenceLabel(t.differenceCents),
                                color = differenceColor(t.differenceCents),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    list?.firstOrNull { it.id == openId }?.let { t ->
        AlertDialog(
            onDismissRequest = { openId = null },
            title = { Text(fmtDayTime(t.createdAtMillis), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Counted by ${t.createdBy.substringBefore('@')}", color = c.textSecondary, fontSize = 12.sp)
                    CountDetails(t)
                    if (t.note.isNotBlank()) {
                        Text("Reason: ${t.note}", color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openId = null }) { Text("Close") } },
        )
    }
}
