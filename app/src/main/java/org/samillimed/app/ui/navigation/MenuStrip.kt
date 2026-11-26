package org.samillimed.app.ui.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** Whether the menu is kept as a small icon strip. Saved on this phone. */
object MenuPrefs {
    private const val PREFS = "menu_prefs"

    var strip by mutableStateOf(false)
        private set

    fun load(context: Context) {
        strip = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("strip", false)
    }

    fun set(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("strip", value).apply()
        strip = value
    }
}

private val stripRoutes = listOf(
    Route.Home, Route.Dispense, Route.ReceiveStock, Route.Register, Route.DrugIndex,
    Route.RegisteredProducts, Route.RegisteredServices, Route.Inventory, Route.Sales,
    Route.Expenses, Route.Reports, Route.Alerts, Route.Till, Route.StaffAccess, Route.Branding,
)

/** A narrow strip of icons down the left side. Its top button opens the full menu with names. */
@Composable
fun MenuStrip(
    current: Route,
    onNavigate: (Route) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    Column(
        modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(Brush.verticalGradient(listOf(c.headerStart, c.headerEnd)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StripButton(SamilliIcons.Menu, "Open the full menu", false, onExpand)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stripRoutes.filter { it.allowed() }.forEach { route ->
                StripButton(route.icon(), route.title, route == current) { onNavigate(route) }
            }
        }
        StripButton(Route.Settings.icon(), "Settings", current == Route.Settings) { onNavigate(Route.Settings) }
    }
}

@Composable
private fun StripButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.gold.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) c.gold else c.onHeader.copy(alpha = 0.8f),
        )
    }
}

/** The switch in the full menu that keeps the small icon strip on screen. */
@Composable
fun PinStripRow() {
    val context = LocalContext.current
    val c = SamilliTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Keep a small icon strip", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Its top button opens this menu", color = c.textSecondary, fontSize = 11.sp)
        }
        Switch(checked = MenuPrefs.strip, onCheckedChange = { MenuPrefs.set(context, it) })
    }
}
