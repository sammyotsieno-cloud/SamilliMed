package org.samillimed.app.data.facility

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Which facility the signed-in person works in, and which database and sign-in to use.
 * Every facility's data lives under facilities/{facilityId}/..., set once after sign-in.
 */
object FacilityScope {
    const val ROOT = "facilities"

    @Volatile
    var facilityId: String = ""

    /** The full path of one of this facility's collections, for example facilities/main/products. */
    fun path(collection: String): String =
        "$ROOT/${facilityId.ifBlank { "_unassigned" }}/$collection"

    /** The database of the connected project. Use this instead of FirebaseFirestore.getInstance(). */
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance(FacilityConnection.app())

    /** The sign-in of the connected project. Use this instead of FirebaseAuth.getInstance(). */
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance(FacilityConnection.app())
}
