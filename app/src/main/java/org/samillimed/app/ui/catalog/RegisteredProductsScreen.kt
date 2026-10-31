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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.ListType
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.ui.common.AddFab
import org.samillimed.app.ui.common.ChipRow
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** All registered products: search, filter by category, show or hide inactive ones. */
@Composable
fun RegisteredProductsScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showInactive by rememberSaveable { mutableStateOf(false) }

    val inactiveCount = catalog.products.count { !it.active }
    val categories = remember(catalog.products, catalog.lists) {
        (catalog.list(ListType.PRODUCT_CATEGORIES) + catalog.products.map { it.category })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val visible = remember(catalog.products, query, category, showInactive) {
        val q = query.trim().lowercase()
        catalog.products.filter { p ->
            (showInactive || p.active) &&
                (category == null || p.category == category) &&
                (q.isEmpty() || listOf(
                    p.name, p.genericName, p.brandName, p.strength, p.code, p.barcode, p.category,
                ).any { it.lowercase().contains(q) })
        }
    }
    val activeCount = catalog.products.size - inactiveCount
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Registered Products",
            subtitle = if (catalog.loaded) "$activeCount active" + if (inactiveCount > 0) " · $inactiveCount inactive" else "" else null,
            onBack = onBack,
        )

        Box(Modifier.weight(1f)) {
            when {
                !catalog.loaded -> LoadingState()
                catalog.products.isEmpty() -> EmptyState(
                    icon = MoreIcons.ProductTag,
                    title = "No products yet",
                    message = "Tap Add product to register your first product.",
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(14.dp))
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search products",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipRow(options = categories, selected = category, onSelect = { category = it })
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
                            icon = MoreIcons.ProductTag,
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
                            items(visible, key = { it.id }) { product ->
                                ProductCard(product, onClick = { onEdit(product) })
                            }
                        }
                    }
                }
            }

            AddFab(
                text = "Add product",
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
private fun ProductCard(p: Product, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (p.active) 1f else 0.6f),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(text = p.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val detail = listOf(p.strength, p.dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(text = detail, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                }
            }
            if (!p.active) Tag("INACTIVE", c.textMuted.copy(alpha = 0.25f), c.textSecondary)
        }

        val hasTags = p.category.isNotBlank() || p.prescriptionStatus.isNotBlank() || p.controlled
        if (hasTags) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (p.category.isNotBlank()) Tag(p.category, c.iconBg, c.accentText)
                if (p.prescriptionStatus.isNotBlank()) Tag(p.prescriptionStatus, c.deltaBg, c.deltaText)
                if (p.controlled) Tag("Controlled", c.urgent, c.onUrgent)
            }
        }

        if (p.units.isNotEmpty()) {
            val unitsText = p.units.joinToString(" · ") {
                if (it.baseQty <= 1) it.name else "${it.name} (${it.baseQty})"
            }
            Text(
                text = "Sold by: $unitsText",
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (p.reorderLevel > 0) {
            val unit = p.units.firstOrNull()?.name?.lowercase().orEmpty()
            Text(
                text = "Reorder at ${p.reorderLevel} $unit".trim(),
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Tag(text: String, background: Color, textColor: Color) {
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
