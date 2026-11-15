package com.vuhpquang.appdepot

import android.app.Application
import com.vuhpquang.appdepot.android.CatalogRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppDepotApplication : Application() {
    /** Outlives every screen: installs and refreshes started from the UI run here. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val versionNameOrNull: String? by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
    }
    val versionName: String get() = versionNameOrNull ?: "0"
    val versionCode: Long by lazy {
        runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode }.getOrDefault(0L)
    }

    val api: DepotApi by lazy { DepotApi(DEPOT_BASE_URL, versionName) }
    val pairing: Pairing by lazy { Pairing(this, api, versionName) }
    val history: History by lazy { History(this) { scope.launch { uploader.flush() } } }
    val uploader: EventUploader by lazy { EventUploader(history.db, pairing, api) }

    val settings: Settings by lazy { Settings(this) }
    val catalog: CatalogRepository by lazy { CatalogRepository(this, DEPOT_BASE_URL) }
    val installedApps: InstalledApps by lazy { InstalledApps(this) }

    /** Process-wide: installs outlive any screen. */
    val coordinator: InstallCoordinator by lazy { InstallCoordinator(this, history, installedApps) }

    override fun onCreate() {
        super.onCreate()
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
