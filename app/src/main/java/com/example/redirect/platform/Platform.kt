package com.example.redirect.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import com.example.redirect.data.AppData
import com.example.redirect.data.TriggerApp
import com.example.redirect.interrupt.InterruptionService

data class InstalledApp(val packageName: String, val label: String)

/**
 * Real, working access to installed apps. Visibility is granted by the launcher
 * <queries> entry in the manifest, so only apps with a launcher icon are listed.
 */
class InstalledApps(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun launchable(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    /** First installed package among an app's known package names. */
    fun installedPackageFor(trigger: TriggerApp): String? =
        (listOfNotNull(trigger.packageName) + trigger.altPackageNames).firstOrNull { isInstalled(it) }

    fun icon(packageName: String): Drawable? =
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    /** Launches an installed app, optionally through a deep link. Returns false if it couldn't be opened. */
    fun launch(packageName: String, deepLink: String? = null): Boolean {
        val intent = deepLink?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(packageName) }
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

/**
 * Boundary between the app and the native interruption layer. The rest of the app only talks
 * to this interface, so the detection mechanism can change without touching rules or screens.
 */
interface RedirectionBridge {
    /** True once a monitor is actually running and able to intercept launches. */
    val isMonitoringActive: Boolean

    /** Called whenever rules, triggers or destinations change so the monitor can re-sync. */
    fun onConfigurationChanged(data: AppData)
}

/** Backed by [InterruptionService], which reads the live store on every event, so no re-sync is needed. */
object AccessibilityBridge : RedirectionBridge {
    override val isMonitoringActive: Boolean get() = InterruptionService.running
    override fun onConfigurationChanged(data: AppData) = Unit
}

enum class CapabilityStatus { WORKS_NOW, PLANNED, NOT_SUPPORTED }

data class Capability(val title: String, val detail: String, val status: CapabilityStatus)

object PlatformCapabilities {
    val all = listOf(
        Capability(
            "Built-in reader and library",
            "Read included books, free Project Gutenberg downloads and EPUBs you import. Progress, bookmarks and settings stay on this device.",
            CapabilityStatus.WORKS_NOW,
        ),
        Capability(
            "Noticing when you open a distracting app",
            "Uses an Android Accessibility service that only sees which app is in front. Needs to be switched on once in Accessibility settings.",
            CapabilityStatus.WORKS_NOW,
        ),
        Capability(
            "Redirecting to your book or another app",
            "When a rule fires, Redirect comes to the front with your book, your choices, or the app you picked instead.",
            CapabilityStatus.WORKS_NOW,
        ),
        Capability(
            "Rules and alternatives",
            "Create, schedule, pause and prioritise rules. Preview exactly what each rule would do.",
            CapabilityStatus.WORKS_NOW,
        ),
        Capability(
            "Detecting Reels, Shorts and other in-app sections",
            "Android doesn't tell other apps which screen an app is showing without reading its content, which Redirect deliberately doesn't do. Rules for these are saved but won't fire.",
            CapabilityStatus.NOT_SUPPORTED,
        ),
    )
}
