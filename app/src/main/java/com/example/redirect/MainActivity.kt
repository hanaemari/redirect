package com.example.redirect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.redirect.data.AppTheme
import com.example.redirect.ui.AppNavigation
import com.example.redirect.ui.AppViewModel
import com.example.redirect.ui.theme.RedirectTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val loaded = data
            RedirectTheme(loaded?.preferences?.appTheme ?: AppTheme.SYSTEM) {
                if (loaded == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    AppNavigation(vm, loaded)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshInterruptionStatus()
    }

    private fun handleIntent(intent: Intent?) {
        val trigger = intent?.getStringExtra(EXTRA_INTERRUPTED_TRIGGER) ?: return
        vm.showInterruption(trigger)
        // Don't replay the interruption if the activity is recreated from this intent.
        intent.removeExtra(EXTRA_INTERRUPTED_TRIGGER)
    }

    override fun onStop() {
        super.onStop()
        val app = application as RedirectApp
        app.appScope.launch { app.repository.store.flush() }
    }

    companion object {
        private const val EXTRA_INTERRUPTED_TRIGGER = "interrupted_trigger"

        /** Opens the intervention for [triggerId] on top of whatever the user was doing in this app. */
        fun interruptionIntent(context: Context, triggerId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_INTERRUPTED_TRIGGER, triggerId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
