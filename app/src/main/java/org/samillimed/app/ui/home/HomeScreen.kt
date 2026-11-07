package org.samillimed.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.samillimed.app.R
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.reports.marginPercent
import org.samillimed.app.ui.alerts.alertDetail
import org.samillimed.app.ui.alerts.alertIcon
import org.samillimed.app.ui.alerts.alertTag
import org.samillimed.app.ui.alerts.alertUrgent
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.fmtNumber
import org.samillimed.app.ui.reports.MetricCard
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home screen, on live data.
 *
 * onMenuClick:    the menu (three lines) button in the header was tapped.
 * onTabSelected:  a bottom bar tab was tapped (0 Home, 1 Dispense, 2 Receive Stock, 3 Register).
 * onOpenAlerts:   the bell, the alert tiles or "View all" were tapped.
 * onOpenSales:    the sales card was tapped (today's bills).
 * onOpenReports:  the profit card was tapped.
 * onOpenInventory: the stock card was tapped.
 */
@Composable
fun HomeScreen(
    data: HomeData,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onTabSelected: (Int) -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onOpenSales: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenInventory: () -> Unit = {},
    isAdmin: Boolean = true,
) {
    val c = SamilliTheme.colors
    val alerts = data.alerts
    val today = data.date
    val bills = data.today.billCount

    Column(modifier.fillMaxSize().background(c.background)) {
        HomeHeader(
            notificationCount = alerts?.total ?: 0,
            onBellClick = onOpenAlerts,
            onMenuClick = onMenuClick,
            modifier = Modifier.zIndex(1f),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Greeting()

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---- alert tiles ------------------------------------------------------------
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val expiring = alerts?.expiringProducts
                    val expired = alerts?.expiredProducts ?: 0
                    AlertTile(
                        icon = SamilliIcons.CalendarClock,
                        pill = when {
                            alerts == null -> "…"
                            expired > 0 -> "EXPIRED"
                            (expiring ?: 0) > 0 -> "URGENT"
                            else -> "CLEAR"
                        },
                        pillBackground = if ((expiring ?: 0) > 0) c.urgent else c.deltaBg,
                        pillTextColor = if ((expiring ?: 0) > 0) c.onUrgent else c.deltaText,
                        count = expiring?.toString() ?: "–",
                        label = "Expiring soon",
                        caption = if (expired > 0) "$expired with expired stock" else "Within the next 30 days",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onOpenAlerts),
                    )
                    val low = alerts?.lowProducts
                    AlertTile(
                        icon = SamilliIcons.Box,
                        pill = when {
                            alerts == null -> "…"
                            (low ?: 0) > 0 -> "REORDER"
                            else -> "CLEAR"
                        },
                        pillBackground = if ((low ?: 0) > 0) c.reorder else c.deltaBg,
                        pillTextColor = if ((low ?: 0) > 0) c.onReorder else c.deltaText,
                        count = low?.toString() ?: "–",
                        label = "Low stock",
                        caption = "At or below reorder level",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onOpenAlerts),
                    )
                }

                // ---- sales and profit -------------------------------------------------------
                val salesChip = ofYesterday(data.today.totalSalesCents, data.yesterday.totalSalesCents)
                LiveTrendCard(
                    icon = SamilliIcons.Coins,
                    title = "Today’s sales",
                    amountCents = if (data.salesLoaded) data.today.totalSalesCents else null,
                    caption = when {
                        !data.salesLoaded -> "Loading…"
                        bills == 0L -> "No bills yet today"
                        else -> "$bills bill${if (bills == 1L) "" else "s"} so far"
                    },
                    chipText = salesChip.first,
                    chipGood = salesChip.second,
                    trend = data.weekSalesCents.map { it.toFloat() },
                    trendColor = c.sparkUp,
                    onClick = onOpenSales,
                )
                val profitChip = ofYesterday(data.today.profitCents, data.yesterday.profitCents)
                LiveTrendCard(
                    icon = SamilliIcons.Wallet,
                    title = "Today’s profit",
                    amountCents = if (data.salesLoaded) data.today.profitCents else null,
                    caption = if (data.today.totalSalesCents > 0L) {
                        String.format(Locale.US, "Gross profit · %.0f%% margin", data.today.marginPercent)
                    } else {
                        "Gross profit, before expenses"
                    },
                    chipText = profitChip.first,
                    chipGood = profitChip.second,
                    trend = data.weekProfitCents.map { it.toFloat() },
                    trendColor = c.sparkDown,
                    onClick = onOpenReports,
                )

                // ---- average bill and stock value -------------------------------------------
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val average = if (bills > 0L) data.today.totalSalesCents / bills else 0L
                    MetricCard(
                        title = "Average bill",
                        value = if (data.salesLoaded) formatKsh(average) else "–",
                        sub = if (data.salesLoaded && data.yesterday.billCount > 0L) {
                            "yesterday ${formatKsh(data.yesterday.totalSalesCents / data.yesterday.billCount)}"
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenSales),
                    )
                    MetricCard(
                        title = "Stock at cost",
                        value = alerts?.let { formatKsh(it.stockValueCents) } ?: "–",
                        sub = "open Inventory",
                        modifier = Modifier.weight(1f).clickable(onClick = onOpenInventory),
                    )
                }

                // ---- needs attention --------------------------------------------------------
                Spacer(Modifier.height(2.dp))
                SectionHeader(
                    title = "Needs attention",
                    action = "View all",
                    modifier = Modifier.clickable(onClick = onOpenAlerts),
                )
                when {
                    alerts == null -> SamilliCard(Modifier.fillMaxWidth()) {
                        Text("Checking stock…", color = c.textSecondary, fontSize = 13.sp)
                    }
                    alerts.items.isEmpty() -> SamilliCard(Modifier.fillMaxWidth()) {
                        Text("Nothing needs attention", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Nothing is expired, out of stock, low or close to expiry.",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    else -> {
                        alerts.items.take(5).forEach { a ->
                            val urgent = alertUrgent(a, today)
                            AttentionRow(
                                icon = alertIcon(a),
                                title = a.product.name,
                                detail = alertDetail(a, today),
                                tag = alertTag(a, today),
                                tagBackground = if (urgent) c.urgent else c.reorder,
                                tagTextColor = if (urgent) c.onUrgent else c.onReorder,
                                modifier = Modifier.clickable(onClick = onOpenAlerts),
                            )
                        }
                        if (alerts.items.size > 5) {
                            Text(
                                text = "+ ${alerts.items.size - 5} more",
                                color = c.sectionLink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable(onClick = onOpenAlerts),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        HomeBottomBar(isAdmin = isAdmin, selected = 0, onSelect = onTabSelected)
    }
}

/** "62% of yesterday", green once today has caught up. Null when yesterday had nothing to compare with. */
private fun ofYesterday(today: Long, yesterday: Long): Pair<String?, Boolean> =
    if (yesterday <= 0L) {
        null to false
    } else {
        "${Math.round(today * 100.0 / yesterday)}% of yesterday" to (today >= yesterday)
    }

/** Like TrendCard, but with a plain "of yesterday" chip, because today is only part of a day. */
@Composable
private fun LiveTrendCard(
    icon: ImageVector,
    title: String,
    amountCents: Long?,
    caption: String,
    chipText: String?,
    chipGood: Boolean,
    trend: List<Float>,
    trendColor: Color,
    onClick: () -> Unit,
) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
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
                    Text(
                        text = if (amountCents == null) "–" else fmtNumber(amountCents / 100L),
                        color = c.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
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
                if (chipText != null) {
                    Text(
                        text = chipText,
                        color = if (chipGood) c.deltaText else c.accentText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(if (chipGood) c.deltaBg else c.iconBg, RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }
                Sparkline(trend, trendColor)
            }
        }
    }
}

@Composable
private fun Greeting() {
    val c = SamilliTheme.colors
    val hello = remember {
        val h = LocalTime.now().hour
        when {
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)) {
        Text(text = hello, color = c.textMuted, fontSize = 12.sp)
        Text(text = date, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Logo as the first letter, "amilli" in cream, "med" in gold: same as the splash. */
@Composable
private fun Wordmark() {
    val c = SamilliTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.samillimed_logo),
            contentDescription = "SamilliMed",
            modifier = Modifier
                .height(44.dp)
                .aspectRatio(600f / 850f),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = c.onHeader)) { append("amilli") }
                withStyle(SpanStyle(color = c.gold)) { append("med") }
            },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit = {},
) {
    val c = SamilliTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x17F3ECD8))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp), tint = c.onHeader)
    }
}

