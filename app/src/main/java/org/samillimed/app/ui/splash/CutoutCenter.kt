package org.samillimed.app.ui.splash

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Centre (window px) of the camera cut-out closest to the top edge.
 * Falls back to the middle of the status bar, then to [fallbackY], when the phone has no cut-out.
 * Requires an edge-to-edge window so window coordinates match canvas coordinates.
 */
internal fun cutoutCenter(view: View, fallbackY: Float): Offset {
    val insets = ViewCompat.getRootWindowInsets(view)
    val rect = insets?.displayCutout?.boundingRects?.minByOrNull { it.top }
    if (rect != null && rect.top < view.height * 0.15f) {
        return Offset(rect.exactCenterX(), rect.exactCenterY())
    }
    val statusTop = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val y = if (statusTop > 0) statusTop / 2f else fallbackY
    return Offset(view.width / 2f, y)
}
