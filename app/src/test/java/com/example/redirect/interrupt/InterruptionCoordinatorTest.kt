package com.example.redirect.interrupt

import com.example.redirect.data.AppData
import com.example.redirect.data.Book
import com.example.redirect.data.BookFile
import com.example.redirect.data.BookSource
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.RedirectMode
import com.example.redirect.data.RedirectRule
import com.example.redirect.data.SeedData
import com.example.redirect.data.UserPreferences
import com.example.redirect.data.isAvailable
import com.example.redirect.interrupt.InterruptionCoordinator.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class InterruptionCoordinatorTest {

    private val own = "com.example.redirect"
    private val tiktok = "com.zhiliaoapp.musically"
    private val tiktokRegional = "com.ss.android.ugc.trill"
    private val instagram = "com.instagram.android"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val minecraft = "com.mojang.minecraftpe"

    private val book = Book(
        id = "b1", title = "Walden", author = "Thoreau", file = BookFile.asset("books/walden.epub"),
        source = BookSource.BUNDLED, createdAt = 0, updatedAt = 0,
    )

    private fun rule(trigger: String = tiktok, dests: List<String> = listOf(SeedData.DEST_CURRENT_BOOK), grace: Int = 0) =
        RedirectRule("r-$trigger", "rule", trigger, RedirectMode.AUTOMATIC, dests, gracePeriodMinutes = grace, createdAt = 0, updatedAt = 0)

    private fun data(
        rules: List<RedirectRule> = listOf(rule()),
        selected: List<String> = listOf(tiktok),
        prefs: UserPreferences = UserPreferences(onboardingComplete = true, currentBookId = "b1"),
        graceUntil: Map<String, Long> = emptyMap(),
    ) = AppData(
        preferences = prefs,
        books = listOf(book),
        selectedTriggerIds = selected,
        triggerApps = SeedData.triggerCatalog,
        destinations = SeedData.defaultDestinations,
        rules = rules,
        graceUntil = graceUntil,
    )

    private var clock = 1_000_000L
    private fun env() = InterruptionCoordinator.Env(
        nowMillis = clock,
        now = LocalDateTime.of(2026, 10, 26, 12, 0),
        protectedPackages = setOf(launcher, "com.android.settings"),
        transientPackages = setOf("com.android.systemui"),
        isInstalled = { true },
    )

    private val c = InterruptionCoordinator(own)

    @Test fun openingDistractingAppInterrupts() {
        c.onWindowChanged(launcher, data(), env())
        assertEquals(Outcome.Interrupt(tiktok, tiktok), c.onWindowChanged(tiktok, data(), env()))
    }

    @Test fun regionalPackageVariantMapsToSameTrigger() {
        assertEquals(Outcome.Interrupt(tiktok, tiktokRegional), c.onWindowChanged(tiktokRegional, data(), env()))
    }

    @Test fun screensInsideTheSameAppDoNotInterruptWhenGraceEndsMidSession() {
        val grace = data(graceUntil = mapOf(tiktok to clock + 60_000))
        assertTrue(c.onWindowChanged(tiktok, grace, env()) is Outcome.Allowed)
        clock += 120_000 // grace over, user still scrolling
        assertEquals(Outcome.Ignore, c.onWindowChanged(tiktok, data(), env()))
    }

    @Test fun notificationShadeDoesNotCountAsLeaving() {
        val grace = data(graceUntil = mapOf(tiktok to clock + 60_000))
        assertTrue(c.onWindowChanged(tiktok, grace, env()) is Outcome.Allowed)
        clock += 120_000
        c.onWindowChanged("com.android.systemui", data(), env())
        assertEquals(Outcome.Ignore, c.onWindowChanged(tiktok, data(), env()))
    }

    @Test fun reopeningAfterLeavingInterruptsAgain() {
        assertTrue(c.onWindowChanged(tiktok, data(), env()) is Outcome.Interrupt)
        c.onWindowChanged(own, data(), env())
        clock += 60_000
        assertTrue(c.onWindowChanged(tiktok, data(), env()) is Outcome.Interrupt)
    }

    @Test fun appLandingOnTopRightAfterInterruptionIsReassertedAFewTimes() {
        // e.g. splash activity → main activity starting on top of the intervention
        assertTrue(c.onWindowChanged(tiktok, data(), env()) is Outcome.Interrupt)
        repeat(InterruptionCoordinator.MAX_REASSERTS) {
            clock += 200
            assertTrue("re-assert ${it + 1}", c.onWindowChanged(tiktok, data(), env()) is Outcome.Interrupt)
        }
        clock += 200
        assertTrue("never fight forever", c.onWindowChanged(tiktok, data(), env()) is Outcome.Allowed)
    }

    @Test fun continuingToTheAppIsNotFought() {
        assertTrue(c.onWindowChanged(tiktok, data(), env()) is Outcome.Interrupt)
        clock += 300
        val continued = data(graceUntil = mapOf(tiktok to clock + 60_000))
        assertTrue(c.onWindowChanged(tiktok, continued, env()) is Outcome.Allowed)
    }

    @Test fun ownAppLauncherAndSettingsAreNeverInterrupted() {
        val everything = data(rules = listOf(rule(SeedData.TRIGGER_ANY)), selected = listOf(launcher, own))
        assertEquals(Outcome.Ignore, c.onWindowChanged(own, everything, env()))
        assertEquals(Outcome.Ignore, c.onWindowChanged(launcher, everything, env()))
        assertEquals(Outcome.Ignore, c.onWindowChanged("com.android.settings", everything, env()))
    }

    @Test fun unrelatedAppsAreIgnored() {
        assertEquals(Outcome.Ignore, c.onWindowChanged("com.android.chrome", data(), env()))
    }

    @Test fun pausedOrOffOrGraceAllowsTheApp() {
        val paused = data(prefs = UserPreferences(onboardingComplete = true, interruptionsPausedUntil = clock + 60_000))
        assertTrue(c.onWindowChanged(tiktok, paused, env()) is Outcome.Allowed)

        c.reset()
        val off = data(prefs = UserPreferences(onboardingComplete = true, interruptionsEnabled = false))
        assertTrue(c.onWindowChanged(tiktok, off, env()) is Outcome.Allowed)

        c.reset()
        val grace = data(graceUntil = mapOf(tiktok to clock + 60_000))
        assertTrue(c.onWindowChanged(tiktok, grace, env()) is Outcome.Allowed)
    }

    @Test fun expiredPauseInterruptsAgain() {
        val expired = data(prefs = UserPreferences(onboardingComplete = true, currentBookId = "b1", interruptionsPausedUntil = clock - 1))
        assertTrue(c.onWindowChanged(tiktok, expired, env()) is Outcome.Interrupt)
    }

    @Test fun nothingHappensBeforeOnboarding() {
        val fresh = data(prefs = UserPreferences(onboardingComplete = false))
        assertEquals(Outcome.Ignore, c.onWindowChanged(tiktok, fresh, env()))
    }

    @Test fun appWithoutAnyRuleOpensNormally() {
        assertTrue(c.onWindowChanged(instagram, data(selected = listOf(tiktok, instagram)), env()) is Outcome.Allowed)
    }

    @Test fun anySelectedRuleCoversEverySelectedApp() {
        val d = data(rules = listOf(rule(SeedData.TRIGGER_ANY)), selected = listOf(tiktok, instagram))
        assertTrue(c.onWindowChanged(instagram, d, env()) is Outcome.Interrupt)
    }

    @Test fun externalAppThatIsItselfDistractingIsNeverATarget() {
        val toInstagram = Destination("dest:ig", DestinationType.EXTERNAL_APP, instagram, "Instagram")
        val toMinecraft = Destination("dest:mc", DestinationType.EXTERNAL_APP, minecraft, "Minecraft")
        val d = data(selected = listOf(tiktok, instagram)).let {
            it.copy(destinations = it.destinations + toInstagram + toMinecraft)
        }
        assertFalse(d.isAvailable(toInstagram) { true })
        assertTrue(d.isAvailable(toMinecraft) { true })
        assertFalse("uninstalled target", d.isAvailable(toMinecraft) { false })
    }
}
