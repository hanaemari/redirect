package com.example.redirect.ui.rules

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.redirect.data.AppData
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.PlatformSupport
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectRule
import com.example.redirect.data.RedirectStyle
import com.example.redirect.data.Schedule
import com.example.redirect.data.SeedData
import com.example.redirect.data.TriggerApp
import com.example.redirect.data.TriggerKind
import com.example.redirect.data.describe
import com.example.redirect.data.trigger
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.DAY_LETTER
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.BackBar
import com.example.redirect.ui.components.DestinationIcon
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.describeSchedule
import com.example.redirect.ui.formatMinute
import com.example.redirect.ui.label

/** Editable copy of a rule while the guided flow is open. */
private data class Draft(
    val triggerId: String? = null,
    val mode: RedirectMode = RedirectMode.AUTOMATIC,
    val destinationIds: List<String> = listOf(SeedData.DEST_CURRENT_BOOK),
    val includeSaved: Boolean = false,
    val schedule: Schedule? = null,
    val grace: Int = 0,
    val priority: Int = 0,
    val name: String = "",
    val enabled: Boolean = true,
)

private val STEP_TITLES = listOf("App", "Action", "When", "Review")

@Composable
fun RuleEditorScreen(vm: AppViewModel, data: AppData, actions: AppActions, ruleId: String?, initialTrigger: String?) {
    val existing = remember(ruleId) { ruleId?.let { id -> data.rules.firstOrNull { it.id == id } } }
    var draft by remember {
        mutableStateOf(
            existing?.let {
                Draft(it.triggerId, it.mode, it.destinationIds, it.includeSavedAlternatives, it.schedule, it.gracePeriodMinutes, it.priority, it.name, it.enabled)
            } ?: Draft(
                triggerId = initialTrigger,
                mode = if (data.preferences.defaultRedirectStyle == RedirectStyle.CHOICE) RedirectMode.CHOICE else RedirectMode.AUTOMATIC,
                destinationIds = if (data.preferences.defaultRedirectStyle == RedirectStyle.CHOICE) listOf(SeedData.DEST_CURRENT_BOOK, SeedData.DEST_LIBRARY) else listOf(SeedData.DEST_CURRENT_BOOK),
                schedule = if (data.preferences.defaultRedirectStyle == RedirectStyle.SCHEDULED) Schedule(Schedule.WEEKDAYS) else null,
            )
        )
    }
    var step by rememberSaveable { mutableIntStateOf(if (existing != null) 3 else if (initialTrigger != null) 1 else 0) }

    fun save() {
        val trigger = draft.triggerId ?: return
        val t = System.currentTimeMillis()
        val name = draft.name.trim().ifEmpty { autoName(data, draft) }
        val rule = RedirectRule(
            id = existing?.id ?: vm.repo.newRuleId(),
            name = name,
            triggerId = trigger,
            mode = draft.mode,
            destinationIds = draft.destinationIds,
            includeSavedAlternatives = draft.mode == RedirectMode.CHOICE && draft.includeSaved,
            schedule = draft.schedule,
            enabled = draft.enabled,
            pausedUntil = existing?.pausedUntil,
            gracePeriodMinutes = draft.grace,
            priority = draft.priority,
            createdAt = existing?.createdAt ?: t,
            updatedAt = t,
        )
        // Make sure a newly chosen distracting app is part of the user's list.
        if (trigger != SeedData.TRIGGER_ANY && trigger !in data.selectedTriggerIds) vm.repo.setTriggerSelected(trigger, true)
        vm.repo.upsertRule(rule)
        vm.toast(if (existing != null) "Rule updated" else "Rule created")
        actions.back()
    }

    val canContinue = when (step) {
        0 -> draft.triggerId != null
        1 -> draft.destinationIds.isNotEmpty() || (draft.mode == RedirectMode.CHOICE && draft.includeSaved)
        2 -> draft.schedule == null || draft.schedule!!.days.isNotEmpty()
        else -> draft.triggerId != null
    }

    BackHandler(enabled = step > 0 && existing == null) { step-- }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(if (existing != null) "Edit rule" else "New rule", onBack = { actions.back() })
        StepIndicator(step, onStep = { if (existing != null || it < step) step = it })
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val fwd = targetState > initialState
                (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { if (fwd) it / 8 else -it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(250)) { if (fwd) -it / 8 else it / 8 })
            },
            modifier = Modifier.weight(1f),
            label = "rule-step",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(12.dp))
                when (s) {
                    0 -> TriggerStep(vm, data, draft.triggerId) { draft = draft.copy(triggerId = it) }
                    1 -> ActionStep(vm, data, draft) { draft = it }
                    2 -> ScheduleStep(draft.schedule) { draft = draft.copy(schedule = it) }
                    else -> ReviewStep(data, draft, onChange = { draft = it }, onJump = { step = it })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0 && existing == null) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (step < 3) {
                if (existing != null) {
                    TextButton(onClick = ::save, enabled = draft.triggerId != null && canContinue) { Text("Save") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { step++ }, enabled = canContinue) { Text("Next") }
            } else {
                Button(onClick = ::save, enabled = canContinue) { Text(if (existing != null) "Save changes" else "Create rule") }
            }
        }
    }
}

