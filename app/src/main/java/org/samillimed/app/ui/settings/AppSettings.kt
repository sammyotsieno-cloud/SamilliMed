package org.samillimed.app.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.samillimed.app.ui.theme.FontChoice
import org.samillimed.app.ui.theme.TextSize
import org.samillimed.app.ui.theme.ThemeMode

/** Saves the user's settings on the phone. Defaults: follow the phone, brand font, default size. */
class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("samillimed_settings", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> load(key: String, default: E): E =
        runCatching { enumValueOf<E>(prefs.getString(key, null) ?: default.name) }
            .getOrDefault(default)

    fun loadMode(): ThemeMode = load("theme_mode", ThemeMode.System)
    fun loadFont(): FontChoice = load("font_choice", FontChoice.Brand)
    fun loadSize(): TextSize = load("text_size", TextSize.Default)

    fun saveMode(v: ThemeMode) = prefs.edit().putString("theme_mode", v.name).apply()
    fun saveFont(v: FontChoice) = prefs.edit().putString("font_choice", v.name).apply()
    fun saveSize(v: TextSize) = prefs.edit().putString("text_size", v.name).apply()
}

/** Current settings plus functions that apply a change instantly and save it. */
class AppSettings(
    val mode: ThemeMode,
    val font: FontChoice,
    val textSize: TextSize,
    val setMode: (ThemeMode) -> Unit,
    val setFont: (FontChoice) -> Unit,
    val setTextSize: (TextSize) -> Unit,
)

@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    val store = remember { AppSettingsStore(context) }
    var mode by remember { mutableStateOf(store.loadMode()) }
    var font by remember { mutableStateOf(store.loadFont()) }
    var size by remember { mutableStateOf(store.loadSize()) }
    return AppSettings(
        mode = mode,
        font = font,
        textSize = size,
        setMode = { mode = it; store.saveMode(it) },
        setFont = { font = it; store.saveFont(it) },
        setTextSize = { size = it; store.saveSize(it) },
    )
}
