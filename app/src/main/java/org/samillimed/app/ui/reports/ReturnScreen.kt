package org.samillimed.app.ui.reports

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.inventory.BillStatus
import org.samillimed.app.data.inventory.DispensingLine
import org.samillimed.app.data.inventory.DispensingRecord
import org.samillimed.app.data.inventory.DispensingServiceLine
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.ReturnLineInput
import org.samillimed.app.data.inventory.ReturnRepository
import org.samillimed.app.data.inventory.ReturnRequest
import org.samillimed.app.data.reports.SalesReturn
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.inventory.InvChip
import org.samillimed.app.ui.inventory.fmtDayTime
import org.samillimed.app.ui.inventory.fmtNumber
import org.samillimed.app.ui.theme.SamilliTheme
import java.util.UUID

/** The returns already recorded on a bill, and the button to start a new one (admins only). */
@Composable
internal fun ReturnsSection(
    bill: DispensingRecord,
    returns: List<SalesReturn>,
    ready: Boolean,
    onStart: () -> Unit,
) {
    val c = SamilliTheme.colors
    if (returns.isNotEmpty()) {
        SamilliCard(Modifier.fillMaxWidth()) {
            Text("Returns", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            returns.sortedByDescending { it.createdAtMillis }.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = fmtDayTime(r.createdAtMillis) + if (r.fullVoid) " · bill voided" else "",
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatKsh(r.refundCents), color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = r.lines.joinToString { "${fmtNumber(it.quantity)} ${it.unitName} ${it.name}".replace("  ", " ").trim() },
                        color = c.textPrimary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = PaymentMethod.label(r.refundMethod) + " · " +
                            (if (r.restocked) "put back on the shelf" else "not put back") + " · " + r.reason,
                        color = c.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
    if (bill.status != BillStatus.VOIDED) {
        if (CurrentUser.isAdmin) {
            Button(
                onClick = onStart,
                enabled = ready,
                colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Return or void", fontWeight = FontWeight.Bold) }
        } else {
            Text(
                text = "Returns and voids need an administrator.",
                color = c.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Choose what comes back, how the refund is paid and why. Every refund asks for confirmation. */
@Composable
fun ReturnScreen(
    bill: DispensingRecord,
    lines: List<DispensingLine>,
    serviceLines: List<DispensingServiceLine>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val repository = remember { ReturnRepository() }

    // One ID for this return, so saving twice can never refund twice.
    val returnId = rememberSaveable { UUID.randomUUID().toString() }
    val productQty = remember { mutableStateMapOf<String, Long>() }
    val serviceQty = remember { mutableStateMapOf<String, Long>() }
    var restockChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var method by rememberSaveable { mutableStateOf(bill.paymentMethod) }
    var reason by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    val productRows = remember(lines) { lines.sortedBy { it.productNameSnapshot.lowercase() } }
    val serviceRows = remember(serviceLines) { serviceLines.sortedBy { it.serviceNameSnapshot.lowercase() } }

    // The same rounding the database step uses, so the preview matches the refund.
    fun refundFor(total: Long, original: Long, alreadyQty: Long, alreadyRefunded: Long, q: Long): Long = when {
        q <= 0L || original <= 0L -> 0L
        q == original - alreadyQty -> total - alreadyRefunded
        else -> Math.round(total.toDouble() * q / original)
    }

    val refundTotal =
        productRows.sumOf {
            refundFor(it.totalSaleValueCents, it.quantityDispensed, it.returnedQuantity, it.returnedCents, productQty[it.id] ?: 0L)
        } + serviceRows.sumOf {
            refundFor(it.totalCents, it.quantity, it.returnedQuantity, it.returnedCents, serviceQty[it.id] ?: 0L)
        }
    val chosenCount = productRows.count { (productQty[it.id] ?: 0L) > 0L } +
        serviceRows.count { (serviceQty[it.id] ?: 0L) > 0L }
    val anyMedicine = productRows.any { (productQty[it.id] ?: 0L) > 0L }
    val everything = chosenCount > 0 &&
        productRows.all { (productQty[it.id] ?: 0L) == it.quantityDispensed - it.returnedQuantity } &&
        serviceRows.all { (serviceQty[it.id] ?: 0L) == it.quantity - it.returnedQuantity }

    fun startSave() {
        error = null
        when {
            chosenCount == 0 -> error = "Choose what is being returned."
            anyMedicine && restockChoice == null -> error = "Choose whether the medicines go back on the shelf."
            reason.isBlank() -> error = "Write the reason for the return."
            else -> confirm = true
        }
    }

    fun doSave() {
        saving = true
        val chosen = productRows.filter { (productQty[it.id] ?: 0L) > 0L }
            .map { ReturnLineInput(false, it.id, productQty[it.id] ?: 0L) } +
            serviceRows.filter { (serviceQty[it.id] ?: 0L) > 0L }
                .map { ReturnLineInput(true, it.id, serviceQty[it.id] ?: 0L) }
        repository.recordReturn(
            request = ReturnRequest(
                id = returnId,
                billId = bill.id,
                lines = chosen,
                restock = restockChoice == true,
                refundMethod = method,
                reason = reason,
            ),
            onSuccess = {
                Toast.makeText(context, "Return recorded", Toast.LENGTH_LONG).show()
                onBack()
            },
            onFailure = { message ->
                saving = false
                error = message
            },
        )
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(
            title = if (everything) "Void bill" else "Return items",
            subtitle = bill.referenceNumber,
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SamilliCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "What is coming back",
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        productRows.forEach { productQty[it.id] = it.quantityDispensed - it.returnedQuantity }
                        serviceRows.forEach { serviceQty[it.id] = it.quantity - it.returnedQuantity }
                    }) { Text("Everything", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = {
                        productQty.clear()
                        serviceQty.clear()
                    }) { Text("Clear") }
                }
                productRows.forEach { l ->
                    val left = l.quantityDispensed - l.returnedQuantity
                    val q = productQty[l.id] ?: 0L
                    ReturnLineRow(
                        name = l.productNameSnapshot,
                        detail = "Sold ${fmtNumber(l.quantityDispensed)} ${l.dispensingUnit.unitName} × " +
                            formatKsh(l.actualSellingPricePerUnitCents) +
                            if (l.returnedQuantity > 0L) " · ${fmtNumber(l.returnedQuantity)} already returned" else "",
                        left = left,
                        qty = q,
                        refundCents = refundFor(l.totalSaleValueCents, l.quantityDispensed, l.returnedQuantity, l.returnedCents, q),
                        onQty = { productQty[l.id] = it },
                    )
                }
                serviceRows.forEach { s ->
                    val left = s.quantity - s.returnedQuantity
                    val q = serviceQty[s.id] ?: 0L
                    ReturnLineRow(
                        name = s.serviceNameSnapshot,
                        detail = "Service · ${fmtNumber(s.quantity)} × ${formatKsh(s.priceCents)}" +
                            if (s.returnedQuantity > 0L) " · ${fmtNumber(s.returnedQuantity)} already returned" else "",
                        left = left,
                        qty = q,
                        refundCents = refundFor(s.totalCents, s.quantity, s.returnedQuantity, s.returnedCents, q),
                        onQty = { serviceQty[s.id] = it },
                    )
                }
            }

            if (anyMedicine) {
                SamilliCard(Modifier.fillMaxWidth()) {
                    Text("The medicines", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InvChip("Put back on the shelf", restockChoice == true) { restockChoice = true }
                        InvChip("Do not put back", restockChoice == false) { restockChoice = false }
                    }
                    Text(
                        text = "Put them back only if they are sealed and safe to sell again, for example a wrong item " +
                            "handed over. Stock from an expired batch is never put back.",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SamilliCard(Modifier.fillMaxWidth()) {
                Text("Refund", color = c.textSecondary, fontSize = 12.sp)
                Text(formatKsh(refundTotal), color = c.urgent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Paid back by", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.all.forEach { code ->
                        InvChip(PaymentMethod.label(code), method == code) { method = code }
                    }
                }
            }

            FormTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Reason *",
                singleLine = false,
                minLines = 2,
            )

            error?.let {
                Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = { startSave() },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        saving -> "Saving…"
                        everything -> "Void bill"
                        else -> "Record return"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (confirm) {
        val shelf = when {
            !anyMedicine -> ""
            restockChoice == true -> " The medicines go back on the shelf."
            else -> " The medicines are not put back on the shelf."
        }
        AlertDialog(
            onDismissRequest = { if (!saving) confirm = false },
            title = { Text(if (everything) "Void this bill?" else "Record this return?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Refund ${formatKsh(refundTotal)} by ${PaymentMethod.label(method)}.$shelf This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        doSave()
                    },
                    enabled = !saving,
                ) { Text("Confirm", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReturnLineRow(
    name: String,
    detail: String,
    left: Long,
    qty: Long,
    refundCents: Long,
    onQty: (Long) -> Unit,
) {
    val c = SamilliTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(name, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = c.textSecondary, fontSize = 12.sp)
        if (left <= 0L) {
            Text("Already fully returned", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onQty((qty - 1L).coerceAtLeast(0L)) }, enabled = qty > 0L) {
                    Text("−", fontSize = 20.sp)
                }
                Text(
                    text = qty.toString(),
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(36.dp),
                )
                TextButton(onClick = { onQty((qty + 1L).coerceAtMost(left)) }, enabled = qty < left) {
                    Text("+", fontSize = 20.sp)
                }
                Text("of ${fmtNumber(left)}", color = c.textSecondary, fontSize = 12.sp)
                Text(
                    text = if (refundCents > 0L) formatKsh(refundCents) else "",
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
