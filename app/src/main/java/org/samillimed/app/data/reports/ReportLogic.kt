package org.samillimed.app.data.reports

import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.inventory.DispensingRecord
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.shortLabel
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodKind(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    MONTH("This month"),
    LAST_MONTH("Last month"),
    CUSTOM("Custom"),
}

private val fullDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** A stretch of days, both ends included. */
class Period(val kind: PeriodKind, val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1L).toInt()
    val fromKey: String get() = from.toString()
    val toKey: String get() = to.toString()

    /** "2026-11" when the period is a whole month or the month so far (the monthly totals then apply). */
    fun monthKey(today: LocalDate): String? {
        val ym = YearMonth.from(from)
        if (from != ym.atDay(1)) return null
        val wholeMonth = to == ym.atEndOfMonth()
        val monthSoFar = to == today && YearMonth.from(to) == ym
        return if (wholeMonth || monthSoFar) ym.toString() else null
    }

    fun title(): String =
        if (from == to) from.format(fullDay) else "${from.format(shortDay)} to ${to.format(fullDay)}"
}

fun periodFor(kind: PeriodKind, today: LocalDate, customFrom: LocalDate?, customTo: LocalDate?): Period =
    when (kind) {
        PeriodKind.TODAY -> Period(kind, today, today)
        PeriodKind.YESTERDAY -> today.minusDays(1).let { Period(kind, it, it) }
        PeriodKind.WEEK -> Period(kind, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
        PeriodKind.MONTH -> Period(kind, today.withDayOfMonth(1), today)
        PeriodKind.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { Period(kind, it.atDay(1), it.atEndOfMonth()) }
        PeriodKind.CUSTOM ->
            if (customFrom != null && customTo != null && !customFrom.isAfter(customTo)) {
                Period(kind, customFrom, customTo)
            } else {
                Period(kind, today, today)
            }
    }

/** The period it is compared with: same length, straight before it. */
fun Period.previous(): Period = when (kind) {
    PeriodKind.WEEK -> Period(kind, from.minusDays(7), to.minusDays(7))
    PeriodKind.MONTH -> {
        val ym = YearMonth.from(from).minusMonths(1)
        Period(kind, ym.atDay(1), ym.atDay(minOf(to.dayOfMonth, ym.lengthOfMonth())))
    }
    PeriodKind.LAST_MONTH -> {
        val ym = YearMonth.from(from).minusMonths(1)
        Period(kind, ym.atDay(1), ym.atEndOfMonth())
    }
    else -> {
        val length = days
        val end = from.minusDays(1)
        Period(kind, end.minusDays(length - 1L), end)
    }
}

fun Period.comparisonLabel(): String = when (kind) {
    PeriodKind.TODAY -> "vs yesterday"
    PeriodKind.YESTERDAY -> "vs the day before"
    PeriodKind.WEEK -> "vs last week"
    PeriodKind.MONTH, PeriodKind.LAST_MONTH -> "vs last month"
    PeriodKind.CUSTOM -> "vs the $days days before"
}

/** Reads 01/11/2026, 1-11-26 or 01112026. Returns null when it is not a real date. */
fun parseDay(text: String): LocalDate? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = if (t.any { !it.isDigit() }) {
        t.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
    } else if (t.length == 8) {
        listOf(t.substring(0, 2), t.substring(2, 4), t.substring(4, 8))
    } else {
        return null
    }
    if (parts.size != 3) return null
    return runCatching {
        val year = parts[2].toInt().let { if (it < 100) 2000 + it else it }
        LocalDate.of(year, parts[1].toInt(), parts[0].toInt())
    }.getOrNull()
}

// ---- totals -----------------------------------------------------------------------------------

class ShiftTotal(val id: String, val name: String, val code: String, val bills: Long, val salesCents: Long)

/** Several days added together. */
class ReportTotals(
    val salesCents: Long,
    val productSalesCents: Long,
    val serviceSalesCents: Long,
    val costCents: Long,
    val profitCents: Long,
    val bills: Long,
    val payments: Map<String, Long>,
    val shifts: List<ShiftTotal>,
    val writeOffCents: Long,
    val lossByReason: Map<String, Long>,
    val returnedToSupplierCents: Long,
    val afterShiftBills: Long,
) {
    val averageBillCents: Long get() = if (bills > 0L) salesCents / bills else 0L
    val marginPercent: Double get() = if (salesCents > 0L) profitCents * 100.0 / salesCents else 0.0
}

