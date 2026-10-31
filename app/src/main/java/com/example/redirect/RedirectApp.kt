package com.example.redirect

import android.app.Application
import com.example.redirect.data.AppRepository
import com.example.redirect.data.AppStore
import com.example.redirect.platform.InstalledApps
import com.example.redirect.platform.AccessibilityBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class RedirectApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val store = AppStore(File(filesDir, "redirect-data.json"), appScope)
        repository = AppRepository(this, store, InstalledApps(this), AccessibilityBridge)
        appScope.launch {
            store.load()
            repository.ensureSeeded()
        }
    }
}
