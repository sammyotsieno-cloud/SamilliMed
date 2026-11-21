package org.samillimed.app.ui.reports

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.reports.ReportTotals
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.theme.SamilliTheme

val ReportTotals.netProfitCents: Long get() = profitCents - expensesCents

/** Gross profit, the expenses recorded in the period, and what is really left. */
@Composable
internal fun ExpensesSection(totals: ReportTotals) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Text("Expenses and net profit", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Gross profit", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(formatKsh(totals.profitCents), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Expenses", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("− " + formatKsh(totals.expensesCents), color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Net profit", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                formatKsh(totals.netProfitCents),
                color = if (totals.netProfitCents < 0L) c.urgent else c.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (totals.expensesCents <= 0L) {
            Text(
                "No expenses recorded in this period, so net profit equals gross profit until costs such as rent and salaries are entered.",
                color = c.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            totals.expenseByCategory.entries.filter { it.value > 0L }.sortedByDescending { it.value }.forEach { (name, amount) ->
                LegendRow(c.gold, name, formatKsh(amount), "${Math.round(amount * 100.0 / totals.expensesCents)}%")
            }
        }
    }
}
