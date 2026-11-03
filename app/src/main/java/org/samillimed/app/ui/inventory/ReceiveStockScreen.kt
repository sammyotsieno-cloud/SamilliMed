package org.samillimed.app.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.ProductUnit
import org.samillimed.app.data.catalog.baseUnit
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.inventory.GoodsReceipt
import org.samillimed.app.data.inventory.GoodsReceiptLine
import org.samillimed.app.data.inventory.InventoryCalculations
import org.samillimed.app.data.inventory.InventoryRepository
import org.samillimed.app.ui.common.CommonIcons
import org.samillimed.app.ui.common.ConfirmDialog
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.FormSection
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.PrimaryButton
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.common.SearchField
import org.samillimed.app.ui.common.formFieldColors
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ---- the receipt being typed -----------------------------------------------------------------

/** One batch on the receipt. Product name and type are copied so history never changes. */
private data class DraftLine(
    val productId: String,
    val productName: String,
    val productType: String,
    val quantity: Long,
    val unitName: String,
    val baseQty: Long,
    val batchNumber: String,
    /** The LAST valid day of the batch (local midnight), or null when it has no expiry. */
    val expiryMillis: Long?,
    val purchaseCostCents: Long,
    val sellingPriceCents: Long,
)

/** Lets the receipt survive screen rotation and the app being recreated. */
private val draftLinesSaver = listSaver<SnapshotStateList<DraftLine>, String>(
    save = { lines ->
        lines.flatMap {
            listOf(
                it.productId, it.productName, it.productType, it.quantity.toString(), it.unitName,
                it.baseQty.toString(), it.batchNumber, it.expiryMillis?.toString().orEmpty(),
                it.purchaseCostCents.toString(), it.sellingPriceCents.toString(),
            )
        }
    },
    restore = { flat ->
        mutableStateListOf<DraftLine>().apply {
            flat.chunked(10).forEach {
                add(
                    DraftLine(
                        productId = it[0], productName = it[1], productType = it[2],
                        quantity = it[3].toLong(), unitName = it[4], baseQty = it[5].toLong(),
                        batchNumber = it[6], expiryMillis = it[7].toLongOrNull(),
                        purchaseCostCents = it[8].toLong(), sellingPriceCents = it[9].toLong(),
                    ),
                )
            }
        }
    },
)

private class LineErrors(
    val product: String?,
    val unit: String?,
    val quantity: String?,
    val cost: String?,
    val price: String?,
    val batch: String?,
    val expiry: String?,
) {
    val any: Boolean
        get() = listOf(product, unit, quantity, cost, price, batch, expiry).any { it != null }
}

// ---- dates and numbers -----------------------------------------------------------------------

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val longFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun todayText(): String = LocalDate.now().format(dayFormat)

private fun fullYear(y: Int): Int = if (y < 100) 2000 + y else y

/**
 * Reads what was typed: "05/2027" or "0527" -> the last day of May 2027;
 * "15/05/2027" or "15052027" -> that day. Returns null when it is not a real date.
 */
private fun parseDate(text: String): LocalDate? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts: List<String> = if (t.any { !it.isDigit() }) {
        t.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
    } else {
        when (t.length) {
            4 -> listOf(t.substring(0, 2), t.substring(2, 4))
            6 -> listOf(t.substring(0, 2), t.substring(2, 6))
            8 -> listOf(t.substring(0, 2), t.substring(2, 4), t.substring(4, 8))
            else -> return null
        }
    }
    return runCatching {
        when (parts.size) {
            2 -> YearMonth.of(fullYear(parts[1].toInt()), parts[0].toInt()).atEndOfMonth()
            3 -> LocalDate.of(fullYear(parts[2].toInt()), parts[1].toInt(), parts[0].toInt())
            else -> null
        }
    }.getOrNull()
}

private fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dateLabel(millis: Long?): String = millis?.toLocalDate()?.format(longFormat) ?: "Not set"

private fun formatCount(n: Long): String = String.format(Locale.US, "%,d", n)

private fun moneyError(text: String, cents: Long?): String? = when {
    text.isBlank() -> "Enter an amount"
    cents == null -> "Use an amount like 1250 or 12.50"
    else -> null
}

