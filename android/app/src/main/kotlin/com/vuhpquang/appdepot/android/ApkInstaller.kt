package com.vuhpquang.appdepot.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import com.vuhpquang.appdepot.core.InstallOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Final PackageInstaller answer for one session. */
data class SessionResult(val status: Int, val message: String?)

class ApkInstaller(private val context: Context) {
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<SessionResult>>()
    @Volatile var onPendingUserAction: (sessionId: Int) -> Unit = {}
    @Volatile var onConfirmLaunched: (sessionId: Int) -> Unit = {}
    private val backgroundSessions = java.util.Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    /** True unless the session was started by a background run (which never opens the confirm screen). */
    internal fun isInteractive(sessionId: Int) = sessionId !in backgroundSessions

    /** SHA-256 (lowercase hex) of the first signing certificate of the APK at [path], or null if unreadable. */
    fun apkSignerSha256(path: String): String? {
        val info = context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info?.signingInfo ?: return null
        val cert = (signing.apkContentsSigners.firstOrNull() ?: signing.signingCertificateHistory?.firstOrNull())
            ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Commits [apk] as an update/install of [appId] and waits for the final result. */
    suspend fun install(apk: File, appId: String, appName: String, interactive: Boolean = true): InstallOutcome {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appId)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        var id = -1
        val result = CompletableDeferred<SessionResult>()
        try {
            id = installer.createSession(params)
            waiting[id] = result
            if (!interactive) backgroundSessions += id
            withContext(Dispatchers.IO) {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val status = PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(status.intentSender)
                }
            }
            // Bounded: an ignored or suppressed confirm dialog must not block the coordinator's lock forever.
            val r = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { result.await() }
            if (r == null) {
                runCatching { installer.abandonSession(id) }
                return InstallOutcome.Failed("The install wasn't confirmed. Try again.")
            }
            return when (r.status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallOutcome.NeedsUserAction
                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success("")
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Failed("Install cancelled")
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    InstallOutcome.Failed("Android refused the update: it isn't signed like the installed app.")
                else -> InstallOutcome.Failed(
                    "Android couldn't install $appName${r.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}",
                )
            }
        } catch (e: CancellationException) {
            if (id >= 0) runCatching { installer.abandonSession(id) }
            throw e
        } catch (e: Exception) {
            Log.w("ApkInstaller", "Install of $appId failed", e)
            if (id >= 0) runCatching { installer.abandonSession(id) }
            return InstallOutcome.Failed("Android couldn't install $appName. Try again.")
        } finally {
            if (id >= 0) {
                waiting.remove(id)
                backgroundSessions.remove(id)
            }
        }
    }

    companion object {
        const val CONFIRM_TIMEOUT_MS = 5 * 60 * 1000L
    }

    internal fun onResult(sessionId: Int, status: Int, message: String?) {
        waiting[sessionId]?.complete(SessionResult(status, message))
    }
}

/** PackageInstaller's answers: show its confirmation screen, or hand the final status to the waiting install. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val coordinator = (context.applicationContext as com.vuhpquang.appdepot.AppDepotApplication).coordinator
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (!coordinator.installer.isInteractive(sessionId)) {
                // Background run: never pop the confirm screen. Give up on the session; the worker posts a notification.
                runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                coordinator.installer.onResult(sessionId, PackageInstaller.STATUS_PENDING_USER_ACTION, null)
                return
            }
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
            coordinator.installer.onPendingUserAction(sessionId)
            try {
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                coordinator.installer.onConfirmLaunched(sessionId)
            } catch (e: Exception) {
                Log.w("ApkInstaller", "Couldn't open the install confirmation", e)
                coordinator.installer.onResult(sessionId, PackageInstaller.STATUS_FAILURE, "couldn't open the confirmation screen")
            }
            return
        }
        coordinator.installer.onResult(sessionId, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
