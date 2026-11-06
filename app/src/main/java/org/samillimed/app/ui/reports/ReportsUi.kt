package org.samillimed.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    Text(
        text = text,
        color = c.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** "▲ +12% vs yesterday" in green, or "▼ −8% vs yesterday" in red. */
@Composable
internal fun ChangeChip(percent: Double?, comparison: String, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    if (percent == null) {
        Text(text = "No data $comparison", color = c.textSecondary, fontSize = 11.sp, modifier = modifier)
        return
    }
    val up = percent >= 0.0
    val shown = String.format(Locale.US, "%.0f", abs(percent))
    Text(
        text = "${if (up) "▲ +" else "▼ −"}$shown% $comparison",
        color = if (up) c.deltaText else c.urgent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(if (up) c.deltaBg else c.urgent.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
internal fun MetricCard(title: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    SamilliCard(modifier, contentPadding = PaddingValues(12.dp)) {
        Text(title, color = c.textSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (sub != null) {
            Text(sub, color = c.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** One bar per day. The best day is gold. */
@Composable
internal fun SalesBars(points: List<Pair<LocalDate, Long>>, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    if (points.isEmpty()) return
    val rawPeak = points.maxOf { it.second }
    val peak = rawPeak.coerceAtLeast(1L)
    val format = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { (_, value) ->
                val fraction = (value.toFloat() / peak.toFloat()).coerceIn(0.03f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when {
                                value > 0L && value == rawPeak -> c.gold
                                value > 0L -> c.deltaText
                                else -> c.cardBorder
                            },
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().first.format(format), color = c.textSecondary, fontSize = 11.sp)
            if (rawPeak > 0L) {
                Text("Best day ${formatKsh(rawPeak)}", color = c.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(points.last().first.format(format), color = c.textSecondary, fontSize = 11.sp)
        }
    }
}

/** A single bar split into coloured parts, for example products against services. */
@Composable
internal fun StackedBar(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(c.cardBorder),
    ) {
        parts.filter { it.first > 0L }.forEach { (value, color) ->
            Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color))
        }
    }
}

@Composable
internal fun LegendRow(color: Color, label: String, amount: String, share: String) {
    val c = SamilliTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(amount, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            text = share,
            color = c.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
    }
}
