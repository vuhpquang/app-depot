package com.vuhpquang.appdepot

import android.app.Application
import android.content.pm.ApplicationInfo
import com.vuhpquang.appdepot.android.BackgroundScheduler
import com.vuhpquang.appdepot.android.CatalogRepository
import com.vuhpquang.appdepot.android.DownloadNotifier
import com.vuhpquang.appdepot.android.DEPOT_BASE_URL
import com.vuhpquang.appdepot.android.DepotApi
import com.vuhpquang.appdepot.android.EventUploader
import com.vuhpquang.appdepot.android.History
import com.vuhpquang.appdepot.android.InstallCoordinator
import com.vuhpquang.appdepot.android.InstalledApps
import com.vuhpquang.appdepot.android.Pairing
import com.vuhpquang.appdepot.android.Settings
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.ui.parsePretendVersions
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AppDepotApplication : Application() {
    /** Outlives every screen: installs and refreshes started from the UI run here. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w("AppDepot", "uncaught error in app scope", e) },
    )

    /** True while MainActivity is visible; background work avoids restarting the app then. */
    @Volatile var foreground = false

    private val versionNameOrNull: String? by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
    }
    val versionName: String get() = versionNameOrNull ?: "0"
    val versionCode: Long by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode }.getOrDefault(0L)
    }

    val isDebuggable: Boolean by lazy { (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    /** vuhpquang.com, or the debug-only Server setting in debuggable builds. */
    val baseUrl: String get() = if (isDebuggable) settings.serverUrl ?: DEPOT_BASE_URL else DEPOT_BASE_URL

    val api: DepotApi by lazy { DepotApi({ baseUrl }, versionName) }
    val pairing: Pairing by lazy { Pairing(this, api, versionName) }
    val history: History by lazy { History(this) { scope.launch { uploader.flush() } } }
    val uploader: EventUploader by lazy { EventUploader(history.db, pairing, api) }

    val settings: Settings by lazy { Settings(this) }
    val catalog: CatalogRepository by lazy { CatalogRepository(this) { baseUrl } }
    val installedApps: InstalledApps by lazy { InstalledApps(this) }

    /** Process-wide: installs outlive any screen. */
    val coordinator: InstallCoordinator by lazy { InstallCoordinator(this, history, installedApps) }

    /** Debug builds only: files/pretend.txt lines `slug=version` override an installed version. */
    fun readPretend(): Map<String, String> {
        if (!isDebuggable) return emptyMap()
        val f = File(filesDir, "pretend.txt")
        return if (f.exists()) runCatching { parsePretendVersions(f.readText()) }.getOrDefault(emptyMap()) else emptyMap()
    }

    override fun onCreate() {
        super.onCreate()
        if (settings.autoCheck) BackgroundScheduler.schedule(this)
        DownloadNotifier(this) { catalog.cached() }.start(scope, coordinator.progress)
        scope.launch {
            // The process dies during a self-update, so the coordinator can't record it: detect it here.
            val state = getSharedPreferences("appdepot.state", MODE_PRIVATE)
            val current = versionNameOrNull
            if (current != null) {
                val last = state.getString("lastVersionName", null)
                if (last != null && last != current) {
                    history.record(DepotEvent("self_update", appSlug = SELF_SLUG, fromVersion = last, toVersion = current))
                }
                state.edit().putString("lastVersionName", current).apply()
            }
            history.record(DepotEvent("depot_started"))
        }
    }
}
