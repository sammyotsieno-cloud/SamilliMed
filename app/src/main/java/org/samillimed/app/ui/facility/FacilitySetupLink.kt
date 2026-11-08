package org.samillimed.app.ui.facility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.FacilityConnection
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.theme.SamilliTheme

/** Shows which project the app uses and lets the owner connect a facility's project. */
@Composable
fun FacilitySetupLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = SamilliTheme.colors
    var open by remember { mutableStateOf(false) }
    val saved = remember(open) { FacilityConnection.saved(context) }

    Column(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (saved == null) "Default project" else "Facility project: ${saved.projectId}",
            color = c.textMuted,
            fontSize = 11.sp,
        )
        TextButton(onClick = { open = true }) {
            Text("Set up a facility", fontWeight = FontWeight.Bold)
        }
    }

    if (open) SetupDialog(onDismiss = { open = false })
}

@Composable
private fun SetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = SamilliTheme.colors
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    val current = remember { FacilityConnection.saved(context) }
    val doneMessage = done

    AlertDialog(
        onDismissRequest = { if (doneMessage == null) onDismiss() },
        title = { Text("Set up a facility", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (doneMessage != null) {
                    Text(doneMessage, color = c.textPrimary, fontSize = 14.sp)
                } else {
                    Text(
                        "Paste the setup code the owner gave you. The app will then use that facility's own database.",
                        color = c.textSecondary,
                        fontSize = 13.sp,
                    )
                    FormTextField(
                        value = code,
                        onValueChange = {
                            code = it
                            error = null
                        },
                        label = "Setup code",
                        singleLine = false,
                        minLines = 2,
                    )
                    if (current != null) {
                        Text("Connected now to ${current.projectId}.", color = c.textSecondary, fontSize = 12.sp)
                    }
                    error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
        },
        confirmButton = {
            if (doneMessage == null) {
                TextButton(onClick = {
                    val settings = FacilityConnection.parse(code)
                    if (settings == null) {
                        error = "That is not a valid setup code. It should look like projectid|1:123:android:abc|AIza…"
                    } else {
                        FacilityConnection.save(context, settings)
                        done = "Saved. The app must close to connect to ${settings.projectId}. Open it again afterwards."
                    }
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                    Text("Close app", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (doneMessage == null) {
                Row {
                    if (current != null) {
                        TextButton(onClick = {
                            FacilityConnection.clear(context)
                            done = "Reset. The app must close to use the default project again. Open it again afterwards."
                        }) { Text("Use default") }
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        },
    )
}
