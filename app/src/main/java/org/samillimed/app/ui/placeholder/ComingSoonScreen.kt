package org.samillimed.app.ui.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.theme.SamilliTheme

private val ArrowLeft: ImageVector = ImageVector.Builder(
    name = "ArrowLeft",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString("M19 12H5M11 18l-6-6 6-6").toNodes(),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.8f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()

/** Temporary screen for features that are not built yet. Has the app's own back arrow. */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Column(modifier.fillMaxSize().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape, clip = false)
                .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)), shape)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x17F3ECD8))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ArrowLeft, contentDescription = "Back", modifier = Modifier.size(22.dp), tint = c.onHeader)
            }
            Spacer(Modifier.width(12.dp))
            Text(text = title, color = c.onHeader, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(c.iconBg, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = c.iconTint)
            }
            Spacer(Modifier.height(20.dp))
            Text(text = "Coming soon", color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text = "This screen is being built.", color = c.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
