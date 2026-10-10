package org.SamilliMed.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.domain.model.ProductCategory
import core.domain.model.ProductMaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.SamilliMed.app.data.AppContainer
import org.SamilliMed.app.scanner.ProductScanDraft

private val TEXT_PRIMARY = Color(0xFF23201D)
private val TEXT_MUTED = Color(0xFF6B655D)
private val BAR_SURFACE = Color(0xFFFAF7F2)
private val BAR_BORDER = Color(0xFFE5DFD5)
private val ACCENT_GREEN = Color(0xFF2E6B4E)
private val ACCENT_TEAL = Color(0xFF285E61)

@Composable
fun ProductsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onScanProduct: () -> Unit,
    initialScanDraft: ProductScanDraft? = null,
    onScanDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Navigation Stack representing progressive drill-down
    var navigationStack by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    val currentParent = navigationStack.lastOrNull()

    // Current category level items
    var displayedCategories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    var childCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var productsInCurrentCategory by remember { mutableStateOf<List<ProductMaster>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Dialog states for complete editability
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<ProductCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<ProductCategory?>(null) }
    var selectedDrugDetail by remember { mutableStateOf<ProductCategory?>(null) }

    // Intercept hardware/system back button to pop one hierarchy level
    BackHandler(enabled = navigationStack.isNotEmpty()) {
        navigationStack = navigationStack.dropLast(1)
    }

    fun refreshCurrentLevel() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                container.productCategoryRepository.ensureDefaultTaxonomy()

                val items = if (currentParent == null) {
                    container.productCategoryRepository.getRoots()
                } else {
                    container.productCategoryRepository.getChildren(currentParent.id)
                }

                val counts = mutableMapOf<String, Int>()
                for (cat in items) {
                    counts[cat.id] = container.productCategoryRepository.countChildren(cat.id)
                }

                val prods = if (currentParent != null) {
                    container.productMasterDao.getProductsByCategoryId(currentParent.id)
                } else emptyList()

                withContext(Dispatchers.Main) {
                    displayedCategories = items
                    childCounts = counts
                    productsInCurrentCategory = prods
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(currentParent) {
        refreshCurrentLevel()
    }

    // Live search query handling
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
        } else {
            isSearching = true
            val query = searchQuery.trim()
            val results = withContext(Dispatchers.IO) {
                container.productCategoryRepository.search(query)
            }
            searchResults = results
            isSearching = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // ==========================================
            // 1. SEAMLESS TOP HEADER BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            if (navigationStack.isNotEmpty()) {
                                navigationStack = navigationStack.dropLast(1)
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TEXT_PRIMARY
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Text(
                            text = "Products Database",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = TEXT_PRIMARY
                        )
                        Text(
                            text = if (currentParent == null) "Reference Taxonomy & Medical Formulations"
                            else currentParent.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TEXT_MUTED,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Database",
                            tint = TEXT_PRIMARY
                        )
                    }

                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Category",
                            tint = ACCENT_GREEN
                        )
                    }
                }
            }

            // ==========================================
            // 2. SEARCH BAR (COLLAPSIBLE)
            // ==========================================
            AnimatedVisibility(visible = isSearchActive) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search classes, substances, or formulations...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // ==========================================
            // 3. CLINICAL BREADCRUMB NAVIGATION
            // ==========================================
            if (navigationStack.isNotEmpty() && !isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        modifier = Modifier.clickable { navigationStack = emptyList() }
                    ) {
                        Text(
                            text = "All Categories",
                            style = MaterialTheme.typography.labelMedium,
                            color = ACCENT_TEAL,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    navigationStack.forEachIndexed { index, ancestor ->
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TEXT_MUTED
                        )

                        val isLast = index == navigationStack.lastIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLast) Color(0xFFE9E4DC) else Color.Transparent,
                            modifier = Modifier.clickable {
                                navigationStack = navigationStack.take(index + 1)
                            }
                        ) {
                            Text(
                                text = ancestor.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLast) TEXT_PRIMARY else ACCENT_TEAL,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. MAIN HIERARCHY CONTENT
            // ==========================================
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ACCENT_GREEN)
                }
            } else if (isSearchActive && searchQuery.isNotBlank()) {
                // Search Mode
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ACCENT_GREEN)
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching categories or substances found.", style = MaterialTheme.typography.bodyMedium, color = TEXT_MUTED)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { resultCat ->
                            CategoryHorizontalBar(
                                category = resultCat,
                                childCount = childCounts[resultCat.id] ?: 0,
                                isLeaf = false,
                                onClick = {
                                    scope.launch {
                                        val ancestors = container.productCategoryRepository.getAncestors(resultCat.id)
                                        navigationStack = ancestors + resultCat
                                        isSearchActive = false
                                        searchQuery = ""
                                    }
                                },
                                onEdit = { categoryToEdit = resultCat },
                                onDelete = { categoryToDelete = resultCat }
                            )
                        }
                    }
                }
            } else {
                // Normal Progressive Hierarchy
                val isTerminalLeaf = displayedCategories.isEmpty()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isTerminalLeaf) {
                        // Subclasses / Subcategories Listed Downwards as Horizontal Bars
                        items(displayedCategories) { cat ->
                            val childrenCount = childCounts[cat.id] ?: 0
                            val isSubstance = childrenCount == 0 && currentParent != null

                            CategoryHorizontalBar(
                                category = cat,
                                childCount = childrenCount,
                                isLeaf = isSubstance,
                                onClick = {
                                    if (isSubstance) {
                                        selectedDrugDetail = cat
                                    } else {
                                        navigationStack = navigationStack + cat
                                    }
                                },
                                onEdit = { categoryToEdit = cat },
                                onDelete = { categoryToDelete = cat }
                            )
                        }
                    } else {
                        // Leaf view: Reached individual substance or terminal drug level
                        item {
                            currentParent?.let { parentNode ->
                                TerminalSubstanceProfileCard(
                                    substance = parentNode,
                                    registeredProducts = productsInCurrentCategory,
                                    onEdit = { categoryToEdit = parentNode },
                                    onRegisterFormulation = {
                                        // User can register a physical product under this substance
                                        showAddCategoryDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Add Subcategory / Substance Action at bottom of list
                    item {
                        OutlinedButton(
                            onClick = { showAddCategoryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ACCENT_GREEN.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = ACCENT_GREEN)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentParent == null) "Add New Primary Category"
                                else "Add Subcategory to ${currentParent.name}",
                                color = ACCENT_GREEN,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. DIALOGS FOR FULL DATABASE EDITABILITY
        // ==========================================

        // A. Add Category / Subcategory Dialog
        if (showAddCategoryDialog) {
            AddCategoryDialog(
                parent = currentParent,
                onDismiss = { showAddCategoryDialog = false },
                onSave = { name, description ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.productCategoryRepository.create(
                                name = name,
                                parentId = currentParent?.id,
                                description = description
                            )
                        }
                        showAddCategoryDialog = false
                        refreshCurrentLevel()
                    }
                }
            )
        }

        // B. Edit Category Dialog
        categoryToEdit?.let { cat ->
            EditCategoryDialog(
                category = cat,
                onDismiss = { categoryToEdit = null },
                onSave = { updatedName, updatedDesc ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.productCategoryRepository.update(
                                category = cat,
                                name = updatedName,
                                parentId = cat.parentCategoryId,
                                description = updatedDesc
                            )
                        }
                        categoryToEdit = null
                        refreshCurrentLevel()
                    }
                }
            )
        }

        // C. Delete / Archive Category Dialog
        categoryToDelete?.let { cat ->
            DeleteCategoryDialog(
                category = cat,
                onDismiss = { categoryToDelete = null },
                onConfirmDelete = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                container.productCategoryRepository.deleteIfUnused(cat.id)
                            } catch (_: Exception) {
                                container.productCategoryRepository.archive(cat.id)
                            }
                        }
                        categoryToDelete = null
                        refreshCurrentLevel()
                    }
                }
            )
        }

        // D. Substance Detail Card Dialog
        selectedDrugDetail?.let { drug ->
            SubstanceDetailDialog(
                substance = drug,
                onDismiss = { selectedDrugDetail = null },
                onEdit = {
                    selectedDrugDetail = null
                    categoryToEdit = drug
                }
            )
        }
    }
}

