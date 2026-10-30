package org.samillimed.app.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.R
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** The icon shown for each screen, in the side menu and on "Coming soon" screens. */
fun Route.icon(): ImageVector = when (this) {
    Route.Home -> SamilliIcons.Home
    Route.Dispense -> SamilliIcons.Pill
    Route.ReceiveStock -> SamilliIcons.BoxPlus
    Route.Register -> SamilliIcons.ClipboardPlus
    Route.DrugIndex -> MoreIcons.BookOpen
    Route.RegisteredProducts -> MoreIcons.ProductTag
    Route.RegisteredServices -> MoreIcons.Stethoscope
    Route.Inventory -> SamilliIcons.Box
    Route.Sales -> SamilliIcons.Coins
    Route.Expenses -> SamilliIcons.Wallet
    Route.Reports -> MoreIcons.Reports
    Route.Alerts -> SamilliIcons.Bell
    Route.Settings -> MoreIcons.Settings
}

private val mainRoutes = listOf(Route.Home, Route.Dispense, Route.ReceiveStock, Route.Register)
private val catalogueRoutes = listOf(Route.DrugIndex, Route.RegisteredProducts, Route.RegisteredServices)
private val manageRoutes = listOf(Route.Inventory, Route.Sales, Route.Expenses, Route.Reports, Route.Alerts)

/**
 * Wraps a screen with the side menu. Open it with drawerState.open() (menu button).
 * Swiping only closes it, so it can't open by accident.
 */
@Composable
fun AppDrawer(
    drawerState: DrawerState,
    current: Route,
    onNavigate: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = { DrawerSheet(current, onNavigate) },
        content = content,
    )
}

@Composable
private fun DrawerSheet(current: Route, onNavigate: (Route) -> Unit) {
    val c = SamilliTheme.colors
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = c.background,
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Column(Modifier.fillMaxHeight()) {
            DrawerHeader()

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
            ) {
                DrawerLabel("MAIN")
                mainRoutes.forEach { route ->
                    DrawerItem(route.icon(), route.title, route == current) { onNavigate(route) }
                }
                Spacer(Modifier.height(10.dp))
                DrawerLabel("CATALOGUE")
                catalogueRoutes.forEach { route ->
                    DrawerItem(route.icon(), route.title, route == current) { onNavigate(route) }
                }
                Spacer(Modifier.height(10.dp))
                DrawerLabel("MANAGE")
                manageRoutes.forEach { route ->
                    DrawerItem(route.icon(), route.title, route == current) { onNavigate(route) }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Settings stays pinned at the bottom
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardBorder))
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 8.dp),
            ) {
                DrawerItem(
                    Route.Settings.icon(),
                    Route.Settings.title,
                    Route.Settings == current,
                ) { onNavigate(Route.Settings) }
            }
        }
    }
}

@Composable
private fun DrawerHeader() {
    val c = SamilliTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.samillimed_logo),
                contentDescription = "SamilliMed",
                modifier = Modifier
                    .height(40.dp)
                    .aspectRatio(600f / 850f),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.onHeader)) { append("amilli") }
                    withStyle(SpanStyle(color = c.gold)) { append("med") }
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pharmacy & Clinic Management",
            color = c.onHeader.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DrawerLabel(text: String) {
    val c = SamilliTheme.colors
    Text(
        text = text,
        color = c.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 26.dp, vertical = 6.dp),
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = SamilliTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.iconBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) c.iconTint else c.textSecondary,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
