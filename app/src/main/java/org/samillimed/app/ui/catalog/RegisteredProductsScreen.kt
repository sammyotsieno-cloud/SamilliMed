package org.samillimed.app.ui.catalog

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.MEDICINE_TYPE
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.buildProductsCsv
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isCombination
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.productExportFileName
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.catalog.writeTextToUri
import org.samillimed.app.ui.common.AddFab
import org.samillimed.app.ui.common.ChipRow
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** All registered products: search, filter by type, show or hide inactive ones, export the list. */
@Composable
fun RegisteredProductsScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var showInactive by rememberSaveable { mutableStateOf(false) }

    // Export: the phone's "Save as" screen asks where to put the spreadsheet file.
    val latestProducts by rememberUpdatedState(catalog.products)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val list = latestProducts
            val ok = writeTextToUri(context, uri, buildProductsCsv(list))
            Toast.makeText(
                context,
                if (ok) "Saved ${list.size} products" else "Could not save the file",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val inactiveCount = catalog.products.count { !it.active }
    val activeCount = catalog.products.size - inactiveCount
    val types = remember(catalog.products) {
        catalog.products.map { it.type.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { if (it.equals(MEDICINE_TYPE, ignoreCase = true)) "" else it.lowercase() }
    }
    val typeFilter = selectedType?.takeIf { it in types }
    val visible = remember(catalog.products, query, typeFilter, showInactive) {
        val q = query.trim().lowercase()
        catalog.products.filter { p ->
            (showInactive || p.active) &&
                (typeFilter == null || p.type.trim() == typeFilter) &&
                (q.isEmpty() || listOf(
                    p.name, p.brandName, p.genericNames, p.strengthText, p.dosageForm, p.description, p.type,
                ).any { it.lowercase().contains(q) })
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val canExport = catalog.loaded && catalog.products.isNotEmpty()

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = "Registered Products",
            subtitle = if (catalog.loaded) {
                "$activeCount active" + if (inactiveCount > 0) " · $inactiveCount inactive" else ""
            } else {
                null
            },
            onBack = onBack,
            actions = {
                Text(
                    text = "Export",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x17F3ECD8))
                        .clickable(enabled = canExport) { exportLauncher.launch(productExportFileName()) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = c.onHeader.copy(alpha = if (canExport) 1f else 0.4f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
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
                    if (types.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        ChipRow(options = types, selected = typeFilter, onSelect = { selectedType = it })
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
                if (p.isMedicine) {
                    val detail = listOf(p.genericNames, p.strengthText, p.dosageForm)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                if (p.brandName.isNotBlank() && !p.name.contains(p.brandName, ignoreCase = true)) {
                    Text(
                        text = "Brand: ${p.brandName}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (!p.active) Tag("INACTIVE", c.textMuted.copy(alpha = 0.25f), c.textSecondary)
        }

        if (p.type.isNotBlank() || p.isCombination) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (p.type.isNotBlank()) Tag(p.type, c.iconBg, c.accentText)
                if (p.isCombination) Tag("Combination", c.deltaBg, c.deltaText)
            }
        }

        if (p.description.isNotBlank()) {
            Text(
                text = p.description,
                color = c.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (p.units.isNotEmpty()) {
            val unitsText = p.units.joinToString(" · ") {
                if (it.baseQty <= 1) it.name else "${it.name} (${it.baseQty})"
            }
            Text(
                text = "Units: $unitsText",
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
