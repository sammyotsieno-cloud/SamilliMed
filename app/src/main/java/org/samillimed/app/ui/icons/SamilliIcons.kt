package org.samillimed.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Builds a 24x24 outline icon from SVG path strings. The colour is applied later via Icon(tint = ...). */
private fun outline(name: String, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    paths.forEach { d ->
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** The SamilliMed icon set (same drawings as the design mockups). */
object SamilliIcons {
    val CalendarClock = outline(
        "CalendarClock",
        "M5.2 4.5H14.8A2.2 2.2 0 0 1 17 6.7V17.3A2.2 2.2 0 0 1 14.8 19.5H5.2A2.2 2.2 0 0 1 3 17.3V6.7A2.2 2.2 0 0 1 5.2 4.5Z",
        "M7 2.5v4M13 2.5v4M3 9.5h14",
        "M21.8 17.5A4.3 4.3 0 1 1 13.2 17.5A4.3 4.3 0 1 1 21.8 17.5Z",
        "M17.5 15.4v2.2l1.4 .9",
    )

    val Box = outline(
        "Box",
        "M12 2.8l8 4.4v9.6l-8 4.4-8-4.4V7.2l8-4.4z",
        "M4.2 7.3L12 11.7l7.8-4.4M12 11.7v9.4",
    )

    val Coins = outline(
        "Coins",
        "M15 6.5A6 2.8 0 1 1 3 6.5A6 2.8 0 1 1 15 6.5Z",
        "M3 6.5v4c0 1.5 2.7 2.8 6 2.8s6-1.3 6-2.8v-4",
        "M3 10.5v4c0 1.5 2.7 2.8 6 2.8",
        "M15 11.2c3 .1 6 1 6 2.4v4c0 1.5-2.7 2.8-6 2.8s-6-1.3-6-2.8v-1",
        "M21 13.6A6 2.4 0 1 1 9 13.6A6 2.4 0 1 1 21 13.6Z",
    )

    val Wallet = outline(
        "Wallet",
        "M4 7.5A2.5 2.5 0 0 1 6.5 5H18v3",
        "M5.5 7.5H18.5A2.5 2.5 0 0 1 21 10V17.5A2.5 2.5 0 0 1 18.5 20H5.5A2.5 2.5 0 0 1 3 17.5V10A2.5 2.5 0 0 1 5.5 7.5Z",
        "M17.8 13.8A1.2 1.2 0 1 1 15.4 13.8A1.2 1.2 0 1 1 17.8 13.8Z",
    )

    val Bell = outline(
        "Bell",
        "M6 9.5a6 6 0 0 1 12 0c0 5 2 6.2 2 7.2H4c0-1 2-2.2 2-7.2z",
        "M10 19.5a2 2 0 0 0 4 0",
    )

    val Menu = outline("Menu", "M4 7h16M4 12h16M4 17h16")

    val Home = outline(
        "Home",
        "M3.5 11.2L12 3.5l8.5 7.7V19.5a1 1 0 0 1-1 1H15v-6H9v6H4.5a1 1 0 0 1-1-1z",
    )

    val Pill = outline(
        "Pill",
        "M10.66 18.72L18.72 10.66A3.8 3.8 0 0 0 13.34 5.28L5.28 13.34A3.8 3.8 0 0 0 10.66 18.72Z",
        "M9.3 9.3l5.4 5.4",
    )

    val BoxPlus = outline(
        "BoxPlus",
        "M11 3l7.5 4.2v8.4M11 3L3.5 7.2v8.6L11 20M3.5 7.2L11 11.4l7.5-4.2M11 11.4V20",
        "M18 17v5M15.5 19.5h5",
    )

    val ClipboardPlus = outline(
        "ClipboardPlus",
        "M7.2 4.5H16.8A2.2 2.2 0 0 1 19 6.7V19.3A2.2 2.2 0 0 1 16.8 21.5H7.2A2.2 2.2 0 0 1 5 19.3V6.7A2.2 2.2 0 0 1 7.2 4.5Z",
        "M9 4.5V3.2h6v1.3",
        "M12 10.5v6M9 13.5h6",
    )

    val ArrowUp = outline("ArrowUp", "M12 19V5M6 11l6-6 6 6")
    val ArrowDown = outline("ArrowDown", "M12 5v14M6 13l6 6 6-6")
}
