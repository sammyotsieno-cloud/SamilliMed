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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.ProductUnit
import org.samillimed.app.data.catalog.ServiceItem
import org.samillimed.app.data.catalog.baseUnit
import org.samillimed.app.data.catalog.formatAmount
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.inventory.DispenseProductInput
import org.samillimed.app.data.inventory.DispenseRequest
import org.samillimed.app.data.inventory.DispenseResult
import org.samillimed.app.data.inventory.DispenseServiceInput
import org.samillimed.app.data.inventory.DispensingRepository
import org.samillimed.app.data.inventory.InventoryCalculations
import org.samillimed.app.data.inventory.InventoryRepository
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.StockBatch
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ---- the bill being typed --------------------------------------------------------------------

private data class ProductBillLine(
    val productId: String,
    val productName: String,
    val productType: String,
    val quantity: Long,
    val unitName: String,
    val baseQty: Long,
    val priceCents: Long,
)

private data class ServiceBillLine(
    val serviceId: String,
    val serviceName: String,
    val category: String,
    val department: String,
    val quantity: Long,
    val priceCents: Long,
)

/** Lets the bill survive screen rotation and the app being recreated. */
private val productLinesSaver = listSaver<SnapshotStateList<ProductBillLine>, String>(
    save = { lines ->
        lines.flatMap {
            listOf(
                it.productId, it.productName, it.productType, it.quantity.toString(),
                it.unitName, it.baseQty.toString(), it.priceCents.toString(),
            )
        }
    },
    restore = { flat ->
        mutableStateListOf<ProductBillLine>().apply {
            flat.chunked(7).forEach {
                add(ProductBillLine(it[0], it[1], it[2], it[3].toLong(), it[4], it[5].toLong(), it[6].toLong()))
            }
        }
    },
)

private val serviceLinesSaver = listSaver<SnapshotStateList<ServiceBillLine>, String>(
    save = { lines ->
        lines.flatMap {
            listOf(it.serviceId, it.serviceName, it.category, it.department, it.quantity.toString(), it.priceCents.toString())
        }
    },
    restore = { flat ->
        mutableStateListOf<ServiceBillLine>().apply {
            flat.chunked(6).forEach {
                add(ServiceBillLine(it[0], it[1], it[2], it[3], it[4].toLong(), it[5].toLong()))
            }
        }
    },
)

private class ItemErrors(
    val product: String?,
    val unit: String?,
    val quantity: String?,
    val price: String?,
) {
    val any: Boolean get() = listOf(product, unit, quantity, price).any { it != null }
}

// ---- small helpers ---------------------------------------------------------------------------

private val longFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatCount(n: Long): String = String.format(Locale.US, "%,d", n)

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** amount x numerator / denominator, rounded to the nearest cent. */
private fun ratioCents(amountCents: Long, numerator: Long, denominator: Long): Long =
    BigDecimal.valueOf(amountCents)
        .multiply(BigDecimal.valueOf(numerator))
        .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
        .toLong()

private fun parseMoney(text: String): Long? = if (text.isBlank()) null else parseKshToCents(text)

