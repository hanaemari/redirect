package com.example.redirect.ui

import android.app.Application
import android.net.Uri
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
import com.example.redirect.interrupt.InterruptionService
import com.example.redirect.platform.InstalledApp
import kotlinx.coroutines.Dispatchers
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

    private val _serviceEnabled = MutableStateFlow(InterruptionService.isEnabled(app))
    /** Whether the interruption service is switched on in Accessibility settings. */
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    fun refreshInterruptionStatus() {
        _serviceEnabled.value = InterruptionService.isEnabled(getApplication())
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
