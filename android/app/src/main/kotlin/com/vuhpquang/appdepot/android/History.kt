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

    fun recent(limit: Int = 200, appSlug: String? = null): List<HistoryRow> = db.recent(limit, appSlug)
}
