package org.samillimed.app.data.catalog

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private val header = listOf(
    "Type",
    "Name",
    "Brand",
    "Generic names",
    "Strengths",
    "Combination",
    "Dosage form",
    "Description",
    "Smallest unit",
    "Units",
    "Track expiry",
    "Reorder level (smallest unit)",
    "Active",
    "Created by",
    "Created on",
    "Product ID",
    "Category (for review)",
)

/** A good name for the file, e.g. samillimed-products-2026-11-01.csv */
fun productExportFileName(): String = "samillimed-products-${LocalDate.now()}.csv"

/**
 * One row per product (inactive ones included), as CSV text. Opens correctly in Excel and Google Sheets.
 * Generic names and strengths are listed in the same order, separated by " / ".
 */
fun buildProductsCsv(products: List<Product>): String {
    val out = StringBuilder()
    out.append(header.joinToString(",") { csvCell(it) }).append("\r\n")
    products.forEach { p ->
        val baseName = p.baseUnit?.name.orEmpty()
        val units = p.units.mapIndexedNotNull { i, u ->
            when {
                u.name.isBlank() -> null
                i == 0 -> "1 ${u.name}"
                else -> "1 ${u.name} = ${u.baseQty} $baseName"
            }
        }.joinToString("; ")
        val created = if (p.createdAtMillis > 0) {
            Instant.ofEpochMilli(p.createdAtMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormat)
        } else {
            ""
        }
        val row = listOf(
            p.type,
            p.name,
            p.brandName,
            p.genericNames,
            p.strengthText,
            if (p.isCombination) "Yes" else "No",
            p.dosageForm,
            p.description,
            baseName,
            units,
            if (p.trackExpiry) "Yes" else "No",
            if (p.reorderLevel > 0) p.reorderLevel.toString() else "",
            if (p.active) "Yes" else "No",
            p.createdBy,
            created,
            p.id,
            "",
        )
        out.append(row.joinToString(",") { csvCell(it) }).append("\r\n")
    }
    return out.toString()
}

/**
 * Wraps a value for CSV: quotes it when needed and doubles any quote marks. A value that starts
 * with = + - or @ gets a leading apostrophe so a spreadsheet never treats it as a formula.
 */
private fun csvCell(value: String): String {
    var v = value.replace("\r", " ").replace("\n", " ").trim()
    if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
    return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** Writes the text to the file the user chose. Returns true if it worked. */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            // A byte-order mark makes Excel read accents and symbols correctly
            stream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            stream.write(text.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)
