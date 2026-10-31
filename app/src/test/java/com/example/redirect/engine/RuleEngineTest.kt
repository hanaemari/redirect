package com.example.redirect.engine

import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectRule
import com.example.redirect.data.Schedule
import com.example.redirect.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RuleEngineTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val instagram = "com.instagram.android"
    private val book = Destination(SeedData.DEST_CURRENT_BOOK, DestinationType.CURRENT_BOOK, displayName = "Book")
    private val library = Destination(SeedData.DEST_LIBRARY, DestinationType.LIBRARY, displayName = "Library")
    private val game = Destination("dest:game", DestinationType.EXTERNAL_APP, "com.mojang.minecraftpe", "Minecraft")
    private val walk = Destination("dest:walk", DestinationType.OFFLINE_ACTIVITY, displayName = "Walk")

    // Monday 2026-10-26
    private val mondayNoon = LocalDateTime.of(2026, 10, 26, 12, 0)
    private val saturdayNoon = LocalDateTime.of(2026, 10, 31, 12, 0)
    private val now = 1_000_000L

    private fun rule(
        id: String, trigger: String = tiktok, mode: RedirectMode = RedirectMode.AUTOMATIC,
        dests: List<String> = listOf(book.id), schedule: Schedule? = null, priority: Int = 0,
        enabled: Boolean = true, pausedUntil: Long? = null, includeSaved: Boolean = false, updatedAt: Long = 0,
    ) = RedirectRule(id, id, trigger, mode, dests, includeSaved, schedule, enabled, pausedUntil, 0, priority, 0, updatedAt)

    private fun input(
        vararg rules: RedirectRule,
        available: (Destination) -> Boolean = { true },
        grace: Map<String, Long> = emptyMap(),
    ) = RuleEngine.Input(rules.toList(), listOf(book, library, game, walk), listOf(tiktok, instagram), grace, available)

    @Test fun noRulesMeansNothingHappens() {
        assertEquals(RedirectDecision.None, RuleEngine.decide(tiktok, mondayNoon, now, input()))
    }

    @Test fun automaticRuleRedirectsToFirstDestination() {
        val d = RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a")))
        assertTrue(d is RedirectDecision.Redirect)
        assertEquals(book, (d as RedirectDecision.Redirect).destination)
    }

    @Test fun automaticFallsBackToNextAvailableDestination() {
        val d = RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a", dests = listOf(game.id, book.id)), available = { it != game }))
        assertEquals(book, (d as RedirectDecision.Redirect).destination)
    }

    @Test fun automaticWithNothingAvailableShowsChoice() {
        val d = RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a", dests = listOf(game.id)), available = { it != game }))
        assertTrue(d is RedirectDecision.ShowChoice)
    }

    @Test fun choiceRuleOffersListedAndSavedAlternatives() {
        val r = rule("a", mode = RedirectMode.CHOICE, dests = listOf(book.id), includeSaved = true)
        val d = RuleEngine.decide(tiktok, mondayNoon, now, input(r, available = { it != game })) as RedirectDecision.ShowChoice
        assertEquals(listOf(book, library, walk), d.options)
    }

    @Test fun disabledAndPausedRulesDoNotFire() {
        assertEquals(RedirectDecision.None, RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a", enabled = false))))
        assertEquals(RedirectDecision.None, RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a", pausedUntil = now + 1))))
        assertTrue(RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a", pausedUntil = now - 1))) is RedirectDecision.Redirect)
    }

    @Test fun weekdayAndWeekendRulesPickTheRightOne() {
        val weekday = rule("weekday", schedule = Schedule(Schedule.WEEKDAYS))
        val weekend = rule("weekend", mode = RedirectMode.CHOICE, schedule = Schedule(Schedule.WEEKEND))
        assertEquals("weekday", (RuleEngine.decide(tiktok, mondayNoon, now, input(weekday, weekend)) as RedirectDecision.Redirect).rule.id)
        assertEquals("weekend", (RuleEngine.decide(tiktok, saturdayNoon, now, input(weekday, weekend)) as RedirectDecision.ShowChoice).rule.id)
    }

    @Test fun higherPriorityWins() {
        val low = rule("low", dests = listOf(library.id))
        val high = rule("high", priority = 2)
        assertEquals("high", (RuleEngine.decide(tiktok, mondayNoon, now, input(low, high)) as RedirectDecision.Redirect).rule.id)
    }

    @Test fun specificTriggerBeatsCatchAllAtSamePriority() {
        val any = rule("any", trigger = SeedData.TRIGGER_ANY, dests = listOf(library.id), updatedAt = 10)
        val specific = rule("specific")
        assertEquals("specific", RuleEngine.applicableRules(tiktok, mondayNoon, now, input(any, specific)).first().id)
    }

    @Test fun catchAllOnlyCoversSelectedApps() {
        val any = rule("any", trigger = SeedData.TRIGGER_ANY)
        assertTrue(RuleEngine.decide(instagram, mondayNoon, now, input(any)) is RedirectDecision.Redirect)
        assertEquals(RedirectDecision.None, RuleEngine.decide("com.reddit.frontpage", mondayNoon, now, input(any)))
    }

    @Test fun gracePeriodSuppressesRedirect() {
        val d = RuleEngine.decide(tiktok, mondayNoon, now, input(rule("a"), grace = mapOf(tiktok to now + 60_000)))
        assertTrue(d is RedirectDecision.InGracePeriod)
    }

    @Test fun timeWindowsIncludingOvernight() {
        val evening = Schedule(Schedule.ALL_DAYS, 21 * 60, 23 * 60)
        assertTrue(RuleEngine.scheduleMatches(evening, mondayNoon.withHour(21).withMinute(30)))
        assertFalse(RuleEngine.scheduleMatches(evening, mondayNoon.withHour(23)))

        // Friday 22:00 – 02:00 should cover early Saturday, but not early Friday.
        val overnight = Schedule(setOf(5), 22 * 60, 2 * 60)
        val friday = LocalDateTime.of(2026, 10, 30, 23, 0)
        assertTrue(RuleEngine.scheduleMatches(overnight, friday))
        assertTrue(RuleEngine.scheduleMatches(overnight, friday.plusHours(2)))
        assertFalse(RuleEngine.scheduleMatches(overnight, friday.withHour(1)))
    }
}
