package org.samillimed.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme

// ---- icons used by the shared pieces --------------------------------------------------------

private fun vector(name: String, vararg paths: String): ImageVector {
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

object CommonIcons {
    val Search = vector("Search", "M21 21l-4.3-4.3", "M17 11A6 6 0 1 1 5 11A6 6 0 1 1 17 11Z")
    val Plus = vector("Plus", "M12 5v14M5 12h14")
    val Close = vector("Close", "M6 6l12 12M18 6L6 18")
    val Check = vector("Check", "M5 12.5l4.5 4.5L19 7.5")
    val ChevronDown = vector("ChevronDown", "M6 9l6 6 6-6")
}

// ---- header -----------------------------------------------------------------------------------

/** Green header with a back arrow, a title and an optional line under it. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .background(Brush.linearGradient(listOf(c.headerStart, c.headerEnd)), shape)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
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
            Icon(MoreIcons.ArrowLeft, contentDescription = "Back", modifier = Modifier.size(22.dp), tint = c.onHeader)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.onHeader,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.onHeader.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

// ---- search and filters -----------------------------------------------------------------------

/** Rounded search box with a magnifier and a clear button. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(CommonIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(CommonIcons.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.card,
            unfocusedContainerColor = c.card,
            cursorColor = c.accentText,
            focusedBorderColor = c.accentText,
            unfocusedBorderColor = c.cardBorder,
            focusedLeadingIconColor = c.textSecondary,
            unfocusedLeadingIconColor = c.textSecondary,
            focusedTrailingIconColor = c.textSecondary,
            unfocusedTrailingIconColor = c.textSecondary,
            focusedPlaceholderColor = c.textSecondary,
            unfocusedPlaceholderColor = c.textSecondary,
        ),
    )
}

/** Sideways-scrolling chips: "All" plus one per option. Tapping the selected chip clears it. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All",
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChipItem(allLabel, selected == null) { onSelect(null) }
        options.forEach { option ->
            FilterChipItem(option, option == selected) {
                onSelect(if (option == selected) null else option)
            }
        }
    }
}

@Composable
private fun FilterChipItem(text: String, on: Boolean, onClick: () -> Unit) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .background(if (on) c.gold else c.card, shape)
            .border(1.dp, if (on) c.gold else c.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (on) c.onReorder else c.textPrimary,
        fontSize = 13.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
    )
}

// ---- states -------------------------------------------------------------------------------------

/** Shown when a list has nothing to show. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .background(c.iconBg, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = c.iconTint)
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text = message, color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Centered spinner while the first data arrives. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = c.accentText)
    }
}

// ---- buttons ------------------------------------------------------------------------------------

/** Gold pill button with a plus, meant to float at the bottom right of a list. */
@Composable
fun AddFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(8.dp, shape)
            .background(c.gold, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CommonIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp), tint = c.onReorder)
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = c.onReorder, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
