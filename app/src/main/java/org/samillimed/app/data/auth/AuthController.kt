package org.samillimed.app.data.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/**
 * Who is signed in, and how to sign in or out.
 * Staff accounts are created by the owner in the Firebase console; the app has no sign-up.
 */
class AuthController(
    val isSignedIn: Boolean,
    val email: String?,
    private val auth: FirebaseAuth,
) {
    /** onDone receives null on success, or a message that is safe to show to the user. */
    fun signIn(email: String, password: String, onDone: (errorMessage: String?) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onDone(null) else onDone(friendlyMessage(task.exception))
            }
    }

    fun signOut() {
        auth.signOut()
    }
}

private fun friendlyMessage(e: Exception?): String = when (e) {
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "Wrong email or password."
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a moment and try again."
    else -> "Could not sign in. Please try again."
}

/** Keeps the signed-in state up to date: the screen changes the moment someone signs in or out. */
@Composable
fun rememberAuthController(): AuthController {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    return AuthController(isSignedIn = user != null, email = user?.email, auth = auth)
}
