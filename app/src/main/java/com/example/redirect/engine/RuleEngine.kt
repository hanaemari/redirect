package com.example.redirect.engine

import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectRule
import com.example.redirect.data.Schedule
import com.example.redirect.data.SeedData
import java.time.LocalDateTime

/** What should happen when a trigger fires. */
sealed interface RedirectDecision {
    /** No enabled rule applies right now. */
    data object None : RedirectDecision

    /** A rule applies, but the user recently chose to continue and the grace period is running. */
    data class InGracePeriod(val rule: RedirectRule, val until: Long) : RedirectDecision

    /** Open [destination] straight away. */
    data class Redirect(val rule: RedirectRule, val destination: Destination) : RedirectDecision

    /** Show the choice screen with these options (in order). */
    data class ShowChoice(val rule: RedirectRule, val options: List<Destination>) : RedirectDecision
}

/**
 * Pure rule resolution. The native monitoring layer (future) and the in-app preview
 * both call [decide], so behaviour is identical in both places.
 */
object RuleEngine {

    data class Input(
        val rules: List<RedirectRule>,
        val destinations: List<Destination>,
        val selectedTriggerIds: List<String>,
        val graceUntil: Map<String, Long>,
        /** Whether a destination can be opened right now (book has a file, app installed...). */
        val isAvailable: (Destination) -> Boolean,
    )

    fun decide(triggerId: String, at: LocalDateTime, nowMillis: Long, input: Input): RedirectDecision {
        val rule = applicableRules(triggerId, at, nowMillis, input).firstOrNull() ?: return RedirectDecision.None

        val grace = input.graceUntil[triggerId]
        if (grace != null && grace > nowMillis) return RedirectDecision.InGracePeriod(rule, grace)

        val byId = input.destinations.associateBy { it.id }
        val listed = rule.destinationIds.mapNotNull { byId[it] }

        return when (rule.mode) {
            RedirectMode.AUTOMATIC -> {
                val target = listed.firstOrNull { input.isAvailable(it) }
                when {
                    target == null -> RedirectDecision.ShowChoice(rule, choiceOptions(rule, listed, input))
                    target.type == DestinationType.CHOICE_SCREEN ->
                        RedirectDecision.ShowChoice(rule, choiceOptions(rule.copy(includeSavedAlternatives = true), emptyList(), input))
                    else -> RedirectDecision.Redirect(rule, target)
                }
            }
            RedirectMode.CHOICE -> RedirectDecision.ShowChoice(rule, choiceOptions(rule, listed, input))
        }
    }

    /** Rules that would fire for [triggerId] at [at], best match first. */
    fun applicableRules(triggerId: String, at: LocalDateTime, nowMillis: Long, input: Input): List<RedirectRule> =
        input.rules
            .filter { isActive(it, nowMillis) }
            .filter { matchesTrigger(it, triggerId, input.selectedTriggerIds) }
            .filter { it.schedule == null || scheduleMatches(it.schedule, at) }
            .sortedWith(
                compareByDescending<RedirectRule> { it.priority }
                    // Prefer a rule written for this exact app over the catch-all.
                    .thenByDescending { it.triggerId == triggerId }
                    // Prefer a scheduled rule over an always-on one.
                    .thenByDescending { it.schedule != null && !(it.schedule.isAllDay && it.schedule.days.size == 7) }
                    .thenByDescending { it.updatedAt }
            )

    fun isActive(rule: RedirectRule, nowMillis: Long): Boolean =
        rule.enabled && (rule.pausedUntil == null || rule.pausedUntil <= nowMillis)

    fun matchesTrigger(rule: RedirectRule, triggerId: String, selected: List<String>): Boolean =
        rule.triggerId == triggerId || (rule.triggerId == SeedData.TRIGGER_ANY && triggerId in selected)

    fun scheduleMatches(schedule: Schedule, at: LocalDateTime): Boolean {
        val day = at.dayOfWeek.value
        val minute = at.hour * 60 + at.minute
        if (schedule.isAllDay) return day in schedule.days
        return if (schedule.startMinute < schedule.endMinute) {
            day in schedule.days && minute >= schedule.startMinute && minute < schedule.endMinute
        } else {
            // Overnight window, e.g. 22:00-02:00. The early-morning part belongs to the previous day.
            val previousDay = if (day == 1) 7 else day - 1
            (day in schedule.days && minute >= schedule.startMinute) ||
                (previousDay in schedule.days && minute < schedule.endMinute)
        }
    }

    private fun choiceOptions(rule: RedirectRule, listed: List<Destination>, input: Input): List<Destination> {
        val extra = if (rule.includeSavedAlternatives) input.destinations else emptyList()
        return (listed + extra)
            .distinctBy { it.id }
            .filter { it.type != DestinationType.CHOICE_SCREEN }
            .filter { input.isAvailable(it) }
    }
}
