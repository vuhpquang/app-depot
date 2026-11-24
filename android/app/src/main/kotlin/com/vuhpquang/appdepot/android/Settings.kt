package com.vuhpquang.appdepot.android

import android.content.Context
import com.vuhpquang.appdepot.core.formatSlugMap
import com.vuhpquang.appdepot.core.parseSlugMap

/** User settings and small update bookkeeping. Keys are stable: never rename them. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("appdepot.settings", Context.MODE_PRIVATE)

    /** Background check every 6 h (Task 6). Default on. */
    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_CHECK, v).apply()

    /** Install found updates without asking. Default off; only effective while [autoCheck] is on. */
    var autoInstall: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSTALL, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_INSTALL, v).apply()

    /** Epoch ms of the last successful catalog check; 0 = never. */
    var lastChecked: Long
        get() = prefs.getLong(KEY_LAST_CHECKED, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_CHECKED, v).apply()

    /** App Depot's own version whose What's New was last shown (or silently recorded on first run). */
    var lastSeenVersion: String?
        get() = prefs.getString(KEY_LAST_SEEN, null)
        set(v) = prefs.edit().putString(KEY_LAST_SEEN, v).apply()

    /** The one-time notification permission prompt has been shown. */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(v) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, v).apply()

    /** Background checks: slug -> latest version the user was already told about (survives process death). */
    var backgroundNotified: Map<String, String>
        get() = readMap(KEY_BG_NOTIFIED)
        set(v) = writeMap(KEY_BG_NOTIFIED, v)

    /** Background installs that failed or needed the user: slug -> version; not retried until a newer one. */
    var backgroundFailed: Map<String, String>
        get() = readMap(KEY_BG_FAILED)
        set(v) = writeMap(KEY_BG_FAILED, v)

    /** Skipped updates: slug -> the version the user chose to skip (a newer one shows up again). */
    var skipped: Map<String, String>
        get() = readMap(KEY_SKIPPED)
        set(v) = writeMap(KEY_SKIPPED, v)

    /** Home layout: "list" | "grid". */
    var layout: String?
        get() = prefs.getString(KEY_LAYOUT, null)
        set(v) = prefs.edit().putString(KEY_LAYOUT, v).apply()

    /** Home sort: "updates" | "name" | "recent". */
    var sort: String?
        get() = prefs.getString(KEY_SORT, null)
        set(v) = prefs.edit().putString(KEY_SORT, v).apply()

    /** Home platform filter: "device" (this phone's apps, default) | "all". */
    var platformFilter: String?
        get() = prefs.getString(KEY_PLATFORM_FILTER, null)
        set(v) = prefs.edit().putString(KEY_PLATFORM_FILTER, v).apply()

    /** Material You colours from the wallpaper instead of depot green. Default off. */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply()

    private fun readMap(key: String): Map<String, String> = parseSlugMap(prefs.getString(key, null))

    private fun writeMap(key: String, v: Map<String, String>) = prefs.edit().putString(key, formatSlugMap(v)).apply()

    /** Debug builds only: catalog + device API server; null = [DEPOT_BASE_URL]. */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, v) }.apply()

    companion object {
        const val KEY_AUTO_CHECK = "appdepot.updates.autoCheck"
        const val KEY_AUTO_INSTALL = "appdepot.updates.autoInstall"
        const val KEY_LAST_CHECKED = "appdepot.updates.lastChecked"
        const val KEY_LAST_SEEN = "appdepot.whatsNew.lastSeenVersion"
        const val KEY_NOTIF_ASKED = "appdepot.notifications.asked"
        const val KEY_BG_NOTIFIED = "appdepot.background.notified"
        const val KEY_BG_FAILED = "appdepot.background.failed"
        const val KEY_SERVER_URL = "appdepot.debug.serverUrl"
        const val KEY_SKIPPED = "appdepot.skipped"
        const val KEY_LAYOUT = "appdepot.ui.layout"
        const val KEY_SORT = "appdepot.ui.sort"
        const val KEY_PLATFORM_FILTER = "appdepot.ui.platformFilter"
        const val KEY_DYNAMIC_COLOR = "appdepot.ui.dynamicColor"
    }
}
