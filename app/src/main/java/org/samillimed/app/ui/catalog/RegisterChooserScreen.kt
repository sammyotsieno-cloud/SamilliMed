package org.samillimed.app.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.MoreIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** The Register tab: choose whether to register a product or a service. */
@Composable
fun RegisterChooserScreen(
    onBack: () -> Unit,
    onProduct: () -> Unit,
    onService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SamilliTheme.colors
    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Register", subtitle = "What are you registering?", onBack = onBack)
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChooserCard(
                icon = MoreIcons.ProductTag,
                title = "Product",
                description = "A medicine, or any item you buy and sell: gauze, gloves, test kits and so on.",
                onClick = onProduct,
            )
            ChooserCard(
                icon = MoreIcons.Stethoscope,
                title = "Service",
                description = "Something the clinic charges for: a consultation, a laboratory test, a procedure.",
                onClick = onService,
            )
        }
    }
}

@Composable
private fun ChooserCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(c.iconBg, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = c.iconTint)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = description,
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
