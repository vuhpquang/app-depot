package com.vuhpquang.appdepot.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vuhpquang.appdepot.MainActivity
import com.vuhpquang.appdepot.R

/** Update notifications. Every one opens Home. Silent no-op while notifications are off or denied. */
object Notifications {
    const val CHANNEL_ID = "updates"
    private const val ID_AVAILABLE = 1001
    private const val ID_FINISH = 1002
    private const val ID_UPDATED_BASE = 2000

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_updates_desc) },
        )
    }

    private fun homeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers the runtime permission
    private fun post(context: Context, id: Int, title: String, text: String): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        ensureChannel(context)
        nm.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_depot)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(homeIntent(context))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
        return true
    }

    /** "3 app updates" with the names. Replaces the previous one. */
    fun updatesAvailable(context: Context, names: List<String>): Boolean {
        if (names.isEmpty()) return true
        return post(context, ID_AVAILABLE, context.resources.getQuantityString(R.plurals.notif_updates_title, names.size, names.size), names.joinToString(", "))
    }

    /** "Updated ZenTask to 0.15.2". One per app. */
    fun updated(context: Context, slug: String, name: String, version: String) =
        post(context, ID_UPDATED_BASE + (slug.hashCode() and 0xFFF), context.getString(R.string.notif_updated_title, name, version), "")

    /** Installs that need the user: "Tap to finish updating". */
    fun finishUpdating(context: Context, names: List<String>): Boolean {
        if (names.isEmpty()) return true
        return post(context, ID_FINISH, context.getString(R.string.notif_finish_title), context.getString(R.string.notif_finish_text, names.joinToString(", ")))
    }
}
