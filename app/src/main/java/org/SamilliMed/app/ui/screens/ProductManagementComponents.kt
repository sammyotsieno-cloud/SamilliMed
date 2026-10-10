package org.SamilliMed.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import core.domain.model.Money
import core.domain.model.PharmaceuticalDetail
import core.domain.model.ProductCategory
import core.domain.model.ProductImage
import core.domain.model.ProductMaster
import core.domain.model.ProductRecognitionIdentifier
import core.domain.model.ProductRecognitionObservation
import core.domain.model.ProductUnit
import core.domain.model.QuantityScale
import core.domain.model.UnitPriceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.SamilliMed.app.data.AppContainer
import org.SamilliMed.app.scanner.ProductScanDraft
import java.util.UUID

data class ProductWithDetails(
    val product: ProductMaster,
    val units: List<ProductUnit>,
    val priceConfigsByUnitId: Map<String, UnitPriceConfig>
)

@Composable
fun ProductCategoryPickerDialog(
    categories: List<ProductCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search categories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategorySelected(cat.id) },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AddProductDialog(
    container: AppContainer,
    categories: List<ProductCategory>,
    initialScanDraft: ProductScanDraft?,
    onDismiss: () -> Unit,
    onProductSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var brandName by remember { mutableStateOf(initialScanDraft?.brandName ?: "") }
    var genericName by remember { mutableStateOf(initialScanDraft?.genericName ?: "") }
    var productType by remember { mutableStateOf(initialScanDraft?.dosageForm ?: "") }
    var manufacturer by remember { mutableStateOf(initialScanDraft?.manufacturer ?: "") }
    var activeIngredients by remember { mutableStateOf(initialScanDraft?.activeIngredients ?: "") }
    var strength by remember { mutableStateOf(initialScanDraft?.strength ?: "") }
    var dosageForm by remember { mutableStateOf(initialScanDraft?.dosageForm ?: "") }
    var route by remember { mutableStateOf(initialScanDraft?.route ?: "") }
    var therapeuticCategory by remember { mutableStateOf(initialScanDraft?.therapeuticCategory ?: "") }
    var prescriptionClassification by remember { mutableStateOf(initialScanDraft?.prescriptionClassification ?: "") }
    var storageCondition by remember { mutableStateOf(initialScanDraft?.storageCondition ?: "") }
    var description by remember { mutableStateOf("") }

    var selectedCategoryId by remember {
        mutableStateOf(
            initialScanDraft?.recognitionCategoryId
                ?: categories.firstOrNull()?.id
        )
    }
    var showCategoryPicker by remember { mutableStateOf(false) }

    var baseUnitName by remember { mutableStateOf("Piece") }
    var baseUnitAbbreviation by remember { mutableStateOf("pcs") }
    var baseUnitCostPrice by remember { mutableStateOf("") }
    var baseUnitSellingPrice by remember { mutableStateOf("") }

    var addSecondaryUnit by remember { mutableStateOf(false) }
    var secondaryUnitName by remember { mutableStateOf("Box") }
    var secondaryUnitAbbreviation by remember { mutableStateOf("box") }
    var secondaryConversionMultiplier by remember { mutableStateOf("10") }
    var secondaryCostPrice by remember { mutableStateOf("") }
    var secondarySellingPrice by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (showCategoryPicker) {
        ProductCategoryPickerDialog(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = {
                selectedCategoryId = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Product") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = { Text("Brand / Trade Name") },
                    placeholder = { Text("e.g. Panadol, Amoxil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = genericName,
                    onValueChange = { genericName = it },
                    label = { Text("Generic / INN Name") },
                    placeholder = { Text("e.g. Paracetamol 500mg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = productType,
                    onValueChange = { productType = it },
                    label = { Text("Product Type") },
                    placeholder = { Text("e.g. Tablet, Capsule, Syrup, Vial") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Manufacturer") },
                    placeholder = { Text("e.g. GSK, Dawa Ltd") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(value = activeIngredients, onValueChange = { activeIngredients = it }, label = { Text("Active Ingredient(s) (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = strength, onValueChange = { strength = it }, label = { Text("Strength (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dosageForm, onValueChange = { dosageForm = it }, label = { Text("Dosage Form (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("Route (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = therapeuticCategory, onValueChange = { therapeuticCategory = it }, label = { Text("Therapeutic Category (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prescriptionClassification, onValueChange = { prescriptionClassification = it }, label = { Text("Prescription Classification (Optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = storageCondition, onValueChange = { storageCondition = it }, label = { Text("Storage Condition (Optional)") }, modifier = Modifier.fillMaxWidth())

                OutlinedButton(
                    onClick = { showCategoryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selected = categories.firstOrNull { it.id == selectedCategoryId }
                    Text(if (selected == null) "Select Primary Product Category" else "Category: " + selected.name)
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Canonical Base Unit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseUnitName,
                        onValueChange = { baseUnitName = it },
                        label = { Text("Base Unit Name") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = baseUnitAbbreviation,
                        onValueChange = { baseUnitAbbreviation = it },
                        label = { Text("Abbreviation") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseUnitCostPrice,
                        onValueChange = { baseUnitCostPrice = it },
                        label = { Text("Cost Price (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = baseUnitSellingPrice,
                        onValueChange = { baseUnitSellingPrice = it },
                        label = { Text("Selling Price (KES)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addSecondaryUnit, onCheckedChange = { addSecondaryUnit = it })
                    Text("Add Packaging Unit (e.g. Box, Strip)")
                }

                if (addSecondaryUnit) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = secondaryUnitName, onValueChange = { secondaryUnitName = it }, label = { Text("Unit Name") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = secondaryUnitAbbreviation, onValueChange = { secondaryUnitAbbreviation = it }, label = { Text("Abbreviation") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = secondaryConversionMultiplier,
                        onValueChange = { secondaryConversionMultiplier = it },
                        label = { Text("Conversion (Contains how many base units?)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = secondaryCostPrice, onValueChange = { secondaryCostPrice = it }, label = { Text("Pack Cost (KES)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = secondarySellingPrice, onValueChange = { secondarySellingPrice = it }, label = { Text("Pack Price (KES)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }

                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    if (brandName.isBlank() && genericName.isBlank()) {
                        errorMessage = "Please enter at least a Brand Name or Generic Name."
                        return@Button
                    }
                    if (baseUnitName.isBlank() || baseUnitAbbreviation.isBlank()) {
                        errorMessage = "Base unit name and abbreviation are required."
                        return@Button
                    }

                    isSaving = true
                    errorMessage = null

                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val productId = UUID.randomUUID().toString()
                                val now = System.currentTimeMillis()

                                val product = ProductMaster(
                                    id = productId,
                                    brandName = brandName.trim().ifBlank { null },
                                    genericName = genericName.trim().ifBlank { null },
                                    productType = productType.trim().ifBlank { null },
                                    categoryId = selectedCategoryId,
                                    manufacturer = manufacturer.trim().ifBlank { null },
                                    description = description.trim().ifBlank { null },
                                    quantityScale = QuantityScale.SCALE_0,
                                    minimumTransactionIncrementStorageUnits = 1L,
                                    isActive = true,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                container.productMasterDao.insertProduct(product)

                                val baseUnitId = UUID.randomUUID().toString()
                                val baseUnit = ProductUnit(
                                    id = baseUnitId,
                                    productId = productId,
                                    name = baseUnitName.trim(),
                                    abbreviation = baseUnitAbbreviation.trim(),
                                    isBaseUnit = true,
                                    conversionMultiplier = 1L,
                                    conversionDivisor = 1L,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                container.productMasterDao.insertUnit(baseUnit)

                                val baseCostKsh = baseUnitCostPrice.toDoubleOrNull()
                                val baseSellKsh = baseUnitSellingPrice.toDoubleOrNull()
                                if (baseCostKsh != null || baseSellKsh != null) {
                                    container.productMasterDao.savePriceConfig(
                                        UnitPriceConfig(
                                            id = UUID.randomUUID().toString(),
                                            productUnitId = baseUnitId,
                                            buyingPrice = baseCostKsh?.let { Money.kes((it * 100).toLong()) },
                                            sellingPrice = baseSellKsh?.let { Money.kes((it * 100).toLong()) },
                                            isActive = true,
                                            createdAt = now,
                                            updatedAt = now
                                        )
                                    )
                                }

                                if (addSecondaryUnit && secondaryUnitName.isNotBlank() && secondaryUnitAbbreviation.isNotBlank()) {
                                    val mult = secondaryConversionMultiplier.toLongOrNull() ?: 1L
                                    val secUnitId = UUID.randomUUID().toString()
                                    val secUnit = ProductUnit(
                                        id = secUnitId,
                                        productId = productId,
                                        name = secondaryUnitName.trim(),
                                        abbreviation = secondaryUnitAbbreviation.trim(),
                                        isBaseUnit = false,
                                        conversionMultiplier = mult,
                                        conversionDivisor = 1L,
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                    container.productMasterDao.insertUnit(secUnit)

                                    val secCostKsh = secondaryCostPrice.toDoubleOrNull()
                                    val secSellKsh = secondarySellingPrice.toDoubleOrNull()
                                    if (secCostKsh != null || secSellKsh != null) {
                                        container.productMasterDao.savePriceConfig(
                                            UnitPriceConfig(
                                                id = UUID.randomUUID().toString(),
                                                productUnitId = secUnitId,
                                                buyingPrice = secCostKsh?.let { Money.kes((it * 100).toLong()) },
                                                sellingPrice = secSellKsh?.let { Money.kes((it * 100).toLong()) },
                                                isActive = true,
                                                createdAt = now,
                                                updatedAt = now
                                            )
                                        )
                                    }
                                }

                                if (dosageForm.isNotBlank() || strength.isNotBlank() || activeIngredients.isNotBlank() || route.isNotBlank()) {
                                    container.productMasterDao.insertPharmaceuticalDetail(
                                        PharmaceuticalDetail(
                                            id = UUID.randomUUID().toString(),
                                            productId = productId,
                                            activeIngredients = activeIngredients.trim().ifBlank { null },
                                            strength = strength.trim().ifBlank { null },
                                            dosageForm = dosageForm.trim().ifBlank { null },
                                            route = route.trim().ifBlank { null },
                                            therapeuticCategory = therapeuticCategory.trim().ifBlank { null },
                                            prescriptionClassification = prescriptionClassification.trim().ifBlank { null },
                                            storageCondition = storageCondition.trim().ifBlank { null },
                                            createdAt = now,
                                            updatedAt = now
                                        )
                                    )
                                }

                                initialScanDraft?.let { draft ->
                                    draft.barcodeValue?.trim()?.takeIf { it.isNotBlank() }?.let { barcode ->
                                        val normalized = core.domain.recognition.ProductRecognitionService.normalize(barcode)
                                        container.productRecognitionDao.insertIdentifier(
                                            ProductRecognitionIdentifier(
                                                id = UUID.randomUUID().toString(),
                                                productId = productId,
                                                identifierType = ProductRecognitionIdentifier.TYPE_BARCODE,
                                                normalizedValue = normalized,
                                                rawValue = barcode,
                                                format = draft.barcodeFormat,
                                                isVerified = true,
                                                createdAt = now,
                                                updatedAt = now
                                            )
                                        )
                                    }
                                }
                            }
                            onProductSaved()
                        } catch (e: Exception) {
                            errorMessage = "Failed to save product: ${e.message}"
                            isSaving = false
                        }
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Register Product")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ProductDetailsDialog(
    details: ProductWithDetails,
    onDismiss: () -> Unit,
    onAddUnitClick: (ProductMaster) -> Unit,
    onEditPriceClick: (ProductUnit, UnitPriceConfig?) -> Unit
) {
    val product = details.product

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.displayName) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!product.brandName.isNullOrBlank()) Text("Brand: ${product.brandName}", style = MaterialTheme.typography.bodyMedium)
                if (!product.genericName.isNullOrBlank()) Text("Generic: ${product.genericName}", style = MaterialTheme.typography.bodyMedium)
                if (!product.productType.isNullOrBlank()) Text("Type: ${product.productType}", style = MaterialTheme.typography.bodyMedium)
                if (!product.manufacturer.isNullOrBlank()) Text("Manufacturer: ${product.manufacturer}", style = MaterialTheme.typography.bodyMedium)
                if (!product.description.isNullOrBlank()) Text("Description: ${product.description}", style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Configured Commercial Units", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                details.units.forEach { unit ->
                    val priceConfig = details.priceConfigsByUnitId[unit.id]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (unit.isBaseUnit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(unit.name, fontWeight = FontWeight.Bold)
                                    if (unit.isBaseUnit) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(Base Unit)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    if (unit.isBaseUnit) "1 base unit"
                                    else "1 ${unit.name} = ${unit.conversionMultiplier} ${details.units.firstOrNull { it.isBaseUnit }?.name ?: "base units"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Cost: ${priceConfig?.buyingPrice?.let { "KES " + (it.minorUnits.toDouble() / 100.0) } ?: "Not set"}", style = MaterialTheme.typography.bodySmall)
                                Text("Selling: ${priceConfig?.sellingPrice?.let { "KES " + (it.minorUnits.toDouble() / 100.0) } ?: "Not set"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(onClick = { onEditPriceClick(unit, priceConfig) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Price", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onAddUnitClick(product) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Packaging Unit")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun AddUnitDialog(
    product: ProductMaster,
    onDismiss: () -> Unit,
    onSaveUnit: (String, String, Long, Double?, Double?) -> Unit
) {
    var unitName by remember { mutableStateOf("") }
    var abbreviation by remember { mutableStateOf("") }
    var multiplier by remember { mutableStateOf("10") }
    var costPrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Unit for ${product.displayName}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = unitName, onValueChange = { unitName = it }, label = { Text("Unit Name (e.g. Box, Strip)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = abbreviation, onValueChange = { abbreviation = it }, label = { Text("Abbreviation (e.g. bx, str)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = multiplier,
                    onValueChange = { multiplier = it },
                    label = { Text("Contains how many base units?") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price (KES) (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = it }, label = { Text("Selling Price (KES) (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (unitName.isBlank() || abbreviation.isBlank()) {
                    errorMessage = "Name and abbreviation are required."
                    return@Button
                }
                val m = multiplier.toLongOrNull() ?: 1L
                if (m <= 0) {
                    errorMessage = "Multiplier must be greater than 0."
                    return@Button
                }
                onSaveUnit(unitName.trim(), abbreviation.trim(), m, costPrice.toDoubleOrNull(), sellingPrice.toDoubleOrNull())
            }) {
                Text("Add Unit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditPriceDialog(
    unit: ProductUnit,
    currentPriceConfig: UnitPriceConfig?,
    onDismiss: () -> Unit,
    onSavePrice: (Double?, Double?) -> Unit
) {
    var costPrice by remember {
        mutableStateOf(currentPriceConfig?.buyingPrice?.let { (it.minorUnits.toDouble() / 100.0).toString() } ?: "")
    }
    var sellingPrice by remember {
        mutableStateOf(currentPriceConfig?.sellingPrice?.let { (it.minorUnits.toDouble() / 100.0).toString() } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Prices for ${unit.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = costPrice,
                    onValueChange = { costPrice = it },
                    label = { Text("Cost / Buying Price (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sellingPrice,
                    onValueChange = { sellingPrice = it },
                    label = { Text("Selling Price (KES)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSavePrice(costPrice.toDoubleOrNull(), sellingPrice.toDoubleOrNull())
            }) {
                Text("Save Prices")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Full product catalog lookup & pricing management sheet/dialog,
 * preserving the direct operational product search, pricing, and unit
 * configurations on Dashboard.
 */
@Composable
fun ProductCatalogManagementDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onScanProduct: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var productsWithDetails by remember { mutableStateOf<List<ProductWithDetails>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var selectedProductForDetails by remember { mutableStateOf<ProductWithDetails?>(null) }
    var showAddUnitDialogForProduct by remember { mutableStateOf<ProductMaster?>(null) }
    var showEditPriceDialogForUnit by remember { mutableStateOf<Pair<ProductUnit, UnitPriceConfig?>?>(null) }
    var categories by remember { mutableStateOf<List<ProductCategory>>(emptyList()) }

    fun refreshProducts() {
        scope.launch {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) {
                val products = container.productMasterDao.getAllProducts()
                val allUnits = container.productMasterDao.getAllUnits()
                val allPrices = container.productMasterDao.getAllPriceConfigs()
                val unitsByProductId = allUnits.groupBy { it.productId }
                val pricesByUnitId = allPrices.associateBy { it.productUnitId }

                products.map { product ->
                    ProductWithDetails(
                        product = product,
                        units = unitsByProductId[product.id] ?: emptyList(),
                        priceConfigsByUnitId = pricesByUnitId
                    )
                }
            }
            productsWithDetails = loaded
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProducts()
        categories = withContext(Dispatchers.IO) {
            container.productCategoryRepository.getActive()
        }
    }

    val filteredProducts = remember(productsWithDetails, searchQuery) {
        if (searchQuery.isBlank()) productsWithDetails
        else {
            val q = searchQuery.trim().lowercase()
            productsWithDetails.filter {
                it.product.brandName?.lowercase()?.contains(q) == true ||
                    it.product.genericName?.lowercase()?.contains(q) == true ||
                    it.product.manufacturer?.lowercase()?.contains(q) == true ||
                    it.product.productType?.lowercase()?.contains(q) == true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Product Catalog & Pricing",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onScanProduct,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Barcode")
                    }
                    Button(
                        onClick = { showAddProductDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Register Product")
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by brand, generic name, manufacturer...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching products found", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredProducts) { item ->
                            val product = item.product
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductForDetails = item },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(product.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    product.manufacturer?.let {
                                        Text("Manufacturer: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("${item.units.size} unit(s) configured", style = MaterialTheme.typography.bodySmall)
                                        val basePrice = item.units.firstOrNull { it.isBaseUnit }?.let { item.priceConfigsByUnitId[it.id] }
                                        basePrice?.sellingPrice?.let {
                                            Text("Base: KES ${it.minorUnits.toDouble() / 100.0}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProductDialog) {
        AddProductDialog(
            container = container,
            categories = categories,
            initialScanDraft = null,
            onDismiss = { showAddProductDialog = false },
            onProductSaved = {
                showAddProductDialog = false
                refreshProducts()
            }
        )
    }

    selectedProductForDetails?.let { details ->
        ProductDetailsDialog(
            details = details,
            onDismiss = { selectedProductForDetails = null },
            onAddUnitClick = { prod ->
                selectedProductForDetails = null
                showAddUnitDialogForProduct = prod
            },
            onEditPriceClick = { unit, config ->
                selectedProductForDetails = null
                showEditPriceDialogForUnit = unit to config
            }
        )
    }

    showAddUnitDialogForProduct?.let { product ->
        AddUnitDialog(
            product = product,
            onDismiss = { showAddUnitDialogForProduct = null },
            onSaveUnit = { name, abbr, multiplier, cost, sell ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val unitId = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        container.productMasterDao.insertUnit(
                            ProductUnit(
                                id = unitId,
                                productId = product.id,
                                name = name,
                                abbreviation = abbr,
                                isBaseUnit = false,
                                conversionMultiplier = multiplier,
                                conversionDivisor = 1L,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                        if (cost != null || sell != null) {
                            container.productMasterDao.savePriceConfig(
                                UnitPriceConfig(
                                    id = UUID.randomUUID().toString(),
                                    productUnitId = unitId,
                                    buyingPrice = cost?.let { Money.kes((it * 100).toLong()) },
                                    sellingPrice = sell?.let { Money.kes((it * 100).toLong()) },
                                    isActive = true,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                    showAddUnitDialogForProduct = null
                    refreshProducts()
                }
            }
        )
    }

    showEditPriceDialogForUnit?.let { (unit, currentConfig) ->
        EditPriceDialog(
            unit = unit,
            currentPriceConfig = currentConfig,
            onDismiss = { showEditPriceDialogForUnit = null },
            onSavePrice = { cost, sell ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        container.productMasterDao.savePriceConfig(
                            UnitPriceConfig(
                                id = currentConfig?.id ?: UUID.randomUUID().toString(),
                                productUnitId = unit.id,
                                buyingPrice = cost?.let { Money.kes((it * 100).toLong()) },
                                sellingPrice = sell?.let { Money.kes((it * 100).toLong()) },
                                isActive = true,
                                createdAt = currentConfig?.createdAt ?: now,
                                updatedAt = now
                            )
                        )
                    }
                    showEditPriceDialogForUnit = null
                    refreshProducts()
                }
            }
        )
    }
}
