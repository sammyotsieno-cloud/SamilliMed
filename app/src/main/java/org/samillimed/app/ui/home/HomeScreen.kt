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
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Home screen. All numbers and items below are SAMPLE DATA from the design mockup;
 * they get replaced when the real data layer exists.
 *
 * onMenuClick:    the menu (three lines) button in the header was tapped.
 * onTabSelected:  a bottom bar tab was tapped (0 Home, 1 Dispense, 2 Receive Stock, 3 Register).
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onTabSelected: (Int) -> Unit = {},
) {
    val c = SamilliTheme.colors

    Column(modifier.fillMaxSize().background(c.background)) {
        HomeHeader(
            notificationCount = 3,
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
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlertTile(
                        icon = SamilliIcons.CalendarClock,
                        pill = "URGENT",
                        pillBackground = c.urgent,
                        pillTextColor = c.onUrgent,
                        count = "2",
                        label = "Expiring soon",
                        caption = "Within the next 30 days",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    AlertTile(
                        icon = SamilliIcons.Box,
                        pill = "REORDER",
                        pillBackground = c.reorder,
                        pillTextColor = c.onReorder,
                        count = "5",
                        label = "Low stock",
                        caption = "Below reorder level",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                TrendCard(
                    icon = SamilliIcons.Coins,
                    title = "Today’s sales",
                    amount = "12,450",
                    caption = "Total revenue today",
                    delta = "+12%",
                    deltaUp = true,
                    trend = listOf(1f, 2.2f, 1.7f, 3.4f, 3.2f, 4.6f, 5.4f),
                    trendColor = c.sparkUp,
                )
                TrendCard(
                    icon = SamilliIcons.Wallet,
                    title = "Today’s expenses",
                    amount = "2,100",
                    caption = "Total expenses today",
                    delta = "−8%",
                    deltaUp = false,
                    trend = listOf(5f, 4.4f, 4.7f, 3.0f, 3.3f, 2.4f, 2.1f),
                    trendColor = c.sparkDown,
                )

                Spacer(Modifier.height(2.dp))
                SectionHeader(title = "Needs attention", action = "View all")
                AttentionRow(
                    icon = SamilliIcons.CalendarClock,
                    title = "Amoxicillin 500 mg",
                    detail = "Expires in 12 days · 3 packs",
                    tag = "12 days",
                    tagBackground = c.urgent,
                    tagTextColor = c.onUrgent,
                )
                AttentionRow(
                    icon = SamilliIcons.Box,
                    title = "Paracetamol 500 mg",
                    detail = "8 left · reorder level 20",
                    tag = "Low",
                    tagBackground = c.reorder,
                    tagTextColor = c.onReorder,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        HomeBottomBar(selected = 0, onSelect = onTabSelected)
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
private fun BellButton(count: Int) {
    val c = SamilliTheme.colors
    Box(Modifier.size(40.dp)) {
        HeaderButton(SamilliIcons.Bell, "Notifications")
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
            BellButton(notificationCount)
            HeaderButton(SamilliIcons.Menu, "Menu", onMenuClick)
        }
    }
}

@Composable
private fun HomeBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val tabs = listOf(
        "Home" to SamilliIcons.Home,
        "Dispense" to SamilliIcons.Pill,
        "Receive Stock" to SamilliIcons.BoxPlus,
        "Register" to SamilliIcons.ClipboardPlus,
    )
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
