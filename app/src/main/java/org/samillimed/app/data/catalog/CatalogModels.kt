package org.samillimed.app.data.catalog

import com.google.firebase.firestore.DocumentId
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * One way a product can be counted, bought or sold, for example Tablet = 1, Strip = 10, Box = 100.
 * baseQty = how many of the smallest unit it contains. The FIRST unit in a product's list is
 * the smallest one (baseQty 1); stock is always kept in that smallest unit. Any unit in the list
 * can be used both when buying stock and when selling or dispensing.
 */
data class ProductUnit(
    val name: String = "",
    val baseQty: Int = 1,
)

/**
 * One active ingredient of a medicine. A combination medicine has several of these.
 * genericName is the link to the future Drug Index.
 */
data class Ingredient(
    val genericName: String = "",
    /** Strength of this ingredient as printed on the pack, e.g. 500 mg or 125 mg/5 ml. */
    val strength: String = "",
)

/** Every drug is registered under this one type: no further classification for now. */
const val MEDICINE_TYPE = "Medicine"

/**
 * Anything that is bought, stocked and sold. Prices are NOT here: buying and selling prices
 * are entered per batch when stock is received.
 */
data class Product(
    @DocumentId val id: String = "",
    /** Medicine, or the kind of item it is: Gauze, Cotton wool, Surgical blade... */
    val type: String = MEDICINE_TYPE,
    val name: String = "",
    // ---- medicines only ----
    /** One entry for a single-ingredient medicine, several for a combination. */
    val ingredients: List<Ingredient> = emptyList(),
    val brandName: String = "",
    val dosageForm: String = "",
    // ---- everything ----
    val description: String = "",
    /** All the ways this product can be bought or sold. First entry = smallest unit. */
    val units: List<ProductUnit> = emptyList(),
    /** When true, every received batch needs an expiry date. */
    val trackExpiry: Boolean = true,
    /** Flag as low stock at or below this quantity, counted in the SMALLEST unit. */
    val reorderLevel: Int = 0,
    /** Inactive products are hidden from sales but kept, so old records stay correct. */
    val active: Boolean = true,
    val createdAtMillis: Long = 0,
    val createdBy: String = "",
    val updatedAtMillis: Long = 0,
)

/** The smallest unit of a product (the one stock is counted in), if it has any units yet. */
val Product.baseUnit: ProductUnit? get() = units.firstOrNull()

val Product.isMedicine: Boolean get() = type.equals(MEDICINE_TYPE, ignoreCase = true)

/** All generic names, e.g. "Amoxicillin / Clavulanic acid". */
val Product.genericNames: String
    get() = ingredients.map { it.genericName.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")

/** All strengths in the same order, e.g. "500 mg / 125 mg". */
val Product.strengthText: String
    get() = ingredients.map { it.strength.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")

val Product.isCombination: Boolean
    get() = ingredients.count { it.genericName.isNotBlank() } > 1

/**
 * A suggested product name built from the brand (or, with no brand, the generic names), the
 * strengths and the dosage form: "Copred 5 mg tablet", "Augmentin 500 mg / 125 mg tablet".
 */
fun suggestedMedicineName(brandName: String, ingredients: List<Ingredient>, dosageForm: String): String {
    val generics = ingredients.map { it.genericName.trim() }.filter { it.isNotEmpty() }
    val strengths = ingredients.map { it.strength.trim() }.filter { it.isNotEmpty() }
    val first = brandName.trim().ifEmpty { generics.joinToString(" / ") }
    return listOf(first, strengths.joinToString(" / "), dosageForm.trim().lowercase())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

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
    const val PRODUCT_TYPES = "product_types"
    const val UNITS = "units"
    const val DOSAGE_FORMS = "dosage_forms"
    const val SERVICE_CATEGORIES = "service_categories"
    const val DEPARTMENTS = "departments"
}

/**
 * Suggestions used ONLY to start each list the first time. After that the list belongs to the
 * user: they can add, rename and remove entries, and new values are added automatically.
 */
val defaultLists: Map<String, List<String>> = mapOf(
    ListType.PRODUCT_TYPES to listOf(
        MEDICINE_TYPE,
        "Gauze", "Cotton wool", "Bandage", "Plaster", "Dressing", "Surgical blade", "Suture",
        "Syringe", "Needle", "Cannula", "IV giving set", "Catheter", "Gloves", "Face mask",
        "Gown", "Test kit", "Test strip", "Lancet", "Specimen container", "Thermometer",
        "Blood pressure monitor", "Glucose meter", "Nebuliser", "Crutches", "Brace", "Condom",
        "Sanitary pad", "Diaper", "Soap", "Hand sanitiser", "Disinfectant", "Toothpaste",
        "Skin care product", "Baby product", "Vitamin supplement", "Herbal product", "Infant formula",
        "Dispensing envelope", "Other",
    ),
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
    ListType.SERVICE_CATEGORIES to listOf(
        "Consultation", "Laboratory", "Imaging", "Procedures", "Injections and dressings",
        "Antenatal care", "Immunisation", "Screening", "Counselling", "Other",
    ),
    ListType.DEPARTMENTS to listOf(
        "Clinic", "Pharmacy", "Laboratory", "Maternal and child health", "Dental", "Other",
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
