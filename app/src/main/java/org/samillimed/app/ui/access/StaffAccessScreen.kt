package org.samillimed.app.ui.access

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.samillimed.app.data.facility.CurrentUser
import org.samillimed.app.data.facility.Permission
import org.samillimed.app.data.facility.PermissionRepository
import org.samillimed.app.data.facility.Permissions
import org.samillimed.app.ui.common.EmptyState
import org.samillimed.app.ui.common.ScreenHeader
import org.samillimed.app.ui.home.SamilliCard
import org.samillimed.app.ui.icons.SamilliIcons
import org.samillimed.app.ui.theme.SamilliTheme

/** The administrator decides here what every staff account in the facility can see and do. */
@Composable
fun StaffAccessScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = SamilliTheme.colors
    val context = LocalContext.current
    val repository = remember { PermissionRepository() }
    val draft = remember {
        mutableStateMapOf<String, Boolean>().apply {
            Permission.values().forEach { put(it.key, Permissions.staffAllowed(it)) }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val changed = Permission.values().any { draft[it.key] != Permissions.staffAllowed(it) }

    Column(modifier.fillMaxSize().background(c.background)) {
        ScreenHeader(title = "Staff Access", subtitle = "What staff accounts can see and do", onBack = onBack)
        if (!CurrentUser.isAdmin) {
            EmptyState(
                icon = SamilliIcons.Bell,
                title = "Administrators only",
                message = "Only an administrator can change what staff can see and do.",
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SamilliCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Owners and administrators always have full access. These switches apply to every staff account in this facility.",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                AccessGroup("What staff can see", "Hidden in the app", Permission.values().filter { !it.action }, draft)
                AccessGroup("What staff can do", "Blocked on the server", Permission.values().filter { it.action }, draft)
                error?.let { Text(it, color = c.urgent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = changed && !saving,
                        onClick = {
                            saving = true
                            error = null
                            repository.save(
                                flags = draft.toMap(),
                                onSuccess = {
                                    saving = false
                                    Toast.makeText(context, "Staff access saved", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { message ->
                                    saving = false
                                    error = message
                                },
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.gold, contentColor = c.onReorder),
                    ) { Text(if (saving) "Saving…" else "Save changes", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { Permission.values().forEach { draft[it.key] = it.staffDefault } }) {
                        Text("Reset to defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessGroup(
    title: String,
    how: String,
    items: List<Permission>,
    draft: MutableMap<String, Boolean>,
) {
    val c = SamilliTheme.colors
    SamilliCard(Modifier.fillMaxWidth()) {
        Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(how, color = c.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        items.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(p.title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(p.description, color = c.textSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = draft[p.key] ?: p.staffDefault,
                    onCheckedChange = { draft[p.key] = it },
                )
            }
        }
    }
}
