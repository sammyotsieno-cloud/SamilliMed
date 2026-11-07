package org.samillimed.app.data.facility

/**
 * Which facility the signed-in person works in. Every facility's data lives under
 * facilities/{facilityId}/..., so facilities can never see each other's records.
 * Set once after sign-in; the data repositories build their paths with path().
 */
object FacilityScope {
    const val ROOT = "facilities"

    @Volatile
    var facilityId: String = ""

    /** The full path of one of this facility's collections, for example facilities/main/products. */
    fun path(collection: String): String =
        "$ROOT/${facilityId.ifBlank { "_unassigned" }}/$collection"
}
