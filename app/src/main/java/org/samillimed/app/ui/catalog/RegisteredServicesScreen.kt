package org.samillimed.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.ServiceItem
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.ui.common.AddFab
import org.samillimed.app.ui.common.ChipRow
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** All registered services: search, filter by category, show or hide inactive ones. */
@Composable
fun RegisteredServicesScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ServiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showInactive by rememberSaveable { mutableStateOf(false) }

    val inactiveCount = catalog.services.count { !it.active }
    val activeCount = catalog.services.size - inactiveCount
    val categories = remember(catalog.services) {
        catalog.services.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
    }
    val categoryFilter = selectedCategory?.takeIf { it in categories }
    val visible = remember(catalog.services, query, categoryFilter, showInactive) {
        val q = query.trim().lowercase()
        catalog.services.filter { s ->
            (showInactive || s.active) &&
                (categoryFilter == null || s.category.trim() == categoryFilter) &&
                (q.isEmpty() || listOf(s.name, s.category, s.department, s.notes).any { it.lowercase().contains(q) })
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Registered Services",
            subtitle = if (catalog.loaded) {
                "$activeCount active" + if (inactiveCount > 0) " · $inactiveCount inactive" else ""
            } else {
                null
            },
            onBack = onBack,
        )

        Box(Modifier.weight(1f)) {
            when {
                !catalog.loaded -> LoadingState()
                catalog.services.isEmpty() -> EmptyState(
                    icon = MoreIcons.Stethoscope,
                    title = "No services yet",
                    message = "Tap Add service to register your first service.",
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(14.dp))
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search services",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (categories.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        ChipRow(options = categories, selected = categoryFilter, onSelect = { selectedCategory = it })
                    }
                    if (inactiveCount > 0) {
                        Row(
                            Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Show inactive ($inactiveCount)",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = showInactive, onCheckedChange = { showInactive = it })
                        }
                    }
                    if (visible.isEmpty()) {
                        EmptyState(
                            icon = MoreIcons.Stethoscope,
                            title = "No matches",
                            message = "Try a different search, or clear the filters.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp + navBottom,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.id }) { service ->
                                ServiceCard(service, onClick = { onEdit(service) })
                            }
                        }
                    }
                }
            }

            AddFab(
                text = "Add service",
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ServiceCard(s: ServiceItem, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (s.active) 1f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(text = s.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (s.priceCents > 0L) formatKsh(s.priceCents) else "Price entered at billing",
                    color = c.accentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!s.active) ServiceTag("INACTIVE", c.textMuted.copy(alpha = 0.25f), c.textSecondary)
        }

        if (s.category.isNotBlank() || s.department.isNotBlank()) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (s.category.isNotBlank()) ServiceTag(s.category, c.iconBg, c.accentText)
                if (s.department.isNotBlank()) ServiceTag(s.department, c.deltaBg, c.deltaText)
            }
        }

        if (s.notes.isNotBlank()) {
            Text(
                text = s.notes,
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ServiceTag(text: String, background: Color, textColor: Color) {
    Text(
        text = text,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        color = textColor,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
    )
}