private fun autoName(data: AppData, d: Draft): String {
    val app = d.triggerId?.let { data.trigger(it)?.displayName } ?: "App"
    val target = when {
        d.mode == RedirectMode.CHOICE -> "choose"
        else -> when (d.destinationIds.firstOrNull()?.let { id -> data.destinations.firstOrNull { it.id == id }?.type }) {
            DestinationType.CURRENT_BOOK -> "my book"
            DestinationType.SPECIFIC_BOOK -> "a book"
            DestinationType.LIBRARY -> "library"
            DestinationType.EXTERNAL_APP -> data.destinations.firstOrNull { it.id == d.destinationIds.first() }?.displayName ?: "an app"
            DestinationType.OFFLINE_ACTIVITY -> "step away"
            else -> "choose"
        }
    }
    return "$app → $target"
}

@Composable
private fun StepIndicator(step: Int, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        STEP_TITLES.forEachIndexed { i, title ->
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { onStep(i) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.height(24.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == step) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(question: String, hint: String? = null) {
    Text(question, style = MaterialTheme.typography.headlineMedium)
    if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
}

// -- Step 1: trigger ----------------------------------------------------------------

@Composable
private fun TriggerStep(vm: AppViewModel, data: AppData, selected: String?, onSelect: (String) -> Unit) {
    var addingInstalled by remember { mutableStateOf(false) }
    StepHeading("When I open…", "Choose the app this rule is for.")

    val all = data.triggerApps.ifEmpty { SeedData.triggerCatalog }
    val mine = data.selectedTriggerIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    val others = all.filter { it.kind == TriggerKind.APP && it.id !in data.selectedTriggerIds }
    val features = all.filter { it.kind == TriggerKind.IN_APP_FEATURE }
    val any = all.firstOrNull { it.kind == TriggerKind.ANY_SELECTED }

    if (mine.isNotEmpty()) GroupLabel("Your distracting apps")
    mine.forEach { TriggerOption(vm, it, selected == it.id) { onSelect(it.id) } }
    any?.let {
        GroupLabel("Broader")
        TriggerOption(vm, it, selected == it.id) { onSelect(it.id) }
    }
    if (others.isNotEmpty()) GroupLabel("Other apps")
    others.forEach { TriggerOption(vm, it, selected == it.id) { onSelect(it.id) } }
    TextButton(onClick = { addingInstalled = true }) {
        Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Choose another installed app")
    }
    GroupLabel("Inside apps")
    features.forEach { TriggerOption(vm, it, selected == it.id) { onSelect(it.id) } }

    val chosen = selected?.let { data.trigger(it) }
    if (chosen != null) {
        Spacer(Modifier.height(12.dp))
        InfoNote(supportExplanation(chosen), warning = chosen.support == PlatformSupport.NOT_VALIDATED)
    }

    if (addingInstalled) {
        InstalledAppSheet(vm, "Choose an app", "It will be added to your distracting apps.", onDismiss = { addingInstalled = false }) { app ->
            vm.repo.addCustomTrigger(app.packageName, app.label)
            onSelect(app.packageName)
            addingInstalled = false
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TriggerOption(vm: AppViewModel, trigger: TriggerApp, selected: Boolean, onClick: () -> Unit) {
    val pkg = remember(trigger.id) { vm.repo.installedApps.installedPackageFor(trigger) }
    OptionRow(selected, onClick, radio = true) {
        if (trigger.kind == TriggerKind.ANY_SELECTED) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AllInclusive, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        } else {
            AppIcon(pkg ?: trigger.packageName, trigger.displayName, 36.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(trigger.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    trigger.kind == TriggerKind.ANY_SELECTED -> "Every app in your distracting list"
                    trigger.support == PlatformSupport.NOT_VALIDATED -> "Can't be detected on Android"
                    pkg != null -> "Installed"
                    else -> "Not installed"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (trigger.support == PlatformSupport.NOT_VALIDATED) com.example.redirect.ui.theme.LocalExtraColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionRow(selected: Boolean, onClick: () -> Unit, radio: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.5f) else scheme.surfaceContainerLowest,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
            Spacer(Modifier.width(8.dp))
            Icon(
                when {
                    radio && selected -> Icons.Rounded.RadioButtonChecked
                    radio -> Icons.Rounded.RadioButtonUnchecked
                    selected -> Icons.Rounded.CheckBox
                    else -> Icons.Rounded.CheckBoxOutlineBlank
                },
                null,
                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoNote(text: String, warning: Boolean = false) {
    val extra = com.example.redirect.ui.theme.LocalExtraColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (warning) extra.warningContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = if (warning) extra.warning else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (warning) extra.warning else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -- Step 2: action -----------------------------------------------------------------

@Composable
private fun ActionStep(vm: AppViewModel, data: AppData, draft: Draft, onChange: (Draft) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val app = draft.triggerId?.let { data.trigger(it)?.displayName } ?: "it"
    StepHeading("What would you like to happen?", "When I open $app…")

    ModeCard(
        Icons.Rounded.AltRoute, "Redirect automatically", "Go straight to one place. Smooth and quick.",
        draft.mode == RedirectMode.AUTOMATIC,
    ) {
        onChange(draft.copy(mode = RedirectMode.AUTOMATIC, destinationIds = draft.destinationIds.take(1).ifEmpty { listOf(SeedData.DEST_CURRENT_BOOK) }))
    }
    Spacer(Modifier.height(10.dp))
    ModeCard(
        Icons.Rounded.TouchApp, "Show a choice screen", "A calm pause with a few alternatives. You decide.",
        draft.mode == RedirectMode.CHOICE,
    ) { onChange(draft.copy(mode = RedirectMode.CHOICE)) }

    Spacer(Modifier.height(24.dp))
    val options = data.destinations.filter { it.type != DestinationType.CHOICE_SCREEN }
    if (draft.mode == RedirectMode.AUTOMATIC) {
        Text("Take me to", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        options.forEach { d ->
            DestinationOption(vm, data, d, draft.destinationIds.firstOrNull() == d.id, radio = true) {
                onChange(draft.copy(destinationIds = listOf(d.id)))
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoNote("If this isn't available when the rule fires (say, the app was uninstalled), you'll see your choice screen instead.")
    } else {
        Text("Offer me", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        options.forEach { d ->
            val checked = d.id in draft.destinationIds
            DestinationOption(vm, data, d, checked, radio = false) {
                onChange(draft.copy(destinationIds = if (checked) draft.destinationIds - d.id else draft.destinationIds + d.id))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also include all my alternatives", style = MaterialTheme.typography.bodyLarge)
                Text("Anything you add later appears automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(draft.includeSaved, onCheckedChange = { onChange(draft.copy(includeSaved = it)) })
        }
    }
    TextButton(onClick = { adding = true }) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add a book, app or activity")
    }

    if (adding) {
        AddAlternativeSheet(vm, data, onDismiss = { adding = false }) { id ->
            adding = false
            onChange(
                if (draft.mode == RedirectMode.AUTOMATIC) draft.copy(destinationIds = listOf(id))
                else draft.copy(destinationIds = (draft.destinationIds + id).distinct())
            )
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    OptionRow(selected, onClick, radio = true) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationOption(vm: AppViewModel, data: AppData, d: Destination, selected: Boolean, radio: Boolean, onClick: () -> Unit) {
    val available = vm.isAvailable(d)
    OptionRow(selected, onClick, radio) {
        DestinationIcon(d, 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(data.describe(d), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !available && d.type == DestinationType.EXTERNAL_APP -> "Not installed"
                    !available -> "Not available right now"
                    d.type == DestinationType.EXTERNAL_APP -> "App on this device"
                    d.type == DestinationType.OFFLINE_ACTIVITY -> "Offline"
                    else -> "In this app"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// -- Step 3: schedule ---------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleStep(schedule: Schedule?, onChange: (Schedule?) -> Unit) {
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    StepHeading("When should this apply?", "Use different rules for weekdays and weekends, or only in the evening.")

    ModeCard(Icons.Rounded.AllInclusive, "Any time", "Always on, every day.", schedule == null) { onChange(null) }
    Spacer(Modifier.height(10.dp))
    ModeCard(Icons.Rounded.Schedule, "Only at certain times", "Pick days and an optional time window.", schedule != null) {
        if (schedule == null) onChange(Schedule(Schedule.ALL_DAYS))
    }

    AnimatedVisibility(schedule != null) {
        val s = schedule ?: Schedule()
        Column {
            Spacer(Modifier.height(24.dp))
            Text("Days", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    val on = day in s.days
                    Surface(
                        onClick = { onChange(s.copy(days = if (on) s.days - day else s.days + day)) },
                        shape = CircleShape,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                DAY_LETTER[day - 1],
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Every day" to Schedule.ALL_DAYS, "Weekdays" to Schedule.WEEKDAYS, "Weekends" to Schedule.WEEKEND).forEach { (label, days) ->
                    FilterChip(
                        selected = s.days == days,
                        onClick = { onChange(s.copy(days = days)) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
            if (s.days.isEmpty()) {
                Text("Pick at least one day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("All day", style = MaterialTheme.typography.titleLarge)
                    Text("Turn off to choose a time window.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = s.isAllDay,
                    onCheckedChange = { allDay -> onChange(if (allDay) s.copy(startMinute = 0, endMinute = 0) else s.copy(startMinute = 21 * 60, endMinute = 23 * 60)) },
                )
            }
            if (!s.isAllDay) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeBox("From", formatMinute(s.startMinute), Modifier.weight(1f)) { picking = true }
                    TimeBox("Until", formatMinute(s.endMinute), Modifier.weight(1f)) { picking = false }
                }
                if (s.endMinute < s.startMinute) {
                    Spacer(Modifier.height(8.dp))
                    Text("Runs overnight, into the next morning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Pill(describeSchedule(s), PillTone.Accent)
        }
    }

    picking?.let { isStart ->
        val s = schedule ?: Schedule()
        TimeDialog(
            initialMinute = if (isStart) s.startMinute else s.endMinute,
            onDismiss = { picking = null },
        ) { minute ->
            val updated = if (isStart) s.copy(startMinute = minute) else s.copy(endMinute = minute)
            // Equal start and end would mean "all day"; nudge the end by an hour.
            onChange(if (updated.startMinute == updated.endMinute) updated.copy(endMinute = (updated.endMinute + 60) % (24 * 60)) else updated)
            picking = null
        }
    }
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    SoftCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// -- Step 4: review -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReviewStep(data: AppData, draft: Draft, onChange: (Draft) -> Unit, onJump: (Int) -> Unit) {
    val trigger = draft.triggerId?.let { data.trigger(it) }
    val dests = draft.destinationIds.mapNotNull { id -> data.destinations.firstOrNull { it.id == id } }
    StepHeading("Finishing touches")

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(4.dp)) {
            ReviewLine("When I open", trigger?.displayName ?: "Choose an app") { onJump(0) }
            ReviewLine(
                draft.mode.label(),
                if (draft.mode == RedirectMode.AUTOMATIC) dests.firstOrNull()?.let { data.describe(it) } ?: "Choose a destination"
                else (dests.map { data.describe(it) } + if (draft.includeSaved) listOf("all my alternatives") else emptyList()).joinToString(", ").ifEmpty { "Choose options" },
            ) { onJump(1) }
            ReviewLine("Applies", describeSchedule(draft.schedule)) { onJump(2) }
        }
    }

    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onChange(draft.copy(name = it)) },
        label = { Text("Name (optional)") },
        placeholder = { Text(autoName(data, draft)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    Text("Grace period", style = MaterialTheme.typography.titleLarge)
    Text(
        "If you choose to continue to the app anyway, stay quiet for a while before stepping in again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 2, 5, 10, 15, 30).forEach { m ->
            FilterChip(
                selected = draft.grace == m,
                onClick = { onChange(draft.copy(grace = m)) },
                label = { Text(if (m == 0) "None" else "$m min") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Text("Priority", style = MaterialTheme.typography.titleLarge)
    Text(
        "When two rules apply at the same moment, the higher one wins.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    val levels = listOf(0 to "Normal", 1 to "High", 2 to "Top")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        levels.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = draft.priority == value,
                onClick = { onChange(draft.copy(priority = value)) },
                shape = SegmentedButtonDefaults.itemShape(i, levels.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer),
                label = { Text(label) },
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Rule is on", style = MaterialTheme.typography.titleLarge)
            Text("You can pause or switch it off any time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(draft.enabled, onCheckedChange = { onChange(draft.copy(enabled = it)) })
    }

    if (trigger != null) {
        Spacer(Modifier.height(20.dp))
        InfoNote(supportExplanation(trigger), warning = trigger.support == PlatformSupport.NOT_VALIDATED)
    }
}

@Composable
private fun ReviewLine(label: String, value: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onEdit) { Text("Change") }
    }
}
