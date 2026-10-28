package org.samillimed.app.ui.splash

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every number here is taken 1:1 from the approved preview video.
 *
 * Geometry is authored in a 1080 px wide "design space" and multiplied by
 * k = actualWidthPx / 1080 at draw time, so the splash looks identical on any screen.
 * Times are in seconds.
 */
internal object SplashSpec {
    // ---- design space ---------------------------------------------------
    const val DESIGN_W = 1080f
    const val DESIGN_H = 1920f
    const val Y_CEN = 900f            // vertical centre of the lock-up
    const val FONT_SIZE = 128f        // "amillimed"
    const val LOGO_H = 214f           // logo is slightly larger than the letters
    const val GAP = 16f               // logo -> first letter
    const val TAG_FONT_SIZE = 38f
    const val TAG_TRACKING = 5f
    const val DOT_R = 17f
    const val RING_R0 = 23.4f         // start radius of the circular reveal
    const val NAME = "amillimed"
    const val TAGLINE = "Pharmacy & Clinic Management"
    const val MED_FROM = 6            // letters from this index ("med") are gold

    // ---- palette --------------------------------------------------------
    val BG = Color(0xFF053A26)
    val CREAM = Color(0xFFF3ECD8)
    val GOLD = Color(0xFFE4AE42)

    // ---- timeline -------------------------------------------------------
    const val T0 = 0.12f              // logo starts falling
    const val D0 = 0.33f              // fall duration
    const val H0 = 440f               // drop height
    val BOUNCES: List<Pair<Float, Float>> = listOf(0.30f to 100f, 0.18f to 30f, 0.10f to 8f)
    val SQUASH_AMPS = listOf(1.0f, 0.5f, 0.25f, 0.12f)

    val LAND: List<Float> = run {
        var t = T0 + D0
        val out = mutableListOf(t)
        for ((d, _) in BOUNCES) { t += d; out += t }
        out
    }

    val T_REV = LAND.last()           // name reveal starts (~1.03 s)
    val T_TAG = T_REV + 0.62f         // tagline in   (~1.65 s)
    val T_COL = T_TAG + 0.35f + 0.55f // collapse     (~2.55 s)
    val T_COLEND = T_COL + 0.42f
    const val SINK_DUR = 0.17f
    const val RISE_DUR = 0.50f
    val T_SINK = T_COLEND
    val T_RISE = T_SINK + SINK_DUR    // dot flies to the cut-out (~3.14 s)
    val T_ARR = T_RISE + RISE_DUR
    val T_RV = T_ARR + 0.02f          // circular reveal (~3.66 s)
    val T_RVEND = T_RV + 0.58f
    /** The video holds on the dashboard for 0.5 s; in the app the real UI is already live. */
    val DURATION = T_RVEND + 0.05f

    // ---- easing ---------------------------------------------------------
    fun clamp01(x: Float) = x.coerceIn(0f, 1f)
    fun easeIO(x: Float): Float {
        val t = clamp01(x)
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    }
    fun easeO(x: Float) = 1f - (1f - clamp01(x)).pow(3)
    fun easeI(x: Float) = clamp01(x).pow(3)
    fun back(x: Float, s: Float = 1.4f): Float {
        val t = clamp01(x) - 1f
        return t * t * ((s + 1f) * t + s) + 1f
    }

    // ---- bounce ---------------------------------------------------------
    data class Bounce(val off: Float, val sy: Float, val sx: Float)

    /** Height above the ground (design px) plus the in-flight stretch factors. */
    fun bounce(t: Float): Bounce {
        if (t < T0) return Bounce(H0, 1f, 1f)
        if (t < LAND[0]) {
            val u = (t - T0) / D0
            return Bounce(H0 * (1f - u * u), 1f - 0.05f * u * u, 1f + 0.10f * u * u)
        }
        var tt = LAND[0]
        for ((d, peak) in BOUNCES) {
            if (t < tt + d) {
                val u = (t - tt) / d
                val v = 2f * u - 1f
                return Bounce(peak * (1f - v * v), 1f, 1f)
            }
            tt += d
        }
        return Bounce(0f, 1f, 1f)
    }

    /** 0..1 impact squash around each landing. */
    fun squash(t: Float): Float {
        var s = 0f
        for (i in LAND.indices) {
            val u = (t - LAND[i]) / 0.09f
            if (u in 0f..1f) s = max(s, SQUASH_AMPS[i] * sin(PI.toFloat() * u))
        }
        return s
    }
}
