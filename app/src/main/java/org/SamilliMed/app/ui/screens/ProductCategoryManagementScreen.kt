package org.SamilliMed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.domain.model.ProductCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.SamilliMed.app.data.AppContainer

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProductCategoryManagementScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<ProductCategory?>(null) }
    var addingParent by remember { mutableStateOf<ProductCategory?>(null) }
    var showAddRoot by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    container.productCategoryRepository.ensureDefaultTaxonomy()
                    categories = container.productCategoryRepository.getAll()
                }
                error = null
            } catch (t: Throwable) {
                error = t.message ?: "Unable to load categories."
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Product Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddRoot = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add root category")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Facility taxonomy", style = MaterialTheme.typography.titleMedium)
            Text(
                "Defaults are editable. Changes organize products but do not change product identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories.filter { it.parentCategoryId == null }, key = { it.id }) { root ->
                    CategoryTreeItem(
                        category = root,
                        categories = categories,
                        depth = 0,
                        onAddChild = { addingParent = it },
                        onEdit = { editor = it },
                        onArchive = { category ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (category.isActive) container.productCategoryRepository.archive(category.id)
                                        else container.productCategoryRepository.restore(category.id)
                                    }
                                }.onSuccess { refresh() }
                                    .onFailure { error = it.message ?: "Unable to update category." }
                            }
                        },
                        onDelete = { category ->
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        container.productCategoryRepository.deleteIfUnused(category.id)
                                    }
                                }.onSuccess { refresh() }
                                    .onFailure { error = it.message ?: "Unable to delete category." }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddRoot) {
        CategoryEditorDialog(
            category = null,
            parent = null,
            repository = container.productCategoryRepository,
            categories = categories,
            onDismiss = { showAddRoot = false },
            onSaved = { showAddRoot = false; refresh() }
        )
    }
    addingParent?.let { parent ->
        CategoryEditorDialog(
            category = null,
            parent = parent,
            repository = container.productCategoryRepository,
            categories = categories,
            onDismiss = { addingParent = null },
            onSaved = { addingParent = null; refresh() }
        )
    }
    editor?.let { category ->
        CategoryEditorDialog(
            category = category,
            parent = null,
            repository = container.productCategoryRepository,
            categories = categories,
            onDismiss = { editor = null },
            onSaved = { editor = null; refresh() }
        )
    }
}

@Composable
private fun CategoryTreeItem(
    category: ProductCategory,
    categories: List<ProductCategory>,
    depth: Int,
    onAddChild: (ProductCategory) -> Unit,
    onEdit: (ProductCategory) -> Unit,
    onArchive: (ProductCategory) -> Unit,
    onDelete: (ProductCategory) -> Unit
) {
    var expanded by remember(category.id) { mutableStateOf(true) }
    val children = categories.filter { it.parentCategoryId == category.id }.sortedBy { it.sortOrder }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = (depth * 16).dp + 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                if (children.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                } else {
                    Text("•", modifier = Modifier.padding(12.dp))
                }
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(category.name, style = MaterialTheme.typography.titleSmall)
                    when {
                        !category.isActive -> Text("Archived", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        category.isSystemDefault -> Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { onAddChild(category) }) { Icon(Icons.Default.Add, contentDescription = "Add child") }
                IconButton(onClick = { onEdit(category) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                TextButton(onClick = { onArchive(category) }) { Text(if (category.isActive) "Archive" else "Restore") }
                IconButton(onClick = { onDelete(category) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
            if (expanded) {
                children.forEach { child ->
                    CategoryTreeItem(child, categories, depth + 1, onAddChild, onEdit, onArchive, onDelete)
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    category: ProductCategory?,
    parent: ProductCategory?,
    repository: core.domain.category.ProductCategoryRepository,
    categories: List<ProductCategory>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember(category?.id, parent?.id) { mutableStateOf(category?.name.orEmpty()) }
    var description by remember(category?.id, parent?.id) { mutableStateOf(category?.description.orEmpty()) }
    var error by remember(category?.id, parent?.id) { mutableStateOf<String?>(null) }
    var selectedParentId by remember(category?.id, parent?.id) { mutableStateOf(category?.parentCategoryId ?: parent?.id) }
    var parentMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Add Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    selectedParentId?.let { id ->
                        "Parent: " + (categories.firstOrNull { it.id == id }?.name ?: "Unknown")
                    } ?: "Parent: None (root)",
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = { parentMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose Parent") }
                DropdownMenu(
                    expanded = parentMenuExpanded,
                    onDismissRequest = { parentMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None (root)") },
                        onClick = { selectedParentId = null; parentMenuExpanded = false }
                    )
                    categories.filter { it.id != category?.id && it.isActive }
                        .sortedBy { it.name.lowercase() }
                        .forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.name) },
                                onClick = { selectedParentId = candidate.id; parentMenuExpanded = false }
                            )
                        }
                }
                androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Category name") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.material3.OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (category == null) repository.create(name, selectedParentId, description)
                            else repository.update(category, name, selectedParentId, description)
                        }
                    }.onSuccess { onSaved() }
                        .onFailure { error = it.message ?: "Unable to save category." }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProductCategoryPickerDialog(
    categories: List<ProductCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedIds by remember { mutableStateOf(categories.filter { it.parentCategoryId == null }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Product Category") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(categories.filter { it.parentCategoryId == null }, key = { it.id }) { root ->
                    CategoryPickerNode(root, categories, selectedId, expandedIds, { id ->
                        expandedIds = if (expandedIds.contains(id)) expandedIds - id else expandedIds + id
                    }, onSelect)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun CategoryPickerNode(
    category: ProductCategory,
    categories: List<ProductCategory>,
    selectedId: String?,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val children = categories.filter { it.parentCategoryId == category.id && it.isActive }.sortedBy { it.sortOrder }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        if (children.isNotEmpty()) {
            IconButton(onClick = { onToggle(category.id) }) {
                Icon(if (expandedIds.contains(category.id)) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
        } else {
            Text("•", modifier = Modifier.padding(12.dp))
        }
        TextButton(onClick = { onSelect(category.id) }, Modifier.weight(1f)) {
            Text(
                if (category.id == selectedId) "✓ @@{category.name}" else category.name,
                color = if (category.id == selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    if (expandedIds.contains(category.id)) {
        children.forEach { child ->
            Column(Modifier.padding(start = 20.dp)) {
                CategoryPickerNode(child, categories, selectedId, expandedIds, onToggle, onSelect)
            }
        }
    }
}
