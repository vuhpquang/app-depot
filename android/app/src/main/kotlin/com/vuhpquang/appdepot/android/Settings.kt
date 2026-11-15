package com.vuhpquang.appdepot.android

import android.content.Context

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

    /** Debug builds only: catalog + device API server; null = [DEPOT_BASE_URL]. */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_SERVER_URL) else putString(KEY_SERVER_URL, v) }.apply()

    companion object {
        const val KEY_AUTO_CHECK = "appdepot.updates.autoCheck"
        const val KEY_AUTO_INSTALL = "appdepot.updates.autoInstall"
        const val KEY_LAST_CHECKED = "appdepot.updates.lastChecked"
        const val KEY_LAST_SEEN = "appdepot.whatsNew.lastSeenVersion"
        const val KEY_SERVER_URL = "appdepot.debug.serverUrl"
    }
}
