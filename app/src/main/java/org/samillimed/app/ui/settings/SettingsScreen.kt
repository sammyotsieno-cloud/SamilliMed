package org.samillimed.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.home.IconChip
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.FontChoice
import org.samillimed.app.ui.theme.SamilliTheme
import org.samillimed.app.ui.theme.TextSize
import org.samillimed.app.ui.theme.ThemeMode
import org.samillimed.app.ui.theme.family
import kotlin.math.roundToInt

/** Settings list: Appearance (theme), Font type and Font size. Every change applies instantly. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
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
                Icon(MoreIcons.ArrowLeft, contentDescription = "Back", modifier = Modifier.size(22.dp), tint = c.onHeader)
            }
            Spacer(Modifier.width(12.dp))
            Text(text = "Settings", color = c.onHeader, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(title = "Appearance", icon = MoreIcons.Contrast) {
                listOf(
                    Triple(ThemeMode.System, "System default", "Matches your phone's light or dark setting"),
                    Triple(ThemeMode.Light, "Light", "Always use the light theme"),
                    Triple(ThemeMode.Dark, "Dark", "Always use the dark theme"),
                ).forEach { (mode, title, subtitle) ->
                    ChoiceRow(
                        selected = settings.mode == mode,
                        title = title,
                        subtitle = subtitle,
                        onClick = { settings.setMode(mode) },
                    )
                }
            }

            SettingsSection(title = "Font type", icon = MoreIcons.Type) {
                listOf(
                    Triple(FontChoice.Brand, "Poppins", "The SamilliMed brand font"),
                    Triple(FontChoice.Device, "System default", "Your phone's own font"),
                    Triple(FontChoice.Serif, "Serif", "Classic, book-style letters"),
                ).forEach { (choice, title, subtitle) ->
                    ChoiceRow(
                        selected = settings.font == choice,
                        title = title,
                        subtitle = subtitle,
                        fontFamily = choice.family(),
                        onClick = { settings.setFont(choice) },
                    )
                }
            }

            SettingsSection(title = "Font size", icon = MoreIcons.FontSize) {
                TextSize.entries.forEach { size ->
                    ChoiceRow(
                        selected = settings.textSize == size,
                        title = size.label,
                        subtitle = "${(size.scale * 100).roundToInt()}% of normal size",
                        onClick = { settings.setTextSize(size) },
                    )
                }
            }

            TextButton(
                onClick = {
                    settings.setMode(ThemeMode.System)
                    settings.setFont(FontChoice.Brand)
                    settings.setTextSize(TextSize.Default)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Reset to defaults")
            }
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon)
            Spacer(Modifier.width(10.dp))
            Text(text = title, color = c.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Column(Modifier.selectableGroup(), content = content)
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    fontFamily: FontFamily? = null,
) {
    val c = SamilliTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
            )
            Text(
                text = subtitle,
                color = c.textSecondary,
                fontSize = 12.sp,
                fontFamily = fontFamily,
            )
        }
    }
}
