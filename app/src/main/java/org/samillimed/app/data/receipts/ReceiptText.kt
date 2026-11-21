package org.samillimed.app.data.receipts

import android.content.Context
import org.samillimed.app.data.catalog.formatKsh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The header and footer printed on every receipt. Saved on this phone. */
object ReceiptSettings {
    private const val PREFS = "receipt_settings"

    var businessName: String = ""
        private set
    var phone: String = ""
        private set
    var address: String = ""
        private set
    var footer: String = "Thank you. Get well soon."
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        businessName = p.getString("name", "").orEmpty()
        phone = p.getString("phone", "").orEmpty()
        address = p.getString("address", "").orEmpty()
        footer = p.getString("footer", footer).orEmpty()
        loaded = true
    }

    fun save(context: Context, name: String, phone: String, address: String, footer: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", name.trim())
            .putString("phone", phone.trim())
            .putString("address", address.trim())
            .putString("footer", footer.trim())
            .apply()
        this.businessName = name.trim()
        this.phone = phone.trim()
        this.address = address.trim()
        this.footer = footer.trim()
        loaded = true
    }
}

/** One line of a receipt: the item, how many at what price, and the line total. */
class ReceiptLine(val name: String, val detail: String, val totalCents: Long)

private val receiptTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** A plain-text receipt that reads well in WhatsApp, SMS and email. */
fun buildReceiptText(
    reference: String,
    shortLabel: String,
    timeMillis: Long,
    servedBy: String,
    lines: List<ReceiptLine>,
    totalCents: Long,
    paymentLabel: String,
    receivedCents: Long,
    changeCents: Long,
    voided: Boolean = false,
    returnedCents: Long = 0L,
): String {
    val rule = "-".repeat(28)
    val out = ArrayList<String>()
    out.add(ReceiptSettings.businessName.ifBlank { "SamilliMed" }.uppercase())
    if (ReceiptSettings.address.isNotBlank()) out.add(ReceiptSettings.address)
    if (ReceiptSettings.phone.isNotBlank()) out.add("Tel: " + ReceiptSettings.phone)
    out.add(rule)
    if (voided) {
        out.add("*** VOIDED ***")
    }
    out.add("Receipt $shortLabel ($reference)")
    out.add(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).format(receiptTime))
    if (servedBy.isNotBlank()) out.add("Served by: $servedBy")
    out.add(rule)
    lines.forEach {
        out.add(it.name)
        out.add("  ${it.detail} = ${formatKsh(it.totalCents)}")
    }
    out.add(rule)
    out.add("TOTAL: ${formatKsh(totalCents)}")
    out.add("Paid by $paymentLabel")
    if (receivedCents > totalCents) {
        out.add("Received: ${formatKsh(receivedCents)}")
        out.add("Change: ${formatKsh(changeCents)}")
    }
    if (returnedCents > 0L) out.add("Returned and refunded: ${formatKsh(returnedCents)}")
    if (ReceiptSettings.footer.isNotBlank()) {
        out.add(rule)
        out.add(ReceiptSettings.footer)
    }
    return out.joinToString("\n")
}
