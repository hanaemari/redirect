package com.example.redirect.ui.rules

import com.example.redirect.ui.components.AccessibilityBanner
import com.example.redirect.ui.components.InterruptionStatusCard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.redirect.data.AppData
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationCategory
import com.example.redirect.data.DestinationType
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectRule
import com.example.redirect.data.SeedData
import com.example.redirect.data.describe
import com.example.redirect.data.destination
import com.example.redirect.data.trigger
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.Routes
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.DestinationIcon
import com.example.redirect.ui.components.EmptyState
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.ScreenTitle
import com.example.redirect.ui.components.SectionHeader
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.components.iconFor
import com.example.redirect.ui.describeSchedule
import com.example.redirect.ui.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun RulesScreen(vm: AppViewModel, data: AppData, actions: AppActions) {
    var showApps by remember { mutableStateOf(false) }
    var showAddAlt by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<RedirectRule?>(null) }
    var confirmRemoveDest by remember { mutableStateOf<Destination?>(null) }
    var showTryIt by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ScreenTitle("Rules", "What happens when you reach for a distracting app.") {
                FilledTonalIconButton(onClick = { actions.editRule() }) { Icon(Icons.Rounded.Add, "New rule") }
            }
        }
        item { AccessibilityBanner(vm, data, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item { InterruptionStatusCard(vm, data, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }

        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Your rules", action = "Try it", onAction = { showTryIt = true })
        }
        if (data.rules.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Add, "No rules yet",
                    "Pick an app and decide where you'd rather go instead.",
                ) { Button(onClick = { actions.editRule() }) { Text("Create a rule") } }
            }
        }
        items(data.rules, key = { it.id }) { rule ->
            RuleCard(
                rule, data,
                modifier = Modifier.animateItem().padding(horizontal = 20.dp, vertical = 6.dp),
                onEdit = { actions.editRule(rule.id) },
                onToggle = { vm.repo.setRuleEnabled(rule.id, it) },
                onPreview = { actions.previewRule(rule.id, rule.triggerId) },
                onPause = { until -> vm.repo.pauseRule(rule.id, until) },
                onDuplicate = { vm.repo.duplicateRule(rule.id); vm.toast("Rule duplicated. The copy starts switched off.") },
                onDelete = { confirmDelete = rule },
            )
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Distracting apps", action = "Edit", onAction = { showApps = true })
            val selected = data.selectedTriggerIds.mapNotNull { data.trigger(it) }
            if (selected.isEmpty()) {
                Text(
                    "None selected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                FlowRow(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selected.forEach { app ->
                        val pkg = remember(app.id) { vm.repo.installedApps.installedPackageFor(app) }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(pkg, app.displayName, 24.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(app.displayName, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Your alternatives", action = "Add", onAction = { showAddAlt = true })
            Text(
                "Places a rule can send you. Built-in ones open inside this app; apps open on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            SoftCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                val dests = data.destinations.filter { it.type != DestinationType.CHOICE_SCREEN }
                dests.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 70.dp))
                    DestinationRow(vm, data, d, onRemove = if (d.id == SeedData.DEST_CURRENT_BOOK) null else ({ confirmRemoveDest = d }))
                }
                TextButton(onClick = { showAddAlt = true }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add an alternative")
                }
            }
        }
    }

    if (showApps) DistractingAppsSheet(vm, data, onDismiss = { showApps = false })
    if (showAddAlt) AddAlternativeSheet(vm, data, onDismiss = { showAddAlt = false }) { showAddAlt = false; vm.toast("Alternative added") }
    if (showTryIt) TryItSheet(vm, data, onDismiss = { showTryIt = false }) { triggerId ->
        showTryIt = false
        actions.previewTrigger(triggerId)
    }

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this rule?") },
            text = { Text("“${rule.name}” will be removed. Your alternatives and books aren't affected.") },
            confirmButton = { TextButton(onClick = { vm.repo.deleteRule(rule.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    confirmRemoveDest?.let { d ->
        val used = data.rules.count { d.id in it.destinationIds }
        AlertDialog(
            onDismissRequest = { confirmRemoveDest = null },
            title = { Text("Remove “${d.displayName}”?") },
            text = { Text(if (used > 0) "It's used by $used ${if (used == 1) "rule" else "rules"}, which will stop offering it." else "You can add it again any time.") },
            confirmButton = { TextButton(onClick = { vm.repo.removeDestination(d.id); confirmRemoveDest = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemoveDest = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RuleCard(
    rule: RedirectRule,
    data: AppData,
    modifier: Modifier,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onPause: (Long) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val trigger = data.trigger(rule.triggerId)
    val dests = rule.destinationIds.mapNotNull { data.destination(it) }
    val now = System.currentTimeMillis()
    val paused = rule.enabled && rule.pausedUntil != null && rule.pausedUntil > now
    val active = rule.enabled && !paused
    var menu by remember { mutableStateOf(false) }

    SoftCard(modifier.fillMaxWidth().animateContentSize(), onClick = onEdit) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).alpha(if (active) 1f else 0.55f), verticalAlignment = Alignment.CenterVertically) {
                    TriggerBadge(trigger)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward, null,
                        Modifier.padding(horizontal = 10.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rule.mode == RedirectMode.CHOICE) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(iconFor("choice"), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    } else {
                        dests.firstOrNull()?.let { DestinationIcon(it, 36.dp) }
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Rule options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem({ Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem({ Text("Preview") }, leadingIcon = { Icon(Icons.Rounded.Visibility, null) }, onClick = { menu = false; onPreview() })
                        if (paused) {
                            DropdownMenuItem({ Text("Resume now") }, leadingIcon = { Icon(Icons.Rounded.PlayCircle, null) }, onClick = { menu = false; onToggle(true) })
                        } else {
                            DropdownMenuItem({ Text("Pause for 1 hour") }, leadingIcon = { Icon(Icons.Rounded.PauseCircle, null) }, onClick = {
                                menu = false; onPause(System.currentTimeMillis() + 60 * 60_000L)
                            })
                            DropdownMenuItem({ Text("Pause until tomorrow") }, leadingIcon = { Icon(Icons.Rounded.PauseCircle, null) }, onClick = {
                                menu = false
                                onPause(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                            })
                        }
                        DropdownMenuItem({ Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem({ Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "When I open ${trigger?.displayName ?: "an unknown app"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                when {
                    rule.mode == RedirectMode.CHOICE -> "Show me: " + (dests.map { data.describe(it) } + if (rule.includeSavedAlternatives) listOf("all my alternatives") else emptyList())
                        .ifEmpty { listOf("my alternatives") }.joinToString(", ")
                    dests.isEmpty() -> "No destination chosen"
                    else -> data.describe(dests.first())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !rule.enabled -> Pill("Off", PillTone.Muted)
                    paused -> Pill("Paused until ${formatTime(rule.pausedUntil!!)}", PillTone.Warning)
                    else -> Pill("On", PillTone.Success)
                }
                Pill(rule.mode.label(), PillTone.Neutral)
                Pill(describeSchedule(rule.schedule), PillTone.Neutral)
                if (rule.priority > 0) Pill(if (rule.priority >= 2) "Top priority" else "High priority", PillTone.Accent)
                if (rule.gracePeriodMinutes > 0) Pill("${rule.gracePeriodMinutes} min grace", PillTone.Neutral)
                SupportPill(trigger)
            }
        }
    }
}

@Composable
private fun TriggerBadge(trigger: com.example.redirect.data.TriggerApp?) {
    if (trigger?.id == SeedData.TRIGGER_ANY) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("All", style = MaterialTheme.typography.labelMedium) }
    } else {
        AppIcon(trigger?.packageName, trigger?.displayName ?: "?", 36.dp)
    }
}

@Composable
private fun DestinationRow(vm: AppViewModel, data: AppData, d: Destination, onRemove: (() -> Unit)?) {
    val available = vm.isAvailable(d)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        DestinationIcon(d, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(data.describe(d), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    d.category == DestinationCategory.EXTERNAL_APP && !available -> "App not installed"
                    d.category == DestinationCategory.EXTERNAL_APP -> "Opens the app on this device"
                    d.type == DestinationType.CURRENT_BOOK && !available -> "No current book yet"
                    d.type == DestinationType.OFFLINE_ACTIVITY -> "A gentle prompt to step away"
                    d.type == DestinationType.SPECIFIC_BOOK && !available -> "Book no longer readable"
                    else -> "Opens in this app"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.RemoveCircleOutline, "Remove ${d.displayName}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TryItSheet(vm: AppViewModel, data: AppData, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val triggers = (data.selectedTriggerIds + data.rules.map { it.triggerId }).distinct()
        .filter { it != SeedData.TRIGGER_ANY }
        .mapNotNull { data.trigger(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Imagine you just opened…") },
        text = {
            Column {
                Text(
                    "See what your rules would do right now, using today's day and time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (triggers.isEmpty()) Text("Add a distracting app first.")
                triggers.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(t.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(remember(t.id) { vm.repo.installedApps.installedPackageFor(t) }, t.displayName, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(t.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("EEE h:mm a")
fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)
