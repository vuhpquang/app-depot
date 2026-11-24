package com.vuhpquang.appdepot.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vuhpquang.appdepot.MainActivity
import com.vuhpquang.appdepot.R
import com.vuhpquang.appdepot.core.CatalogApp
import com.vuhpquang.appdepot.core.DownloadBatch
import com.vuhpquang.appdepot.core.DownloadNotice
import com.vuhpquang.appdepot.core.InstallProgress
import com.vuhpquang.appdepot.ui.iconFileName
import com.vuhpquang.appdepot.ui.theme.DepotTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * The install progress notification (Material 3 Expressive redesign, 2026-11-24). One ongoing
 * notification per batch: on Android 16+ a segmented `ProgressStyle` bar (one segment per app, the
 * current app's icon riding the bar) promoted as a Live Update with a "42%" chip; older Android
 * gets the classic progress bar. Gone as soon as nothing is queued or installing.
 */
class DownloadNotifier(
    private val context: Context,
    private val apps: () -> List<CatalogApp>,
) {
    private var known: List<CatalogApp> = emptyList()

    fun start(scope: CoroutineScope, progress: StateFlow<Map<String, InstallProgress>>) {
        val batch = DownloadBatch()
        scope.launch {
            progress.map { batch.update(it) }
                .distinctUntilChanged()
                .conflate()
                .collect { n ->
                    if (n == null) cancel() else post(n)
                    delay(UPDATE_INTERVAL_MS) // the system drops notification updates that come faster
                }
        }
    }

    private fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the runtime permission
    private fun post(n: DownloadNotice) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel()
        // The catalog is a file read: keep it, and read it again only for an app it doesn't know yet.
        val app = known.firstOrNull { it.slug == n.slug } ?: apps().also { known = it }.firstOrNull { it.slug == n.slug }
        val name = app?.name ?: n.slug
        val title = context.getString(
            when (n.phase) {
                DownloadNotice.Phase.Queued -> R.string.notif_dl_queued
                is DownloadNotice.Phase.Downloading -> R.string.notif_dl_downloading
                DownloadNotice.Phase.Verifying -> R.string.notif_dl_verifying
                DownloadNotice.Phase.Installing -> R.string.notif_dl_installing
                DownloadNotice.Phase.Confirm -> R.string.notif_dl_confirm
            },
            name,
        )
        val text = when {
            n.phase == DownloadNotice.Phase.Confirm -> context.getString(R.string.notif_dl_confirm_text)
            n.total > 1 -> context.getString(R.string.notif_dl_batch, n.index, n.total, n.overallPercent)
            n.phase is DownloadNotice.Phase.Downloading -> context.getString(R.string.notif_dl_percent, n.overallPercent)
            else -> null
        }
        val icon = app?.iconUrl?.let(::cachedIcon)
        val notification = if (Build.VERSION.SDK_INT >= 36) {
            progressStyle(n, title, text, icon)
        } else {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_depot)
                .setContentTitle(title)
                .setContentText(text)
                .setLargeIcon(icon)
                .setProgress(100, n.overallPercent, n.total == 1 && n.indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(homeIntent())
                .build()
        }
        nm.notify(ID, notification)
    }

    @SuppressLint("NewApi") // called on API 36+ only
    private fun progressStyle(n: DownloadNotice, title: String, text: String?, icon: Bitmap?): Notification {
        val green = DepotTokens.GreenLight.toArgb()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(List(n.total) { Notification.ProgressStyle.Segment(SEGMENT).setColor(green) })
            .setProgressPoints((1 until n.total).map { Notification.ProgressStyle.Point(it * SEGMENT).setColor(green) })
            .setProgress(n.units)
            .setProgressIndeterminate(n.total == 1 && n.indeterminate)
        icon?.let { style.setProgressTrackerIcon(Icon.createWithBitmap(it)) }
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_depot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(homeIntent())
            // Live Update: an install is an ongoing task the user started; the chip says "42%".
            .setShortCriticalText(context.getString(R.string.notif_dl_percent, n.overallPercent))
            .also { if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) it.setRequestPromotedOngoing(true) }
            .build()
    }

    /** The catalog icon App Depot already cached for its own cards (cacheDir/icons). */
    private fun cachedIcon(url: String): Bitmap? = runCatching {
        val f = File(File(context.cacheDir, "icons"), iconFileName(url))
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }.getOrNull()

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            // Default importance so it sits with the other notifications (and can be a Live Update), but silent.
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = context.getString(R.string.channel_downloads_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
        )
    }

    private fun homeIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "installs"
        const val ID = 3001
        const val SEGMENT = 100
        const val UPDATE_INTERVAL_MS = 500L
    }
}
