package org.samillimed.app.ui.facility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import org.samillimed.app.data.facility.FacilityScope
import org.samillimed.app.data.facility.ProfileState
import kotlin.coroutines.resume

/**
 * Tries one read of the facility's data straight from the server, and returns the error text if
 * the database refuses or cannot answer. Returns null when all is well (or still checking).
 * It also sets the facility path as early as possible, before any screen builds its paths.
 */
@Composable
fun rememberAccessProbe(state: ProfileState): String? {
    val facilityId = (state as? ProfileState.Ready)?.profile?.facilityId
    if (facilityId != null) FacilityScope.facilityId = facilityId

    val error by produceState<String?>(null, facilityId) {
        value = null
        if (facilityId == null) return@produceState
        val path = "facilities/$facilityId/lists"
        value = suspendCancellableCoroutine<String?> { cont ->
            FacilityScope.firestore().collection(path).limit(1).get(Source.SERVER)
                .addOnSuccessListener { if (cont.isActive) cont.resume(null) }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resume("Could not read $path.\n\n${e.message ?: "Unknown error"}")
                }
        }
    }
    return error
}
