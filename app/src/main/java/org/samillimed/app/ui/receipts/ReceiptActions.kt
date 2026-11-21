package org.samillimed.app.ui.receipts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.catalog.formatKsh
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.inventory.BillStatus
import org.samillimed.app.data.inventory.DispensingLine
import org.samillimed.app.data.inventory.DispensingRecord
import org.samillimed.app.data.inventory.DispensingServiceLine
import org.samillimed.app.data.inventory.PaymentMethod
import org.samillimed.app.data.inventory.shortLabel
import org.samillimed.app.data.receipts.ReceiptLine
import org.samillimed.app.data.receipts.ReceiptSettings
import org.samillimed.app.data.receipts.buildReceiptText
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.theme.SamilliTheme

/** Share or copy a receipt. buildText is called when the button is tapped, so it is always current. */
@Composable
fun ShareReceiptButtons(buildText: () -> String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = SamilliTheme.colors
    remember { ReceiptSettings.load(context) }
    var showHeader by remember { mutableStateOf(false) }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildText())
        }
        context.startActivity(Intent.createChooser(send, "Share receipt"))
    }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = {
                if (ReceiptSettings.businessName.isBlank() && CurrentUser.isAdmin) showHeader = true else share()
            },
            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
        ) { Text("Share receipt", fontWeight = FontWeight.Bold) }
        TextButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Receipt", buildText()))
            Toast.makeText(context, "Receipt copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy") }
        if (CurrentUser.isAdmin) {
            TextButton(onClick = { showHeader = true }) { Text("Edit header") }
        }
    }

    if (showHeader) ReceiptHeaderDialog(onDone = { showHeader = false })
}

/** The business name, phone, address and footer printed on receipts. */
@Composable
private fun ReceiptHeaderDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = SamilliTheme.colors
    var name by remember { mutableStateOf(ReceiptSettings.businessName) }
    var phone by remember { mutableStateOf(ReceiptSettings.phone) }
    var address by remember { mutableStateOf(ReceiptSettings.address) }
    var footer by remember { mutableStateOf(ReceiptSettings.footer) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Receipt header", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Saved on this phone and printed on every receipt.", color = c.textSecondary, fontSize = 12.sp)
                FormTextField(value = name, onValueChange = { name = it }, label = "Business name", capitalization = KeyboardCapitalization.Words)
                FormTextField(value = phone, onValueChange = { phone = it }, label = "Phone", keyboardType = KeyboardType.Phone)
                FormTextField(value = address, onValueChange = { address = it }, label = "Address", capitalization = KeyboardCapitalization.Words)
                FormTextField(value = footer, onValueChange = { footer = it }, label = "Footer message")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ReceiptSettings.save(context, name, phone, address, footer)
                onDone()
            }) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** Receipt buttons for a bill that is already saved (its page in Sales). */
@Composable
fun BillReceiptButtons(
    bill: DispensingRecord,
    lines: List<DispensingLine>?,
    serviceLines: List<DispensingServiceLine>?,
) {
    if (lines == null || serviceLines == null) return
    ShareReceiptButtons(buildText = {
        buildReceiptText(
            reference = bill.referenceNumber,
            shortLabel = bill.shortLabel,
            timeMillis = bill.dispensedAtMillis,
            servedBy = bill.createdBy.substringBefore('@'),
            lines = lines.sortedBy { it.productNameSnapshot.lowercase() }.map {
                ReceiptLine(
                    it.productNameSnapshot,
                    "${it.quantityDispensed} ${it.dispensingUnit.unitName} x ${formatKsh(it.actualSellingPricePerUnitCents)}",
                    it.totalSaleValueCents,
                )
            } + serviceLines.sortedBy { it.serviceNameSnapshot.lowercase() }.map {
                ReceiptLine(it.serviceNameSnapshot, "${it.quantity} x ${formatKsh(it.priceCents)}", it.totalCents)
            },
            totalCents = bill.totalAmountCents,
            paymentLabel = PaymentMethod.label(bill.paymentMethod),
            receivedCents = bill.amountReceivedCents,
            changeCents = bill.changeGivenCents,
            voided = bill.status == BillStatus.VOIDED,
            returnedCents = bill.returnedCents,
        )
    })
}
