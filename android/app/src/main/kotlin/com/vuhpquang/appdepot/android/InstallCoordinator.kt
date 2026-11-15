package com.vuhpquang.appdepot.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.EventSink
import com.vuhpquang.appdepot.core.InstallOutcome
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.core.SELF_SLUG
import com.vuhpquang.appdepot.core.VerifyResult
import com.vuhpquang.appdepot.core.runInstallQueue
import com.vuhpquang.appdepot.core.signerMatches
import com.vuhpquang.appdepot.core.verifyDownload
import kotlinx.coroutines.CancellationException
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
    private val lock = Mutex()
    private val _progress = MutableStateFlow<Map<String, InstallProgress>>(emptyMap())
    val progress: StateFlow<Map<String, InstallProgress>> = _progress.asStateFlow()
    // Written only while holding [lock], so there is at most one install in flight and one current slug.
    @Volatile private var currentSlug: String? = null

    init {
        File(context.cacheDir, "downloads").listFiles()?.forEach { it.delete() }
        installer.onPendingUserAction = { currentSlug?.let { set(it, InstallProgress.NeedsUserAction) } }
        installer.onConfirmLaunched = { currentSlug?.let { set(it, InstallProgress.Installing) } }
    }

    private fun set(slug: String, p: InstallProgress) = _progress.update { it + (slug to p) }

    /** Forget a finished/failed state once the UI has shown it. */
    fun clear(slug: String) = _progress.update { it - slug }

    suspend fun install(app: CatalogApp) {
        lock.withLock { runOne(app) }
    }

    suspend fun updateAll(apps: List<CatalogApp>) {
        try {
            lock.withLock { runInstallQueue(apps, { a -> runOne(a) }) { slug, p -> set(slug, p) } }
        } catch (e: CancellationException) {
            _progress.update { m -> m.filterValues { it != InstallProgress.Queued } }
            throw e
        }
    }

    private suspend fun runOne(app: CatalogApp): InstallOutcome {
        val release = app.latest
        if (release == null) return finish(app, null, InstallOutcome.Failed("${app.name} has no release to install."))
        val fromVersion = installed.versionOf(app.appId)
        if (!context.packageManager.canRequestPackageInstalls()) {
            set(app.slug, InstallProgress.NeedsPermission)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallOutcome.NeedsPermission
        }
        val dir = File(context.cacheDir, "downloads")
        val file = File(dir, "${app.slug}-${release.version}.apk")
        currentSlug = app.slug
        try {
            set(app.slug, InstallProgress.Downloading(0))
            try {
                downloader.download(release.downloadUrl, file) { set(app.slug, InstallProgress.Downloading(it)) }
            } catch (e: CancellationException) {
                throw e
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
            val outcome = when (val o = installer.install(file, app.appId, app.name)) {
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
