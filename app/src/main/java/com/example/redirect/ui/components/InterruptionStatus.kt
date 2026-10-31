package com.example.redirect.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.interrupt.InterruptionService
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.rules.formatTime
import com.example.redirect.ui.theme.LocalExtraColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class InterruptionState { NEEDS_PERMISSION, OFF, PAUSED, ON }

fun interruptionState(data: AppData, serviceEnabled: Boolean, now: Long = System.currentTimeMillis()): InterruptionState {
    val prefs = data.preferences
    return when {
        !serviceEnabled -> InterruptionState.NEEDS_PERMISSION
        !prefs.interruptionsEnabled -> InterruptionState.OFF
        prefs.interruptionsPausedUntil != null && prefs.interruptionsPausedUntil > now -> InterruptionState.PAUSED
        else -> InterruptionState.ON
    }
}

/** Status of app interruptions with the one action that matters right now. */
@Composable
fun InterruptionStatusCard(vm: AppViewModel, data: AppData, modifier: Modifier = Modifier, onSetUp: () -> Unit) {
    val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()
    val state = interruptionState(data, serviceEnabled)
    val extra = LocalExtraColors.current
    val appCount = data.selectedTriggerIds.size
    var pauseMenu by remember { mutableStateOf(false) }

    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (state) {
                    InterruptionState.ON -> extra.success
                    InterruptionState.PAUSED -> extra.warning
                    else -> MaterialTheme.colorScheme.outline
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                Text(
                    when (state) {
                        InterruptionState.NEEDS_PERMISSION -> "Interruptions need one permission"
                        InterruptionState.OFF -> "Interruptions are off"
                        InterruptionState.PAUSED -> "Paused until ${formatTime(data.preferences.interruptionsPausedUntil!!)}"
                        InterruptionState.ON -> "Interruptions are on"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (state) {
                    InterruptionState.NEEDS_PERMISSION -> "Switch on Redirect in Accessibility settings so it can notice when you open a distracting app."
                    InterruptionState.OFF, InterruptionState.PAUSED -> "Distracting apps open normally for now."
                    InterruptionState.ON -> when (appCount) {
                        0 -> "No distracting apps chosen yet."
                        1 -> "Watching 1 app. Opening it brings you back to your book."
                        else -> "Watching $appCount apps. Opening one brings you back to your book."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    InterruptionState.NEEDS_PERMISSION -> Button(onClick = onSetUp) { Text("Set up") }
                    InterruptionState.OFF -> FilledTonalButton(onClick = { vm.repo.setInterruptionsEnabled(true) }) { Text("Turn on") }
                    InterruptionState.PAUSED -> FilledTonalButton(onClick = { vm.repo.resumeInterruptions() }) { Text("Resume now") }
                    InterruptionState.ON -> Box {
                        OutlinedButton(onClick = { pauseMenu = true }) { Text("Pause") }
                        DropdownMenu(pauseMenu, onDismissRequest = { pauseMenu = false }) {
                            pauseOptions().forEach { (label, until) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    pauseMenu = false
                                    vm.repo.pauseInterruptions(until())
                                })
                            }
                            DropdownMenuItem(
                                text = { Text("Turn off", color = MaterialTheme.colorScheme.error) },
                                onClick = { pauseMenu = false; vm.repo.setInterruptionsEnabled(false) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun pauseOptions(): List<Pair<String, () -> Long>> = listOf(
    "For 15 minutes" to { System.currentTimeMillis() + 15 * 60_000L },
    "For 1 hour" to { System.currentTimeMillis() + 60 * 60_000L },
    "Until tomorrow" to {
        LocalDate.now().plusDays(1).atTime(LocalTime.of(6, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    },
)

/**
 * Prominent disclosure and consent for the Accessibility service, as required before sending
 * the user to switch it on. Shown in onboarding and from the "Set up" action.
 */
@Composable
fun AccessibilityDisclosure(vm: AppViewModel, modifier: Modifier = Modifier, onSkip: (() -> Unit)? = null) {
    val context = LocalContext.current
    val enabled by vm.serviceEnabled.collectAsStateWithLifecycle()
    Column(modifier) {
        DisclosurePoint("What it sees", "Only the name of the app that just opened, so Redirect knows when you open one you chose.")
        DisclosurePoint("What it never sees", "The content of your screen, what you type, or anything inside other apps.")
        DisclosurePoint("Where data goes", "Nowhere. Everything stays on this device, and there's no account.")
        DisclosurePoint("How to stop it", "Pause in Redirect any time, or switch it off in Accessibility settings.")
        Spacer(Modifier.height(16.dp))
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(LocalExtraColors.current.success))
                Spacer(Modifier.width(10.dp))
                Text("Permission granted. You're ready.", style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Button(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Agree and open settings")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "In settings, find Redirect (sometimes under “Installed apps” or “Downloaded apps”) and switch it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Switch greyed out? Android limits this for apps installed outside an app store. Open App info, tap ⋮, choose “Allow restricted settings”, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { context.startActivity(InterruptionService.appDetailsIntent(context)) }) { Text("Open App info") }
            }
            if (onSkip != null) {
                TextButton(onClick = onSkip) { Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun DisclosurePoint(title: String, body: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openAccessibilitySettings(context: Context) {
    runCatching { context.startActivity(InterruptionService.accessibilitySettingsIntent()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySetupSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Let Redirect notice distracting apps", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Redirect uses Android's Accessibility service for one thing: noticing when you open an app you chose, so it can offer your book instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AccessibilityDisclosure(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}
