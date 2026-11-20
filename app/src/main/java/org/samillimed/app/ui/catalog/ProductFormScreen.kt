package org.samillimed.app.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Ingredient
import org.samillimed.app.data.catalog.ListType
import org.samillimed.app.data.catalog.MEDICINE_TYPE
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.ProductUnit
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.suggestedMedicineName
import org.samillimed.app.ui.common.ComboField
import org.samillimed.app.ui.common.CommonIcons
import org.samillimed.app.ui.common.ConfirmDialog
import org.samillimed.app.ui.common.FormSection
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.PrimaryButton
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SwitchRow
import org.samillimed.app.ui.theme.SamilliTheme

/** One line of the units editor. qty = how many of the smallest unit it holds (text, as typed). */
private data class UnitRow(val name: String, val qty: String)

/** One ingredient line of a medicine: generic name and its strength, as typed. */
private data class IngredientRow(val name: String, val strength: String)

private val unitRowsSaver = listSaver<SnapshotStateList<UnitRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.name, it.qty) } },
    restore = { flat ->
        mutableStateListOf<UnitRow>().apply {
            flat.chunked(2).forEach { add(UnitRow(it[0], it[1])) }
        }
    },
)

private val ingredientRowsSaver = listSaver<SnapshotStateList<IngredientRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.name, it.strength) } },
    restore = { flat ->
        mutableStateListOf<IngredientRow>().apply {
            flat.chunked(2).forEach { add(IngredientRow(it[0], it[1])) }
        }
    },
)

private class FormErrors(
    val type: String?,
    val name: String?,
    val ingredientName: Map<Int, String>,
    val baseUnit: String?,
    val extraName: Map<Int, String>,
    val extraQty: Map<Int, String>,
) {
    val any: Boolean
        get() = type != null || name != null || ingredientName.isNotEmpty() || baseUnit != null ||
            extraName.isNotEmpty() || extraQty.isNotEmpty()
}

/**
 * Register a new product (product = null) or edit an existing one.
 * onClose is called after saving, or when the user leaves without saving.
 */
