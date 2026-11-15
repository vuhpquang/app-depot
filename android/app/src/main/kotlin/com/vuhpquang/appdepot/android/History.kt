package com.vuhpquang.appdepot.android

import android.content.Context
import android.util.Log
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.EventSink

/** Local history plus the upload outbox. [onRecorded] lets the app kick an upload after each event. */
class History(context: Context, private val onRecorded: () -> Unit = {}) : EventSink {
    val db = HistoryDb(context.applicationContext)

    override fun record(e: DepotEvent) {
        runCatching { db.insert(e) }
            .onFailure { Log.w("AppDepot", "could not record ${e.type}", it); return }
        onRecorded()
    }

    /**
     * A `check` row only when it says something new: repeated identical checks collapse into one.
     * `depot_started` rows (hidden telemetry, one per launch) don't count as "something in between".
     */
    fun recordCheck(message: String) {
        val last = recent(20).firstOrNull { it.type != "depot_started" }
        if (last != null && last.type == "check" && last.message == message) return
        record(DepotEvent("check", message = message))
    }

    fun recent(limit: Int = 200, appSlug: String? = null): List<HistoryRow> = db.recent(limit, appSlug)
}
