package org.samillimed.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.theme.SamilliTheme

/** Colours for every text box in the forms. */
@Composable
fun formFieldColors(): TextFieldColors {
    val c = SamilliTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        errorTextColor = c.textPrimary,
        focusedBorderColor = c.accentText,
        unfocusedBorderColor = c.textSecondary.copy(alpha = 0.35f),
        errorBorderColor = c.urgent,
        focusedLabelColor = c.accentText,
        unfocusedLabelColor = c.textSecondary,
        errorLabelColor = c.urgent,
        cursorColor = c.accentText,
        errorCursorColor = c.urgent,
        focusedSupportingTextColor = c.textSecondary,
        unfocusedSupportingTextColor = c.textSecondary,
        errorSupportingTextColor = c.urgent,
        focusedTrailingIconColor = c.textSecondary,
        unfocusedTrailingIconColor = c.textSecondary,
        errorTrailingIconColor = c.urgent,
    )
}

/** A card with a title, grouping related fields. */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = SamilliTheme.colors
    SamilliCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(text = title, color = c.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (hint != null) {
            Text(text = hint, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** A normal text box. error shows in red under the box; supporting is a grey hint. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    val support: (@Composable () -> Unit)? =
        if (error != null || supporting != null) {
            { Text(error ?: supporting.orEmpty()) }
        } else {
            null
        }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = error != null,
        supportingText = support,
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
        colors = formFieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
    )
}

/**
 * A box you can type in, with an arrow that opens the saved options to pick from.
 * Typing something that is not in the list is allowed: the screen can save it as a new option.
 */
@Composable
fun ComboField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        FormTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            error = error,
            supporting = supporting,
            capitalization = capitalization,
            trailing = {
                IconButton(onClick = { open = !open }) {
                    Icon(CommonIcons.ChevronDown, contentDescription = "Show options", modifier = Modifier.size(22.dp))
                }
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Nothing saved yet. Type a new one.") },
                    onClick = { open = false },
                    enabled = false,
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** A row with a title, a grey line under it, and an on/off switch. Tapping anywhere toggles it. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = SamilliTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(text = subtitle, color = c.textSecondary, fontSize = 12.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** The big gold button, used for Save. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = SamilliTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.gold,
            contentColor = c.onReorder,
            disabledContainerColor = c.gold.copy(alpha = 0.4f),
            disabledContentColor = c.onReorder.copy(alpha = 0.6f),
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** A two-button question: for example "Discard changes?" or "Save anyway?". */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
