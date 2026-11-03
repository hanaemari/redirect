package com.example.redirect.interrupt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.example.redirect.BuildConfig
import com.example.redirect.MainActivity
import com.example.redirect.RedirectApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/** Whether interruptions can actually work right now. */
enum class AccessStatus {
    /** Service switched on and running. */
    ON,
    /** Not switched on in Accessibility settings (never granted, or revoked). */
    OFF,
    /** Switched on, but Android stopped it (usually after a crash); it must be switched off and on. */
    STOPPED,
}

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
        _running.value = true
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
        _running.value = false
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

        private val _running = MutableStateFlow(false)

        /** True while the system has the service bound in this process. */
        val runningState: StateFlow<Boolean> = _running.asStateFlow()
        val running: Boolean get() = _running.value

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

        /**
         * The real state, not a saved flag. "Switched on" in Settings is not enough: after a crash
         * Android keeps the switch on but stops the service, so also ask whether it is running.
         */
        fun accessStatus(context: Context): AccessStatus {
            if (!isEnabled(context)) return AccessStatus.OFF
            if (running) return AccessStatus.ON
            val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
            val bound = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo?.serviceInfo?.let { si -> si.packageName == context.packageName && si.name == InterruptionService::class.java.name } == true
            }
            return if (bound) AccessStatus.ON else AccessStatus.STOPPED
        }

        /** Whether the user has switched the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, InterruptionService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Accessibility settings. Apps can't open one service's own page (that action is system-only),
         * so this opens the list and asks Settings to highlight Redirect, which many devices honor
         * (unknown extras are ignored elsewhere).
         */
        fun accessibilitySettingsIntent(context: Context): Intent {
            val component = ComponentName(context, InterruptionService::class.java).flattenToString()
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
                .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component) })
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

        /** App info, where "Allow restricted settings" lives for sideloaded apps on Android 13+. */
        fun appDetailsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
