package com.example.redirect.interrupt

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.example.redirect.BuildConfig
import com.example.redirect.MainActivity
import com.example.redirect.RedirectApp
import java.time.LocalDateTime

/**
 * Notices when a distracting app comes to the front and opens the intervention screen.
 *
 * Why an AccessibilityService: window-change events arrive as they happen (no polling, so very
 * little battery use), and Android allows a bound accessibility service to start an activity from
 * the background, which a Usage Access poller cannot do on Android 10+ without an extra
 * "display over other apps" permission. The service is configured to receive only window-state
 * changes and cannot read screen content (see res/xml/interruption_service.xml).
 */
class InterruptionService : AccessibilityService() {

    private lateinit var coordinator: InterruptionCoordinator
    private var protectedPackages: Set<String> = emptySet()
    private var transientPackages: Set<String> = emptySet()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = coordinator.reset()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator = InterruptionCoordinator(packageName)
        refreshSystemPackages()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        running = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (BuildConfig.DEBUG) Log.v(TAG, "window: $pkg")
        val app = application as RedirectApp
        // The store loads asynchronously at process start; skip events until it is ready.
        val data = app.repository.store.data.value ?: return

        val outcome = coordinator.onWindowChanged(
            pkg, data,
            InterruptionCoordinator.Env(
                nowMillis = System.currentTimeMillis(),
                now = LocalDateTime.now(),
                protectedPackages = protectedPackages,
                transientPackages = transientPackages,
                isInstalled = app.repository.installedApps::isInstalled,
            ),
        )
        if (BuildConfig.DEBUG && outcome != InterruptionCoordinator.Outcome.Ignore) Log.d(TAG, "$pkg -> $outcome")
        if (outcome is InterruptionCoordinator.Outcome.Interrupt) {
            Log.i(TAG, "Interrupting ${outcome.packageName} (${outcome.triggerId})")
            startActivity(MainActivity.interruptionIntent(this, outcome.triggerId))
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        runCatching { unregisterReceiver(screenOffReceiver) }
        return super.onUnbind(intent)
    }

    /** Launcher, dialer, keyboard and settings are never interrupted. */
    private fun refreshSystemPackages() {
        val pm = packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        val dialer = runCatching { (getSystemService(TELECOM_SERVICE) as TelecomManager).defaultDialerPackage }.getOrNull()
        val keyboards = runCatching {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }
        }.getOrDefault(emptyList())
        protectedPackages = (homes + listOfNotNull(dialer) + ALWAYS_PROTECTED).toSet()
        transientPackages = (keyboards + TRANSIENT).toSet()
    }

    companion object {
        private const val TAG = "Interruption"

        /** True while the system has the service bound in this process. */
        @Volatile
        var running: Boolean = false
            private set

        private val ALWAYS_PROTECTED = setOf(
            "android",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
        )
        private val TRANSIENT = setOf("com.android.systemui")

        /** Whether the user has switched the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, InterruptionService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun accessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** App info, where "Allow restricted settings" lives for sideloaded apps on Android 13+. */
        fun appDetailsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
