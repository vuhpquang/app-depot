package com.vuhpquang.appdepot.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vuhpquang.appdepot.AppDepotApplication
import com.vuhpquang.appdepot.android.BackgroundScheduler
import com.vuhpquang.appdepot.android.HistoryRow
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.CatalogRelease
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.core.PairResult
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.othersInFlight
import com.vuhpquang.appdepot.core.planSelfUpdate
import com.vuhpquang.appdepot.core.planFor
import com.vuhpquang.appdepot.core.ANDROID
import com.vuhpquang.appdepot.core.localApps
import android.util.Log
import com.vuhpquang.appdepot.android.DEPOT_BASE_URL
import com.vuhpquang.appdepot.core.normalizeServerUrl
import kotlinx.coroutines.CancellationException
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
import android.provider.Settings as SystemSettings

data class AppRow(
    val app: CatalogApp,
    val state: AppState,
    val model: CardModel,
    val launchable: Boolean,
)

data class HomeUi(
    /** This phone's apps (Android catalog entries only), in the chosen [sort] order. Update all covers these. */
    val rows: List<AppRow> = emptyList(),
    /** Every product of the full catalog (all platforms), unsorted; [sections] is what the list shows. */
    val products: List<ProductRow> = emptyList(),
    val filter: PlatformFilter = PlatformFilter.Device,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** True once a catalog (cached or fresh) has been read, so "no apps" isn't shown while loading. */
    val loaded: Boolean = false,
    val updatingAll: Boolean = false,
    val layout: HomeLayout = HomeLayout.List,
    val sort: SortOrder = SortOrder.UpdatesFirst,
    val query: String = "",
) {
    val updatable: List<AppRow> get() = rows.filter { it.state is AppState.UpdateAvailable }

    /** What the list shows: products by filter, search and sort; this phone's first. Update all still covers every app. */
    val sections: HomeSections get() = homeSections(products, filter, sort, query)

    val counts: FilterCounts get() = filterCounts(products)

    /** The product a detail page shows, found by any of its entries' slugs. */
    fun product(slug: String): ProductRow? = products.firstOrNull { p -> p.product.variants.values.any { it.slug == slug } }

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
    /** Debuggable builds only; null hides the Server field. */
    val serverUrl: String? = null,
)

enum class PairError { BadCode, Failed }

/** App Depot's own What's New, shown once per new version. */
data class WhatsNew(val version: String, val notes: String)

class AppDepotViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AppDepotApplication
    private val settings = app.settings
    private val coordinator = app.coordinator
    private val isDebuggable = app.isDebuggable

    private val catalog = MutableStateFlow<List<CatalogApp>?>(null)
    private val installed = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val launchable = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)
    private val updatingAll = MutableStateFlow(false)
    private val skipped = MutableStateFlow(settings.skipped)

    /** Layout, sort and search: how Home shows the list. */
    private data class Inputs(
        val apps: List<CatalogApp>?, val installed: Map<String, String?>, val launchable: Set<String>, val skipped: Map<String, String>,
    )
    private data class View(val layout: HomeLayout, val sort: SortOrder, val query: String, val filter: PlatformFilter)
    private val view = MutableStateFlow(
        View(HomeLayout.fromKey(settings.layout), SortOrder.fromKey(settings.sort), "", PlatformFilter.fromKey(settings.platformFilter)),
    )

    val versionName: String get() = app.versionName
    val versionCode: Long get() = app.versionCode

    val home: StateFlow<HomeUi> = combine(
        combine(catalog, installed, launchable, skipped, ::Inputs),
        coordinator.progress,
        combine(refreshing, refreshFailed, updatingAll, ::Triple),
        view,
    ) { (apps, inst, launch, skip), progress, (isRefreshing, failed, all), v ->
        // Rows are planned for this phone's entries only; the other platforms just get a product card.
        val products = productRows(apps ?: emptyList(), ANDROID) { a -> rowFor(a, inst[a.slug], a.slug in launch, progress[a.slug], skip[a.slug], if (a.slug == SELF_SLUG) othersInFlight(progress) else 0) }
        HomeUi(
            rows = sortRows(products.mapNotNull { it.row }, v.sort),
            products = products,
            filter = v.filter,
            refreshing = isRefreshing,
            refreshFailed = failed,
            loaded = apps != null,
            updatingAll = all,
            layout = v.layout,
            sort = v.sort,
            query = v.query,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

    private val _settings = MutableStateFlow(readSettings())
    val settingsUi: StateFlow<SettingsUi> = _settings.asStateFlow()

    private val _whatsNew = MutableStateFlow<WhatsNew?>(null)
    val whatsNew: StateFlow<WhatsNew?> = _whatsNew.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryRow>>(emptyList())
    val history: StateFlow<List<HistoryRow>> = _history.asStateFlow()

    /** What to run again once "Install unknown apps" is granted; set only when an install stopped for it. */
    @Volatile private var afterPermission: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            catalog.value = withContext(Dispatchers.IO) { app.catalog.cached() }
            reloadInstalled()
            // The cached catalog already holds the notes of the version we just updated to.
            maybeShowWhatsNew(fromCache = true)
            autoRefresh()
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

    private fun rowFor(a: CatalogApp, version: String?, canLaunch: Boolean, progress: InstallProgress?, skippedVersion: String?, othersFirst: Int = 0): AppRow {
        val state = planFor(a, version, skippedVersion)
        return AppRow(a, state, cardModel(state, progress, canLaunch, a.latest, othersFirst), canLaunch)
    }

    fun row(slug: String): AppRow? = home.value.rows.firstOrNull { it.app.slug == slug }

    // --- Catalog -------------------------------------------------------------------------------

    /** Manual check (pull to refresh, Check now, Check for updates): always runs. */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
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
                result.getOrNull()?.let(::localApps)?.let { apps ->
                    val versions = installed.value
                    val skip = skipped.value
                    val n = apps.count { planFor(it, versions[it.slug], skip[it.slug]) is AppState.UpdateAvailable }
                    withContext(Dispatchers.IO) {
                        app.history.recordCheck(if (n == 1) "1 update" else "$n updates")
                        app.uploader.flush()
                    }
                    maybeShowWhatsNew()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AppDepot", "refresh failed", e)
            } finally {
                refreshing.value = false
                loadHistory()
            }
        }
    }

    /**
     * Launch / resume: only while "Check for updates automatically" is on (or there is no list at all yet),
     * and at most every [RESUME_REFRESH_MS].
     */
    private fun autoRefresh() {
        val stale = System.currentTimeMillis() - settings.lastChecked > RESUME_REFRESH_MS
        if (catalog.value.isNullOrEmpty() || (settings.autoCheck && stale)) refresh()
    }

    /** Coming back to the app: pick up installs/uninstalls made elsewhere and a granted permission. */
    fun onResume() {
        viewModelScope.launch {
            reloadInstalled()
            val canInstall = app.packageManager.canRequestPackageInstalls()
            val waiting = coordinator.progress.value.filterValues { it == InstallProgress.NeedsPermission }.keys
            if (canInstall && waiting.isNotEmpty()) {
                // The user just granted "Install unknown apps" for this: carry on with what they asked for.
                waiting.forEach { coordinator.clear(it) }
                val retry = afterPermission
                afterPermission = null
                retry?.invoke()
            } else if (catalog.value != null) {
                autoRefresh()
            }
        }
    }

    private suspend fun reloadInstalled() {
        val apps = catalog.value?.let(::localApps) ?: return
        val pretend = withContext(Dispatchers.IO) { app.readPretend() }
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

    // --- Installs ------------------------------------------------------------------------------

    /** This phone's catalog entry for [slug]; entries of other platforms are never installed, opened or removed. */
    private fun localApp(slug: String): CatalogApp? = catalog.value?.firstOrNull { it.slug == slug && it.platform == ANDROID }

    fun install(slug: String) {
        val a = localApp(slug) ?: return
        if (slug == SELF_SLUG) return updateSelfLast()
        app.scope.launch {
            coordinator.install(a)
            if (coordinator.progress.value[slug] == InstallProgress.NeedsPermission) afterPermission = { install(slug) }
        }
    }

    /** App Depot updates last: every other available update goes first, through the same queue as Update all. */
    private fun updateSelfLast() {
        val updatable = home.value.updatable.map { it.app }
        val order = planSelfUpdate(updatable.map { it.slug })
        val bySlug = (updatable + listOfNotNull(localApp(SELF_SLUG))).associateBy { it.slug }
        val apps = order.mapNotNull { bySlug[it] }
        app.scope.launch {
            coordinator.updateAll(apps)
            if (coordinator.progress.value.values.any { it == InstallProgress.NeedsPermission }) afterPermission = { updateSelfLast() }
        }
    }

    fun updateAll() {
        val apps = home.value.updatable.map { it.app }
        if (apps.isEmpty() || home.value.busy) return
        updatingAll.value = true
        app.scope.launch {
            try {
                coordinator.updateAll(apps)
            } finally {
                updatingAll.value = false
            }
            if (coordinator.progress.value.values.any { it == InstallProgress.NeedsPermission }) afterPermission = { updateAll() }
        }
    }

    /** Looks the launch intent up off the main thread, then hands it to [start] on the main thread. */
    fun open(slug: String, start: (Intent) -> Unit) {
        val a = localApp(slug) ?: return
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) { app.installedApps.launchIntent(a.appId) } ?: return@launch
            start(intent)
            recordOpen(slug)
        }
    }

    fun recordOpen(slug: String) {
        app.scope.launch { app.history.record(DepotEvent("open", appSlug = slug, toVersion = installed.value[slug])) }
    }

    // --- Uninstall, skip, view ------------------------------------------------------------------

    /** The system uninstall dialog for [slug]; null for App Depot itself or an unknown app. */
    fun uninstallIntent(slug: String): Intent? {
        if (slug == SELF_SLUG) return null
        val a = localApp(slug) ?: return null
        return Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.appId}"))
    }

    /** Back from the uninstall dialog: re-read versions; a local-only history row if the app is gone. */
    fun afterUninstall(slug: String) {
        val before = installed.value[slug]
        viewModelScope.launch {
            reloadInstalled()
            if (before != null && installed.value[slug] == null) {
                withContext(Dispatchers.IO) { app.history.record(DepotEvent("uninstall", appSlug = slug, fromVersion = before)) }
                loadHistory()
            }
        }
    }

    fun skipVersion(slug: String, version: String) {
        settings.skipped = settings.skipped + (slug to version)
        skipped.value = settings.skipped
    }

    fun undoSkip(slug: String) {
        settings.skipped = settings.skipped - slug
        skipped.value = settings.skipped
    }

    fun setLayout(layout: HomeLayout) {
        settings.layout = layout.key
        view.update { it.copy(layout = layout) }
    }

    fun setSort(sort: SortOrder) {
        settings.sort = sort.key
        view.update { it.copy(sort = sort) }
    }

    fun setQuery(query: String) = view.update { it.copy(query = query) }

    fun setFilter(filter: PlatformFilter) {
        settings.platformFilter = filter.key
        view.update { it.copy(filter = filter) }
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
        serverUrl = if (isDebuggable) app.baseUrl else null,
    )

    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun setAutoCheck(on: Boolean) {
        settings.autoCheck = on
        if (on) BackgroundScheduler.schedule(app) else BackgroundScheduler.cancel(app)
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

    /** Debug only. Returns false when the text isn't an http(s) URL. Blank = back to vuhpquang.com. */
    fun setServerUrl(input: String): Boolean {
        if (!isDebuggable) return false
        val url = if (input.isBlank()) null else normalizeServerUrl(input) ?: return false
        settings.serverUrl = url?.takeIf { it != DEPOT_BASE_URL }
        _settings.update { it.copy(serverUrl = app.baseUrl) }
        refresh()
        return true
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

    private fun maybeShowWhatsNew(fromCache: Boolean = false) {
        val current = app.versionName
        val seen = settings.lastSeenVersion
        if (seen == null) {
            settings.lastSeenVersion = current // first run: record silently
            return
        }
        if (seen == current) return
        val notes = selfReleaseForRunningVersion()?.notes?.takeIf { it.isNotBlank() }
        // The cache may predate this version's release: wait for the refresh instead of marking it seen.
        if (notes == null && fromCache) return
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
