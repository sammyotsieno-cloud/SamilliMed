package org.samillimed.app.ui.facility

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.ProfileState
import org.samillimed.app.ui.common.LoadingState
import org.samillimed.app.ui.theme.SamilliTheme
import org.samillimed.app.data.facility.FacilityScope

/** Shown instead of the app while the account is loading, not linked to a facility, or disabled. */
@Composable
fun AccountGate(state: ProfileState, onSignOut: () -> Unit) {
    val c = SamilliTheme.colors
    when (state) {
        is ProfileState.Ready -> Unit
        ProfileState.Loading, ProfileState.SignedOut -> LoadingState()
        else -> {
            val (title, message) = when (state) {
                ProfileState.NotLinked -> {
                    val user = FacilityScope.auth().currentUser
                    "Account not set up" to
                        "No users document matches this account. In Firestore, the users collection needs a document whose ID is exactly this UID, with facilityId = main and active = true.\n\nEmail: ${user?.email}\nUID: ${user?.uid}"
                }
                ProfileState.Disabled ->
                    "Account disabled" to
                        "This account has been disabled. Contact your facility administrator."
                is ProfileState.Failed -> "Could not load your account" to state.message
                else -> "" to ""
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(c.background)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                SelectionContainer {
                    Text(
                        text = message,
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                }
                TextButton(onClick = onSignOut) { Text("Sign out", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
