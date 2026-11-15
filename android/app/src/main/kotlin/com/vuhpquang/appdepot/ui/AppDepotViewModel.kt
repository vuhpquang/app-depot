package com.vuhpquang.appdepot.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vuhpquang.appdepot.AppDepotApplication
import com.vuhpquang.appdepot.android.HistoryRow
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.core.PairResult
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.planFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.provider.Settings as SystemSettings

data class AppRow(
    val app: CatalogApp,
    val state: AppState,
    val model: CardModel,
    val launchable: Boolean,
)

data class HomeUi(
    val rows: List<AppRow> = emptyList(),
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** True once a catalog (cached or fresh) has been read, so "no apps" isn't shown while loading. */
    val loaded: Boolean = false,
    val updatingAll: Boolean = false,
) {
    val updatable: List<AppRow> get() = rows.filter { it.state is AppState.UpdateAvailable }

    /** Any install running (one app or Update all): Update all waits for it. */
    val busy: Boolean get() = updatingAll || rows.any { it.model.activity != null }
}

data class SettingsUi(
    val autoCheck: Boolean,
    val autoInstall: Boolean,
    val lastChecked: Long,
    val paired: Boolean,
    val deviceName: String,
    val pairing: Boolean = false,
    val pairError: PairError? = null,
)

enum class PairError { BadCode, Failed }

/** App Depot's own What's New, shown once per new version. */
data class WhatsNew(val version: String, val notes: String)

class AppDepotViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AppDepotApplication
    private val settings = app.settings
    private val coordinator = app.coordinator
    private val isDebuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val catalog = MutableStateFlow<List<CatalogApp>?>(null)
    private val installed = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val launchable = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)
    private val updatingAll = MutableStateFlow(false)

    val versionName: String get() = app.versionName
    val versionCode: Long get() = app.versionCode

    val home: StateFlow<HomeUi> = combine(
        combine(catalog, installed, launchable, ::Triple),
        coordinator.progress,
        refreshing,
        refreshFailed,
        updatingAll,
    ) { (apps, inst, launch), progress, isRefreshing, failed, all ->
        HomeUi(
            rows = (apps ?: emptyList()).map { a -> rowFor(a, inst[a.slug], a.slug in launch, progress[a.slug]) },
            refreshing = isRefreshing,
            refreshFailed = failed,
            loaded = apps != null,
            updatingAll = all,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

    private val _settings = MutableStateFlow(readSettings())
    val settingsUi: StateFlow<SettingsUi> = _settings.asStateFlow()

    private val _whatsNew = MutableStateFlow<WhatsNew?>(null)
    val whatsNew: StateFlow<WhatsNew?> = _whatsNew.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryRow>>(emptyList())
    val history: StateFlow<List<HistoryRow>> = _history.asStateFlow()

    /** What to run again once "Install unknown apps" is granted. */
    private var afterPermission: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            catalog.value = withContext(Dispatchers.IO) { app.catalog.cached() }
            reloadInstalled()
            refresh()
        }
        // A finished install changes the installed version: re-read it, then let the card show the new state.
        viewModelScope.launch {
            coordinator.progress.collect { map ->
                val done = map.filterValues { it is InstallProgress.Done }.keys
                if (done.isNotEmpty()) {
                    reloadInstalled()
                    done.forEach { coordinator.clear(it) }
                    loadHistory()
                }
            }
        }
    }

    private fun rowFor(a: CatalogApp, version: String?, canLaunch: Boolean, progress: InstallProgress?): AppRow {
        val state = planFor(a, version)
        return AppRow(a, state, cardModel(state, progress, canLaunch, a.latest), canLaunch)
    }

    fun row(slug: String): AppRow? = home.value.rows.firstOrNull { it.app.slug == slug }

    // --- Catalog -------------------------------------------------------------------------------

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            val result = app.catalog.refresh()
            result.onSuccess { apps ->
                catalog.value = apps
                refreshFailed.value = false
                settings.lastChecked = System.currentTimeMillis()
                _settings.update { it.copy(lastChecked = settings.lastChecked) }
            }.onFailure {
                refreshFailed.value = true
                if (catalog.value == null) catalog.value = emptyList()
            }
            reloadInstalled()
            if (result.isSuccess) {
                val n = home.value.updatable.size
                withContext(Dispatchers.IO) {
                    app.history.record(DepotEvent("check", message = if (n == 1) "1 update" else "$n updates"))
                    app.uploader.flush()
                }
                maybeShowWhatsNew()
            }
            refreshing.value = false
            loadHistory()
        }
    }

    /** Coming back to the app: pick up installs/uninstalls made elsewhere and a granted permission. */
    fun onResume() {
        viewModelScope.launch {
            reloadInstalled()
            val canInstall = app.packageManager.canRequestPackageInstalls()
            val waiting = coordinator.progress.value.filterValues { it == InstallProgress.NeedsPermission }.keys
            if (canInstall && waiting.isNotEmpty()) {
                waiting.forEach { coordinator.clear(it) }
                afterPermission?.invoke()
                afterPermission = null
                refresh()
            } else if (System.currentTimeMillis() - settings.lastChecked > RESUME_REFRESH_MS) {
                refresh()
            }
        }
    }

    private suspend fun reloadInstalled() {
        val apps = catalog.value ?: return
        val pretend = withContext(Dispatchers.IO) { readPretend() }
        val versions = HashMap<String, String?>()
        val launch = HashSet<String>()
        withContext(Dispatchers.IO) {
            for (a in apps) {
                // App Depot itself: the running copy is the installed one (the debug build has a .dev id).
                val real = if (a.slug == SELF_SLUG) app.versionName else app.installedApps.versionOf(a.appId)
                versions[a.slug] = if (real != null) pretend[a.slug] ?: real else null
                if (a.slug != SELF_SLUG && app.installedApps.launchIntent(a.appId) != null) launch += a.slug
            }
        }
        installed.value = versions
        launchable.value = launch
    }

    /** Debug builds only: files/pretend.txt lines `slug=version` override an installed version. */
    private fun readPretend(): Map<String, String> {
        if (!isDebuggable) return emptyMap()
        val f = File(app.filesDir, "pretend.txt")
        return if (f.exists()) runCatching { parsePretendVersions(f.readText()) }.getOrDefault(emptyMap()) else emptyMap()
    }

    // --- Installs ------------------------------------------------------------------------------

    fun install(slug: String) {
        val a = catalog.value?.firstOrNull { it.slug == slug } ?: return
        afterPermission = { install(slug) }
        app.scope.launch { coordinator.install(a) }
    }

    fun updateAll() {
        val apps = home.value.updatable.map { it.app }
        if (apps.isEmpty() || home.value.busy) return
        afterPermission = { updateAll() }
        updatingAll.value = true
        app.scope.launch {
            try {
                coordinator.updateAll(apps)
            } finally {
                updatingAll.value = false
            }
        }
    }

    fun launchIntent(slug: String): Intent? =
        catalog.value?.firstOrNull { it.slug == slug }?.let { app.installedApps.launchIntent(it.appId) }

    fun recordOpen(slug: String) {
        app.scope.launch { app.history.record(DepotEvent("open", appSlug = slug, toVersion = installed.value[slug])) }
    }

    fun unknownSourcesIntent(): Intent =
        Intent(SystemSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))

    // --- History -------------------------------------------------------------------------------

    fun loadHistory() {
        viewModelScope.launch {
            _history.value = withContext(Dispatchers.IO) { app.history.recent(200) }
        }
    }

    fun appName(slug: String?): String? = when (slug) {
        null -> null
        SELF_SLUG -> "App Depot"
        else -> catalog.value?.firstOrNull { it.slug == slug }?.name ?: slug
    }

    // --- Settings ------------------------------------------------------------------------------

    private fun readSettings() = SettingsUi(
        autoCheck = settings.autoCheck,
        autoInstall = settings.autoInstall,
        lastChecked = settings.lastChecked,
        paired = app.pairing.isPaired,
        deviceName = deviceName(),
    )

    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun setAutoCheck(on: Boolean) {
        settings.autoCheck = on
        _settings.update { it.copy(autoCheck = on) }
    }

    fun setAutoInstall(on: Boolean) {
        settings.autoInstall = on
        _settings.update { it.copy(autoInstall = on) }
    }

    fun pair(code: String) {
        _settings.update { it.copy(pairing = true, pairError = null) }
        viewModelScope.launch {
            val r = app.pairing.pair(code, deviceName())
            if (r is PairResult.Paired) withContext(Dispatchers.IO) { app.uploader.flush() }
            _settings.update {
                it.copy(
                    pairing = false,
                    paired = r is PairResult.Paired,
                    pairError = when (r) {
                        is PairResult.Paired -> null
                        PairResult.BadCode -> PairError.BadCode
                        is PairResult.Failed -> PairError.Failed
                    },
                )
            }
        }
    }

    fun disconnect() {
        app.pairing.clearToken()
        _settings.update { it.copy(paired = false, pairError = null) }
    }

    // --- App Depot's own update + What's New ---------------------------------------------------

    /** The catalog's row for App Depot itself, if the catalog lists it. */
    fun selfRow(): AppRow? = row(SELF_SLUG)

    private fun selfReleaseForRunningVersion(): CatalogRelease? =
        catalog.value?.firstOrNull { it.slug == SELF_SLUG }?.latest?.takeIf { it.version == app.versionName }

    private fun maybeShowWhatsNew() {
        val current = app.versionName
        val seen = settings.lastSeenVersion
        if (seen == null) {
            settings.lastSeenVersion = current // first run: record silently
            return
        }
        if (seen == current) return
        val notes = selfReleaseForRunningVersion()?.notes?.takeIf { it.isNotBlank() }
        settings.lastSeenVersion = current
        if (notes != null) _whatsNew.value = WhatsNew(current, notes)
    }

    /** About → What's New: the running version's notes, or null when the catalog has none. */
    fun showWhatsNew(): Boolean {
        val notes = selfReleaseForRunningVersion()?.notes?.takeIf { it.isNotBlank() } ?: return false
        _whatsNew.value = WhatsNew(app.versionName, notes)
        return true
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
    }

    companion object {
        private const val RESUME_REFRESH_MS = 5 * 60 * 1000L
    }
}