/**
 * Clean Horizontal Bar component representing each category node.
 * Strictly presents user-friendly clinical and medical names without numbers.
 */
@Composable
private fun CategoryHorizontalBar(
    category: ProductCategory,
    childCount: Int,
    isLeaf: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BAR_SURFACE),
        border = BorderStroke(1.dp, BAR_BORDER),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLeaf) ACCENT_TEAL.copy(alpha = 0.12f)
                            else ACCENT_GREEN.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.id, isLeaf),
                        contentDescription = null,
                        tint = if (isLeaf) ACCENT_TEAL else ACCENT_GREEN,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.1).sp
                        ),
                        color = TEXT_PRIMARY
                    )

                    category.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TEXT_MUTED,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Count or Type badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEBE6DC),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = when {
                            childCount > 0 -> "$childCount"
                            isLeaf -> "Substance"
                            else -> "Empty"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TEXT_MUTED,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Three-dot options menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TEXT_MUTED,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit / Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete / Archive") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }

                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = TEXT_MUTED,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Terminal Substance Profile view when user drills down to an individual drug leaf.
 */
@Composable
private fun TerminalSubstanceProfileCard(
    substance: ProductCategory,
    registeredProducts: List<ProductMaster>,
    onEdit: () -> Unit,
    onRegisterFormulation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BAR_SURFACE),
        border = BorderStroke(1.dp, BAR_BORDER)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Medication,
                        contentDescription = null,
                        tint = ACCENT_GREEN,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = substance.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TEXT_PRIMARY
                        )
                        Text(
                            text = "Canonical Medicinal Substance",
                            style = MaterialTheme.typography.labelMedium,
                            color = ACCENT_TEAL
                        )
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Substance", tint = TEXT_MUTED)
                }
            }

            substance.description?.let { desc ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Clinical Profile / Mechanism:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TEXT_MUTED,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TEXT_PRIMARY
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Registered Commercial Formulations (${registeredProducts.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TEXT_MUTED,
                fontWeight = FontWeight.Bold
            )

            if (registeredProducts.isEmpty()) {
                Text(
                    text = "No physical brand formulations registered under this substance yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TEXT_MUTED,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                registeredProducts.forEach { product ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, Color(0xFFEBE6DC)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    product.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                product.manufacturer?.let {
                                    Text("Manufacturer: $it", style = MaterialTheme.typography.bodySmall, color = TEXT_MUTED)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(
    parent: ProductCategory?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (parent == null) "New Primary Category"
                else "Add Subcategory to ${parent.name}"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category / Substance Name") },
                    placeholder = { Text("e.g. Beta-blockers, Atenolol") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Clinical Description / Mechanism (Optional)") },
                    placeholder = { Text("e.g. Cardioselective beta-1 adrenergic antagonist") },
                    modifier = Modifier.fillMaxWidth()
                )

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorText = "Name cannot be blank"
                        return@Button
                    }
                    onSave(name.trim(), description.trim().ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT_GREEN)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditCategoryDialog(
    category: ProductCategory,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var description by remember { mutableStateOf(category.description ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${category.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Clinical Description / Mechanism") },
                    modifier = Modifier.fillMaxWidth()
                )

                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorText = "Name cannot be blank"
                        return@Button
                    }
                    onSave(name.trim(), description.trim().ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = ACCENT_GREEN)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteCategoryDialog(
    category: ProductCategory,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete or Archive Category") },
        text = {
            Text("Are you sure you want to remove '${category.name}'? Historical links will be safely preserved.")
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SubstanceDetailDialog(
    substance: ProductCategory,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(substance.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Medicinal Substance",
                    style = MaterialTheme.typography.labelSmall,
                    color = ACCENT_TEAL,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                substance.description?.let {
                    Text(text = "Clinical Profile:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                } ?: run {
                    Text(text = "Standard WHO / Reference Active Pharmaceutical Substance.", style = MaterialTheme.typography.bodyMedium, color = TEXT_MUTED)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ACCENT_GREEN)) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Text("Edit Substance")
            }
        }
    )
}

private fun getCategoryIcon(id: String, isLeaf: Boolean): ImageVector {
    if (isLeaf) return Icons.Default.Medication
    return when {
        id == "1" || id.startsWith("1.") -> Icons.Default.Medication
        id == "2" || id.startsWith("2.") -> Icons.Default.MedicalServices
        id == "3" || id.startsWith("3.") -> Icons.Default.Biotech
        id == "4" || id.startsWith("4.") -> Icons.Default.LocalHospital
        id == "5" || id.startsWith("5.") -> Icons.Default.Healing
        id == "6" || id.startsWith("6.") -> Icons.Default.CleanHands
        id == "7" || id.startsWith("7.") -> Icons.Default.ChildCare
        id == "8" || id.startsWith("8.") -> Icons.Default.Spa
        id == "9" || id.startsWith("9.") -> Icons.Default.Restaurant
        id == "10" || id.startsWith("10.") -> Icons.Default.Business
        else -> Icons.Default.Medication
    }
}
