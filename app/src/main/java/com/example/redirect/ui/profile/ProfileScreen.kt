package com.example.redirect.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.redirect.BuildConfig
import com.example.redirect.data.AppData
import com.example.redirect.data.AppTheme
import com.example.redirect.data.ReaderTheme
import com.example.redirect.data.RedirectStyle
import com.example.redirect.platform.CapabilityStatus
import com.example.redirect.platform.PlatformCapabilities
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.ScreenTitle
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.home.IntentionDialog
import com.example.redirect.ui.label
import com.example.redirect.ui.theme.LocalExtraColors
import kotlin.math.abs

@Composable
fun ProfileScreen(vm: AppViewModel, data: AppData, actions: AppActions, onReset: () -> Unit) {
    val prefs = data.preferences
    var editingName by remember { mutableStateOf(false) }
    var editingIntention by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { ScreenTitle("Profile", "Preferences, privacy and what this version can do.") }

        item {
            Group("You") {
                SettingRow("Name", prefs.displayName.ifBlank { "Not set" }, action = "Edit") { editingName = true }
                Divider()
                SettingRow(
                    "Daily reading intention",
                    prefs.readingIntentionMinutes?.let { "Read for $it minutes" } ?: "Off",
                    action = "Change",
                ) { editingIntention = true }
            }
        }

        item {
            Group("Reading") {
                Column(Modifier.padding(16.dp)) {
                    Text("Page", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        listOf(ReaderTheme.PAPER to "Light", ReaderTheme.WARM to "Warm", ReaderTheme.NIGHT to "Dark"),
                        prefs.reader.theme,
                    ) { t -> vm.repo.updateReaderPreferences { it.copy(theme = t) } }
                    Spacer(Modifier.height(16.dp))
                    Text("Line spacing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val spacing = listOf(1.4f, 1.6f, 1.9f).minByOrNull { abs(it - prefs.reader.lineHeight) } ?: 1.6f
                    Segmented(listOf(1.4f to "Compact", 1.6f to "Comfortable", 1.9f to "Airy"), spacing) { v ->
                        vm.repo.updateReaderPreferences { it.copy(lineHeight = v) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Text size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${prefs.reader.fontSizePx}px", style = MaterialTheme.typography.bodyLarge)
                        }
                        FilledTonalIconButton(
                            onClick = { vm.repo.updateReaderPreferences { it.copy(fontSizePx = (it.fontSizePx - 1).coerceAtLeast(14)) } },
                            enabled = prefs.reader.fontSizePx > 14,
                        ) { Icon(Icons.Rounded.Remove, "Smaller text") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = { vm.repo.updateReaderPreferences { it.copy(fontSizePx = (it.fontSizePx + 1).coerceAtMost(30)) } },
                            enabled = prefs.reader.fontSizePx < 30,
                        ) { Icon(Icons.Rounded.Add, "Larger text") }
                    }
                }
            }
        }

        item {
            Group("Redirection") {
                Column(Modifier.padding(16.dp)) {
                    Text("Default for new rules", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(
                        listOf(RedirectStyle.AUTOMATIC to "Automatic", RedirectStyle.CHOICE to "Choice", RedirectStyle.SCHEDULED to "Scheduled"),
                        prefs.defaultRedirectStyle,
                    ) { s -> vm.repo.updatePreferences { it.copy(defaultRedirectStyle = s) } }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        prefs.defaultRedirectStyle.label() + when (prefs.defaultRedirectStyle) {
                            RedirectStyle.AUTOMATIC -> ": go straight to your current book."
                            RedirectStyle.CHOICE -> ": show a few alternatives first."
                            RedirectStyle.SCHEDULED -> ": new rules start with a weekday schedule."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Group("Appearance") {
                Column(Modifier.padding(16.dp)) {
                    Text("App theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf(AppTheme.SYSTEM to "System", AppTheme.LIGHT to "Light", AppTheme.DARK to "Dark"), prefs.appTheme) { t ->
                        vm.repo.updatePreferences { it.copy(appTheme = t) }
                    }
                }
            }
        }

        item {
            Group("What works today") {
                PlatformCapabilities.all.forEachIndexed { i, cap ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        val extra = LocalExtraColors.current
                        val (icon, tint) = when (cap.status) {
                            CapabilityStatus.WORKS_NOW -> Icons.Rounded.CheckCircle to extra.success
                            CapabilityStatus.PLANNED -> Icons.Rounded.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                            CapabilityStatus.NOT_SUPPORTED -> Icons.Rounded.DoNotDisturbOn to extra.warning
                        }
                        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(cap.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (cap.status) {
                                    CapabilityStatus.WORKS_NOW -> "Works now"
                                    CapabilityStatus.PLANNED -> "Planned"
                                    CapabilityStatus.NOT_SUPPORTED -> "Not supported"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = tint,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(cap.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Divider()
                Text(
                    "When app detection is added, you'll be asked to allow Usage Access in Android Settings, with a clear explanation first. This version doesn't request any special permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item {
            Group("Account & data") {
                Text(
                    "No account needed. Your books, reading progress and rules are stored only on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Divider()
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    TextButton(onClick = {
                        vm.repo.updatePreferences { it.copy(onboardingComplete = false) }
                        onReset()
                    }) { Text("Replay introduction") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmReset = true }) { Text("Reset everything", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text("Redirect ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Included books are public-domain editions from Project Gutenberg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(prefs.displayName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("What should we call you?") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Your first name") }) },
            confirmButton = {
                TextButton(onClick = { vm.repo.updatePreferences { it.copy(displayName = name.trim()) }; editingName = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
        )
    }
    if (editingIntention) {
        IntentionDialog(prefs.readingIntentionMinutes, onDismiss = { editingIntention = false }) { minutes ->
            vm.repo.updatePreferences { it.copy(readingIntentionMinutes = minutes) }
            editingIntention = false
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = { Text("This deletes imported books, reading progress, bookmarks, lists and rules from this device, then restarts the introduction.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.repo.resetEverything(); onReset() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { content() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingRow(label: String, value: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}
