package org.samillimed.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** Rounded card: soft shadow in light mode, thin border in both modes. */
@Composable
fun SamilliCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .then(
                if (c.isDark) Modifier
                else Modifier.shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = Color(0x40503C14),
                    spotColor = Color(0x40503C14),
                )
            )
            .background(c.card, shape)
            .border(1.dp, c.cardBorder, shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

/** Small rounded square holding an icon. */
@Composable
fun IconChip(icon: ImageVector, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(32.dp)
            .background(c.iconBg, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = c.iconTint)
    }
}

/** Short status label such as URGENT or REORDER. */
@Composable
fun StatusPill(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = textColor,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.2.sp,
    )
}

/** Line chart with a soft fill and a dot on the latest value. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 120.dp, height = 46.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.minOrNull() ?: 0f
        val hi = values.maxOrNull() ?: 1f
        val span = if (hi - lo > 0f) hi - lo else 1f
        val padX = 4.dp.toPx()
        val padY = 6.dp.toPx()
        val w = size.width - 2 * padX
        val h = size.height - 2 * padY
        val pts = values.mapIndexed { i, v ->
            Offset(padX + w * i / (values.size - 1), padY + h * (1f - (v - lo) / span))
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = color, radius = 3.6.dp.toPx(), center = pts.last())
    }
}

/** Half-width tile: icon, status pill, big number, label and caption. */
@Composable
fun AlertTile(
    icon: ImageVector,
    pill: String,
    pillBackground: Color,
    pillTextColor: Color,
    count: String,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    SamilliCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(icon)
            StatusPill(pill, pillBackground, pillTextColor)
        }
        Text(
            text = count,
            modifier = Modifier.padding(top = 10.dp),
            color = c.textPrimary,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 6.dp),
            color = c.label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = caption,
            modifier = Modifier.padding(top = 4.dp),
            color = c.textSecondary,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun DeltaChip(text: String, up: Boolean) {
    val c = SamilliTheme.colors
    Row(
        Modifier
            .background(c.deltaBg, RoundedCornerShape(50))
            .padding(start = 7.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (up) SamilliIcons.ArrowUp else SamilliIcons.ArrowDown,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = c.deltaText,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = text, color = c.deltaText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Full-width card for sales or expenses: amount on the left, change and trend line on the right. */
@Composable
fun TrendCard(
    icon: ImageVector,
    title: String,
    amount: String,
    caption: String,
    delta: String,
    deltaUp: Boolean,
    trend: List<Float>,
    trendColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    SamilliCard(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(icon)
                    Spacer(Modifier.width(8.dp))
                    Text(text = title, color = c.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "KSh",
                        modifier = Modifier.padding(end = 5.dp, bottom = 3.dp),
                        color = c.accentText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = amount, color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = caption,
                    modifier = Modifier.padding(top = 4.dp),
                    color = c.textSecondary,
                    fontSize = 11.5.sp,
                )
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    DeltaChip(delta, deltaUp)
                    Text(
                        text = "vs yesterday",
                        modifier = Modifier.padding(top = 3.dp),
                        color = c.textSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                Sparkline(trend, trendColor)
            }
        }
    }
}

/** One line of the "Needs attention" list. */
@Composable
fun AttentionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    tag: String,
    tagBackground: Color,
    tagTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    SamilliCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(text = title, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = detail,
                    modifier = Modifier.padding(top = 1.dp),
                    color = c.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = tag,
                modifier = Modifier
                    .background(tagBackground, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                color = tagTextColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Section title with a link on the right. */
@Composable
fun SectionHeader(title: String, action: String, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(text = action, color = c.sectionLink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
