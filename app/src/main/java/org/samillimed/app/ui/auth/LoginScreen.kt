package org.samillimed.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.R
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.theme.SamilliTheme

/**
 * Sign-in screen. onSignIn is called with the typed email and password; it must call onResult
 * with null on success, or a message to show when sign-in fails.
 */
@Composable
fun LoginScreen(
    onSignIn: (email: String, password: String, onResult: (errorMessage: String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !loading

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        loading = true
        error = null
        onSignIn(email, password) { message ->
            loading = false
            error = message
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedBorderColor = c.accentText,
        unfocusedBorderColor = c.textSecondary.copy(alpha = 0.35f),
        focusedLabelColor = c.accentText,
        unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accentText,
        errorBorderColor = c.urgent,
    )

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.headerStart, c.headerEnd)))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.samillimed_logo),
                contentDescription = "SamilliMed",
                modifier = Modifier
                    .height(64.dp)
                    .aspectRatio(600f / 850f),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.onHeader)) { append("amilli") }
                    withStyle(SpanStyle(color = c.gold)) { append("med") }
                },
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pharmacy & Clinic Management",
            color = c.onHeader.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(40.dp))

        SamilliCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            Text(text = "Sign in", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Use the account your administrator created for you.",
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                color = c.textSecondary,
                fontSize = 13.sp,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                isError = error != null,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = c.accentText, fontWeight = FontWeight.Bold)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.padding(top = 10.dp),
                    color = c.urgent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.gold,
                    contentColor = c.onReorder,
                    disabledContainerColor = c.gold.copy(alpha = 0.4f),
                    disabledContentColor = c.onReorder.copy(alpha = 0.6f),
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = c.onReorder,
                    )
                } else {
                    Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "No account? Ask your administrator.",
            color = c.onHeader.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}