private fun mergeLongMaps(maps: List<Map<String, Long>>): Map<String, Long> {
    val out = HashMap<String, Long>()
    maps.forEach { m -> m.forEach { (k, v) -> out[k] = (out[k] ?: 0L) + v } }
    return out
}

fun List<DailySummary>.totals(): ReportTotals {
    val shiftIds = flatMap { it.shiftBills.keys }.distinct()
    val shifts = shiftIds.map { id ->
        ShiftTotal(
            id = id,
            name = firstNotNullOfOrNull { it.shiftNames[id] } ?: id,
            code = firstNotNullOfOrNull { it.shiftCodes[id] } ?: "",
            bills = sumOf { it.shiftBills[id] ?: 0L },
            salesCents = sumOf { it.shiftSales[id] ?: 0L },
        )
    }
    return ReportTotals(
        salesCents = sumOf { it.totalSalesCents },
        productSalesCents = sumOf { it.productSalesCents },
        serviceSalesCents = sumOf { it.serviceSalesCents },
        costCents = sumOf { it.costCents },
        profitCents = sumOf { it.profitCents },
        bills = sumOf { it.billCount },
        payments = mergeLongMaps(map { it.payments }),
        shifts = shifts,
        writeOffCents = sumOf { it.writeOffCostCents },
        lossByReason = mergeLongMaps(map { it.lossByReason }),
        returnedToSupplierCents = sumOf { it.returnedToSupplierCents },
        afterShiftBills = sumOf { it.afterShiftBills },
    )
}

/** Percent change from previous to current. Null when there is nothing to compare with. */
fun changePercent(current: Long, previous: Long): Double? =
    if (previous <= 0L) null else (current - previous) * 100.0 / previous

