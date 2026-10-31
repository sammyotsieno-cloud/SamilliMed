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
import org.samillimed.app.data.catalog.ListType
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.ProductUnit
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

private val unitRowsSaver = listSaver<SnapshotStateList<UnitRow>, String>(
    save = { rows -> rows.flatMap { listOf(it.name, it.qty) } },
    restore = { flat ->
        mutableStateListOf<UnitRow>().apply {
            flat.chunked(2).forEach { add(UnitRow(it[0], it[1])) }
        }
    },
)

private class FormErrors(
    val name: String?,
    val category: String?,
    val baseUnit: String?,
    val extraName: Map<Int, String>,
    val extraQty: Map<Int, String>,
    val expiry: String?,
    val reorder: String?,
    val barcode: String?,
) {
    val any: Boolean
        get() = name != null || category != null || baseUnit != null || extraName.isNotEmpty() ||
            extraQty.isNotEmpty() || expiry != null || reorder != null || barcode != null
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
    var name by rememberSaveable(key) { mutableStateOf(product?.name.orEmpty()) }
    var genericName by rememberSaveable(key) { mutableStateOf(product?.genericName.orEmpty()) }
    var brandName by rememberSaveable(key) { mutableStateOf(product?.brandName.orEmpty()) }
    var strength by rememberSaveable(key) { mutableStateOf(product?.strength.orEmpty()) }
    var dosageForm by rememberSaveable(key) { mutableStateOf(product?.dosageForm.orEmpty()) }
    var manufacturer by rememberSaveable(key) { mutableStateOf(product?.manufacturer.orEmpty()) }
    var category by rememberSaveable(key) { mutableStateOf(product?.category.orEmpty()) }
    var code by rememberSaveable(key) { mutableStateOf(product?.code.orEmpty()) }
    var barcode by rememberSaveable(key) { mutableStateOf(product?.barcode.orEmpty()) }
    var prescription by rememberSaveable(key) { mutableStateOf(product?.prescriptionStatus.orEmpty()) }
    var controlled by rememberSaveable(key) { mutableStateOf(product?.controlled ?: false) }
    var trackExpiry by rememberSaveable(key) { mutableStateOf(product?.trackExpiry ?: true) }
    var expiryDays by rememberSaveable(key) { mutableStateOf(product?.expiryAlertDays?.toString() ?: "90") }
    var reorder by rememberSaveable(key) {
        mutableStateOf(product?.reorderLevel?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var storage by rememberSaveable(key) { mutableStateOf(product?.storage.orEmpty()) }
    var location by rememberSaveable(key) { mutableStateOf(product?.location.orEmpty()) }
    var notes by rememberSaveable(key) { mutableStateOf(product?.notes.orEmpty()) }
    var active by rememberSaveable(key) { mutableStateOf(product?.active ?: true) }
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

    // ---- turning the typed values into a Product -------------------------------------------
    fun cleanUnits(): List<ProductUnit> {
        val first = unitRows.first()
        val extras = unitRows.drop(1).filter { it.name.isNotBlank() || it.qty.isNotBlank() }
        return listOf(ProductUnit(first.name.trim(), 1)) +
            extras.map { ProductUnit(it.name.trim(), it.qty.toIntOrNull() ?: 0) }
    }

    fun build(): Product = Product(
        id = product?.id.orEmpty(),
        name = name.trim(),
        genericName = genericName.trim(),
        brandName = brandName.trim(),
        strength = strength.trim(),
        dosageForm = dosageForm.trim(),
        manufacturer = manufacturer.trim(),
        category = category.trim(),
        units = cleanUnits(),
        code = code.trim(),
        barcode = barcode.trim(),
        prescriptionStatus = prescription.trim(),
        controlled = controlled,
        trackExpiry = trackExpiry,
        expiryAlertDays = expiryDays.toIntOrNull() ?: 90,
        reorderLevel = reorder.toIntOrNull() ?: 0,
        storage = storage.trim(),
        location = location.trim(),
        notes = notes.trim(),
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
    val errors = FormErrors(
        name = if (name.isBlank()) "Enter the product name" else null,
        category = if (category.isBlank()) "Choose or type a category" else null,
        baseUnit = if (unitRows.first().name.isBlank()) "Enter the smallest unit, for example Tablet" else null,
        extraName = extraName,
        extraQty = extraQty,
        expiry = if (!trackExpiry) null else {
            val d = expiryDays.toIntOrNull()
            if (d == null) "Enter the number of days" else if (d > 3650) "Too many days" else null
        },
        reorder = if (reorder.isNotBlank() && reorder.toIntOrNull() == null) "Enter a whole number" else null,
        barcode = if (barcode.isNotBlank() && catalog.products.any {
                it.id != product?.id && it.barcode.equals(barcode.trim(), ignoreCase = true)
            }
        ) "Another product already has this barcode" else null,
    )
    val looksDuplicate = catalog.products.any {
        it.id != product?.id &&
            it.name.equals(name.trim(), ignoreCase = true) &&
            it.strength.equals(strength.trim(), ignoreCase = true) &&
            it.dosageForm.equals(dosageForm.trim(), ignoreCase = true)
    }

    // ---- saving -----------------------------------------------------------------------------
    fun doSave() {
        val p = build()
        catalog.repo.saveProduct(p)
        // anything typed that is not in a list yet is added to it
        val repo = catalog.repo
        repo.addToList(ListType.PRODUCT_CATEGORIES, p.category, catalog.list(ListType.PRODUCT_CATEGORIES))
        repo.addToList(ListType.DOSAGE_FORMS, p.dosageForm, catalog.list(ListType.DOSAGE_FORMS))
        repo.addToList(ListType.PRESCRIPTION_STATUS, p.prescriptionStatus, catalog.list(ListType.PRESCRIPTION_STATUS))
        repo.addToList(ListType.STORAGE_CONDITIONS, p.storage, catalog.list(ListType.STORAGE_CONDITIONS))
        p.units.forEach { repo.addToList(ListType.UNITS, it.name, catalog.list(ListType.UNITS)) }
        onClose()
    }

    fun trySave() {
        showErrors = true
        if (errors.any) return
        if (looksDuplicate) showDuplicate = true else doSave()
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
            FormSection(title = "Product details") {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Product name *",
                    capitalization = KeyboardCapitalization.Words,
                    error = if (showErrors) errors.name else null,
                    supporting = "As it should appear on sales, e.g. Amoxicillin 500 mg capsules",
                )
                FormTextField(
                    value = genericName,
                    onValueChange = { genericName = it },
                    label = "Generic name (active ingredient)",
                    capitalization = KeyboardCapitalization.Words,
                )
                FormTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = "Brand name",
                    capitalization = KeyboardCapitalization.Words,
                )
                FormTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = "Strength",
                    supporting = "e.g. 500 mg, 125 mg/5 ml",
                )
                ComboField(
                    value = dosageForm,
                    onValueChange = { dosageForm = it },
                    label = "Dosage form",
                    options = catalog.list(ListType.DOSAGE_FORMS),
                )
                FormTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = "Manufacturer",
                    capitalization = KeyboardCapitalization.Words,
                )
                ComboField(
                    value = category,
                    onValueChange = { category = it },
                    label = "Category *",
                    options = catalog.list(ListType.PRODUCT_CATEGORIES),
                    error = if (showErrors) errors.category else null,
                    supporting = "Pick one, or type a new one to add it",
                )
            }

            FormSection(
                title = "Units",
                hint = "Stock is counted in the smallest unit. Add bigger units if you also sell by strip, box and so on. " +
                    "Whoever sells picks the unit at the time.",
            ) {
                ComboField(
                    value = unitRows.first().name,
                    onValueChange = { unitRows[0] = unitRows[0].copy(name = it) },
                    label = "Smallest unit *",
                    options = unitOptions,
                    error = if (showErrors) errors.baseUnit else null,
                    supporting = "e.g. Tablet, Capsule, Bottle, Piece",
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

            FormSection(title = "Stock control") {
                FormTextField(
                    value = reorder,
                    onValueChange = { reorder = it.filter { ch -> ch.isDigit() } },
                    label = if (baseName.isNotEmpty()) "Reorder level (counted in $baseName)" else "Reorder level",
                    keyboardType = KeyboardType.Number,
                    error = if (showErrors) errors.reorder else null,
                    supporting = "Flagged as low stock at or below this quantity",
                )
                SwitchRow(
                    title = "Track expiry dates",
                    subtitle = "Every received batch will need an expiry date",
                    checked = trackExpiry,
                    onCheckedChange = { trackExpiry = it },
                )
                if (trackExpiry) {
                    FormTextField(
                        value = expiryDays,
                        onValueChange = { expiryDays = it.filter { ch -> ch.isDigit() } },
                        label = "Alert before expiry (days)",
                        keyboardType = KeyboardType.Number,
                        error = if (showErrors) errors.expiry else null,
                        supporting = "A batch is flagged this many days before it expires",
                    )
                }
            }

            FormSection(title = "Regulatory") {
                ComboField(
                    value = prescription,
                    onValueChange = { prescription = it },
                    label = "Prescription status",
                    options = catalog.list(ListType.PRESCRIPTION_STATUS),
                )
                SwitchRow(
                    title = "Controlled drug",
                    subtitle = "Marks the product so it is easy to find and report on",
                    checked = controlled,
                    onCheckedChange = { controlled = it },
                )
            }

            FormSection(title = "Storage and notes") {
                ComboField(
                    value = storage,
                    onValueChange = { storage = it },
                    label = "Storage condition",
                    options = catalog.list(ListType.STORAGE_CONDITIONS),
                )
                FormTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "Shelf or location",
                    capitalization = KeyboardCapitalization.Sentences,
                    supporting = "e.g. Shelf B2, Fridge",
                )
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes",
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                    singleLine = false,
                    minLines = 3,
                )
            }

            FormSection(title = "Codes") {
                FormTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = "Product code",
                    supporting = "Your own internal code (optional)",
                )
                FormTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = "Barcode",
                    error = if (showErrors) errors.barcode else null,
                    supporting = "Optional. Scanning can be added later",
                    imeAction = ImeAction.Done,
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
            message = "A product with the same name, strength and form is already registered. Save it anyway?",
            confirmText = "Save anyway",
            dismissText = "Cancel",
            onConfirm = { showDuplicate = false; doSave() },
            onDismiss = { showDuplicate = false },
        )
    }
}