private fun Product.detailText(): String =
    if (isMedicine) {
        listOf(genericNames, strengthText, dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        type
    }

private fun Product.unitsText(): String =
    units.joinToString(" · ") { if (it.baseQty <= 1) it.name else "${it.name} (${it.baseQty})" }

/** The reference price of a batch, converted to the chosen unit (same price per smallest unit). */
private fun suggestedPriceCents(batch: StockBatch?, unit: ProductUnit): Long? {
    if (batch == null || batch.sellingPricePerReceivingUnitCents <= 0L || batch.baseUnitsPerReceivingUnit <= 0L) {
        return null
    }
    val cents = ratioCents(
        batch.sellingPricePerReceivingUnitCents,
        unit.baseQty.toLong(),
        batch.baseUnitsPerReceivingUnit,
    )
    return cents.takeIf { it > 0L }
}

// ---- the screen ------------------------------------------------------------------------------

/**
 * Dispense: one bill with medicines/items and services, paid in one go.
 * Stock is taken from the batch that expires soonest. Needs an internet connection to save.
 */
@Composable
fun DispenseScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val dispensing = remember { DispensingRepository() }
    val inventory = remember { InventoryRepository() }

    // the bill. billId stays the same for this bill, so saving twice can never create two bills.
    var billId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val productLines = rememberSaveable(saver = productLinesSaver) { mutableStateListOf<ProductBillLine>() }
    val serviceLines = rememberSaveable(saver = serviceLinesSaver) { mutableStateListOf<ServiceBillLine>() }

    // the item being typed
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var unitName by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }

    // the service being typed
    var serviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var serviceQtyText by rememberSaveable { mutableStateOf("1") }
    var servicePriceText by rememberSaveable { mutableStateOf("") }

    // payment
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var receivedText by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }

    var page by rememberSaveable { mutableStateOf(0) } // 0 bill, 1 product search, 2 service search
    var showItemErrors by remember { mutableStateOf(false) }
    var showServiceErrors by remember { mutableStateOf(false) }
    var showPayErrors by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<DispenseResult?>(null) }

    val activeProducts = remember(catalog.products) {
        catalog.products.filter { it.active && it.units.isNotEmpty() }
    }
    val activeServices = remember(catalog.services) { catalog.services.filter { it.active } }
    val product = activeProducts.firstOrNull { it.id == productId }
    val unit = product?.units?.firstOrNull { it.name.equals(unitName, ignoreCase = true) }
    val service = activeServices.firstOrNull { it.id == serviceId }

    // live stock of the chosen product
    var batches by remember(product?.id) { mutableStateOf<List<StockBatch>?>(null) }
    LaunchedEffect(product?.id) {
        val id = product?.id
        if (id == null) {
            batches = null
        } else {
            inventory.batchesFlow(id).collect { batches = it }
        }
    }
    val todayStart = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val sellable = batches.orEmpty().filter {
        it.remainingQuantity > 0L && (it.expiryDateMillis ?: Long.MAX_VALUE) >= todayStart
    }
    val expiredBase = batches.orEmpty()
        .filter { it.remainingQuantity > 0L && (it.expiryDateMillis ?: Long.MAX_VALUE) < todayStart }
        .sumOf { it.remainingQuantity }
    val onHandBase = sellable.sumOf { it.remainingQuantity }
    val onBillBase = productLines.filter { it.productId == product?.id }.sumOf { it.quantity * it.baseQty }
    val availableBase = onHandBase - onBillBase
    val nextExpiry = sellable.mapNotNull { it.expiryDateMillis }.minOrNull()
    val firstBatch = sellable.minWithOrNull(
        compareBy<StockBatch> { it.expiryDateMillis ?: Long.MAX_VALUE }.thenBy { it.receivedAtMillis },
    )
    val baseName = product?.baseUnit?.name.orEmpty()

    // suggest a price from the batch the stock will come from, if the box is still empty
    LaunchedEffect(firstBatch?.id, unitName) {
        val u = unit
        if (u != null && priceText.isBlank()) {
            suggestedPriceCents(firstBatch, u)?.let { priceText = formatAmount(it) }
        }
    }

    // ---- reading what was typed ------------------------------------------------------------
    val quantity = quantityText.trim().toLongOrNull()
    val price = parseMoney(priceText)
    val baseQuantity: Long? =
        if (quantity != null && quantity > 0L && unit != null) {
            InventoryCalculations.toBaseQuantity(quantity, unit.baseQty.toLong())
        } else {
            null
        }
    val itemTotal: Long? =
        if (quantity != null && quantity > 0L && price != null && price > 0L) {
            InventoryCalculations.calculateDispensingLineTotalCents(quantity, price)
        } else {
            null
        }

    val itemErrors = ItemErrors(
        product = if (product == null) "Choose the medicine or item" else null,
        unit = if (product != null && unit == null) "Choose the unit being sold" else null,
        quantity = when {
            quantityText.isBlank() -> "Enter the quantity"
            quantity == null || quantity <= 0L -> "Enter a whole number above zero"
            unit != null && baseQuantity == null -> "That quantity is too large"
            unit != null && batches != null && availableBase <= 0L -> "No sellable stock for this product"
            unit != null && baseQuantity != null && batches != null && baseQuantity > availableBase ->
                "Only ${formatCount(availableBase / unit.baseQty)} ${unit.name} available" +
                    if (unit.baseQty > 1) " (${formatCount(availableBase)} $baseName in stock)" else ""
            else -> null
        },
        price = when {
            priceText.isBlank() -> "Enter the selling price"
            price == null -> "Use an amount like 150 or 12.50"
            price <= 0L -> "The price must be above zero"
            else -> null
        },
    )

    val serviceQty = serviceQtyText.trim().toLongOrNull()
    val servicePrice = parseMoney(servicePriceText)
    val serviceErrors = ItemErrors(
        product = if (service == null) "Choose the service" else null,
        unit = null,
        quantity = if (serviceQty == null || serviceQty <= 0L) "Enter a whole number above zero" else null,
        price = when {
            servicePriceText.isBlank() -> "Enter the price"
            servicePrice == null -> "Use an amount like 1500 or 1500.50"
            servicePrice <= 0L -> "The price must be above zero"
            else -> null
        },
    )

    // ---- the bill's totals ---------------------------------------------------------------------
    val productTotals = productLines.map {
        InventoryCalculations.calculateDispensingLineTotalCents(it.quantity, it.priceCents) ?: 0L
    }
    val serviceTotals = serviceLines.map {
        InventoryCalculations.calculateDispensingLineTotalCents(it.quantity, it.priceCents) ?: 0L
    }
    val totalCents = InventoryCalculations.calculateDispensingTotalCents(productTotals + serviceTotals) ?: 0L

    val receivedCents = if (method == PaymentMethod.CASH) parseMoney(receivedText) else totalCents
    val payError: String? = when {
        method != PaymentMethod.CASH -> null
        receivedText.isBlank() -> "Enter the amount received"
        receivedCents == null -> "Use an amount like 1000 or 1000.50"
        receivedCents < totalCents -> "The amount received is less than the total"
        else -> null
    }
    val changeCents = if (method == PaymentMethod.CASH && receivedCents != null) receivedCents - totalCents else 0L

    val itemInProgress = productId != null || quantityText.isNotBlank() || priceText.isNotBlank() || serviceId != null
    val dirty = productLines.isNotEmpty() || serviceLines.isNotEmpty() || itemInProgress || label.isNotBlank()

    // ---- actions ---------------------------------------------------------------------------
    fun addItem() {
        showItemErrors = true
        if (itemErrors.any) return
        val p = product ?: return
        val u = unit ?: return
        val q = quantity ?: return
        val pr = price ?: return
        productLines.add(ProductBillLine(p.id, p.name, p.type, q, u.name, u.baseQty.toLong(), pr))
        productId = null
        unitName = ""
        quantityText = ""
        priceText = ""
        showItemErrors = false
    }

    fun addService() {
        showServiceErrors = true
        if (serviceErrors.any) return
        val s = service ?: return
        val q = serviceQty ?: return
        val pr = servicePrice ?: return
        serviceLines.add(ServiceBillLine(s.id, s.name, s.category, s.department, q, pr))
        serviceId = null
        serviceQtyText = "1"
        servicePriceText = ""
        showServiceErrors = false
    }

    fun resetBill() {
        productLines.clear()
        serviceLines.clear()
        productId = null
        unitName = ""
        quantityText = ""
        priceText = ""
        serviceId = null
        serviceQtyText = "1"
        servicePriceText = ""
        method = PaymentMethod.CASH
        receivedText = ""
        label = ""
        showItemErrors = false
        showServiceErrors = false
        showPayErrors = false
        saveError = null
        billId = UUID.randomUUID().toString()
    }

    fun saveBill() {
        showPayErrors = true
        saveError = null
        if (itemInProgress) {
            saveError = "An item is half filled in. Tap Add to bill, or clear it."
            return
        }
        if (productLines.isEmpty() && serviceLines.isEmpty()) {
            saveError = "Add at least one item or service to the bill."
            return
        }
        if (payError != null) return
        saving = true
        dispensing.saveDispensing(
            request = DispenseRequest(
                id = billId,
                products = productLines.map {
                    DispenseProductInput(it.productId, it.productName, it.productType, it.quantity, it.unitName, it.baseQty, it.priceCents)
                },
                services = serviceLines.map {
                    DispenseServiceInput(it.serviceId, it.serviceName, it.category, it.department, it.quantity, it.priceCents)
                },
                paymentMethod = method,
                amountReceivedCents = receivedCents ?: totalCents,
                label = label.trim(),
            ),
            onSuccess = { result ->
                saving = false
                resetBill()
                saved = result
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

    BackHandler(enabled = page != 0 && saved == null) { page = 0 }
    BackHandler(enabled = page == 0 && saved == null) { leave() }

    // ---- screen ----------------------------------------------------------------------------
    val done = saved
    if (done != null) {
        SavedBill(
            result = done,
            onNewBill = { saved = null },
            onDone = onBack,
            modifier = modifier,
        )
    } else if (page == 1) {
        ProductSearch(
            products = activeProducts,
            onPick = { p ->
                productId = p.id
                unitName = if (p.units.size == 1) p.units.first().name else ""
                quantityText = ""
                priceText = ""
                showItemErrors = false
                page = 0
            },
            onClose = { page = 0 },
            modifier = modifier,
        )
    } else if (page == 2) {
        ServiceSearch(
            services = activeServices,
            onPick = { s ->
                serviceId = s.id
                servicePriceText = if (s.priceCents > 0L) formatAmount(s.priceCents) else ""
                showServiceErrors = false
                page = 0
            },
            onClose = { page = 0 },
            modifier = modifier,
        )
    } else {
        Column(modifier.fillMaxSize().background(c.background).imePadding()) {
            ScreenHeader(
                title = "Dispense",
                subtitle = "Medicines and services on one bill",
                onBack = { leave() },
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FormSection(title = "Add a medicine or item") {
                    PickerField(
                        value = product?.name.orEmpty(),
                        label = "Medicine or item *",
                        placeholder = "Tap to search registered products",
                        onClick = { page = 1 },
                        error = if (showItemErrors) itemErrors.product else null,
                        supporting = product?.let {
                            listOf(it.detailText(), "Units: ${it.unitsText()}").filter { s -> s.isNotBlank() }.joinToString("\n")
                        },
                    )

                    if (product != null) {
                        val stockLine = when {
                            batches == null -> "Checking stock…"
                            onHandBase <= 0L -> "No sellable stock"
                            else -> buildString {
                                append("In stock: ${formatCount(availableBase)} $baseName")
                                if (unit != null && unit.baseQty > 1) {
                                    append(" (about ${formatCount(availableBase / unit.baseQty)} ${unit.name})")
                                }
                                if (nextExpiry != null) append("  ·  Next expiry ${nextExpiry.toLocalDate().format(longFormat)}")
                            }
                        }
                        Text(
                            text = stockLine,
                            color = if (batches != null && onHandBase <= 0L) c.urgent else c.textSecondary,
                            fontSize = 12.sp,
                        )
                        if (expiredBase > 0L) {
                            Text(
                                text = "${formatCount(expiredBase)} $baseName in stock have expired and will not be sold.",
                                color = c.urgent,
                                fontSize = 12.sp,
                            )
                        }

                        UnitChips(
                            units = product.units,
                            selected = unitName,
                            error = if (showItemErrors) itemErrors.unit else null,
                            onSelect = { newName ->
                                val newUnit = product.units.firstOrNull { it.name == newName }
                                val oldBase = unit?.baseQty?.toLong()
                                if (newUnit != null) {
                                    val typed = parseMoney(priceText)
                                    priceText = when {
                                        // same price per smallest unit: a strip of 10 costs 10 tablets
                                        typed != null && typed > 0L && oldBase != null && oldBase > 0L ->
                                            formatAmount(ratioCents(typed, newUnit.baseQty.toLong(), oldBase))
                                        priceText.isBlank() ->
                                            suggestedPriceCents(firstBatch, newUnit)?.let { formatAmount(it) }.orEmpty()
                                        else -> priceText
                                    }
                                }
                                unitName = newName
                            },
                        )
                    }

                    FormTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                        label = if (unit != null) "Quantity (in ${unit.name}) *" else "Quantity *",
                        keyboardType = KeyboardType.Number,
                        error = if (showItemErrors) itemErrors.quantity else null,
                    )
                    FormTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                        label = if (unit != null) "Selling price per ${unit.name} (KSh) *" else "Selling price per unit (KSh) *",
                        keyboardType = KeyboardType.Decimal,
                        error = if (showItemErrors) itemErrors.price else null,
                        supporting = "The price charged on this bill. Changing the unit keeps the same price per ${baseName.ifBlank { "smallest unit" }}.",
                    )
                    if (itemTotal != null) {
                        Text(text = "Line total ${formatKsh(itemTotal)}", color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    PrimaryButton(text = "Add to bill", onClick = { addItem() })
                }

                FormSection(title = "Add a service") {
                    PickerField(
                        value = service?.name.orEmpty(),
                        label = "Service",
                        placeholder = "Tap to choose a registered service",
                        onClick = { page = 2 },
                        error = if (showServiceErrors) serviceErrors.product else null,
                        supporting = service?.let { listOf(it.category, it.department).filter { s -> s.isNotBlank() }.joinToString(" · ") },
                    )
                    if (service != null) {
                        FormTextField(
                            value = serviceQtyText,
                            onValueChange = { serviceQtyText = it.filter { ch -> ch.isDigit() } },
                            label = "Quantity *",
                            keyboardType = KeyboardType.Number,
                            error = if (showServiceErrors) serviceErrors.quantity else null,
                        )
                        FormTextField(
                            value = servicePriceText,
                            onValueChange = { servicePriceText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = "Price (KSh) *",
                            keyboardType = KeyboardType.Decimal,
                            error = if (showServiceErrors) serviceErrors.price else null,
                            supporting = "Filled from the registered standard price. You can change it for this bill.",
                        )
                        PrimaryButton(text = "Add service to bill", onClick = { addService() })
                    }
                }

                if (productLines.isNotEmpty() || serviceLines.isNotEmpty()) {
                    FormSection(title = "On this bill") {
                        productLines.forEachIndexed { index, line ->
                            BillLineCard(
                                title = line.productName,
                                detail = "${formatCount(line.quantity)} ${line.unitName} × ${formatKsh(line.priceCents)}",
                                total = productTotals.getOrElse(index) { 0L },
                                onRemove = { productLines.removeAt(index) },
                            )
                        }
                        serviceLines.forEachIndexed { index, line ->
                            BillLineCard(
                                title = line.serviceName,
                                detail = "Service · ${formatCount(line.quantity)} × ${formatKsh(line.priceCents)}",
                                total = serviceTotals.getOrElse(index) { 0L },
                                onRemove = { serviceLines.removeAt(index) },
                            )
                        }
                    }
                }

                FormSection(title = "Payment") {
                    MethodChips(selected = method, onSelect = { method = it })
                    if (method == PaymentMethod.CASH) {
                        FormTextField(
                            value = receivedText,
                            onValueChange = { receivedText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                            label = "Amount received (KSh) *",
                            keyboardType = KeyboardType.Decimal,
                            error = if (showPayErrors) payError else null,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { receivedText = formatAmount(totalCents) }) {
                                Text("Exact amount", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.weight(1f))
                            if (receivedCents != null && receivedCents >= totalCents && totalCents > 0L) {
                                Text(
                                    text = "Change ${formatKsh(changeCents)}",
                                    color = c.accentText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    FormTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = "Label (optional)",
                        supporting = "A short note such as initials. Do not enter full names.",
                    )
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Bill total", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(text = formatKsh(totalCents), color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                PrimaryButton(
                    text = if (saving) "Saving bill…" else "Save bill",
                    onClick = { saveBill() },
                    enabled = !saving,
                )
            }
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = "Discard this bill?",
            message = "What you entered has not been saved.",
            confirmText = "Discard",
            dismissText = "Keep editing",
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false },
        )
    }
}

// ---- pieces ----------------------------------------------------------------------------------

@Composable
private fun BillLineCard(title: String, detail: String, total: Long, onRemove: () -> Unit) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = c.textSecondary, fontSize = 12.sp)
                Text(formatKsh(total), color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRemove) {
                Text("Remove", color = c.urgent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A box that looks like a text field but opens a full-screen search when tapped. */
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

@Composable
private fun SelectChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .background(if (on) c.gold else c.card, shape)
            .border(1.dp, if (on) c.gold else c.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (on) c.onReorder else c.textPrimary,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
    )
}

@Composable
private fun UnitChips(units: List<ProductUnit>, selected: String, error: String?, onSelect: (String) -> Unit) {
    val c = SamilliTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Sold by *", color = c.textSecondary, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            units.forEach { u ->
                SelectChip(
                    text = if (u.baseQty > 1) "${u.name} (${u.baseQty})" else u.name,
                    on = u.name.equals(selected, ignoreCase = true),
                    onClick = { onSelect(u.name) },
                )
            }
        }
        if (error != null) Text(text = error, color = c.urgent, fontSize = 12.sp)
    }
}

@Composable
private fun MethodChips(selected: String, onSelect: (String) -> Unit) {
    val c = SamilliTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Paid by", color = c.textSecondary, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentMethod.all.forEach { code ->
                SelectChip(text = PaymentMethod.label(code), on = code == selected, onClick = { onSelect(code) })
            }
        }
    }
}

