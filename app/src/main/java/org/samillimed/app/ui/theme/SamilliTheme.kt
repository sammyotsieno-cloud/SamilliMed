package org.samillimed.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.samillimed.app.R

/** The three choices shown in Settings > Appearance. */
enum class ThemeMode { System, Light, Dark }

/** Only Medium and Bold are bundled, so lighter weights map onto Medium for now. */
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/** Brand colours that Material's own scheme has no slot for. Read them with SamilliTheme.colors. */
@Immutable
class SamilliColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val label: Color,
    val accentText: Color,
    val iconTint: Color,
    val iconBg: Color,
    val sectionLink: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val navStart: Color,
    val navEnd: Color,
    val onHeader: Color,
    val gold: Color,
    val deltaBg: Color,
    val deltaText: Color,
    val urgent: Color,
    val onUrgent: Color,
    val reorder: Color,
    val onReorder: Color,
    val sparkUp: Color,
    val sparkDown: Color,
)

private val Cream = Color(0xFFF3ECD8)
private val Gold = Color(0xFFE4AE42)

val LightSamilliColors = SamilliColors(
    isDark = false,
    background = Color(0xFFF2E9DA),
    card = Color(0xFFFFFCF6),
    cardBorder = Color(0x1206301F),
    textPrimary = Color(0xFF06301F),
    textSecondary = Color(0xB806301F),
    textMuted = Color(0xFF7A735F),
    label = Color(0xFF06301F),
    accentText = Color(0xFF9A6F0A),
    iconTint = Color(0xFF9A6F0A),
    iconBg = Color(0x38E4AE42),
    sectionLink = Color(0xFF9A6F0A),
    headerStart = Color(0xFF0D4F37),
    headerEnd = Color(0xFF053A26),
    navStart = Color(0xFF0D4F37),
    navEnd = Color(0xFF053A26),
    onHeader = Cream,
    gold = Gold,
    deltaBg = Color(0x1F1F7A4A),
    deltaText = Color(0xFF1F7A4A),
    urgent = Color(0xFFE5533D),
    onUrgent = Color(0xFFFFFFFF),
    reorder = Gold,
    onReorder = Color(0xFF3B2A00),
    sparkUp = Color(0xFF5DAE7C),
    sparkDown = Gold,
)

val DarkSamilliColors = SamilliColors(
    isDark = true,
    background = Color(0xFF06140E),
    card = Color(0xFF0D281C),
    cardBorder = Color(0x12F3ECD8),
    textPrimary = Cream,
    textSecondary = Color(0xB8F3ECD8),
    textMuted = Color(0xFF8FA398),
    label = Gold,
    accentText = Gold,
    iconTint = Gold,
    iconBg = Color(0x29E4AE42),
    sectionLink = Gold,
    headerStart = Color(0xFF0B3D2A),
    headerEnd = Color(0xFF062B1C),
    navStart = Color(0xFF0B2A1D),
    navEnd = Color(0xFF0B2A1D),
    onHeader = Cream,
    gold = Gold,
    deltaBg = Color(0x335DAE7C),
    deltaText = Color(0xFF8FD6A8),
    urgent = Color(0xFFE5533D),
    onUrgent = Color(0xFFFFFFFF),
    reorder = Gold,
    onReorder = Color(0xFF3B2A00),
    sparkUp = Color(0xFF5DAE7C),
    sparkDown = Gold,
)

val LocalSamilliColors = staticCompositionLocalOf { LightSamilliColors }

object SamilliTheme {
    val colors: SamilliColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSamilliColors.current
}

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private val SamilliTypography = Typography().withFont(Poppins)

/** Wrap the whole app in this. mode = System follows the phone's light/dark setting. */
@Composable
fun SamilliMedTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) DarkSamilliColors else LightSamilliColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.gold, onPrimary = c.onReorder,
            background = c.background, onBackground = c.textPrimary,
            surface = c.card, onSurface = c.textPrimary,
            error = c.urgent, onError = c.onUrgent,
        )
    } else {
        lightColorScheme(
            primary = c.headerStart, onPrimary = c.onHeader,
            background = c.background, onBackground = c.textPrimary,
            surface = c.card, onSurface = c.textPrimary,
            error = c.urgent, onError = c.onUrgent,
        )
    }
    CompositionLocalProvider(LocalSamilliColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = SamilliTypography, content = content)
    }
}
