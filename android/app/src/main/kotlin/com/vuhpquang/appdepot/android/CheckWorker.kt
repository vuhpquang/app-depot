package com.vuhpquang.appdepot.android

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vuhpquang.appdepot.AppDepotApplication
import com.vuhpquang.appdepot.core.AppState
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.planBackground
import com.vuhpquang.appdepot.core.planFor
import kotlinx.coroutines.CancellationException

/**
 * Background update check. Never surfaces an error: a failed catalog fetch just ends the run quietly.
 * Installs only apps whose installer of record is App Depot (those can update without a prompt);
 * anything else is announced instead.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AppDepotApplication
        val settings = app.settings
        if (!settings.autoCheck) return Result.success()
        // The launch-time run is redundant when the foreground just refreshed.
        if (inputData.getBoolean(KEY_STARTUP, false) && System.currentTimeMillis() - settings.lastChecked < STARTUP_SKIP_MS) {
            return Result.success()
        }
        try {
            run(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AppDepot", "background check failed", e)
        }
        return Result.success()
    }

    private suspend fun run(app: AppDepotApplication) {
        val settings = app.settings
        val apps = app.catalog.refresh().getOrNull() ?: return
        settings.lastChecked = System.currentTimeMillis()

        val pretend = app.readPretend()
        val next: Map<String, AppState> = apps.associate { a ->
            val real = if (a.slug == SELF_SLUG) app.versionName else app.installedApps.versionOf(a.appId)
            a.slug to planFor(a, if (real != null) pretend[a.slug] ?: real else null)
        }
        val n = next.values.count { it is AppState.UpdateAvailable }
        app.history.recordCheck(if (n == 1) "1 update" else "$n updates")
        app.uploader.flush()

        val told = settings.backgroundNotified
        val failed = settings.backgroundFailed
        val bySlug = apps.associateBy { it.slug }
        val canInstall = app.packageManager.canRequestPackageInstalls()
        val silentCapable = if (settings.autoInstall && canInstall) {
            next.filterValues { it is AppState.UpdateAvailable }.keys.filter { installerIsUs(app, bySlug.getValue(it)) }.toSet()
        } else emptySet()
        val busy = app.coordinator.progress.value.keys
        val plan = planBackground(told, failed, next, settings.autoInstall, silentCapable, busy)

        val newFailed = failed.toMutableMap()
        val finish = mutableListOf<String>()
        if (plan.install.isNotEmpty()) {
            installSilently(app, plan.install.map { bySlug.getValue(it) }, newFailed, finish, next)
        }

        val posted = Notifications.updatesAvailable(app, plan.announce.map { bySlug.getValue(it).name })
        Notifications.finishUpdating(app, finish.map { bySlug.getValue(it).name })
        // Only advance "told" when the user could actually be told; otherwise try again next run.
        val saveTold = plan.notified.toMutableMap()
        if (!posted) plan.announce.forEach { slug -> told[slug]?.let { saveTold[slug] = it } ?: saveTold.remove(slug) }
        settings.backgroundNotified = saveTold
        // Forget failures once the app moved on to another version or is no longer updatable.
        settings.backgroundFailed = newFailed.filter { (slug, v) -> (next[slug] as? AppState.UpdateAvailable)?.latest?.version == v }
    }

    private suspend fun installSilently(
        app: AppDepotApplication, apps: List<CatalogApp>, failed: MutableMap<String, String>,
        finish: MutableList<String>, next: Map<String, AppState>,
    ) {
        app.coordinator.updateAll(apps)
        val progress = app.coordinator.progress.value
        for (a in apps) {
            val version = (next[a.slug] as? AppState.UpdateAvailable)?.latest?.version
            when (val p = progress[a.slug]) {
                is InstallProgress.Done -> {
                    Notifications.updated(app, a.slug, a.name, p.version)
                    app.coordinator.clear(a.slug) // only finished ones; Failed / NeedsUserAction stay visible in the app
                }
                InstallProgress.NeedsUserAction, InstallProgress.NeedsPermission -> {
                    if (version != null) failed[a.slug] = version
                    finish += a.slug
                }
                is InstallProgress.Failed -> if (version != null) failed[a.slug] = version
                else -> {}
            }
        }
    }

    private fun installerIsUs(app: AppDepotApplication, a: CatalogApp): Boolean = runCatching {
        app.packageManager.getInstallSourceInfo(a.appId).installingPackageName == app.packageName
    }.getOrDefault(false)

    companion object {
        const val KEY_STARTUP = "startup"
        private const val STARTUP_SKIP_MS = 5 * 60 * 1000L
    }
}
