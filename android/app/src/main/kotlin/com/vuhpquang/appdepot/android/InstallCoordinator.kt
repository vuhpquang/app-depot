package com.vuhpquang.appdepot.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.EventSink
import com.vuhpquang.appdepot.core.InstallOutcome
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.VerifyResult
import com.vuhpquang.appdepot.core.claimable
import com.vuhpquang.appdepot.core.runInstallQueue
import com.vuhpquang.appdepot.core.selfMayStart
import com.vuhpquang.appdepot.core.safeFileSegment
import com.vuhpquang.appdepot.core.signerMatches
import com.vuhpquang.appdepot.core.verifyDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The one place that installs: download, verify, hand to PackageInstaller, record the outcome.
 * Process-wide (created once by the Application); installs never overlap.
 */
class InstallCoordinator(
    private val context: Context,
    private val events: EventSink,
    private val installed: InstalledApps = InstalledApps(context),
    private val downloader: ApkDownloader = ApkDownloader("AppDepot-Android"),
) {
    val installer = ApkInstaller(context)
    private companion object { const val SELF_POLL_MS = 400L }
    private val lock = Mutex()
    private val claimLock = Any()
    private val _progress = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    val progress: StateFlow<Map<String, InstallProgress>> = _progress.asStateFlow()
    // Written only while holding [lock], so there is at most one install in flight and one current slug.
    @Volatile private var currentSlug: String? = null

    init {
        // Leftovers from a killed install; off the main thread, the Application creates us lazily from anywhere.
        val started = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            // Only files older than this process's coordinator: an install may already be writing a new one.
            runCatching { File(context.cacheDir, "downloads").listFiles()?.filter { it.lastModified() < started }?.forEach { it.delete() } }
        }
        installer.onPendingUserAction = { currentSlug?.let { set(it, InstallProgress.NeedsUserAction) } }
        installer.onConfirmLaunched = { currentSlug?.let { set(it, InstallProgress.Installing) } }
    }

    private fun set(slug: String, p: InstallProgress) = _progress.update { it + (slug to p) }

    /** Forget a finished/failed state once the UI has shown it. */
    fun clear(slug: String) = _progress.update { it - slug }

    /** Marks the slugs that may start as Queued, atomically; returns them (empty when all are already busy). */
    private fun claim(slugs: List<String>): List<String> = synchronized(claimLock) {
        val ok = claimable(_progress.value, slugs, currentSlug)
        if (ok.isNotEmpty()) _progress.update { m -> m + ok.associateWith { InstallProgress.Queued } }
        ok
    }

    private fun unqueue(slugs: List<String>) =
        _progress.update { m -> m.filter { (k, v) -> !(k in slugs && v == InstallProgress.Queued) } }

    /**
     * App Depot's own install: its restart kills the process, so it waits until no other install is
     * queued or running ([waitForOthers]; false = give up and return null instead of waiting).
     * The slug must already be claimed.
     */
    private suspend fun runSelfWhenClear(app: CatalogApp, interactive: Boolean, waitForOthers: Boolean): InstallOutcome? {
        try {
            while (true) {
                lock.withLock { if (selfMayStart(_progress.value)) return runOne(app, interactive) }
                if (!waitForOthers) {
                    unqueue(listOf(app.slug))
                    return null
                }
                delay(SELF_POLL_MS)
            }
        } catch (e: CancellationException) {
            unqueue(listOf(app.slug))
            throw e
        }
    }

    suspend fun install(app: CatalogApp, interactive: Boolean = true, waitForOthers: Boolean = true): InstallOutcome? {
        if (claim(listOf(app.slug)).isEmpty()) return null
        if (app.slug == SELF_SLUG) return runSelfWhenClear(app, interactive, waitForOthers)
        try {
            return lock.withLock { runOne(app, interactive) }
        } catch (e: CancellationException) {
            unqueue(listOf(app.slug))
            throw e
        }
    }

    /** Installs the apps one at a time (self last). Returns each outcome; apps already busy are left out. */
    suspend fun updateAll(
        apps: List<CatalogApp>, interactive: Boolean = true, waitForOthers: Boolean = true,
    ): Map<String, InstallOutcome> {
        val mine = claim(apps.map { it.slug }).toSet()
        val todo = apps.filter { it.slug in mine }
        if (todo.isEmpty()) return emptyMap()
        val others = todo.filter { it.slug != SELF_SLUG }
        val self = todo.firstOrNull { it.slug == SELF_SLUG }
        val results = LinkedHashMap<String, InstallOutcome>()
        try {
            if (others.isNotEmpty()) {
                results += lock.withLock { runInstallQueue(others, { a -> runOne(a, interactive) }) { slug, p -> set(slug, p) } }
            }
        } catch (e: CancellationException) {
            unqueue(todo.map { it.slug })
            throw e
        }
        if (self != null) {
            if (results.values.any { it == InstallOutcome.NeedsPermission }) {
                set(self.slug, InstallProgress.NeedsPermission)
                results[self.slug] = InstallOutcome.NeedsPermission
            } else {
                runSelfWhenClear(self, interactive, waitForOthers)?.let { results[self.slug] = it }
            }
        }
        return results
    }

    private suspend fun runOne(app: CatalogApp, interactive: Boolean): InstallOutcome {
        try {
            return runOneUnsafe(app, interactive)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("InstallCoordinator", "Install of ${app.slug} crashed", e)
            return finish(app, null, InstallOutcome.Failed("Something went wrong installing ${app.name}. Try again."))
        }
    }

    private suspend fun runOneUnsafe(app: CatalogApp, interactive: Boolean): InstallOutcome {
        val release = app.latest
        if (release == null) return finish(app, null, InstallOutcome.Failed("${app.name} has no release to install."))
        val fromVersion = installed.versionOf(app.appId)
        // A second request queued behind the first one: the work is already done.
        if (fromVersion == release.version) {
            set(app.slug, InstallProgress.Done(release.version))
            return InstallOutcome.Success(release.version)
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            set(app.slug, InstallProgress.NeedsPermission)
            if (interactive) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return InstallOutcome.NeedsPermission
        }
        val dir = File(context.cacheDir, "downloads")
        val file = File(dir, "${app.slug}-${safeFileSegment(release.version)}.apk")
        currentSlug = app.slug
        try {
            set(app.slug, InstallProgress.Downloading(0))
            try {
                downloader.download(release.downloadUrl, file, release.size) { set(app.slug, InstallProgress.Downloading(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DamagedDownloadException) {
                return finish(app, fromVersion, InstallOutcome.Failed("The download was damaged. Try again."))
            } catch (e: Exception) {
                return finish(app, fromVersion, InstallOutcome.Failed("The download failed. Check the connection and try again."))
            }
            set(app.slug, InstallProgress.Verifying)
            if (verifyDownload(file, release.size, release.sha256) != VerifyResult.Ok) {
                return finish(app, fromVersion, InstallOutcome.Failed("The download was damaged. Try again."))
            }
            if (fromVersion == null && !signerMatches(installer.apkSignerSha256(file.path), app.signer)) {
                return finish(app, fromVersion, InstallOutcome.Failed("This file isn't signed like ${app.name}. It wasn't installed."))
            }
            set(app.slug, InstallProgress.Installing)
            val outcome = when (val o = installer.install(file, app.appId, app.name, interactive)) {
                is InstallOutcome.Success -> InstallOutcome.Success(release.version)
                else -> o
            }
            return finish(app, fromVersion, outcome)
        } finally {
            file.delete()
            currentSlug = null
        }
    }

    private fun finish(app: CatalogApp, from: String?, outcome: InstallOutcome): InstallOutcome {
        val to = app.latest?.version
        when (outcome) {
            is InstallOutcome.Success -> {
                set(app.slug, InstallProgress.Done(outcome.version))
                val type = when {
                    app.slug == SELF_SLUG -> "self_update"
                    from == null -> "install"
                    else -> "update"
                }
                events.record(DepotEvent(type, app.slug, from, outcome.version))
            }
            is InstallOutcome.Failed -> {
                set(app.slug, InstallProgress.Failed(outcome.message))
                events.record(DepotEvent("install_failed", app.slug, from, to, outcome.message.take(2000)))
            }
            InstallOutcome.NeedsUserAction -> set(app.slug, InstallProgress.NeedsUserAction)
            InstallOutcome.NeedsPermission -> set(app.slug, InstallProgress.NeedsPermission)
        }
        return outcome
    }
}
