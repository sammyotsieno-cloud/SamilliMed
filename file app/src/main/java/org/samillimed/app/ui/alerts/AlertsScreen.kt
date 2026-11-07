package org.samillimed.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.inventory.AlertKind
import org.samillimed.app.data.inventory.StockAlert
import org.samillimed.app.data.inventory.StockAlerts
import org.samillimed.app.data.inventory.daysUntilExpiry
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.AttentionRow
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.inventory.InvHeaderPill
import org.samillimed.app.ui.inventory.expiryLabel
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate

// ---- shared by Home and Alerts ------------------------------------------------------------------

internal fun alertIcon(a: StockAlert): ImageVector = when (a.kind) {
    AlertKind.EXPIRED, AlertKind.EXPIRING -> SamilliIcons.CalendarClock
    AlertKind.OUT, AlertKind.LOW -> SamilliIcons.Box
}

internal fun alertDetail(a: StockAlert, today: LocalDate): String {
    val units = a.product.units
    val reorder = a.product.reorderLevel.toLong()
    return when (a.kind) {
        AlertKind.EXPIRED -> "${formatMixedUnits(a.expiredBase, units)} expired · remove from the shelf"
        AlertKind.OUT -> "Out of stock · reorder level ${formatMixedUnits(reorder, units)}"
        AlertKind.LOW -> "${formatMixedUnits(a.sellableBase, units)} left · reorder level ${formatMixedUnits(reorder, units)}"
        AlertKind.EXPIRING -> "${formatMixedUnits(a.sellableBase, units)} · " + expiryLabel(a.nearestExpiryMillis, today)
    }
}

internal fun alertTag(a: StockAlert, today: LocalDate): String = when (a.kind) {
    AlertKind.EXPIRED -> "Expired"
    AlertKind.OUT -> "Out"
    AlertKind.LOW -> "Low"
    AlertKind.EXPIRING -> {
        val days = a.nearestExpiryMillis?.let { daysUntilExpiry(it, today) }
        when {
            days == null -> "Soon"
            days <= 0L -> "Today"
            days == 1L -> "1 day"
            else -> "$days days"
        }
    }
}

/** Red for anything that needs action now; gold for the rest. */
internal fun alertUrgent(a: StockAlert, today: LocalDate): Boolean = when (a.kind) {
    AlertKind.EXPIRED, AlertKind.OUT -> true
    AlertKind.LOW -> false
    AlertKind.EXPIRING -> (a.nearestExpiryMillis?.let { daysUntilExpiry(it, today) } ?: 99L) <= 14L
}

// ---- the screen ---------------------------------------------------------------------------------

/** Everything that needs attention, grouped by reason. Open Inventory to write off or reorder. */
@Composable
fun AlertsScreen(
    alerts: StockAlerts?,
    onBack: () -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val today = remember { LocalDate.now() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Alerts",
            subtitle = when {
                alerts == null -> null
                alerts.total == 0 -> "All clear"
                else -> "${alerts.total} product${if (alerts.total == 1) "" else "s"} need attention"
            },
            onBack = onBack,
            actions = { InvHeaderPill("Inventory", onClick = onOpenInventory) },
        )

        when {
            alerts == null -> LoadingState()
            alerts.items.isEmpty() -> EmptyState(
                icon = SamilliIcons.Bell,
                title = "All clear",
                message = "Nothing is expired, out of stock, low or close to expiry. " +
                    "Low-stock alerts need a reorder level on the product.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val groups = listOf(
                    AlertKind.EXPIRED to "Expired stock on the shelf",
                    AlertKind.OUT to "Out of stock",
                    AlertKind.LOW to "Low stock",
                    AlertKind.EXPIRING to "Expiring within 30 days",
                )
                groups.forEach { (kind, title) ->
                    val list = alerts.items.filter { it.kind == kind }
                    if (list.isNotEmpty()) {
                        item(key = "title-${kind.name}") {
                            Text(
                                text = "$title (${list.size})",
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            )
                        }
                        items(list, key = { "${kind.name}-${it.product.id}" }) { a ->
                            val urgent = alertUrgent(a, today)
                            AttentionRow(
                                icon = alertIcon(a),
                                title = a.product.name,
                                detail = alertDetail(a, today),
                                tag = alertTag(a, today),
                                tagBackground = if (urgent) c.urgent else c.reorder,
                                tagTextColor = if (urgent) c.onUrgent else c.onReorder,
                                modifier = Modifier.clickable(onClick = onOpenInventory),
                            )
                        }
                    }
                }
            }
        }
    }
}