/** Full-screen search over registered products. */
@Composable
private fun ProductSearch(
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
        ScreenHeader(title = "Choose a product", subtitle = "${products.size} active products", onBack = onClose)
        if (products.isEmpty()) {
            EmptyState(
                icon = MoreIcons.ProductTag,
                title = "No products to sell",
                message = "Register a product with at least one unit, and receive stock, then come back.",
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
                EmptyState(icon = MoreIcons.ProductTag, title = "No matches", message = "Try a different search.")
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

/** Full-screen search over registered services. */
@Composable
private fun ServiceSearch(
    services: List<ServiceItem>,
    onPick: (ServiceItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(services, query) {
        val q = query.trim().lowercase()
        services.filter { s ->
            q.isEmpty() || listOf(s.name, s.category, s.department, s.notes).any { it.lowercase().contains(q) }
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier.fillMaxSize().background(c.background).imePadding()) {
        ScreenHeader(title = "Choose a service", subtitle = "${services.size} active services", onBack = onClose)
        if (services.isEmpty()) {
            EmptyState(
                icon = MoreIcons.Stethoscope,
                title = "No services registered",
                message = "Register a service from the Register tab, then come back.",
            )
        } else {
            Spacer(Modifier.height(14.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search services",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (shown.isEmpty()) {
                EmptyState(icon = MoreIcons.Stethoscope, title = "No matches", message = "Try a different search.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { s ->
                        SamilliCard(Modifier.fillMaxWidth().clickable { onPick(s) }) {
                            Text(s.name, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (s.priceCents > 0L) formatKsh(s.priceCents) else "Price entered at billing",
                                color = c.accentText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            val detail = listOf(s.category, s.department).filter { it.isNotBlank() }.joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(detail, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown after a bill is saved: the visit reference, the total and the change. */
@Composable
private fun SavedBill(
    result: DispenseResult,
    onNewBill: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + navBottom),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(c.gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CommonIcons.Check, contentDescription = null, modifier = Modifier.size(36.dp), tint = c.onReorder)
        }
        Spacer(Modifier.height(20.dp))
        Text("Bill saved", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(result.shortLabel, color = c.accentText, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(result.reference, color = c.textSecondary, fontSize = 13.sp)
        Text(
            text = "Number ${result.shiftNumber} of this shift  ·  ${result.dayNumber} of the day",
            color = c.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("Total ${formatKsh(result.totalCents)}", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (result.changeCents > 0L) {
            Text("Change ${formatKsh(result.changeCents)}", color = c.accentText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(text = "New bill", onClick = onNewBill)
        TextButton(onClick = onDone) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}
