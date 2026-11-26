package org.samillimed.app.ui.branding

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.branding.Brand
import org.samillimed.app.data.branding.BrandDoc
import org.samillimed.app.data.branding.BrandRepository
import org.samillimed.app.data.branding.LogoProcessor
import org.samillimed.app.data.branding.decodeLogo
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.FormTextField
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.BrandPresets
import org.samillimed.app.ui.theme.SamilliTheme
import org.samillimed.app.ui.theme.parseHexColor
import org.samillimed.app.ui.theme.toHex
import org.samillimed.app.ui.theme.withBrand

/** The administrator sets the facility's name, logo and colours here. The structure of the app never changes. */
@Composable
fun BrandingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val repository = remember { BrandRepository() }
    var name by rememberSaveable { mutableStateOf(Brand.name) }
    var primary by rememberSaveable { mutableStateOf(Brand.primaryHex.ifBlank { BrandPresets.first().primary }) }
    var accent by rememberSaveable { mutableStateOf(Brand.accentHex.ifBlank { BrandPresets.first().accent }) }
    var logoText by remember { mutableStateOf(Brand.logoBase64) }
    val logoImage = remember(logoText) { decodeLogo(logoText) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val p = parseHexColor(primary)
    val a = parseHexColor(accent)
    val preview = if (p != null && a != null) c.withBrand(p, a) else c

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = LogoProcessor.toBase64(context, uri)
            if (text == null) {
                error = "That picture could not be used. Try a PNG or JPG."
            } else {
                logoText = text
                error = null
            }
        }
    }

    fun save() {
        val allBlank = name.isBlank() && logoText.isBlank()
        when {
            !allBlank && name.isBlank() -> error = "Type your facility's name."
            !allBlank && (p == null || a == null) -> error = "Check the two colour codes."
            else -> {
                saving = true
                error = null
                repository.save(
                    BrandDoc(
                        displayName = name.trim(),
                        logoBase64 = logoText,
                        primaryHex = if (allBlank) "" else p?.toHex().orEmpty(),
                        accentHex = if (allBlank) "" else a?.toHex().orEmpty(),
                    ),
                    onSuccess = {
                        saving = false
                        Toast.makeText(context, "Branding saved", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { message ->
                        saving = false
                        error = message
                    },
                )
            }
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Facility branding", subtitle = "Your name, logo and colours", onBack = onBack)
        if (!CurrentUser.isAdmin) {
            EmptyState(
                icon = SamilliIcons.Bell,
                title = "Administrators only",
                message = "Only an administrator can change the facility's name, logo and colours.",
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Preview
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(preview.headerStart, preview.headerEnd)))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoPlate(52.dp, logo = logoImage, name = name, ink = preview.headerStart)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                name.ifBlank { "Your facility name" },
                                color = preview.onHeader,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("How the top bar will look", color = preview.onHeader.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = preview.gold, contentColor = preview.onReorder),
                    ) { Text("Sample button", fontWeight = FontWeight.Bold) }
                    Text("Sample link", color = preview.sectionLink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Facility name *",
                    capitalization = KeyboardCapitalization.Words,
                    supporting = "Shown at the top of the app and on receipts.",
                )

                SamilliCard(Modifier.fillMaxWidth()) {
                    Text("Logo", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose a picture from the phone. A square logo with a plain background looks best.",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                    Row(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LogoPlate(56.dp, logo = logoImage, name = name, ink = c.headerStart)
                        Button(
                            onClick = { picker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                        ) { Text(if (logoText.isBlank()) "Choose logo" else "Change logo", fontWeight = FontWeight.Bold) }
                        if (logoText.isNotBlank()) TextButton(onClick = { logoText = "" }) { Text("Remove") }
                    }
                }

                SamilliCard(Modifier.fillMaxWidth()) {
                    Text("Colours", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Pick a ready-made set, or type your own codes below. Text is kept readable automatically.",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrandPresets.forEach { preset ->
                            val selected = primary.equals(preset.primary, ignoreCase = true) &&
                                accent.equals(preset.accent, ignoreCase = true)
                            Column(
                                Modifier.clickable {
                                    primary = preset.primary
                                    accent = preset.accent
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(preset.primary) ?: c.headerStart)
                                        .border(if (selected) 3.dp else 0.dp, c.gold, CircleShape),
                                    contentAlignment = Alignment.BottomEnd,
                                ) {
                                    Box(
                                        Modifier
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(preset.accent) ?: c.gold),
                                    )
                                }
                                Text(
                                    preset.name,
                                    color = if (selected) c.textPrimary else c.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    FormTextField(
                        value = primary,
                        onValueChange = { primary = it.take(7) },
                        label = "Main colour (like #0D4F37)",
                        error = if (p == null) "Use six letters or numbers, like #0D4F37." else null,
                    )
                    FormTextField(
                        value = accent,
                        onValueChange = { accent = it.take(7) },
                        label = "Accent colour (like #E4AE42)",
                        error = if (a == null) "Use six letters or numbers, like #E4AE42." else null,
                    )
                }

                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = !saving,
                        onClick = { save() },
                        colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                    ) { Text(if (saving) "Saving…" else "Save branding", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = {
                        name = ""
                        logoText = ""
                        primary = BrandPresets.first().primary
                        accent = BrandPresets.first().accent
                    }) { Text("Reset to the standard look") }
                }
                Text(
                    "To go back to the standard SamilliMed look, tap Reset, then Save branding.",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
