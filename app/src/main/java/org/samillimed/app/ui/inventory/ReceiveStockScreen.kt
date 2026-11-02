package org.samillimed.app.ui.inventory

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.Catalog
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.parseKshToCents
import org.samillimed.app.data.inventory.GoodsReceipt
import org.samillimed.app.data.inventory.GoodsReceiptLine
import org.samillimed.app.data.inventory.InventoryCalculations
import org.samillimed.app.data.inventory.InventoryRepository
import org.samillimed.app.ui.common.ComboField
import org.samillimed.app.ui.common.FormSection
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.PrimaryButton
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.theme.SamilliTheme
import org.samillimed.app.ui.home.SamilliCard
import java.util.Calendar
import java.util.Locale

private data class ReceiptDraftLine(
    val productId: String,
    val productName: String,
    val productType: String,
    val quantity: Long,
    val unitName: String,
    val baseQty: Long,
    val batchNumber: String,
    val expiryMillis: Long?,
    val purchaseCostCents: Long,
    val sellingPriceCents: Long,
)

private fun dateLabel(millis: Long?): String {
    if (millis == null) return "Choose date"
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d/%02d/%04d".format(
        Locale.getDefault(),
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.YEAR),
    )
}

private fun chooseDate(
    context: android.content.Context,
    initialMillis: Long?,
    onChosen: (Long) -> Unit,
) {
    val calendar = Calendar.getInstance().apply {
        if (initialMillis != null) timeInMillis = initialMillis
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onChosen(chosen.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

/**
 * Receives one supplier delivery containing one or more products/batches.
 * Each line keeps its own unit, batch, expiry and historical prices.
 */
@Composable
fun ReceiveStockScreen(
    catalog: Catalog,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val repository = remember { InventoryRepository() }

    var supplier by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var receivedAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    var selectedProductName by rememberSaveable { mutableStateOf("") }
    var selectedUnitName by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("1") }
    var batchNumber by rememberSaveable { mutableStateOf("") }
    var expiryMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var purchaseCostText by rememberSaveable { mutableStateOf("") }
    var sellingPriceText by rememberSaveable { mutableStateOf("") }

    val lines = remember { mutableStateListOf<ReceiptDraftLine>() }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val activeProducts = catalog.products.filter { it.active && it.units.isNotEmpty() }
    val selectedProduct = activeProducts.firstOrNull {
        it.name.equals(selectedProductName.trim(), ignoreCase = true)
    }
    val selectedUnit = selectedProduct?.units?.firstOrNull {
        it.name.equals(selectedUnitName.trim(), ignoreCase = true)
    }
    val quantity = quantityText.trim().toLongOrNull()
    val purchaseCost = parseKshToCents(purchaseCostText)
    val sellingPrice = parseKshToCents(sellingPriceText)

    val totalCostCents = lines.fold(0L) { total, line ->
        val lineCost = InventoryCalculations.calculateLineCostCents(
            line.quantity,
            line.purchaseCostCents,
        ) ?: 0L
        runCatching { Math.addExact(total, lineCost) }.getOrDefault(Long.MAX_VALUE)
    }

    fun addLine() {
        error = null
        val product = selectedProduct
        val unit = selectedUnit
        if (product == null) {
            error = "Choose a registered product."
            return
        }
        if (unit == null) {
            error = "Choose one of this product’s registered units."
            return
        }
        if (quantity == null || quantity <= 0L) {
            error = "Quantity must be a whole number greater than zero."
            return
        }
        if (purchaseCost == null || sellingPrice == null) {
            error = "Enter valid purchase and selling prices (up to 2 decimal places)."
            return
        }
        if (purchaseCost < 0L || sellingPrice < 0L) {
            error = "Prices cannot be negative."
            return
        }
        if (product.trackExpiry && expiryMillis == null) {
            error = "This product requires an expiry date."
            return
        }
        val baseQuantity = InventoryCalculations.toBaseQuantity(
            quantity,
            unit.baseQty.toLong(),
        )
        if (baseQuantity == null) {
            error = "Quantity or unit conversion is invalid."
            return
        }
        if (InventoryCalculations.calculateLineCostCents(quantity, purchaseCost) == null) {
            error = "The line purchase total is too large."
            return
        }

        lines.add(
            ReceiptDraftLine(
                productId = product.id,
                productName = product.name,
                productType = product.type,
                quantity = quantity,
                unitName = unit.name,
                baseQty = unit.baseQty.toLong(),
                batchNumber = batchNumber.trim(),
                expiryMillis = expiryMillis,
                purchaseCostCents = purchaseCost,
                sellingPriceCents = sellingPrice,
            )
        )
        selectedProductName = ""
        selectedUnitName = ""
        quantityText = "1"
        batchNumber = ""
        expiryMillis = null
        purchaseCostText = ""
        sellingPriceText = ""
    }

    fun saveReceipt() {
        error = null
        if (supplier.isBlank()) {
            error = "Enter the supplier name."
            return
        }
        if (lines.isEmpty()) {
            error = "Add at least one product batch."
            return
        }
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
                supplierName = supplier.trim(),
                referenceNumber = reference.trim(),
                receivedAtMillis = receivedAt,
            ),
            lines = receiptLines,
            onSuccess = {
                saving = false
                onBack()
            },
            onFailure = { message ->
                saving = false
                error = message
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Receive Stock",
            subtitle = "Record a supplier delivery",
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FormSection(title = "Receipt details") {
                FormTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = "Supplier name *",
                )
                FormTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = "Invoice / delivery reference",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Date received", color = c.textSecondary, fontSize = 12.sp)
                        Text(dateLabel(receivedAt), color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { chooseDate(context, receivedAt) { receivedAt = it } }) {
                        Text("Change date", color = c.accentText)
                    }
                }
            }

            FormSection(title = "Add product batch", hint = "Each line represents one batch received.") {
                ComboField(
                    value = selectedProductName,
                    onValueChange = {
                        selectedProductName = it
                        selectedUnitName = ""
                        expiryMillis = null
                    },
                    label = "Registered product *",
                    options = activeProducts.map { it.name },
                )
                if (selectedProduct != null) {
                    ComboField(
                        value = selectedUnitName,
                        onValueChange = { selectedUnitName = it },
                        label = "Receiving unit *",
                        options = selectedProduct.units.map { it.name },
                        supporting = selectedProduct.units.firstOrNull { it.name == selectedUnitName }?.let {
                            "1 ${it.name} = ${it.baseQty} ${selectedProduct.units.firstOrNull()?.name.orEmpty()}"
                        },
                    )
                }
                FormTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter(Char::isDigit) },
                    label = "Quantity received *",
                    keyboardType = KeyboardType.Number,
                )
                FormTextField(
                    value = purchaseCostText,
                    onValueChange = { purchaseCostText = it },
                    label = "Purchase cost per receiving unit (KSh) *",
                    keyboardType = KeyboardType.Decimal,
                )
                FormTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it },
                    label = "Reference selling price per unit (KSh) *",
                    keyboardType = KeyboardType.Decimal,
                    supporting = "Stored with this batch. Dispensing can use a different actual price.",
                )
                FormTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = "Batch / lot number",
                )
                if (selectedProduct?.trackExpiry == true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Expiry date *", color = c.textSecondary, fontSize = 12.sp)
                            Text(dateLabel(expiryMillis), color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row {
                            TextButton(onClick = {
                                chooseDate(context, expiryMillis) { expiryMillis = it }
                            }) {
                                Text("Choose", color = c.accentText)
                            }
                            if (expiryMillis != null) {
                                TextButton(onClick = { expiryMillis = null }) {
                                    Text("Clear", color = c.urgent)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Expiry date", color = c.textSecondary, fontSize = 12.sp)
                        TextButton(onClick = {
                            chooseDate(context, expiryMillis) { expiryMillis = it }
                        }) {
                            Text(dateLabel(expiryMillis), color = c.accentText)
                        }
                    }
                }
                PrimaryButton(text = "Add batch to receipt", onClick = ::addLine)
            }

            if (lines.isNotEmpty()) {
                FormSection(title = "Receipt items (${lines.size})") {
                    lines.forEachIndexed { index, line ->
                        SamilliCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.productName, color = c.textPrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${line.quantity} ${line.unitName} · Batch: ${line.batchNumber.ifBlank { "Not specified" }}",
                                        color = c.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "Purchase: ${formatKsh(line.purchaseCostCents)} / unit",
                                        color = c.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "Line total: ${formatKsh(InventoryCalculations.calculateLineCostCents(line.quantity, line.purchaseCostCents) ?: 0L)}",
                                        color = c.accentText,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (line.expiryMillis != null) {
                                        Text("Expires: ${dateLabel(line.expiryMillis)}", color = c.textSecondary, fontSize = 12.sp)
                                    }
                                }
                                TextButton(onClick = { lines.removeAt(index) }) {
                                    Text("Remove", color = c.urgent)
                                }
                            }
                        }
                    }
                }
                SamilliCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    Text("Receipt purchase total", color = c.textSecondary, fontSize = 13.sp)
                    Text(
                        formatKsh(totalCostCents),
                        color = c.accentText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            error?.let {
                Text(it, color = c.urgent, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
        }

        SamilliCard(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            PrimaryButton(
                text = if (saving) "Saving receipt…" else "Save receipt and update stock",
                onClick = ::saveReceipt,
                enabled = !saving && lines.isNotEmpty() && supplier.isNotBlank(),
            )
        }
    }
}
