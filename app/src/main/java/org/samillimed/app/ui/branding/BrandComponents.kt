package org.samillimed.app.ui.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.branding.Brand
import org.samillimed.app.ui.theme.SamilliTheme

private fun initialsOf(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "•"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** The facility's logo on a light plate, or its initials when there is no logo. */
@Composable
fun LogoPlate(
    size: Dp,
    modifier: Modifier = Modifier,
    logo: ImageBitmap? = Brand.logo,
    name: String = Brand.name,
    ink: Color = SamilliTheme.colors.headerStart,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFFFFFCF6)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.1f),
            )
        } else {
            Text(initialsOf(name), color = ink, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The facility's logo and name, shown in the top bar of the home screen. */
@Composable
fun FacilityWordmark() {
    val c = SamilliTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoPlate(44.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = Brand.name.ifBlank { "My facility" },
            color = c.onHeader,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
    }
}

/** The top of the side menu when the facility has its own branding. */
@Composable
fun BrandedDrawerHeader(userEmail: String?) {
    val c = SamilliTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoPlate(48.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = Brand.name.ifBlank { "My facility" },
                color = c.onHeader,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (userEmail != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Signed in as $userEmail",
                color = c.gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Powered by SamilliMed", color = c.onHeader.copy(alpha = 0.55f), fontSize = 10.sp)
    }
}
