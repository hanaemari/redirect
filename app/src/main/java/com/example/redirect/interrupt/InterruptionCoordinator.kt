package com.example.redirect.interrupt

import com.example.redirect.data.AppData
import com.example.redirect.data.TriggerKind
import com.example.redirect.data.isAvailable
import com.example.redirect.engine.RedirectDecision
import com.example.redirect.engine.RuleEngine
import java.time.LocalDateTime

/**
 * Decides whether a foreground-app change should interrupt the user. Pure (no Android types),
 * so it is unit tested; [InterruptionService] feeds it events and carries out the result.
 *
 * Flow: app comes to the front → is it a distracting app? → [RuleEngine.decide] → interrupt or not.
 * Where the user is sent is resolved later by the intervention screen, using the same engine.
 */
class InterruptionCoordinator(
    private val ownPackage: String,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MS,
) {
    /** Environment facts the coordinator can't know by itself. */
    data class Env(
        val nowMillis: Long,
        val now: LocalDateTime,
        /** Packages that must never be interrupted (launcher, dialer, settings…). */
        val protectedPackages: Set<String>,
        /** Packages whose windows pass over the current app without replacing it (shade, keyboard). */
        val transientPackages: Set<String>,
        val isInstalled: (String) -> Boolean,
    )

    sealed interface Outcome {
        /** Not a distracting app, or nothing changed. */
        data object Ignore : Outcome
        /** A distracting app opened, but interruptions are paused, in grace, or no rule applies. */
        data class Allowed(val triggerId: String, val reason: String) : Outcome
        /** Show the intervention for [triggerId]. */
        data class Interrupt(val triggerId: String, val packageName: String) : Outcome
    }

    private var foregroundPackage: String? = null
    private val lastInterruptAt = mutableMapOf<String, Long>()
    private val reasserts = mutableMapOf<String, Int>()

    fun onWindowChanged(packageName: String, data: AppData, env: Env): Outcome {
        if (packageName in env.transientPackages) return Outcome.Ignore
        // Only act when the user moves into an app, not on every screen change inside it.
        if (packageName == foregroundPackage) return Outcome.Ignore
        foregroundPackage = packageName

        if (packageName == ownPackage || packageName in env.protectedPackages) return Outcome.Ignore
        if (!data.preferences.onboardingComplete) return Outcome.Ignore

        val triggerId = triggerFor(packageName, data) ?: return Outcome.Ignore

        if (!data.preferences.interruptionsEnabled) return Outcome.Allowed(triggerId, "interruptions are off")
        val pausedUntil = data.preferences.interruptionsPausedUntil
        if (pausedUntil != null && pausedUntil > env.nowMillis) return Outcome.Allowed(triggerId, "interruptions are paused")

        val decision = RuleEngine.decide(
            triggerId, env.now, env.nowMillis,
            RuleEngine.Input(
                data.rules, data.destinations, data.selectedTriggerIds, data.graceUntil,
                isAvailable = { d -> data.isAvailable(d, env.isInstalled) },
            ),
        )
        return when (decision) {
            RedirectDecision.None -> Outcome.Allowed(triggerId, "no rule applies")
            is RedirectDecision.InGracePeriod -> Outcome.Allowed(triggerId, "grace period")
            is RedirectDecision.Redirect, is RedirectDecision.ShowChoice -> {
                val last = lastInterruptAt[triggerId]
                if (last != null && env.nowMillis - last < cooldownMillis) {
                    // Apps often start a second activity (splash → main) right after ours and land on
                    // top of it. Bring the intervention back a few times, but never fight forever.
                    val count = reasserts[triggerId] ?: 0
                    if (count >= MAX_REASSERTS) return Outcome.Allowed(triggerId, "gave up re-asserting")
                    reasserts[triggerId] = count + 1
                } else {
                    lastInterruptAt[triggerId] = env.nowMillis
                    reasserts[triggerId] = 0
                }
                // We expect to be in front now; the app reappearing counts as a new arrival.
                foregroundPackage = ownPackage
                Outcome.Interrupt(triggerId, packageName)
            }
        }
    }

    /** Forget the current app, e.g. when the screen turns off, so the next open counts as new. */
    fun reset() {
        foregroundPackage = null
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 2_500L
        const val MAX_REASSERTS = 3

        /**
         * The trigger for an app package. An app chosen by the user (in [AppData.selectedTriggerIds])
         * wins; otherwise any trigger with a rule written for it explicitly.
         */
        fun triggerFor(packageName: String, data: AppData): String? {
            val candidates = data.triggerApps.filter {
                it.kind == TriggerKind.APP && (it.packageName == packageName || packageName in it.altPackageNames)
            }
            if (candidates.isEmpty()) return null
            return candidates.firstOrNull { it.id in data.selectedTriggerIds }?.id
                ?: candidates.firstOrNull { c -> data.rules.any { it.triggerId == c.id } }?.id
        }
    }
}
