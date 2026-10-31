package com.example.redirect.ui.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.redirect.data.AppData
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.SeedData
import com.example.redirect.data.currentBook
import com.example.redirect.data.describe
import com.example.redirect.data.trigger
import com.example.redirect.engine.RedirectDecision
import com.example.redirect.engine.RuleEngine
import com.example.redirect.ui.AppActions
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.components.AppIcon
import com.example.redirect.ui.components.BackBar
import com.example.redirect.ui.components.BookCover
import com.example.redirect.ui.components.DestinationIcon
import com.example.redirect.ui.components.Pill
import com.example.redirect.ui.components.PillTone
import com.example.redirect.ui.components.ReadingProgress
import com.example.redirect.ui.components.SoftCard
import com.example.redirect.ui.components.iconFor
import com.example.redirect.ui.describeSchedule
import com.example.redirect.ui.percent
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * The intervention screen. [InterruptionService] opens it with [live] = true when a distracting
 * app is opened; Preview / Try it open the same screen. Choosing an alternative really opens it.
 *
 * With [ruleId], that one rule is previewed regardless of its schedule or switch;
 * without it, the rule engine decides exactly as it would right now.
 */
@Composable
fun InterventionScreen(vm: AppViewModel, data: AppData, actions: AppActions, triggerId: String, ruleId: String?, live: Boolean = false) {
    val trigger = data.trigger(triggerId)
    val appName = trigger?.displayName ?: "that app"
    val decision = remember(triggerId, ruleId) {
        if (ruleId != null) {
            val rule = data.rules.firstOrNull { it.id == ruleId }
            if (rule == null) RedirectDecision.None
            else RuleEngine.decide(
                triggerId, LocalDateTime.now(), System.currentTimeMillis(),
                RuleEngine.Input(
                    rules = listOf(rule.copy(enabled = true, pausedUntil = null, schedule = null)),
                    destinations = data.destinations,
                    selectedTriggerIds = data.selectedTriggerIds + triggerId,
                    graceUntil = emptyMap(),
                    isAvailable = vm::isAvailable,
                ),
            )
        } else vm.decide(triggerId)
    }

    fun open(d: Destination) {
        actions.back()
        actions.openDestination(d, data)
    }

    // Nothing to do (e.g. the user paused interruptions a moment ago): get out of the way.
    if (live && (decision is RedirectDecision.None || decision is RedirectDecision.InGracePeriod)) {
        LaunchedEffect(Unit) { actions.back() }
        return
    }

    fun continueAnyway(grace: Int) {
        // At least a minute, or reopening the app would immediately be interrupted again.
        vm.repo.startGracePeriod(triggerId, grace.coerceAtLeast(MIN_CONTINUE_MINUTES))
        actions.back()
        val pkg = trigger?.let { vm.repo.installedApps.installedPackageFor(it) }
        if (pkg != null && trigger.kind == com.example.redirect.data.TriggerKind.APP) vm.repo.installedApps.launch(pkg)
        else vm.toast("This is where $appName would open")
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackBar(onBack = { actions.back() }) {
            if (!live) Pill("Preview", PillTone.Accent, icon = Icons.Outlined.Visibility, modifier = Modifier.padding(end = 12.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!live) Text(
                if (ruleId != null) "Previewing this rule as if it were on right now."
                else "What your rules would do if you opened $appName now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            when (decision) {
                RedirectDecision.None -> NoRule(data, triggerId, appName, onCreate = { actions.back(); actions.editRule(trigger = triggerId) })
                is RedirectDecision.InGracePeriod -> InGrace(appName, decision.until)
                is RedirectDecision.Redirect -> AutoRedirect(data, appName, trigger?.packageName, decision.destination,
                    onOpen = { open(decision.destination) },
                    onContinue = { continueAnyway(decision.rule.gracePeriodMinutes) })
                is RedirectDecision.ShowChoice -> Choice(vm, data, appName, trigger?.packageName, decision.options,
                    onOpen = ::open,
                    onContinue = { continueAnyway(decision.rule.gracePeriodMinutes) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private const val MIN_CONTINUE_MINUTES = 1

@Composable
private fun Arrival(appName: String, packageName: String?) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 0.45f else 1f, tween(900), label = "fade")
    AppIcon(packageName, appName, 56.dp, modifier = Modifier.alpha(alpha))
}

@Composable
private fun AutoRedirect(
    data: AppData, appName: String, packageName: String?, destination: Destination, onOpen: () -> Unit, onContinue: () -> Unit,
) {
    var seconds by remember { mutableIntStateOf(4) }
    var cancelled by remember { mutableStateOf(false) }
    LaunchedEffect(cancelled) {
        if (cancelled) return@LaunchedEffect
        while (seconds > 0) { delay(1000); seconds-- }
        onOpen()
    }
    Arrival(appName, packageName)
    Spacer(Modifier.height(20.dp))
    Text("Instead of $appName…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val book = when (destination.type) {
        DestinationType.CURRENT_BOOK -> data.currentBook
        DestinationType.SPECIFIC_BOOK -> data.books.firstOrNull { it.id == destination.targetId }
        else -> null
    }
    Text(
        when {
            book != null -> "Back to ${book.title}"
            else -> data.describe(destination)
        },
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))
    if (book != null) {
        BookCover(book, Modifier.width(150.dp))
        Spacer(Modifier.height(16.dp))
        if (book.progress > 0f) {
            ReadingProgress(book.progress, Modifier.width(150.dp))
            Spacer(Modifier.height(6.dp))
            Text("${percent(book.progress)} read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        DestinationIcon(destination, 96.dp)
    }
    Spacer(Modifier.height(32.dp))
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(52.dp)) {
        Text(if (cancelled) "Open now" else "Opening in $seconds…", style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    if (!cancelled) {
        TextButton(onClick = { cancelled = true }) { Text("Wait, stay here") }
    } else {
        TextButton(onClick = onContinue) { Text("Continue to $appName", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Choice(
    vm: AppViewModel, data: AppData, appName: String, packageName: String?, options: List<Destination>,
    onOpen: (Destination) -> Unit, onContinue: () -> Unit,
) {
    Arrival(appName, packageName)
    Spacer(Modifier.height(20.dp))
    Text("A moment before $appName", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Text("What would you like to do instead?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    if (options.isEmpty()) {
        Text(
            "None of this rule's alternatives are available right now. Add one in Rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, d ->
            AnimatedVisibility(visible, enter = fadeIn(tween(400, delayMillis = 80 * i)) + slideInVertically(tween(400, delayMillis = 80 * i)) { it / 3 }) {
                OptionCard(data, d) { onOpen(d) }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onContinue) { Text("Continue to $appName", color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun OptionCard(data: AppData, d: Destination, onClick: () -> Unit) {
    val book = when (d.type) {
        DestinationType.CURRENT_BOOK -> data.currentBook
        DestinationType.SPECIFIC_BOOK -> data.books.firstOrNull { it.id == d.targetId }
        else -> null
    }
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (book != null) BookCover(book, Modifier.width(44.dp), elevated = false) else DestinationIcon(d, 44.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        book != null && d.type == DestinationType.CURRENT_BOOK -> "Continue ${book.title}"
                        book != null -> "Read ${book.title}"
                        else -> data.describe(d)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        book != null && book.progress > 0f -> "${percent(book.progress)} read · ${book.author}"
                        book != null -> book.author
                        d.type == DestinationType.EXTERNAL_APP -> "Open the app"
                        d.type == DestinationType.LIBRARY -> "Pick something new"
                        d.type == DestinationType.OFFLINE_ACTIVITY -> "Step away for a bit"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoRule(data: AppData, triggerId: String, appName: String, onCreate: () -> Unit) {
    val now = System.currentTimeMillis()
    val related = data.rules.filter { it.triggerId == triggerId || (it.triggerId == SeedData.TRIGGER_ANY && triggerId in data.selectedTriggerIds) }
    Text("Nothing would happen", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        if (related.isEmpty()) "There's no rule for $appName yet, so it would open normally."
        else "$appName would open normally right now. Here's why:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    related.forEach { rule ->
        val reason = when {
            !rule.enabled -> "switched off"
            rule.pausedUntil != null && rule.pausedUntil > now -> "paused until ${formatTime(rule.pausedUntil)}"
            rule.schedule != null && !RuleEngine.scheduleMatches(rule.schedule, LocalDateTime.now()) -> "only runs ${describeSchedule(rule.schedule)}"
            else -> "not applicable"
        }
        Text("“${rule.name}” is $reason", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
    }
    Spacer(Modifier.height(20.dp))
    OutlinedButton(onClick = onCreate) { Text("Create a rule for $appName") }
}

@Composable
private fun InGrace(appName: String, until: Long) {
    Text("Taking a break from reminders", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        "You chose to continue to $appName recently, so the grace period is running until ${formatTime(until)}.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Simple full-screen prompt for an offline alternative. */
@Composable
fun OfflineActivityScreen(data: AppData, destinationId: String, onDone: () -> Unit) {
    val d = data.destinations.firstOrNull { it.id == destinationId }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(iconFor(d?.icon ?: "spa"), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(28.dp))
        Text("Time to", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(d?.displayName ?: "Take a break", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your phone will be right here when you get back.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).height(52.dp)) { Text("Done") }
    }
}
