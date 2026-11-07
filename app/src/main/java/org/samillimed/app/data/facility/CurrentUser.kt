package org.samillimed.app.data.facility

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Who is signed in, readable from any screen. Screens update by themselves if the role changes. */
object CurrentUser {
    var role by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set

    val isAdmin: Boolean get() = role == UserRole.OWNER || role == UserRole.ADMIN
    val isOwner: Boolean get() = role == UserRole.OWNER

    fun update(profile: UserProfile) {
        role = profile.role
        name = profile.name
    }

    fun roleLabel(): String = when (role) {
        UserRole.OWNER -> "Owner"
        UserRole.ADMIN -> "Admin"
        else -> "Staff"
    }
}
