package org.samillimed.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.theme.ThemeMode

/** Saves the chosen appearance on the phone. Default is System (follow the phone's setting). */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samillimed_settings", Context.MODE_PRIVATE)

    fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: ThemeMode.System.name) }
            .getOrDefault(ThemeMode.System)

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}

/** Current choice plus a function that applies it instantly and saves it. */
class ThemeController(val mode: ThemeMode, val setMode: (ThemeMode) -> Unit)

@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current
    val store = remember { ThemeStore(context) }
    var mode by remember { mutableStateOf(store.load()) }
    return ThemeController(mode) { newMode ->
        mode = newMode
        store.save(newMode)
    }
}

private data class Option(val mode: ThemeMode, val title: String, val subtitle: String)

private val options = listOf(
    Option(ThemeMode.System, "System default", "Matches your phone's light or dark setting"),
    Option(ThemeMode.Light, "Light", "Always use the light theme"),
    Option(ThemeMode.Dark, "Dark", "Always use the dark theme"),
)

/** Appearance picker. The theme changes the moment an option is tapped. */
@Composable
fun AppearanceDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.mode == current,
                                onClick = { onSelect(option.mode) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.mode == current, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(option.title, fontWeight = FontWeight.Medium)
                            Text(
                                option.subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
