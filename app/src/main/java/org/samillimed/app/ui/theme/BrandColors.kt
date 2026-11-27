package org.samillimed.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

class BrandPreset(val name: String, val primary: String, val accent: String)

/** Ready-made colour sets. Every one keeps text easy to read. */
val BrandPresets = listOf(
    BrandPreset("Forest", "#0D4F37", "#E4AE42"),
    BrandPreset("Ocean", "#0B4F6C", "#F2A541"),
    BrandPreset("Royal", "#2E3A8C", "#F5B83D"),
    BrandPreset("Maroon", "#7B1E3A", "#E7B65C"),
    BrandPreset("Teal", "#0F766E", "#F59E0B"),
    BrandPreset("Plum", "#5B2A86", "#F4C95D"),
    BrandPreset("Slate", "#334155", "#38BDF8"),
    BrandPreset("Charcoal", "#1F2937", "#34D399"),
)

/** Reads #0D4F37 or 0D4F37. Returns null when it is not a real colour code. */
fun parseHexColor(text: String): Color? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || t.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    return Color((0xFF000000L or t.toLong(16)).toInt())
}

fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())

/** A dark shade of the facility's colour, for dark mode surfaces. value is how light it is (0 is black). */
private fun tone(primary: Color, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.6f)
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun contrast(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

private fun darkenUntil(color: Color, against: Color, min: Float): Color {
    var c = color
    var i = 0
    while (contrast(c, against) < min && i < 20) {
        c = lerp(c, Color.Black, 0.12f)
        i++
    }
    return c
}

private fun lightenUntil(color: Color, against: Color, min: Float): Color {
    var c = color
    var i = 0
    while (contrast(c, against) < min && i < 20) {
        c = lerp(c, Color.White, 0.12f)
        i++
    }
    return c
}

/**
 * The facility's two colours applied to the app: the main colour for the top bar, menus and text tint,
 * the accent for buttons and highlights. Colours are adjusted so text always stays readable.
 * Warning, success and error colours never change, because they carry meaning.
 */
fun SamilliColors.withBrand(primary: Color, accent: Color): SamilliColors {
    val deep = darkenUntil(primary, onHeader, 6f)
    val deeper = lerp(deep, Color.Black, 0.28f)
    val ink = lerp(primary, Color.Black, 0.8f)
    val onAccent = if (accent.luminance() > 0.35f) Color(0xFF1F1A0A) else Color.White
    return if (!isDark) {
        val newCard = lerp(Color.White, primary, 0.02f)
        val accentText = darkenUntil(accent, newCard, 4.5f)
        SamilliColors(
            isDark = false,
            background = lerp(Color(0xFFF5F3EE), primary, 0.05f),
            card = newCard,
            cardBorder = ink.copy(alpha = 0.07f),
            textPrimary = ink,
            textSecondary = ink.copy(alpha = 0.72f),
            textMuted = lerp(ink, Color.White, 0.5f),
            label = ink,
            accentText = accentText,
            iconTint = accentText,
            iconBg = accent.copy(alpha = 0.22f),
            sectionLink = accentText,
            headerStart = deep,
            headerEnd = deeper,
            navStart = deep,
            navEnd = deeper,
            onHeader = onHeader,
            gold = accent,
            deltaBg = deltaBg,
            deltaText = deltaText,
            urgent = urgent,
            onUrgent = onUrgent,
            reorder = accent,
            onReorder = onAccent,
            sparkUp = sparkUp,
            sparkDown = accent,
        )
    } else {
        val newBackground = tone(primary, 0.14f)
        val newCard = tone(primary, 0.22f)
        val accentText = lightenUntil(accent, newBackground, 4.5f)
        SamilliColors(
            isDark = true,
            background = newBackground,
            card = newCard,
            cardBorder = onHeader.copy(alpha = 0.18f),
            textPrimary = onHeader,
            textSecondary = onHeader.copy(alpha = 0.72f),
            textMuted = lerp(onHeader, newBackground, 0.45f),
            label = accentText,
            accentText = accentText,
            iconTint = accentText,
            iconBg = accent.copy(alpha = 0.16f),
            sectionLink = accentText,
            headerStart = tone(primary, 0.36f),
            headerEnd = tone(primary, 0.26f),
            navStart = tone(primary, 0.34f),
            navEnd = tone(primary, 0.26f),
            onHeader = onHeader,
            gold = accent,
            deltaBg = deltaBg,
            deltaText = deltaText,
            urgent = urgent,
            onUrgent = onUrgent,
            reorder = accent,
            onReorder = onAccent,
            sparkUp = sparkUp,
            sparkDown = accent,
        )
    }
}
