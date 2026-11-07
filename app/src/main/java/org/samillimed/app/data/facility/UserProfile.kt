package org.samillimed.app.data.facility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object UserRole {
    /** The app's owner: can see every facility. Also has full admin rights in their own facility. */
    const val OWNER = "owner"
    /** A facility's manager. */
    const val ADMIN = "admin"
    const val STAFF = "staff"
}

/** One document per account, at users/{uid}. Created by the owner in the console for now. */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val facilityId: String = "",
    val role: String = UserRole.STAFF,
    val active: Boolean = true,
)

val UserProfile.isOwner: Boolean get() = role == UserRole.OWNER
val UserProfile.isAdmin: Boolean get() = role == UserRole.OWNER || role == UserRole.ADMIN

/** What the app knows about the signed-in account. Only Ready lets the person into the app. */
sealed class ProfileState {
    object Loading : ProfileState()
    object SignedOut : ProfileState()
    object NotLinked : ProfileState()
    object Disabled : ProfileState()
    class Failed(val message: String) : ProfileState()
    class Ready(val profile: UserProfile) : ProfileState()
}

/** Live: if the owner disables the account, the app notices without a restart. One document read. */
private fun userProfileFlow(uid: String): Flow<ProfileState> = callbackFlow {
    val registration = FirebaseFirestore.getInstance()
        .collection("users")
        .document(uid)
        .addSnapshotListener { snap, error ->
            if (error != null) {
                trySend(ProfileState.Failed(error.message ?: "Could not load your account."))
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                // An empty first answer from the phone's own copy is not final: wait for the server.
                if (!snap.metadata.isFromCache) trySend(ProfileState.NotLinked)
                return@addSnapshotListener
            }
            val profile = snap.toObject(UserProfile::class.java)?.copy(uid = uid)
            when {
                profile == null || profile.facilityId.isBlank() -> trySend(ProfileState.NotLinked)
                !profile.active -> trySend(ProfileState.Disabled)
                else -> trySend(ProfileState.Ready(profile))
            }
        }
    awaitClose { registration.remove() }
}

@Composable
fun rememberProfileState(signedIn: Boolean): ProfileState {
    val uid = if (signedIn) FirebaseAuth.getInstance().currentUser?.uid else null
    val state by produceState<ProfileState>(ProfileState.Loading, uid) {
        if (uid == null) {
            value = ProfileState.SignedOut
            return@produceState
        }
        value = ProfileState.Loading
        userProfileFlow(uid).collect { value = it }
    }
    return state
}
