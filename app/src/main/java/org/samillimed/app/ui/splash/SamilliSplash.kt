package org.samillimed.app.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import org.samillimed.app.R
import org.samillimed.app.ui.splash.SplashSpec.BG
import org.samillimed.app.ui.splash.SplashSpec.CREAM
import org.samillimed.app.ui.splash.SplashSpec.GOLD
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wraps the app's real UI. The splash is drawn on top and finishes by punching a circular hole
 * (growing out of the camera cut-out) through which the live UI shows.
 *
 * Pass playSplash = false to skip it (e.g. warm starts).
 */
@Composable
fun SamilliSplashHost(
    modifier: Modifier = Modifier,
    playSplash: Boolean = true,
    onSplashFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var done by rememberSaveable { mutableStateOf(!playSplash) }
    Box(modifier.fillMaxSize()) {
        content()
        if (!done) {
            SamilliSplash(onFinished = { done = true; onSplashFinished() })
        }
    }
}

/** The animated splash on its own. Swallows touches until it finishes. */
@Composable
fun SamilliSplash(modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Respect "Remove animations" / animator scale = 0
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (scale > 0f) {
            progress.animateTo(
                SplashSpec.DURATION,
                tween((SplashSpec.DURATION * 1000f).roundToInt(), easing = LinearEasing),
            )
        }
        finished()
    }
    SamilliSplashCanvas(
        time = { progress.value },
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                }
            }
        },
    )
}

/** Pure function of time: draws the splash at [time] seconds. Used by the app and by the preview scrubber. */
@Composable
fun SamilliSplashCanvas(time: () -> Float, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val logo = ImageBitmap.imageResource(R.drawable.samillimed_logo)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val k = widthPx / SplashSpec.DESIGN_W
        val metrics = rememberSplashMetrics(k, logo)
        var cutout by remember { mutableStateOf<Offset?>(null) }
        LaunchedEffect(view, widthPx) { cutout = cutoutCenter(view, 64f * k) }
        Canvas(Modifier.fillMaxSize()) {
            drawSplash(time(), metrics, logo, cutout ?: Offset(size.width / 2f, 64f * k))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text metrics (measured once per scale)
// ---------------------------------------------------------------------------------------------

private class SplashMetrics(
    val k: Float,
    val logoWDesign: Float,
    val letters: List<TextLayoutResult>,
    /** advance (px) of the first i letters, i = 0..n, kerning included */
    val prefix: List<Float>,
    val tag: TextLayoutResult,
    val tagWidth: Float,
) {
    val nameWidth get() = prefix.last()
}

@Composable
private fun rememberSplashMetrics(k: Float, logo: ImageBitmap): SplashMetrics {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val family = remember {
        FontFamily(
            Font(R.font.poppins_bold, FontWeight.Bold),
            Font(R.font.poppins_medium, FontWeight.Medium),
        )
    }
    return remember(k, density.density, density.fontScale, logo) {
        buildMetrics(measurer, density, family, k, logo)
    }
}

private fun buildMetrics(
    measurer: TextMeasurer,
    density: Density,
    family: FontFamily,
    k: Float,
    logo: ImageBitmap,
): SplashMetrics {
    val nameStyle = with(density) {
        TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (SplashSpec.FONT_SIZE * k).toSp(),
            color = Color.White,
        )
    }
    val tagStyle = with(density) {
        TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (SplashSpec.TAG_FONT_SIZE * k).toSp(),
            letterSpacing = (SplashSpec.TAG_TRACKING * k).toSp(),
            color = Color.White,
        )
    }
    val name = SplashSpec.NAME
    val letters = name.map { measurer.measure(it.toString(), nameStyle) }
    val prefix = (0..name.length).map { i ->
        if (i == 0) 0f else measurer.measure(name.substring(0, i), nameStyle).getLineRight(0)
    }
    val tag = measurer.measure(SplashSpec.TAGLINE, tagStyle)
    return SplashMetrics(
        k = k,
        logoWDesign = SplashSpec.LOGO_H * logo.width / logo.height,
        letters = letters,
        prefix = prefix,
        tag = tag,
        tagWidth = tag.getLineRight(0) - SplashSpec.TAG_TRACKING * k, // drop trailing tracking
    )
}

