package com.example.redirect.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.redirect.RedirectApp
import com.example.redirect.data.AppData
import com.example.redirect.data.AppRepository
import com.example.redirect.data.Book
import com.example.redirect.data.Destination
import com.example.redirect.data.FreeBook
import com.example.redirect.data.ImportResult
import com.example.redirect.data.isAvailable
import com.example.redirect.engine.RedirectDecision
import com.example.redirect.engine.RuleEngine
import com.example.redirect.interrupt.AccessStatus
import com.example.redirect.interrupt.InterruptionService
import com.example.redirect.platform.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Shared app-wide state holder. Screens read [data] and call [repo] for simple writes. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: AppRepository = (app as RedirectApp).repository
    val data: StateFlow<AppData?> = repo.store.data

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    val installedApps: StateFlow<List<InstalledApp>?> = _installedApps.asStateFlow()

    private val _pendingInterruption = MutableStateFlow<String?>(null)
    /** Trigger id of an interruption the service asked us to show; consumed by navigation. */
    val pendingInterruption: StateFlow<String?> = _pendingInterruption.asStateFlow()

    fun showInterruption(triggerId: String) {
        _pendingInterruption.value = triggerId
    }

    fun interruptionShown() {
        _pendingInterruption.value = null
    }

    // Optimistic start: a just-started process may not have its service re-bound yet (see refresh).
    private val _accessStatus = MutableStateFlow(if (InterruptionService.isEnabled(app)) AccessStatus.ON else AccessStatus.OFF)
    /** Actual Accessibility state of the interruption service, kept live (see init). */
    val accessStatus: StateFlow<AccessStatus> = _accessStatus.asStateFlow()

    /** Whether interruptions can work: the service is switched on and running. */
    val serviceEnabled: StateFlow<Boolean> = accessStatus.map { it == AccessStatus.ON }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _accessStatus.value == AccessStatus.ON)

    private var recheck: Job? = null

    fun refreshInterruptionStatus() {
        recheck?.cancel()
        val status = InterruptionService.accessStatus(getApplication())
        if (status != AccessStatus.STOPPED) {
            publishAccessStatus(status)
            return
        }
        // Right after it's switched on (or the app starts) Android may still be binding the
        // service, so only report "stopped" if it stays that way.
        recheck = viewModelScope.launch {
            delay(3_000)
            publishAccessStatus(InterruptionService.accessStatus(getApplication()))
        }
    }

    private fun publishAccessStatus(status: AccessStatus) {
        _accessStatus.value = status
        // Turning it on means the user went through the disclosure; later prompts can go straight to settings.
        val prefs = repo.store.data.value?.preferences
        if (status == AccessStatus.ON && prefs != null && !prefs.accessibilityDisclosureAccepted) {
            repo.updatePreferences { it.copy(accessibilityDisclosureAccepted = true) }
        }
    }

    // Catch changes made while the app is open too (e.g. revoked from quick settings), not only on resume.
    private val accessibilityObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshInterruptionStatus()
    }

    init {
        val resolver = app.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, accessibilityObserver)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, accessibilityObserver)
        // The service connecting or disconnecting in this process.
        viewModelScope.launch { InterruptionService.runningState.collect { refreshInterruptionStatus() } }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(accessibilityObserver)
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    fun loadInstalledApps() {
        if (_installedApps.value != null) return
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) { repo.installedApps.launchable() }
        }
    }

    private val _downloading = MutableStateFlow<Set<Int>>(emptySet())
    /** Gutenberg ids currently downloading. Lives here so downloads survive leaving Discover. */
    val downloading: StateFlow<Set<Int>> = _downloading.asStateFlow()

    fun downloadFreeBook(book: FreeBook, onSuccess: (Book) -> Unit = {}) {
        if (book.gutenbergId in _downloading.value) return
        _downloading.update { it + book.gutenbergId }
        viewModelScope.launch {
            val result = repo.downloadFreeBook(book)
            _downloading.update { it - book.gutenbergId }
            when (result) {
                is ImportResult.Success -> {
                    toast("Added “${result.book.title}” to your library")
                    onSuccess(result.book)
                }
                is ImportResult.Failure -> toast(result.message)
            }
        }
    }

    fun isInstalled(packageName: String) = repo.installedApps.isInstalled(packageName)

    fun isAvailable(d: Destination): Boolean = repo.store.current.isAvailable(d, ::isInstalled)

    /** Imports an EPUB and reports the result. [onSuccess] receives the new book. */
    fun importEpub(uri: Uri, attachTo: String? = null, onSuccess: (Book) -> Unit = {}) {
        viewModelScope.launch {
            _importing.value = true
            val result = if (attachTo != null) repo.attachEpub(attachTo, uri) else repo.importEpub(uri)
            _importing.value = false
            when (result) {
                is ImportResult.Success -> {
                    toast(if (attachTo != null) "EPUB attached. Ready to read." else "Added “${result.book.title}” to your library")
                    onSuccess(result.book)
                }
                is ImportResult.Failure -> toast(result.message)
            }
        }
    }

    /** Runs the rule engine exactly as the interruption service does. */
    fun decide(triggerId: String, at: LocalDateTime = LocalDateTime.now()): RedirectDecision {
        val d = repo.store.current
        return RuleEngine.decide(
            triggerId, at, System.currentTimeMillis(),
            RuleEngine.Input(d.rules, d.destinations, d.selectedTriggerIds, d.graceUntil, ::isAvailable),
        )
    }
}
