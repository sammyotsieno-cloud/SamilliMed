package org.samillimed.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.catalog.Product
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.catalog.genericNames
import org.samillimed.app.data.catalog.isMedicine
import org.samillimed.app.data.catalog.strengthText
import org.samillimed.app.data.inventory.AdjustmentReason
import org.samillimed.app.data.inventory.InventoryCalculations
import org.samillimed.app.data.inventory.InventoryReader
import org.samillimed.app.data.inventory.MovementsResult
import org.samillimed.app.data.inventory.StockAdjustmentRepository
import org.samillimed.app.data.inventory.StockBatch
import org.samillimed.app.data.inventory.StockMovement
import org.samillimed.app.data.inventory.StockMovementType
import org.samillimed.app.data.inventory.batchValueCents
import org.samillimed.app.data.inventory.daysUntilExpiry
import org.samillimed.app.data.inventory.formatMixedUnits
import org.samillimed.app.data.inventory.isExpired
import org.samillimed.app.data.inventory.stockStatus
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.theme.SamilliTheme
import java.time.LocalDate
import kotlin.math.abs

private class AdjustTarget(val batch: StockBatch, val preset: String?)

/** One product: its batches, what to do about them, and its stock card. */
@Composable
fun ProductStockScreen(
    product: Product,
    batches: List<StockBatch>?,
    reader: InventoryReader,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val today = remember { LocalDate.now() }
    val units = product.units
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val own = remember(batches, product.id) {
        batches.orEmpty()
            .filter { it.productId == product.id }
            .sortedWith(compareBy<StockBatch> { it.expiryDateMillis ?: Long.MAX_VALUE }.thenBy { it.receivedAtMillis })
    }
    val sellableBase = own.filter { !isExpired(it.expiryDateMillis, today) }.sumOf { it.remainingQuantity }
    val expiredBase = own.filter { isExpired(it.expiryDateMillis, today) }.sumOf { it.remainingQuantity }
    val nearest = own.filter { !isExpired(it.expiryDateMillis, today) }.mapNotNull { it.expiryDateMillis }.minOrNull()
    val stockValue = own.sumOf { batchValueCents(it) }
    val status = stockStatus(sellableBase, expiredBase > 0L, product.reorderLevel.toLong(), nearest, 90, today)

    val movements by produceState<MovementsResult?>(null, product.id) {
        reader.movementsFlow(product.id).collect { value = it }
    }
    var target by remember { mutableStateOf<AdjustTarget?>(null) }

    val detail = if (product.isMedicine) {
        listOf(product.genericNames, product.strengthText, product.dosageForm).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        product.type
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = product.name, subtitle = detail.ifBlank { null }, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                SamilliCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("On hand (sellable)", color = c.textSecondary, fontSize = 12.sp)
                            Text(
                                text = formatMixedUnits(sellableBase, units),
                                color = c.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        StockStatusPill(status)
                    }
                    if (expiredBase > 0L) {
                        Text(
                            text = "${formatMixedUnits(expiredBase, units)} expired and still on the shelf",
                            color = c.urgent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (CurrentUser.isAdmin) Text(
                        text = "Stock value at cost ${formatKsh(stockValue)}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (product.reorderLevel > 0) {
                        Text(
                            text = "Reorder level ${formatMixedUnits(product.reorderLevel.toLong(), units)}",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (nearest != null) {
                        Text(
                            text = "Next expiry " + expiryLabel(nearest, today),
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            item(key = "batches-title") {
                Text(
                    text = "Batches (${own.size})",
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            when {
                batches == null -> item(key = "batches-loading") {
                    Text("Loading…", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
                own.isEmpty() -> item(key = "batches-empty") {
                    Text(
                        "No stock on hand. Receive stock to add a batch.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                else -> items(own, key = { it.id }) { batch ->
                    BatchCard(
                        batch = batch,
                        product = product,
                        today = today,
                        onAdjust = { target = AdjustTarget(batch, null) },
                        onWriteOff = { target = AdjustTarget(batch, AdjustmentReason.EXPIRED) },
                    )
                }
            }

            item(key = "card-title") {
                Column(Modifier.padding(top = 10.dp, start = 4.dp)) {
                    Text("Stock card", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Every receipt, sale and adjustment, newest first (latest 100).",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            when (val m = movements) {
                null -> item(key = "card-loading") {
                    Text("Loading…", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
                is MovementsResult.Failed -> item(key = "card-failed") { StockCardProblem(m) }
                is MovementsResult.Loaded -> if (m.movements.isEmpty()) {
                    item(key = "card-empty") {
                        Text(
                            "No movements yet.",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                } else {
                    items(m.movements, key = { it.id }) { movement -> MovementRow(movement, product) }
                }
            }
        }
    }

    target?.let { t ->
        AdjustDialog(
            product = product,
            batch = t.batch,
            preset = t.preset,
            onDismiss = { target = null },
        )
    }
}

@Composable
private fun BatchCard(
    batch: StockBatch,
    product: Product,
    today: LocalDate,
    onAdjust: () -> Unit,
    onWriteOff: () -> Unit,
) {
    val c = SamilliTheme.colors
    val units = product.units
    val expired = isExpired(batch.expiryDateMillis, today)
    val soon = batch.expiryDateMillis?.let { !expired && daysUntilExpiry(it, today) <= 90L } == true
    SamilliCard(Modifier.fillMaxWidth()) {
        Text(
            text = "Batch ${batch.batchNumber.ifBlank { "not recorded" }}",
            color = c.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = expiryLabel(batch.expiryDateMillis, today),
            color = when {
                expired -> c.urgent
                soon -> c.accentText
                else -> c.textSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (expired || soon) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "Remaining ${formatMixedUnits(batch.remainingQuantity, units)} of ${formatMixedUnits(batch.receivedQuantity, units)} received",
            color = c.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        val price = if (batch.sellingPricePerReceivingUnitCents > 0L) {
            "  ·  suggested price ${formatKsh(batch.sellingPricePerReceivingUnitCents)}"
        } else {
            ""
        }
        Text(
            text = "Received ${fmtDay(batch.receivedAtMillis)}" +
                if (CurrentUser.isAdmin) "  ·  cost ${formatKsh(batch.purchaseCostPerReceivingUnitCents)} per ${batch.receivingUnitName}$price" else "",
            color = c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (CurrentUser.isAdmin) Text(
            text = "Value at cost ${formatKsh(batchValueCents(batch))}",
            color = c.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (CurrentUser.isAdmin) Row(Modifier.padding(top = 4.dp)) {
            if (expired) {
                TextButton(onClick = onWriteOff) {
                    Text("Write off expired stock", color = c.urgent, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onAdjust) {
                Text("Adjust", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MovementRow(m: StockMovement, product: Product) {
    val c = SamilliTheme.colors
    val units = product.units
    val title = when (m.movementType) {
        StockMovementType.RECEIPT -> "Received"
        StockMovementType.DISPENSING -> "Sold"
        StockMovementType.ADJUSTMENT -> AdjustmentReason.label(m.reason).ifBlank { "Adjustment" }
        else -> m.movementType
    }
    val increase = m.quantityChange > 0L
    val loss = m.movementType == StockMovementType.ADJUSTMENT && !increase && AdjustmentReason.isLoss(m.reason)
    SamilliCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(fmtDayTime(m.createdAtMillis), color = c.textSecondary, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (increase) "+" else "−") + formatMixedUnits(abs(m.quantityChange), units),
                    color = when {
                        increase -> c.deltaText
                        loss -> c.urgent
                        else -> c.textPrimary
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Balance ${formatMixedUnits(m.resultingProductBalance, units)}",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        val extra = buildList {
            if (m.referenceNumber.isNotBlank() && !m.referenceNumber.startsWith("ADJ-")) add(m.referenceNumber)
            if (m.note.isNotBlank()) add(m.note)
            if (m.createdBy.isNotBlank()) add("by ${m.createdBy.substringBefore('@')}")
        }
        if (extra.isNotEmpty()) {
            Text(
                text = extra.joinToString("  ·  "),
                color = c.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Shown when the stock card cannot load, with the exact steps when the one-time index is missing. */
@Composable
private fun StockCardProblem(failed: MovementsResult.Failed) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        if (failed.needsIndex) {
            Text("One-time setup needed", color = c.urgent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "The stock card needs a database index. In the Firebase console open Firestore Database, then " +
                    "Indexes, then Add index. Collection: stockMovements. Fields: productId (Ascending) and " +
                    "createdAtMillis (Descending). Query scope: Collection. Save, wait a minute or two until it " +
                    "says Enabled, then reopen this page.",
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text("The stock card could not be loaded", color = c.urgent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(failed.message, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Write off or adjust one batch by hand. A reason is always required, and every change is costed. */
@Composable
private fun AdjustDialog(
    product: Product,
    batch: StockBatch,
    preset: String?,
    onDismiss: () -> Unit,
) {
    val c = SamilliTheme.colors
    val units = product.units
    val smallest = units.firstOrNull()?.name.orEmpty()
    val repository = remember { StockAdjustmentRepository() }

    var reason by remember { mutableStateOf(preset.orEmpty()) }
    var unitName by remember { mutableStateOf(smallest) }
    var quantityText by remember {
        mutableStateOf(if (preset == AdjustmentReason.EXPIRED) batch.remainingQuantity.toString() else "")
    }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val unit = units.firstOrNull { it.name == unitName }
    val qty = quantityText.trim().toLongOrNull()
    val baseQty: Long? =
        if (qty != null && unit != null) InventoryCalculations.toBaseQuantity(qty, unit.baseQty.toLong()) else null
    val increase = reason in AdjustmentReason.increases
    val limit = if (increase) batch.receivedQuantity - batch.remainingQuantity else batch.remainingQuantity

    fun save() {
        error = null
        when {
            reason.isBlank() -> error = "Choose a reason."
            baseQty == null -> error = "Enter a quantity above zero."
            baseQty > limit -> error = if (increase) {
                "Only ${formatMixedUnits(limit, units)} can be added back to this batch."
            } else {
                "This batch only holds ${formatMixedUnits(limit, units)}."
            }
            AdjustmentReason.needsNote(reason) && note.isBlank() -> error = "Write a short note explaining what happened."
            else -> {
                saving = true
                repository.adjustBatch(
                    batchId = batch.id,
                    quantityBase = baseQty,
                    reason = reason,
                    note = note,
                    onSuccess = { onDismiss() },
                    onFailure = { message ->
                        saving = false
                        error = message
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                if (preset == AdjustmentReason.EXPIRED) "Write off expired stock" else "Adjust stock",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Batch ${batch.batchNumber.ifBlank { "not recorded" }} · holds ${formatMixedUnits(batch.remainingQuantity, units)}",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )

                Text("Reason *", color = c.textSecondary, fontSize = 12.sp)
                Column(Modifier.selectableGroup()) {
                    (AdjustmentReason.decreases + AdjustmentReason.increases).forEach { code ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(selected = reason == code, onClick = { reason = code }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = reason == code, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(AdjustmentReason.label(code), fontSize = 14.sp)
                        }
                    }
                }

                if (units.size > 1) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        units.forEach { u ->
                            InvChip(
                                text = if (u.baseQty > 1) "${u.name} (${u.baseQty})" else u.name,
                                on = u.name == unitName,
                                onClick = { unitName = u.name },
                            )
                        }
                    }
                }
                FormTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                    label = if (unit != null) "Quantity (in ${unit.name}) *" else "Quantity *",
                    keyboardType = KeyboardType.Number,
                )
                if (!increase && batch.remainingQuantity > 0L) {
                    TextButton(onClick = {
                        unitName = smallest
                        quantityText = batch.remainingQuantity.toString()
                    }) {
                        Text("All remaining (${formatMixedUnits(batch.remainingQuantity, units)})", fontWeight = FontWeight.Bold)
                    }
                }
                if (baseQty != null && reason.isNotBlank() && !increase) {
                    Text(
                        text = "Cost value ${formatKsh(batchValueCents(batch.copy(remainingQuantity = baseQty)))}" +
                            if (AdjustmentReason.isLoss(reason)) " is recorded as a loss." else " is credited back.",
                        color = c.accentText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                FormTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = if (AdjustmentReason.needsNote(reason)) "What happened? *" else "Note (optional)",
                    singleLine = false,
                    minLines = 2,
                )
                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !saving) {
                Text(if (saving) "Saving…" else "Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}