// ---------------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawSplash(t: Float, m: SplashMetrics, logo: ImageBitmap, cutout: Offset) {
    val S = SplashSpec
    val k = m.k
    val w = size.width
    val h = size.height
    val ycen = h * (S.Y_CEN / S.DESIGN_H)
    val baseline = ycen + 0.33f * S.FONT_SIZE * k
    val lhPx = S.LOGO_H * k
    val lwPx = m.logoWDesign * k

    // final (settled) logo centre-x so that the whole lock-up is centred on screen
    val lockupW = lwPx + S.GAP * k + m.nameWidth
    val lxFinal = w / 2f - lockupW / 2f + lwPx / 2f

    // ---- circular reveal (end of the animation) ------------------------------------------
    if (t >= S.T_RV) {
        val u = (t - S.T_RV) / (S.T_RVEND - S.T_RV)
        val maxR = hypot(max(cutout.x, w - cutout.x), max(cutout.y, h - cutout.y)) + 4f * k
        val r0 = S.RING_R0 * k
        val r = r0 + (maxR - r0) * S.easeI(u)
        val overlay = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, w, h))
            addOval(Rect(center = cutout, radius = r))
        }
        drawPath(overlay, BG)
        if (u < 1f) {
            drawCircle(
                color = GOLD.copy(alpha = 0.9f * (1f - S.clamp01(u))),
                radius = r,
                center = cutout,
                style = Stroke(width = 8f * k),
            )
        }
        return
    }

    drawRect(BG)

    when {
        // ---- bounce -> name reveal -> tagline -------------------------------------------------
        t < S.T_COL -> {
            val b = S.bounce(t)
            val sq = S.squash(t)
            val sy = b.sy * (1f - 0.22f * sq)
            val sx = b.sx * (1f + 0.14f * sq)
            val r = S.easeIO((t - S.T_REV) / 0.62f)
            val lx = w / 2f + (lxFinal - w / 2f) * r

            // contact shadow
            if (t >= S.T0 && t < S.T_REV + 0.1f) {
                val sa = max(0f, 1f - b.off / S.H0 * 1.2f) * (1f - S.easeO((t - S.T_REV) / 0.1f))
                val al = sa * (0.35f + 0.65f * min(1f, t / 0.4f))
                if (al > 0f) {
                    val c = Offset(w / 2f, ycen + lhPx / 2f + 10f * k)
                    val rr = 96f * k * (1.1f - 0.5f * min(b.off / S.H0, 1f))
                    withTransform({ scale(1f, 0.26f, pivot = c) }) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f * al),
                                    Color.Black.copy(alpha = 0.30f * al),
                                    Color.Transparent,
                                ),
                                center = c,
                                radius = rr,
                            ),
                            radius = rr,
                            center = c,
                        )
                    }
                }
            }

            // letters emerge from behind the logo, staggered
            if (t >= S.T_REV) {
                drawLetters(m, lx, baseline, 1f) { i -> (t - S.T_REV - 0.10f - 0.045f * i) / 0.5f }
            }

            // logo (bottom stays planted while it squashes)
            val bottom = ycen + lhPx / 2f - b.off * k
            drawLogo(logo, lx, bottom - sy * lhPx / 2f, lwPx * sx, lhPx * sy, S.clamp01((t - S.T0) / 0.12f))

            // tagline
            if (t >= S.T_TAG) {
                val p = (t - S.T_TAG) / 0.5f
                val fo = if (t > S.T_COL - 0.25f) 1f - S.easeO((t - (S.T_COL - 0.25f)) / 0.25f) else 1f
                val tagBaseline = ycen + (S.LOGO_H / 2f + 84f) * k + 16f * k * (1f - S.easeO(p)) + 15.2f * k
                drawText(
                    textLayoutResult = m.tag,
                    color = CREAM.copy(alpha = S.easeO(p) * 0.8f * fo),
                    topLeft = Offset(w / 2f - m.tagWidth / 2f, tagBaseline - m.tag.firstBaseline),
                )
            }
        }

        // ---- lock-up squeezes into a gold dot ------------------------------------------------
        t < S.T_COLEND -> {
            val q = (t - S.T_COL) / (S.T_COLEND - S.T_COL)
            val e = S.easeI(q)
            val la = if (q > 0.55f) 1f - S.easeO((q - 0.55f) / 0.4f) else 1f
            withTransform({
                scale(max(1f - e, 0.03f), max(1f - 0.82f * e, 0.12f), pivot = Offset(w / 2f, ycen))
            }) {
                drawLetters(m, lxFinal, baseline, la) { 1f }
                drawLogo(logo, lxFinal, ycen, lwPx, lhPx, la)
            }
            val da = S.easeO((q - 0.5f) / 0.5f)
            val s = 0.4f + 0.6f * da
            drawDot(Offset(w / 2f, ycen), s, s, da, k)
        }

        // ---- dot sinks, then arcs up to the camera cut-out -----------------------------------
        else -> {
            val x: Float
            val y: Float
            val sx: Float
            val sy: Float
            if (t < S.T_RISE) {
                val u = S.easeO((t - S.T_SINK) / S.SINK_DUR)
                x = w / 2f
                y = ycen + 80f * k * u
                sx = 1f + 0.18f * u
                sy = 1f - 0.14f * u
            } else {
                val u = S.easeIO((t - S.T_RISE) / S.RISE_DUR)
                val v = sin(PI.toFloat() * u)
                x = lerp(w / 2f, cutout.x, u) + 70f * k * v * (1f - u)
                y = lerp(ycen + 80f * k, cutout.y, u)
                sx = 1f - 0.18f * v
                sy = 1f + 0.45f * v
            }
            drawDot(Offset(x, y), sx, sy, 1f, k)
        }
    }
}