/** Sales for every day of the range, with 0 for days without bills. */
fun dailySeries(summaries: List<DailySummary>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Long>> {
    val sales = summaries.associate { it.dateKey to it.totalSalesCents }
    val out = ArrayList<Pair<LocalDate, Long>>()
    var day = from
    while (!day.isAfter(to)) {
        out.add(day to (sales[day.toString()] ?: 0L))
        day = day.plusDays(1)
    }
    return out
}

private fun pct(part: Long, whole: Long): Long =
    if (whole <= 0L) 0L else Math.round(part * 100.0 / whole)

/** A few plain-language observations about the period. */
fun buildInsights(totals: ReportTotals, summaries: List<DailySummary>): List<String> {
    val out = mutableListOf<String>()
    if (totals.bills == 0L) return out
    val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    val selling = summaries.filter { it.totalSalesCents > 0L }
    if (selling.size > 1) {
        val best = selling.maxByOrNull { it.totalSalesCents }
        if (best != null) {
            out.add("Best day: ${LocalDate.parse(best.dateKey).format(dayFormat)} with ${formatKsh(best.totalSalesCents)}.")
        }
    }
    if (totals.serviceSalesCents > 0L && totals.salesCents > 0L) {
        out.add("Services were ${pct(totals.serviceSalesCents, totals.salesCents)}% of sales.")
    }
    val paid = totals.payments.values.sum()
    val cash = totals.payments[PaymentMethod.CASH] ?: 0L
    if (paid > 0L && cash > 0L) out.add("Cash was ${pct(cash, paid)}% of payments.")
    if (totals.shifts.size > 1) {
        val busiest = totals.shifts.maxByOrNull { it.bills }
        if (busiest != null) out.add("Busiest shift: ${busiest.name}, ${pct(busiest.bills, totals.bills)}% of bills.")
    }
    if (totals.writeOffCents > 0L) {
        val share = if (totals.salesCents > 0L) ", ${pct(totals.writeOffCents, totals.salesCents)}% of sales" else ""
        out.add("Stock written off cost ${formatKsh(totals.writeOffCents)}$share.")
    }
    if (totals.afterShiftBills > 0L) {
        out.add("${totals.afterShiftBills} bill${if (totals.afterShiftBills == 1L) " was" else "s were"} made after the shift ended.")
    }
    return out
}

// ---- CSV sheets -------------------------------------------------------------------------------

private fun csv(value: String): String {
    var v = value.replace("\r", " ").replace("\n", " ").trim()
    if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
    return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

private fun cents(n: Long): String = BigDecimal.valueOf(n, 2).toPlainString()

private fun row(cells: List<String>): String = cells.joinToString(",") { csv(it) } + "\r\n"

/** One row per day: sales, cost, profit, payments and write-offs. */
fun buildDailyCsv(summaries: List<DailySummary>): String {
    val out = StringBuilder()
    out.append(
        row(
            listOf(
                "Date", "Bills", "Sales (KSh)", "Products (KSh)", "Services (KSh)",
                "Cost of stock sold (KSh)", "Gross profit (KSh)", "Margin %",
                "Cash (KSh)", "M-Pesa (KSh)", "Card (KSh)", "Other (KSh)", "Written off (KSh)",
            ),
        ),
    )
    summaries.sortedBy { it.dateKey }.forEach { d ->
        out.append(
            row(
                listOf(
                    d.dateKey,
                    d.billCount.toString(),
                    cents(d.totalSalesCents),
                    cents(d.productSalesCents),
                    cents(d.serviceSalesCents),
                    cents(d.costCents),
                    cents(d.profitCents),
                    if (d.totalSalesCents > 0L) String.format(Locale.US, "%.1f", d.marginPercent) else "",
                    cents(d.payments[PaymentMethod.CASH] ?: 0L),
                    cents(d.payments[PaymentMethod.MPESA] ?: 0L),
                    cents(d.payments[PaymentMethod.CARD] ?: 0L),
                    cents(d.payments[PaymentMethod.OTHER] ?: 0L),
                    cents(d.writeOffCostCents),
                ),
            ),
        )
    }
    return out.toString()
}

/** One row per product sold. unitsText turns the smallest-unit quantity into "3 Box · 4 Strip". */
fun buildProductsCsv(stats: List<ProductStat>, unitsText: (ProductStat) -> String): String {
    val out = StringBuilder()
    out.append(
        row(
            listOf(
                "Product", "Sold", "Sold (smallest unit)", "Sales (KSh)", "Cost (KSh)",
                "Gross profit (KSh)", "Margin %", "Bills",
            ),
        ),
    )
    stats.forEach { s ->
        out.append(
            row(
                listOf(
                    s.productName,
                    unitsText(s),
                    s.unitsSoldBase.toString(),
                    cents(s.saleCents),
                    cents(s.costCents),
                    cents(s.profitCents),
                    if (s.saleCents > 0L) String.format(Locale.US, "%.1f", s.profitCents * 100.0 / s.saleCents) else "",
                    s.lineCount.toString(),
                ),
            ),
        )
    }
    return out.toString()
}

/** One row per bill of a day. */
fun buildBillsCsv(bills: List<DispensingRecord>): String {
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    val out = StringBuilder()
    out.append(
        row(
            listOf(
                "Reference", "Visit", "Date", "Time", "Shift", "Cashier", "Payment", "Medicine lines",
                "Service lines", "Total (KSh)", "Cost (KSh)", "Gross profit (KSh)", "After shift", "Label",
            ),
        ),
    )
    bills.sortedBy { it.dispensedAtMillis }.forEach { b ->
        val time = Instant.ofEpochMilli(b.dispensedAtMillis).atZone(ZoneId.systemDefault()).format(timeFormat)
        out.append(
            row(
                listOf(
                    b.referenceNumber,
                    b.shortLabel,
                    b.dateKey,
                    time,
                    b.shiftName,
                    b.createdBy,
                    PaymentMethod.label(b.paymentMethod),
                    b.productLineCount.toString(),
                    b.serviceLineCount.toString(),
                    cents(b.totalAmountCents),
                    cents(b.totalPurchaseCostCents),
                    cents(b.grossProfitCents),
                    if (b.afterShift) "Yes" else "",
                    b.label,
                ),
            ),
        )
    }
    return out.toString()
}
