package org.samillimed.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.inventory.StockStatus
import org.samillimed.app.data.inventory.daysUntilExpiry
import org.samillimed.app.ui.home.StatusPill
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val invDayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val invDayTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm")

internal fun fmtNumber(n: Long): String = String.format(Locale.US, "%,d", n)

internal fun asLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun fmtDay(millis: Long): String = asLocalDate(millis).format(invDayFormat)

internal fun fmtDayTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(invDayTimeFormat)

/** "31 May 2027 · 210 days left", "5 Nov 2026 · expires today", "1 Oct 2026 · expired 35 days ago". */
internal fun expiryLabel(expiryMillis: Long?, today: LocalDate = LocalDate.now()): String {
    if (expiryMillis == null) return "No expiry date"
    val days = daysUntilExpiry(expiryMillis, today)
    val date = fmtDay(expiryMillis)
    return when {
        days < 0L -> "$date · expired ${-days} day${if (-days == 1L) "" else "s"} ago"
        days == 0L -> "$date · expires today"
        days == 1L -> "$date · expires tomorrow"
        else -> "$date · $days days left"
    }
}

/** Wraps a value for CSV and stops a spreadsheet treating it as a formula. */
internal fun csvEscape(value: String): String {
    var v = value.replace("\r", " ").replace("\n", " ").trim()
    if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
    return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

@Composable
internal fun StockStatusPill(status: StockStatus) {
    val c = SamilliTheme.colors
    val (text, background, textColor) = when (status) {
        StockStatus.EXPIRED_STOCK -> Triple("EXPIRED STOCK", c.urgent, c.onUrgent)
        StockStatus.OUT -> Triple("OUT OF STOCK", c.urgent, c.onUrgent)
        StockStatus.LOW -> Triple("LOW", c.reorder, c.onReorder)
        StockStatus.EXPIRING_SOON -> Triple("EXPIRING SOON", c.reorder, c.onReorder)
        StockStatus.OK -> Triple("OK", c.deltaBg, c.deltaText)
    }
    StatusPill(text, background, textColor)
}

/** The small rounded button used in the green header. */
@Composable
internal fun InvHeaderPill(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x17F3ECD8))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = c.onHeader.copy(alpha = if (enabled) 1f else 0.4f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** A selectable pill (filters, units, reasons). */
@Composable
internal fun InvChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .background(if (on) c.gold else c.card, shape)
            .border(1.dp, if (on) c.gold else c.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (on) c.onReorder else c.textPrimary,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
    )
}