@Composable
private fun BellButton(count: Int, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    Box(Modifier.size(40.dp)) {
        HeaderButton(SamilliIcons.Bell, "Notifications", onClick)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(18.dp)
                    .background(c.headerEnd, CircleShape)
                    .padding(2.dp)
                    .background(c.gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = c.onReorder,
                    textAlign = TextAlign.Center,
                    // Trim the font's extra top/bottom space so the number sits in the exact middle of the dot.
                    style = TextStyle(
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    notificationCount: Int,
    onBellClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)), shape)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BellButton(notificationCount, onBellClick)
            HeaderButton(SamilliIcons.Menu, "Menu", onMenuClick)
        }
    }
}

@Composable
private fun HomeBottomBar(isAdmin: Boolean, selected: Int, onSelect: (Int) -> Unit) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val tabs = if (isAdmin) {
        listOf(
            "Home" to SamilliIcons.Home,
            "Dispense" to SamilliIcons.Pill,
            "Receive Stock" to SamilliIcons.BoxPlus,
            "Register" to SamilliIcons.ClipboardPlus,
        )
    } else {
        listOf(
            "Home" to SamilliIcons.Home,
            "Dispense" to SamilliIcons.Pill,
            "Inventory" to SamilliIcons.Box,
            "Sales" to SamilliIcons.Coins,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, clip = false)
            .background(Brush.linearGradient(listOf(c.navStart, c.navEnd)), shape)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            val tint = if (on) c.gold else Color(0xB8F3ECD8)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(54.dp)
                        .height(30.dp)
                        .background(if (on) Color(0x2EE4AE42) else Color.Transparent, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp), tint = tint)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = label,
                    color = tint,
                    fontSize = 10.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
