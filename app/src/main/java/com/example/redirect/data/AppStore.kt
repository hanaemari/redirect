package com.example.redirect.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single-document JSON store. The data set is small (a library and a handful of rules),
 * so the whole document is held in memory and written atomically after changes.
 *
 * If accounts are added, [file] becomes per-account (e.g. files/users/<id>/data.json).
 */
class AppStore(private val file: File, private val scope: CoroutineScope) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val writeLock = Mutex()
    private val _data = MutableStateFlow<AppData?>(null)

    /** Null until [load] completes. */
    val data: StateFlow<AppData?> = _data.asStateFlow()

    val current: AppData get() = _data.value ?: AppData()

    @OptIn(FlowPreview::class)
    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { if (file.exists()) json.decodeFromString<AppData>(file.readText()) else null }
                .getOrNull()
        }
        _data.value = loaded ?: AppData()
        scope.launch {
            _data.filterNotNull().drop(1).debounce(300).collect { write(it) }
        }
    }

    fun update(transform: (AppData) -> AppData) {
        while (true) {
            val before = _data.value
            val after = transform(before ?: AppData())
            if (_data.compareAndSet(before, after)) return
        }
    }

    /** Write immediately, e.g. when the app goes to the background. */
    suspend fun flush() {
        _data.value?.let { write(it) }
    }

    private suspend fun write(data: AppData) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
