package org.samillimed.app.data.catalog

import com.google.firebase.firestore.DocumentId
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * One way a product can be counted or sold, for example Tablet = 1, Strip = 10, Box = 100.
 * baseQty = how many of the smallest unit it contains. The FIRST unit in a product's list is
 * the smallest one (baseQty 1); stock is always kept in that smallest unit.
 */
data class ProductUnit(
    val name: String = "",
    val baseQty: Int = 1,
)

/**
 * A product that is bought, stocked and sold (medicines, supplies, and so on).
 * Prices are NOT here: buying and selling prices are entered per batch when stock is received.
 */
data class Product(
    @DocumentId val id: String = "",
    val name: String = "",
    val genericName: String = "",
    val brandName: String = "",
    val strength: String = "",
    val dosageForm: String = "",
    val manufacturer: String = "",
    val category: String = "",
    /** All the ways this product can be sold. First entry = smallest unit. */
    val units: List<ProductUnit> = emptyList(),
    val code: String = "",
    val barcode: String = "",
    val prescriptionStatus: String = "",
    val controlled: Boolean = false,
    /** When true, every received batch needs an expiry date. */
    val trackExpiry: Boolean = true,
    /** Raise an alert this many days before a batch expires. */
    val expiryAlertDays: Int = 90,
    /** Flag as low stock at or below this quantity, counted in the SMALLEST unit. */
    val reorderLevel: Int = 0,
    val storage: String = "",
    val location: String = "",
    val notes: String = "",
    /** Inactive products are hidden from sales but kept, so old records stay correct. */
    val active: Boolean = true,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val updatedAtMillis: Long = 0,
)

/** The smallest unit of a product (the one stock is counted in), if it has any units yet. */
val Product.baseUnit: ProductUnit? get() = units.firstOrNull()

/** A service the clinic charges for. Services have no stock, so the price lives here. */
data class ServiceItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val category: String = "",
    val department: String = "",
    /** Kept in cents (KSh 12.50 = 1250) so that sums are always exact. */
    val priceCents: Long = 0,
    val notes: String = "",
    val active: Boolean = true,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val updatedAtMillis: Long = 0,
)

// ---- user-managed lists -----------------------------------------------------------------------

/** Names of the lists that the user can add to and edit in the app. Stored as lists/{name}. */
object ListType {
    const val UNITS = "units"
    const val DOSAGE_FORMS = "dosage_forms"
    const val PRODUCT_CATEGORIES = "product_categories"
    const val SERVICE_CATEGORIES = "service_categories"
    const val DEPARTMENTS = "departments"
    const val PRESCRIPTION_STATUS = "prescription_status"
    const val STORAGE_CONDITIONS = "storage_conditions"
}

/**
 * Suggestions used ONLY to start each list the first time. After that the list belongs to the
 * user: they can add, rename and remove entries, and new values are added automatically.
 */
val defaultLists: Map<String, List<String>> = mapOf(
    ListType.UNITS to listOf(
        "Tablet", "Capsule", "Strip", "Blister pack", "Bottle", "Vial", "Ampoule", "Sachet",
        "Tube", "Jar", "Inhaler", "Pen", "Syringe", "Suppository", "Pessary", "Patch",
        "Piece", "Pair", "Roll", "Pack", "Box", "Carton", "Set", "Kit",
    ),
    ListType.DOSAGE_FORMS to listOf(
        "Tablet", "Dispersible tablet", "Effervescent tablet", "Chewable tablet", "Capsule",
        "Syrup", "Suspension", "Solution", "Injection", "Infusion", "Cream", "Ointment", "Gel",
        "Lotion", "Eye drops", "Ear drops", "Nasal drops", "Nasal spray", "Inhaler",
        "Suppository", "Pessary", "Powder", "Sachet", "Patch", "Lozenge", "Mouthwash", "Other",
    ),
    ListType.PRODUCT_CATEGORIES to listOf(
        "Medicines", "Medical supplies", "Consumables", "Surgical items", "Laboratory supplies",
        "Dental supplies", "Medical devices", "Personal care", "Baby care",
        "Nutrition and supplements", "Herbal products", "Cosmetics", "Other",
    ),
    ListType.SERVICE_CATEGORIES to listOf(
        "Consultation", "Laboratory", "Imaging", "Procedures", "Injections and dressings",
        "Antenatal care", "Immunisation", "Screening", "Counselling", "Other",
    ),
    ListType.DEPARTMENTS to listOf(
        "Clinic", "Pharmacy", "Laboratory", "Maternal and child health", "Dental", "Other",
    ),
    ListType.PRESCRIPTION_STATUS to listOf(
        "Prescription only", "Pharmacy only", "Over the counter", "Controlled drug",
    ),
    ListType.STORAGE_CONDITIONS to listOf(
        "Room temperature", "Cool place", "Refrigerated (2-8 °C)", "Frozen",
        "Protect from light", "Protect from moisture",
    ),
)

// ---- money helpers ----------------------------------------------------------------------------

/** 125000 -> "1,250"   and   125050 -> "1,250.50". Exact: no decimal rounding drift. */
fun formatAmount(cents: Long): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val value = BigDecimal.valueOf(cents, 2)
    val pattern = if (cents % 100L == 0L) "#,##0" else "#,##0.00"
    return DecimalFormat(pattern, symbols).format(value)
}

/** 125000 -> "KSh 1,250". */
fun formatKsh(cents: Long): String = "KSh " + formatAmount(cents)

/**
 * Turns what a person typed ("1250", "1,250.50") into cents, or null if it is not a valid amount
 * (negative, more than 2 decimals, not a number). Empty text counts as 0.
 */
fun parseKshToCents(text: String): Long? {
    val clean = text.trim().replace(",", "").replace(" ", "")
    if (clean.isEmpty()) return 0L
    val value = clean.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
    if (value.signum() < 0 || value.scale() > 2) return null
    return runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
}
