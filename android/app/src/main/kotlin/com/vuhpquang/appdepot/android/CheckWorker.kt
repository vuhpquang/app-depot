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
import com.vuhpquang.appdepot.core.decide
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
        val prev = next.filter { (slug, s) -> (s as? AppState.UpdateAvailable)?.latest?.version == told[slug] }
        val decision = decide(prev, next, settings.autoInstall)
        val bySlug = apps.associateBy { it.slug }
        val announce = decision.notifySlugs.toMutableList()
        val finish = mutableListOf<String>()

        if (decision.installSlugs.isNotEmpty()) {
            val canInstall = app.packageManager.canRequestPackageInstalls()
            val (silent, manual) = decision.installSlugs.partition { canInstall && installerIsUs(app, bySlug.getValue(it)) }
            announce += manual.filter { it !in prev } // already-told ones don't repeat every 6 h
            if (silent.isNotEmpty()) installSilently(app, silent.map { bySlug.getValue(it) }, finish)
        }

        Notifications.updatesAvailable(app, announce.map { bySlug.getValue(it).name })
        Notifications.finishUpdating(app, finish)
        settings.backgroundNotified = decision.notified
    }

    private suspend fun installSilently(app: AppDepotApplication, apps: List<CatalogApp>, finish: MutableList<String>) {
        app.coordinator.updateAll(apps)
        val progress = app.coordinator.progress.value
        for (a in apps) {
            when (val p = progress[a.slug]) {
                is InstallProgress.Done -> Notifications.updated(app, a.slug, a.name, p.version)
                InstallProgress.NeedsUserAction, InstallProgress.NeedsPermission -> finish += a.name
                else -> {} // Failed is already in History; the user sees it next time they open the app
            }
            if (progress[a.slug] != null) app.coordinator.clear(a.slug)
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
