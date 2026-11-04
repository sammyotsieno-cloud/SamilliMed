package org.samillimed.app.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.ListType
import org.samillimed.app.data.catalog.ServiceItem
import org.samillimed.app.data.catalog.formatAmount
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.ui.common.ComboField
import org.samillimed.app.ui.common.ConfirmDialog
import org.samillimed.app.ui.common.FormSection
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.PrimaryButton
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SwitchRow
import org.samillimed.app.ui.theme.SamilliTheme

/**
 * Register a new service (service = null) or edit an existing one.
 * onClose is called after saving, or when the user leaves without saving.
 */
@Composable
fun ServiceFormScreen(
    catalog: Catalog,
    service: ServiceItem?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val key = service?.id ?: "new"

    // ---- what the user is typing (survives screen rotation) --------------------------------
    var name by rememberSaveable(key) { mutableStateOf(service?.name.orEmpty()) }
    var category by rememberSaveable(key) { mutableStateOf(service?.category.orEmpty()) }
    var department by rememberSaveable(key) { mutableStateOf(service?.department.orEmpty()) }
    var priceText by rememberSaveable(key) {
        mutableStateOf(service?.priceCents?.takeIf { it > 0L }?.let { formatAmount(it) }.orEmpty())
    }
    var notes by rememberSaveable(key) { mutableStateOf(service?.notes.orEmpty()) }
    var active by rememberSaveable(key) { mutableStateOf(service?.active ?: true) }

    var showErrors by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }

    // an empty price means "entered at billing", which is stored as 0
    val price: Long? = if (priceText.isBlank()) 0L else parseKshToCents(priceText)

    fun build(): ServiceItem = ServiceItem(
        id = service?.id.orEmpty(),
        name = name.trim(),
        code = service?.code.orEmpty(),
        category = category.trim(),
        department = department.trim(),
        priceCents = price ?: 0L,
        notes = notes.trim(),
        active = active,
        createdAtMillis = service?.createdAtMillis ?: 0,
        createdBy = service?.createdBy.orEmpty(),
        updatedAtMillis = service?.updatedAtMillis ?: 0,
    )

    val baseline = remember(key) { build() }
    val dirty = build() != baseline

    // ---- checking what was typed ------------------------------------------------------------
    val nameError = if (name.isBlank()) "Enter the service name" else null
    val categoryError = if (category.isBlank()) "Choose or type a category" else null
    val priceError = if (price == null) "Use an amount like 1500 or 1500.50" else null
    val hasErrors = nameError != null || categoryError != null || priceError != null
    val looksDuplicate = catalog.services.any {
        it.id != service?.id && it.name.equals(name.trim(), ignoreCase = true)
    }

    // ---- saving -----------------------------------------------------------------------------
    fun doSave() {
        val s = build()
        val repo = catalog.repo
        repo.saveService(s)
        // anything typed that is not in a list yet is added to it
        repo.addToList(ListType.SERVICE_CATEGORIES, s.category, catalog.list(ListType.SERVICE_CATEGORIES))
        if (s.department.isNotBlank()) {
            repo.addToList(ListType.DEPARTMENTS, s.department, catalog.list(ListType.DEPARTMENTS))
        }
        onClose()
    }

    fun trySave() {
        showErrors = true
        if (hasErrors) return
        if (looksDuplicate) showDuplicate = true else doSave()
    }

    fun leave() {
        if (dirty) showDiscard = true else onClose()
    }

    BackHandler { leave() }

    Column(modifier.fillMaxSize().background(c.background).imePadding()) {
        ScreenHeader(
            title = if (service == null) "Register service" else "Edit service",
            subtitle = service?.name,
            onBack = { leave() },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormSection(title = "Service details") {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Service name *",
                    capitalization = KeyboardCapitalization.Words,
                    error = if (showErrors) nameError else null,
                    supporting = "e.g. General consultation, Malaria test, Wound dressing",
                )
                ComboField(
                    value = category,
                    onValueChange = { category = it },
                    label = "Category *",
                    options = catalog.list(ListType.SERVICE_CATEGORIES),
                    error = if (showErrors) categoryError else null,
                    supporting = "Pick one, or type a new one to add it",
                )
                ComboField(
                    value = department,
                    onValueChange = { department = it },
                    label = "Department",
                    options = catalog.list(ListType.DEPARTMENTS),
                    supporting = "Optional, e.g. Clinic or Laboratory",
                )
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Description",
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                    singleLine = false,
                    minLines = 3,
                    supporting = "Optional: what the service includes",
                )
            }

            FormSection(title = "Price") {
                FormTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                    label = "Standard price (KSh)",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    error = if (showErrors) priceError else null,
                    supporting = "Optional. Leave empty if the price is entered at billing. It can still be changed on each bill.",
                )
            }

            if (service != null) {
                FormSection(title = "Status") {
                    SwitchRow(
                        title = "Active",
                        subtitle = "Inactive services cannot be billed, but old records keep them",
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
                text = if (service == null) "Register service" else "Save changes",
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
            message = "A service with the same name is already registered. Save it anyway?",
            confirmText = "Save anyway",
            dismissText = "Cancel",
            onConfirm = { showDuplicate = false; doSave() },
            onDismiss = { showDuplicate = false },
        )
    }
}
