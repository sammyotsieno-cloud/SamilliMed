package core.domain.category

data class DefaultProductTaxonomyNode(
    val id: String,
    val name: String,
    val description: String? = null
)

object DefaultProductTaxonomyV1 {
    const val VERSION = 1

    val roots = listOf(
        DefaultProductTaxonomyNode("CAT_MEDICINES", "Medicines / Pharmaceuticals"),
        DefaultProductTaxonomyNode("CAT_MEDICAL_CONSUMABLES", "Medical Consumables"),
        DefaultProductTaxonomyNode("CAT_DIAGNOSTIC_LAB", "Diagnostic / Laboratory Supplies"),
        DefaultProductTaxonomyNode("CAT_MEDICAL_DEVICES", "Medical Devices & Equipment"),
        DefaultProductTaxonomyNode("CAT_WOUND_PROCEDURE", "Wound Care & Procedure Supplies"),
        DefaultProductTaxonomyNode("CAT_INFECTION_CONTROL", "Infection Prevention & Control"),
        DefaultProductTaxonomyNode("CAT_MATERNAL_NEWBORN_FP", "Maternal, Newborn & Family Planning"),
        DefaultProductTaxonomyNode("CAT_PERSONAL_HYGIENE", "Personal Care / Hygiene"),
        DefaultProductTaxonomyNode("CAT_NUTRITION_SUPPLEMENTS", "Nutrition & Supplements"),
        DefaultProductTaxonomyNode("CAT_FACILITY_SUPPLIES", "Non-medical / Facility Supplies")
    )
}
