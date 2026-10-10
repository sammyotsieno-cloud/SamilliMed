package core.domain.category

data class DefaultProductTaxonomyNode(
    val id: String,
    val name: String,
    val description: String? = null
)

object DefaultProductTaxonomyV1 {
    const val VERSION = 2

    val roots = listOf(
        DefaultProductTaxonomyNode("1", "Medicines & Pharmaceuticals", "Reference taxonomy of medicinal substances and pharmacological classes."),
        DefaultProductTaxonomyNode("2", "Medical Consumables", "Single-use disposables, PPE, catheters, administration sets, and clinical consumables."),
        DefaultProductTaxonomyNode("3", "Diagnostic / Laboratory Supplies", "Reagents, rapid test kits, collection tubes, microscopy supplies, and laboratory consumables."),
        DefaultProductTaxonomyNode("4", "Medical Devices & Equipment", "Reusable clinical instruments, diagnostic devices, monitoring equipment, and procedural apparatus."),
        DefaultProductTaxonomyNode("5", "Wound Care & Procedure Supplies", "Gauze, bandages, sterile dressings, surgical sutures, tapes, and procedural packs."),
        DefaultProductTaxonomyNode("6", "Infection Prevention & Control", "Hospital-grade antiseptics, high-level disinfectants, sterilisation monitors, and barrier supplies."),
        DefaultProductTaxonomyNode("7", "Maternal, Newborn & Family Planning", "Obstetric delivery kits, contraceptive commodities, neonatal care items, and reproductive health commodities."),
        DefaultProductTaxonomyNode("8", "Personal Care / Hygiene", "Patient cleansing items, skin care barriers, adult briefs, and institutional hygiene commodities."),
        DefaultProductTaxonomyNode("9", "Nutrition & Supplements", "Therapeutic nutrition, enteral feeds, dietary formulations, oral rehydration salts, and clinical macronutrients."),
        DefaultProductTaxonomyNode("10", "Non-medical / Facility Supplies", "Administrative forms, facility stationery, biohazard management bags, and operational utility supplies.")
    )
}
