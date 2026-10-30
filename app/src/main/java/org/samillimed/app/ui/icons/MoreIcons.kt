package org.samillimed.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

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

/** Extra icons for the side menu and the Settings screen. */
object MoreIcons {
    val ArrowLeft = outline("ArrowLeft", "M19 12H5M11 18l-6-6 6-6")

    val Settings = outline(
        "Settings",
        "M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1",
        "M17 6A2 2 0 1 1 13 6A2 2 0 1 1 17 6Z",
        "M11 12A2 2 0 1 1 7 12A2 2 0 1 1 11 12Z",
        "M19 18A2 2 0 1 1 15 18A2 2 0 1 1 19 18Z",
    )

    val Reports = outline("Reports", "M5 20v-8M11 20V5M17 20v-5M3 20h18")

    val Contrast = outline(
        "Contrast",
        "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12Z",
        "M12 3v18",
        "M15.5 8v8M19 10v4",
    )

    val Type = outline("Type", "M5 7V4.5h14V7M12 4.5v15M9 19.5h6")

    val FontSize = outline(
        "FontSize",
        "M3 19l5-13 5 13M5 14h6M15 19l3.5-9 3.5 9M16.3 16h4.4",
    )

    /** Drug Index */
    val BookOpen = outline(
        "BookOpen",
        "M12 6.5C10.5 5.3 8.2 4.8 4.5 5v13.5c3.7-.2 6 .3 7.5 1.5 1.5-1.2 3.8-1.7 7.5-1.5V5c-3.7-.2-6 .3-7.5 1.5z",
        "M12 6.5V20",
    )

    /** Registered Services */
    val Stethoscope = outline(
        "Stethoscope",
        "M11 2v2M5 2v2",
        "M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1",
        "M8 15a6 6 0 0 0 12 0v-3",
        "M22 10A2 2 0 1 1 18 10A2 2 0 1 1 22 10Z",
    )

    /** Registered Products */
    val ProductTag = outline(
        "ProductTag",
        "M12.6 2.6A2 2 0 0 0 11.2 2H4a2 2 0 0 0-2 2v7.2a2 2 0 0 0 .6 1.4l8.7 8.7a2.4 2.4 0 0 0 3.4 0l6.6-6.6a2.4 2.4 0 0 0 0-3.4z",
        "M8.5 7.5A1 1 0 1 1 6.5 7.5A1 1 0 1 1 8.5 7.5Z",
    )
}