private fun DrawScope.drawLetters(
    m: SplashMetrics,
    logoCx: Float,
    baseline: Float,
    alphaMul: Float,
    progress: (Int) -> Float,
) {
    val S = SplashSpec
    val k = m.k
    val lwPx = m.logoWDesign * k
    val clipLeft = logoCx + lwPx / 2f - 2f * k   // letters are hidden behind the logo
    for (i in S.NAME.indices) {
        val p = progress(i)
        if (p <= 0f) continue
        val e = S.back(p, 1.3f)
        val x = logoCx + lwPx / 2f + S.GAP * k + m.prefix[i] * e
        val color = (if (i >= S.MED_FROM) GOLD else CREAM).copy(alpha = min(1f, p * 3f) * alphaMul)
        val layout = m.letters[i]
        clipRect(left = clipLeft, top = 0f, right = size.width, bottom = size.height) {
            drawText(
                textLayoutResult = layout,
                color = color,
                topLeft = Offset(x, baseline - layout.firstBaseline),
            )
        }
    }
}

private fun DrawScope.drawLogo(logo: ImageBitmap, cx: Float, cy: Float, wPx: Float, hPx: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({
        translate(cx - wPx / 2f, cy - hPx / 2f)
        scale(wPx / logo.width, hPx / logo.height, pivot = Offset.Zero)
    }) {
        drawImage(logo, alpha = alpha, filterQuality = FilterQuality.High)
    }
}

private fun DrawScope.drawDot(center: Offset, sx: Float, sy: Float, alpha: Float, k: Float) {
    if (alpha <= 0f) return
    val r = SplashSpec.DOT_R * k
    drawOval(
        color = GOLD.copy(alpha = alpha),
        topLeft = Offset(center.x - r * sx, center.y - r * sy),
        size = Size(2f * r * sx, 2f * r * sy),
    )
}

// ---------------------------------------------------------------------------------------------
// Preview: drag the slider to scrub the animation frame by frame
// ---------------------------------------------------------------------------------------------

@Preview(widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun SamilliSplashScrubPreview() {
    var t by remember { mutableFloatStateOf(1.7f) }
    Column {
        Box(Modifier.weight(1f)) { SamilliSplashCanvas(time = { t }) }
        Slider(value = t, onValueChange = { t = it }, valueRange = 0f..SplashSpec.DURATION)
    }
}