@Composable
fun ProductFormScreen(
    catalog: Catalog,
    product: Product?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val key = product?.id ?: "new"

    // ---- what the user is typing (survives screen rotation) --------------------------------
    var type by rememberSaveable(key) { mutableStateOf(product?.type ?: MEDICINE_TYPE) }
    var name by rememberSaveable(key) { mutableStateOf(product?.name.orEmpty()) }
    // Until the user types their own name, a medicine's name is built from its details.
    var nameEdited by rememberSaveable(key) { mutableStateOf(product != null) }
    var brand by rememberSaveable(key) { mutableStateOf(product?.brandName.orEmpty()) }
    var dosageForm by rememberSaveable(key) { mutableStateOf(product?.dosageForm.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(product?.description.orEmpty()) }
    var trackExpiry by rememberSaveable(key) { mutableStateOf(product?.trackExpiry ?: true) }
    var expiryTouched by rememberSaveable(key) { mutableStateOf(product != null) }
    var reorder by rememberSaveable(key) {
        mutableStateOf(product?.reorderLevel?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var active by rememberSaveable(key) { mutableStateOf(product?.active ?: true) }
    val ingredientRows = rememberSaveable(key, saver = ingredientRowsSaver) {
        mutableStateListOf<IngredientRow>().apply {
            val initial = product?.ingredients.orEmpty()
            if (initial.isEmpty()) add(IngredientRow("", "")) else initial.forEach { add(IngredientRow(it.genericName, it.strength)) }
        }
    }
    val unitRows = rememberSaveable(key, saver = unitRowsSaver) {
        mutableStateListOf<UnitRow>().apply {
            val initial = product?.units.orEmpty()
            if (initial.isEmpty()) {
                add(UnitRow("", "1"))
            } else {
                initial.forEachIndexed { i, u -> add(UnitRow(u.name, if (i == 0) "1" else u.baseQty.toString())) }
            }
        }
    }

    var showErrors by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }

    val isMedicine = type.trim().equals(MEDICINE_TYPE, ignoreCase = true)

    // A new product starts with expiry tracking ON for medicines and OFF for other types,
    // unless the user has already chosen.
    LaunchedEffect(isMedicine) {
        if (product == null && !expiryTouched) trackExpiry = isMedicine
    }

    // ---- turning the typed values into a Product -------------------------------------------
    fun cleanIngredients(): List<Ingredient> =
        ingredientRows
            .filter { it.name.isNotBlank() || it.strength.isNotBlank() }
            .map { Ingredient(it.name.trim(), it.strength.trim()) }

    fun cleanUnits(): List<ProductUnit> {
        val first = unitRows.first()
        val extras = unitRows.drop(1).filter { it.name.isNotBlank() || it.qty.isNotBlank() }
        return listOf(ProductUnit(first.name.trim(), 1)) +
            extras.map { ProductUnit(it.name.trim(), it.qty.toIntOrNull() ?: 0) }
    }

    val suggested = suggestedMedicineName(brand, cleanIngredients(), dosageForm)
    val effectiveName = if (isMedicine && !nameEdited) suggested else name

    fun build(): Product = Product(
        id = product?.id.orEmpty(),
        type = type.trim(),
        name = effectiveName.trim(),
        ingredients = if (isMedicine) cleanIngredients() else emptyList(),
        brandName = brand.trim(),
        dosageForm = if (isMedicine) dosageForm.trim() else "",
        description = description.trim(),
        units = cleanUnits(),
        trackExpiry = trackExpiry,
        reorderLevel = reorder.toIntOrNull() ?: 0,
        active = active,
        createdAtMillis = product?.createdAtMillis ?: 0,
        createdBy = product?.createdBy.orEmpty(),
        updatedAtMillis = product?.updatedAtMillis ?: 0,
    )

    val baseline = remember(key) { build() }
    val dirty = build() != baseline

    // ---- checking what was typed ------------------------------------------------------------
    val unitNames = unitRows.map { it.name.trim().lowercase() }
    val extraName = buildMap<Int, String> {
        for (i in 1 until unitRows.size) {
            val row = unitRows[i]
            if (row.name.isBlank() && row.qty.isBlank()) continue
            if (row.name.isBlank()) put(i, "Enter a unit name")
            else if (unitNames.indexOf(row.name.trim().lowercase()) != i) put(i, "Already used")
        }
    }
    val extraQty = buildMap<Int, String> {
        for (i in 1 until unitRows.size) {
            val row = unitRows[i]
            if (row.name.isBlank() && row.qty.isBlank()) continue
            val q = row.qty.toIntOrNull()
            if (q == null || q < 2) put(i, "2 or more")
        }
    }
    val ingredientName = buildMap<Int, String> {
        if (isMedicine) {
            for (i in ingredientRows.indices) {
                val row = ingredientRows[i]
                if (row.name.isBlank() && (i == 0 || row.strength.isNotBlank())) put(i, "Enter the generic name")
            }
        }
    }
    val errors = FormErrors(
        type = if (type.isBlank()) "Choose or type the product type" else null,
        name = if (effectiveName.isBlank()) "Enter the product name" else null,
        ingredientName = ingredientName,
        baseUnit = if (unitRows.first().name.isBlank()) "Enter the smallest unit, for example Tablet or Piece" else null,
        extraName = extraName,
        extraQty = extraQty,
    )

    // ---- saving -----------------------------------------------------------------------------
    fun doSave() {
        val p = build()
        val repo = catalog.repo
        repo.saveProduct(p)
        // anything typed that is not in a list yet is added to it
        repo.addToList(ListType.PRODUCT_TYPES, p.type, catalog.list(ListType.PRODUCT_TYPES))
        if (p.dosageForm.isNotBlank()) {
            repo.addToList(ListType.DOSAGE_FORMS, p.dosageForm, catalog.list(ListType.DOSAGE_FORMS))
        }
        p.units.forEach { repo.addToList(ListType.UNITS, it.name, catalog.list(ListType.UNITS)) }
        onClose()
    }

    fun looksDuplicate(): Boolean {
        val mine = build()
        return catalog.products.any { other ->
            other.id != product?.id && (
                other.name.equals(mine.name, ignoreCase = true) ||
                    (mine.isMedicine && other.isMedicine && mine.genericNames.isNotEmpty() &&
                        other.genericNames.equals(mine.genericNames, ignoreCase = true) &&
                        other.strengthText.equals(mine.strengthText, ignoreCase = true) &&
                        other.dosageForm.equals(mine.dosageForm, ignoreCase = true) &&
                        other.brandName.equals(mine.brandName, ignoreCase = true))
                )
        }
    }

    fun trySave() {
        showErrors = true
        if (errors.any) return
        if (looksDuplicate()) showDuplicate = true else doSave()
    }

    fun leave() {
        if (dirty) showDiscard = true else onClose()
    }

    BackHandler { leave() }

    val baseName = unitRows.first().name.trim()
    val unitOptions = catalog.list(ListType.UNITS)

    Column(modifier.fillMaxSize().background(c.background).imePadding()) {
        ScreenHeader(
            title = if (product == null) "Register product" else "Edit product",
            subtitle = product?.name,
            onBack = { leave() },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormSection(title = "Product type") {
                ComboField(
                    value = type,
                    onValueChange = { type = it },
                    label = "Product type *",
                    options = catalog.list(ListType.PRODUCT_TYPES),
                    error = if (showErrors) errors.type else null,
                    supporting = "Medicine covers every drug. For anything else, pick or type the kind of item, e.g. Gauze",
                )
            }

            if (isMedicine) {
                FormSection(
                    title = "Medicine details",
                    hint = "Each generic name is the link to the Drug Index later. Combination medicines have several ingredients.",
                ) {
                    FormTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = "Brand name",
                        capitalization = KeyboardCapitalization.Words,
                        supporting = "Leave empty for unbranded generics",
                    )
                    ComboField(
                        value = dosageForm,
                        onValueChange = { dosageForm = it },
                        label = "Dosage form",
                        options = catalog.list(ListType.DOSAGE_FORMS),
                    )
                    for (i in ingredientRows.indices) {
                        val row = ingredientRows[i]
                        if (ingredientRows.size > 1) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Ingredient ${i + 1}",
                                    color = c.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (i > 0) {
                                    TextButton(onClick = { ingredientRows.removeAt(i) }) {
                                        Text("Remove", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        FormTextField(
                            value = row.name,
                            onValueChange = { ingredientRows[i] = ingredientRows[i].copy(name = it) },
                            label = if (i == 0) "Generic name *" else "Generic name",
                            capitalization = KeyboardCapitalization.Words,
                            error = if (showErrors) errors.ingredientName[i] else null,
                            supporting = if (i == 0) "The active ingredient, e.g. Prednisolone" else null,
                        )
                        FormTextField(
                            value = row.strength,
                            onValueChange = { ingredientRows[i] = ingredientRows[i].copy(strength = it) },
                            label = "Strength",
                            supporting = if (i == 0) "As printed on the pack, e.g. 5 mg or 125 mg/5 ml" else null,
                        )
                    }
                    TextButton(onClick = { ingredientRows.add(IngredientRow("", "")) }) {
                        Icon(CommonIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add another ingredient", fontWeight = FontWeight.Bold)
                    }
                    FormTextField(
                        value = effectiveName,
                        onValueChange = { name = it; nameEdited = true },
                        label = "Product name *",
                        capitalization = KeyboardCapitalization.Words,
                        error = if (showErrors) errors.name else null,
                        supporting = "Built from the brand, ingredients and form. You can change it.",
                    )
                    if (nameEdited && suggested.isNotBlank() && effectiveName != suggested) {
                        TextButton(onClick = { nameEdited = false }) {
                            Text("Use suggested name: $suggested", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                FormSection(title = "Product details") {
                    FormTextField(
                        value = name,
                        onValueChange = { name = it; nameEdited = true },
                        label = "Product name *",
                        capitalization = KeyboardCapitalization.Words,
                        error = if (showErrors) errors.name else null,
                        supporting = "e.g. Gauze swab 10 x 10 cm, sterile",
                    )
                    FormTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = "Brand or manufacturer",
                        capitalization = KeyboardCapitalization.Words,
                    )
                }
            }

            FormSection(title = "Description") {
                FormTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                    singleLine = false,
                    minLines = 3,
                    supporting = "Optional: size, use, packaging, anything that helps identify the product",
                )
            }

            FormSection(
                title = "Packaging units",
                hint = "Stock is counted in the smallest unit. Add bigger units if you buy or sell by strip, box and so on. " +
                    "Any unit can be used both when buying stock and when selling or dispensing.",
            ) {
                ComboField(
                    value = unitRows.first().name,
                    onValueChange = { unitRows[0] = unitRows[0].copy(name = it) },
                    label = "Smallest unit *",
                    options = unitOptions,
                    error = if (showErrors) errors.baseUnit else null,
                    supporting = "e.g. Tablet, Piece, Bottle, Roll",
                )
                for (i in 1 until unitRows.size) {
                    val row = unitRows[i]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ComboField(
                            value = row.name,
                            onValueChange = { unitRows[i] = unitRows[i].copy(name = it) },
                            label = "Bigger unit",
                            options = unitOptions,
                            modifier = Modifier.weight(1f),
                            error = if (showErrors) errors.extraName[i] else null,
                        )
                        FormTextField(
                            value = row.qty,
                            onValueChange = { v -> unitRows[i] = unitRows[i].copy(qty = v.filter { it.isDigit() }) },
                            label = "Holds",
                            modifier = Modifier.width(96.dp),
                            keyboardType = KeyboardType.Number,
                            error = if (showErrors) errors.extraQty[i] else null,
                            supporting = if (baseName.isNotEmpty()) baseName else null,
                        )
                        IconButton(
                            onClick = { unitRows.removeAt(i) },
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Icon(CommonIcons.Close, contentDescription = "Remove unit", modifier = Modifier.size(20.dp))
                        }
                    }
                }
                TextButton(onClick = { unitRows.add(UnitRow("", "")) }) {
                    Icon(CommonIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add a bigger unit", fontWeight = FontWeight.Bold)
                }
                val examples = (1 until unitRows.size).mapNotNull { i ->
                    val r = unitRows[i]
                    val q = r.qty.toIntOrNull()
                    if (r.name.isNotBlank() && q != null && q >= 2 && baseName.isNotEmpty()) {
                        "1 ${r.name.trim()} = $q $baseName"
                    } else {
                        null
                    }
                }
                if (examples.isNotEmpty()) {
                    Text(text = examples.joinToString("   ·   "), color = c.textSecondary, fontSize = 12.sp)
                }
            }

            FormSection(title = "Stock settings") {
                SwitchRow(
                    title = "Track expiry dates",
                    subtitle = "Every received batch will need an expiry date",
                    checked = trackExpiry,
                    onCheckedChange = { trackExpiry = it; expiryTouched = true },
                )
                FormTextField(
                    value = reorder,
                    onValueChange = { reorder = it.filter { ch -> ch.isDigit() } },
                    label = if (baseName.isNotEmpty()) "Reorder level (counted in $baseName)" else "Reorder level",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    supporting = "Optional. Flagged as low stock at or below this quantity",
                )
            }

            if (product != null) {
                FormSection(title = "Status") {
                    SwitchRow(
                        title = "Active",
                        subtitle = "Inactive products cannot be sold, but old records keep them",
                        checked = active,
                        onCheckedChange = { active = it },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            PrimaryButton(
                text = if (product == null) "Register product" else "Save changes",
                onClick = { trySave() },
            )
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "What you entered has not been saved.",
            confirmText = "Discard",
            dismissText = "Keep editing",
            onConfirm = { showDiscard = false; onClose() },
            onDismiss = { showDiscard = false },
        )
    }
    if (showDuplicate) {
        ConfirmDialog(
            title = "Possible duplicate",
            message = "A product with the same name or the same medicine details is already registered. Save it anyway?",
            confirmText = "Save anyway",
            dismissText = "Cancel",
            onConfirm = { showDuplicate = false; doSave() },
            onDismiss = { showDuplicate = false },
        )
    }
}
