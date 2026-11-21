package org.samillimed.app.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.finance.Expense
import org.samillimed.app.data.finance.ExpenseCategory
import org.samillimed.app.data.finance.ExpenseRepository
import org.samillimed.app.data.finance.ExpenseStatus
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.reports.PeriodKind
import org.samillimed.app.data.reports.periodFor
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.home.StatusPill
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvChip
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.reports.LegendRow
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import java.util.UUID

/** Money paid out for running the business, by period, with totals by category. Admins only. */
@Composable
fun ExpensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    val repository = remember { ExpenseRepository() }
    val today = remember { LocalDate.now() }
    var kindName by rememberSaveable { mutableStateOf(PeriodKind.TODAY.name) }
    val kind = PeriodKind.values().firstOrNull { it.name == kindName } ?: PeriodKind.TODAY
    val period = remember(kind) { periodFor(kind, today, null, null) }
    val expenses by produceState<List<Expense>?>(null, period.fromKey, period.toKey) {
        value = null
        repository.expensesFlow(period.fromKey, period.toKey).collect { value = it }
    }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val list = expenses
    val active = list.orEmpty().filter { it.status == ExpenseStatus.ACTIVE }
    val total = active.sumOf { it.amountCents }
    val byCategory = active.groupBy { it.category }
        .map { (name, items) -> name to items.sumOf { e -> e.amountCents } }
        .sortedByDescending { it.second }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Expenses",
            subtitle = period.title(),
            onBack = onBack,
            actions = { InvHeaderPill("Add") { showAdd = true } },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(PeriodKind.TODAY, PeriodKind.YESTERDAY, PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.LAST_MONTH)
                .forEach { k -> InvChip(k.label, k == kind) { kindName = k.name } }
        }
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                icon = SamilliIcons.Wallet,
                title = "No expenses",
                message = "Tap Add to record rent, salaries, transport and other costs.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "summary") {
                    SamilliCard(Modifier.fillMaxWidth()) {
                        Text("Total expenses", color = c.textSecondary, fontSize = 12.sp)
                        Text(formatKsh(total), color = c.urgent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        byCategory.forEach { (name, amount) ->
                            LegendRow(
                                c.gold,
                                name,
                                formatKsh(amount),
                                if (total > 0L) "${Math.round(amount * 100.0 / total)}%" else "",
                            )
                        }
                    }
                }
                items(list.sortedByDescending { it.createdAtMillis }, key = { it.id }) { e ->
                    ExpenseRow(e, onClick = { openId = e.id })
                }
            }
        }
    }

    if (showAdd) AddExpenseDialog(repository, onDismiss = { showAdd = false })
    list?.firstOrNull { it.id == openId }?.let { e ->
        ExpenseDetailDialog(e, repository, onDismiss = { openId = null })
    }
}

@Composable
private fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    val voided = e.status == ExpenseStatus.VOIDED
    SamilliCard(Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(e.category, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (e.note.isNotBlank()) Text(e.note, color = c.textSecondary, fontSize = 12.sp)
                Text(
                    "${PaymentMethod.label(e.paymentMethod)} · ${fmtDayTime(e.createdAtMillis)}",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatKsh(e.amountCents),
                    color = if (voided) c.textSecondary else c.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (voided) StatusPill("VOIDED", c.urgent, c.onUrgent)
            }
        }
    }
}

@Composable
private fun AddExpenseDialog(repository: ExpenseRepository, onDismiss: () -> Unit) {
    val c = SamilliTheme.colors
    val id = remember { UUID.randomUUID().toString() }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var yesterday by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Amount (KSh) *",
                    keyboardType = KeyboardType.Decimal,
                )
                Text("What for *", color = c.textSecondary, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.defaults.forEach { name -> InvChip(name, category == name) { category = name } }
                }
                if (category == "Other") {
                    FormTextField(value = custom, onValueChange = { custom = it }, label = "Describe it *")
                }
                FormTextField(value = note, onValueChange = { note = it }, label = "Note (optional)")
                Text("Paid by", color = c.textSecondary, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.all.forEach { code -> InvChip(PaymentMethod.label(code), method == code) { method = code } }
                }
                Text("Date", color = c.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InvChip("Today", !yesterday) { yesterday = false }
                    InvChip("Yesterday", yesterday) { yesterday = true }
                }
                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val cents = parseKshToCents(amountText)
                    val label = if (category == "Other" && custom.isNotBlank()) custom else category
                    when {
                        cents == null || cents <= 0L -> error = "Enter an amount like 1500 or 250.50."
                        category.isBlank() -> error = "Choose what the money was spent on."
                        category == "Other" && custom.isBlank() -> error = "Describe what the money was spent on."
                        else -> {
                            saving = true
                            repository.addExpense(
                                id = id,
                                dateKey = (if (yesterday) LocalDate.now().minusDays(1) else LocalDate.now()).toString(),
                                amountCents = cents,
                                category = label,
                                note = note,
                                method = method,
                                onSuccess = { onDismiss() },
                                onFailure = { message ->
                                    saving = false
                                    error = message
                                },
                            )
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseDetailDialog(e: Expense, repository: ExpenseRepository, onDismiss: () -> Unit) {
    val c = SamilliTheme.colors
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val active = e.status == ExpenseStatus.ACTIVE
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(e.category, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatKsh(e.amountCents), color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Paid by ${PaymentMethod.label(e.paymentMethod)}", color = c.textSecondary, fontSize = 13.sp)
                Text("Dated ${e.dateKey}, recorded ${fmtDayTime(e.createdAtMillis)}", color = c.textSecondary, fontSize = 13.sp)
                Text("By ${e.createdBy.substringBefore('@')}", color = c.textSecondary, fontSize = 13.sp)
                if (e.note.isNotBlank()) Text(e.note, color = c.textPrimary, fontSize = 13.sp)
                if (!active) {
                    Text("Voided: ${e.voidReason}", color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else if (CurrentUser.isAdmin) {
                    FormTextField(value = reason, onValueChange = { reason = it }, label = "Reason, to void it")
                }
                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            if (active && CurrentUser.isAdmin) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        repository.voidExpense(
                            id = e.id,
                            reason = reason,
                            onSuccess = { onDismiss() },
                            onFailure = { message ->
                                saving = false
                                error = message
                            },
                        )
                    },
                ) { Text(if (saving) "Saving…" else "Void expense", color = c.urgent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") } },
    )
}