private fun Product.detailText(): String =
    if (isMedicine) {
        listOf(genericNames, strengthText, dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        type
    }

private fun Product.unitsText(): String =
    units.joinToString(" · ") { if (it.baseQty <= 1) it.name else "${it.name} (${it.baseQty})" }

// ---- the screen ------------------------------------------------------------------------------

/**
 * Receives one supplier delivery containing one or more product batches.
 * Each batch keeps its own unit, batch number, expiry date and prices.
 */
@Composable
fun ReceiveStockScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val repository = remember { InventoryRepository() }

    // the receipt. receiptId stays the same for this draft, so saving twice can never receive twice.
    val receiptId = rememberSaveable { UUID.randomUUID().toString() }
    var supplier by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var receivedText by rememberSaveable { mutableStateOf(todayText()) }
    val lines = rememberSaveable(saver = draftLinesSaver) { mutableStateListOf<DraftLine>() }

    // the batch being typed
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var unitName by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryText by rememberSaveable { mutableStateOf("") }
    var costText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }

    var picking by rememberSaveable { mutableStateOf(false) }
    var showLineErrors by remember { mutableStateOf(false) }
    var showReceiptErrors by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showBelowCost by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val activeProducts = remember(catalog.products) {
        catalog.products.filter { it.active && it.units.isNotEmpty() }
    }
    val product = activeProducts.firstOrNull { it.id == productId }
    val unit = product?.units?.firstOrNull { it.name.equals(unitName, ignoreCase = true) }
    val trackExpiry = product?.trackExpiry == true

    // ---- reading what was typed ------------------------------------------------------------
    val quantity = quantityText.trim().toLongOrNull()
    val cost = if (costText.isBlank()) null else parseKshToCents(costText)
    val price = if (priceText.isBlank()) null else parseKshToCents(priceText)
    val expiryDate = parseDate(expiryText)
    val baseQuantity: Long? =
        if (quantity != null && quantity > 0L && unit != null) {
            InventoryCalculations.toBaseQuantity(quantity, unit.baseQty.toLong())
        } else {
            null
        }
    val lineCost: Long? =
        if (quantity != null && quantity > 0L && cost != null) {
            InventoryCalculations.calculateLineCostCents(quantity, cost)
        } else {
            null
        }
    val duplicate = product != null && batchNumber.isNotBlank() && lines.any {
        it.productId == product.id &&
            it.batchNumber.equals(batchNumber.trim(), ignoreCase = true) &&
            it.expiryMillis == expiryDate?.startOfDayMillis()
    }

    val errors = LineErrors(
        product = if (product == null) "Choose the product received" else null,
        unit = if (product != null && unit == null) "Choose the unit it was delivered in" else null,
        quantity = when {
            quantityText.isBlank() -> "Enter the quantity received"
            quantity == null || quantity <= 0L -> "Enter a whole number above zero"
            unit != null && baseQuantity == null -> "That quantity is too large"
            else -> null
        },
        cost = moneyError(costText, cost)
            ?: if (quantity != null && quantity > 0L && cost != null && lineCost == null) "The line total is too large" else null,
        price = moneyError(priceText, price)
            ?: if (price != null && price <= 0L) "The selling price must be above zero" else null,
        batch = when {
            trackExpiry && batchNumber.isBlank() -> "Enter the batch number printed on the pack"
            duplicate -> "This batch is already on the receipt"
            else -> null
        },
        expiry = when {
            expiryText.isBlank() -> if (trackExpiry) "Enter the expiry date printed on the pack" else null
            expiryDate == null -> "Type it like 05/2027 or 0527"
            !expiryDate.isAfter(today) -> "The expiry date must be in the future"
            expiryDate.isAfter(today.plusYears(15)) -> "Check the year"
            else -> null
        },
    )

    val receivedDate = parseDate(receivedText)
    val receivedError = when {
        receivedDate == null -> "Type the date like 01/11/2026"
        receivedDate.isAfter(today) -> "The date cannot be in the future"
        else -> null
    }
    val supplierError = if (supplier.isBlank()) "Enter the supplier name" else null

    val batchInProgress = productId != null || quantityText.isNotBlank() || batchNumber.isNotBlank() ||
        expiryText.isNotBlank() || costText.isNotBlank() || priceText.isNotBlank()
    val dirty = lines.isNotEmpty() || supplier.isNotBlank() || reference.isNotBlank() || batchInProgress

    val totalCostCents = lines.fold(0L) { total, line ->
        val lc = InventoryCalculations.calculateLineCostCents(line.quantity, line.purchaseCostCents) ?: 0L
        runCatching { Math.addExact(total, lc) }.getOrDefault(Long.MAX_VALUE)
    }

    // ---- actions ---------------------------------------------------------------------------
    fun commitLine() {
        val p = product ?: return
        val u = unit ?: return
        val q = quantity ?: return
        lines.add(
            DraftLine(
                productId = p.id,
                productName = p.name,
                productType = p.type,
                quantity = q,
                unitName = u.name,
                baseQty = u.baseQty.toLong(),
                batchNumber = batchNumber.trim(),
                expiryMillis = expiryDate?.startOfDayMillis(),
                purchaseCostCents = cost ?: 0L,
                sellingPriceCents = price ?: 0L,
            ),
        )
        productId = null
        unitName = ""
        quantityText = ""
        batchNumber = ""
        expiryText = ""
        costText = ""
        priceText = ""
        showLineErrors = false
    }

    fun addLine() {
        showLineErrors = true
        if (errors.any) return
        if (price != null && cost != null && price < cost) showBelowCost = true else commitLine()
    }

    fun saveReceipt() {
        showReceiptErrors = true
        saveError = null
        if (supplierError != null || receivedError != null) return
        if (batchInProgress) {
            saveError = "A batch is half filled in. Tap Add batch to receipt, or clear its fields."
            return
        }
        if (lines.isEmpty()) {
            saveError = "Add at least one batch to the receipt."
            return
        }
        val date = receivedDate ?: return
        val receivedMillis = if (date == today) System.currentTimeMillis() else date.startOfDayMillis()
        val receiptLines = lines.map { line ->
            GoodsReceiptLine(
                productId = line.productId,
                productNameSnapshot = line.productName,
                productTypeSnapshot = line.productType,
                quantityReceived = line.quantity,
                receivingUnitName = line.unitName,
                baseUnitsPerReceivingUnit = line.baseQty,
                batchNumber = line.batchNumber,
                expiryDateMillis = line.expiryMillis,
                purchaseCostPerReceivingUnitCents = line.purchaseCostCents,
                sellingPricePerReceivingUnitCents = line.sellingPriceCents,
            )
        }
        saving = true
        repository.saveReceipt(
            receipt = GoodsReceipt(
                id = receiptId,
                supplierName = supplier.trim(),
                referenceNumber = reference.trim(),
                receivedAtMillis = receivedMillis,
            ),
            lines = receiptLines,
            onSuccess = {
                saving = false
                onBack()
            },
            onFailure = { message ->
                saving = false
                saveError = message
            },
        )
    }

    fun leave() {
        if (dirty) showDiscard = true else onBack()
    }

    BackHandler(enabled = picking) { picking = false }
    BackHandler(enabled = !picking) { leave() }

    // ---- screen ----------------------------------------------------------------------------
    if (picking) {
        ProductPicker(
            products = activeProducts,
            onPick = { p ->
                productId = p.id
                unitName = if (p.units.size == 1) p.units.first().name else ""
                picking = false
            },
            onClose = { picking = false },
            modifier = modifier,
        )
    } else {
        Column(modifier.fillMaxSize().background(c.background).imePadding()) {
            ScreenHeader(
                title = "Receive Stock",
                subtitle = "Record a supplier delivery",
                onBack = { leave() },
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FormSection(title = "Receipt details") {
                    FormTextField(
                        value = supplier,
                        onValueChange = { supplier = it },
                        label = "Supplier name *",
                        error = if (showReceiptErrors) supplierError else null,
                    )
                    FormTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = "Invoice or delivery reference",
                    )
                    FormTextField(
                        value = receivedText,
                        onValueChange = { receivedText = it.filter { ch -> ch.isDigit() || ch == '/' } },
                        label = "Date received",
                        keyboardType = KeyboardType.Number,
                        error = if (showReceiptErrors) receivedError else null,
                        supporting = receivedDate?.takeIf { receivedError == null }?.format(longFormat)
                            ?: "Day, month and year, e.g. 01/11/2026",
                    )
                }

                FormSection(
                    title = "Add a batch",
                    hint = "Add one line for each batch received. The same product can have several batches.",
                ) {
                    PickerField(
                        value = product?.name.orEmpty(),
                        label = "Product *",
                        placeholder = "Tap to search registered products",
                        onClick = { picking = true },
                        error = if (showLineErrors) errors.product else null,
                        supporting = product?.let {
                            listOf(it.detailText(), "Units: ${it.unitsText()}")
                                .filter { s -> s.isNotBlank() }
                                .joinToString("\n")
                        },
                    )

                    if (product != null) {
                        UnitChips(
                            units = product.units,
                            selected = unitName,
                            error = if (showLineErrors) errors.unit else null,
                            onSelect = { unitName = it },
                        )
                    }

                    FormTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                        label = if (unit != null) "Quantity received (in ${unit.name}) *" else "Quantity received *",
                        keyboardType = KeyboardType.Number,
                        error = if (showLineErrors) errors.quantity else null,
                    )
                    if (product != null && unit != null && baseQuantity != null) {
                        val baseName = product.baseUnit?.name.orEmpty()
                        val parts = buildList {
                            if (unit.baseQty > 1) add("= ${formatCount(baseQuantity)} $baseName")
                            if (lineCost != null) add("Line total ${formatKsh(lineCost)}")
                        }
                        if (parts.isNotEmpty()) {
                            Text(text = parts.joinToString("   ·   "), color = c.textSecondary, fontSize = 12.sp)
                        }
                    }

                    FormTextField(
                        value = costText,
                        onValueChange = { costText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = if (unit != null) "Purchase cost per ${unit.name} (KSh) *" else "Purchase cost per unit (KSh) *",
                        keyboardType = KeyboardType.Decimal,
                        error = if (showLineErrors) errors.cost else null,
                    )
                    FormTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = if (unit != null) "Selling price per ${unit.name} (KSh) *" else "Selling price per unit (KSh) *",
                        keyboardType = KeyboardType.Decimal,
                        error = if (showLineErrors) errors.price else null,
                        supporting = "Reference price stored with this batch. Dispensing can use another price.",
                    )
                    FormTextField(
                        value = batchNumber,
                        onValueChange = { batchNumber = it },
                        label = if (trackExpiry) "Batch or lot number *" else "Batch or lot number",
                        error = if (showLineErrors) errors.batch else null,
                    )
                    FormTextField(
                        value = expiryText,
                        onValueChange = { expiryText = it.filter { ch -> ch.isDigit() || ch == '/' } },
                        label = if (trackExpiry) "Expiry date *" else "Expiry date (optional)",
                        keyboardType = KeyboardType.Number,
                        error = if (showLineErrors) errors.expiry else null,
                        supporting = if (expiryDate != null) {
                            "Valid until ${expiryDate.format(longFormat)}"
                        } else {
                            "As printed on the pack: 05/2027, 0527 or 15/05/2027"
                        },
                    )
                    PrimaryButton(text = "Add batch to receipt", onClick = { addLine() })
                }

                if (lines.isNotEmpty()) {
                    FormSection(
                        title = if (lines.size == 1) "On this receipt (1 batch)" else "On this receipt (${lines.size} batches)",
                    ) {
                        lines.forEachIndexed { index, line ->
                            val baseName = catalog.products
                                .firstOrNull { it.id == line.productId }?.baseUnit?.name.orEmpty()
                            SamilliCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            line.productName,
                                            color = c.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        val received = buildString {
                                            append("${formatCount(line.quantity)} ${line.unitName}")
                                            if (line.baseQty > 1 && baseName.isNotEmpty()) {
                                                append(" (= ${formatCount(line.quantity * line.baseQty)} $baseName)")
                                            }
                                        }
                                        Text(received, color = c.textSecondary, fontSize = 12.sp)
                                        Text(
                                            "Batch: ${line.batchNumber.ifBlank { "not recorded" }}" +
                                                (line.expiryMillis?.let { "  ·  Expires ${dateLabel(it)}" } ?: ""),
                                            color = c.textSecondary,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            "Cost ${formatKsh(line.purchaseCostCents)} · Selling ${formatKsh(line.sellingPriceCents)} per ${line.unitName}",
                                            color = c.textSecondary,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            "Line total " + formatKsh(
                                                InventoryCalculations.calculateLineCostCents(
                                                    line.quantity,
                                                    line.purchaseCostCents,
                                                ) ?: 0L,
                                            ),
                                            color = c.accentText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    TextButton(onClick = { lines.removeAt(index) }) {
                                        Text("Remove", color = c.urgent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                saveError?.let {
                    Text(text = it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                if (lines.isNotEmpty()) {
                    Text(
                        text = (if (lines.size == 1) "1 batch" else "${lines.size} batches") +
                            "  ·  Purchase total ${formatKsh(totalCostCents)}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                PrimaryButton(
                    text = if (saving) "Saving receipt…" else "Save receipt and update stock",
                    onClick = { saveReceipt() },
                    enabled = !saving,
                )
            }
        }
    }

    if (showBelowCost) {
        ConfirmDialog(
            title = "Selling below cost",
            message = "The selling price is lower than the purchase cost for this unit. Add the batch anyway?",
            confirmText = "Add anyway",
            dismissText = "Go back",
            onConfirm = { showBelowCost = false; commitLine() },
            onDismiss = { showBelowCost = false },
        )
    }
    if (showDiscard) {
        ConfirmDialog(
            title = "Discard this receipt?",
            message = "What you entered has not been saved.",
            confirmText = "Discard",
            dismissText = "Keep editing",
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

// ---- pieces ----------------------------------------------------------------------------------

/** A box that looks like a text field but opens the product search when tapped. */
@Composable
private fun PickerField(
    value: String,
    label: String,
    placeholder: String,
    onClick: () -> Unit,
    error: String? = null,
    supporting: String? = null,
) {
    val support: (@Composable () -> Unit)? =
        if (error != null || supporting != null) {
            { Text(error ?: supporting.orEmpty()) }
        } else {
            null
        }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = error != null,
            supportingText = support,
            trailingIcon = { Icon(CommonIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(22.dp)) },
            shape = RoundedCornerShape(14.dp),
            colors = formFieldColors(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** The units a product can be received in, as buttons: no typing, no wrong spelling. */
@Composable
private fun UnitChips(
    units: List<ProductUnit>,
    selected: String,
    error: String?,
    onSelect: (String) -> Unit,
) {
    val c = SamilliTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Delivered in *", color = c.textSecondary, fontSize = 12.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            units.forEach { u ->
                val on = u.name.equals(selected, ignoreCase = true)
                val shape = RoundedCornerShape(50)
                Text(
                    text = if (u.baseQty > 1) "${u.name} (${u.baseQty})" else u.name,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (on) c.gold else c.card, shape)
                        .border(1.dp, if (on) c.gold else c.cardBorder, shape)
                        .clickable { onSelect(u.name) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (on) c.onReorder else c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
        if (error != null) {
            Text(text = error, color = c.urgent, fontSize = 12.sp)
        }
    }
}

/** Full-screen product search. Choosing a product returns to the receipt. */
@Composable
private fun ProductPicker(
    products: List<Product>,
    onPick: (Product) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(products, query) {
        val q = query.trim().lowercase()
        products.filter { p ->
            q.isEmpty() || listOf(p.name, p.brandName, p.genericNames, p.strengthText, p.type, p.description)
                .any { it.lowercase().contains(q) }
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier.fillMaxSize().background(c.background).imePadding()) {
        ScreenHeader(
            title = "Choose a product",
            subtitle = "${products.size} active products",
            onBack = onClose,
        )
        if (products.isEmpty()) {
            EmptyState(
                icon = MoreIcons.ProductTag,
                title = "No products to receive",
                message = "Register a product with at least one unit first, then come back.",
            )
        } else {
            Spacer(Modifier.height(14.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search by name, brand or generic name",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (shown.isEmpty()) {
                EmptyState(
                    icon = MoreIcons.ProductTag,
                    title = "No matches",
                    message = "Try a different search.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { p ->
                        SamilliCard(Modifier.fillMaxWidth().clickable { onPick(p) }) {
                            Text(p.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            val detail = p.detailText()
                            if (detail.isNotBlank()) {
                                Text(detail, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                            }
                            Text(
                                text = "Units: ${p.unitsText()}",
                                color = c.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
