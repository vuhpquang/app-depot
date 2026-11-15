package com.vuhpquang.appdepot.android

import android.util.Log
import com.vuhpquang.appdepot.core.EventsOutcome
import com.vuhpquang.appdepot.core.batchesOf
import com.vuhpquang.appdepot.core.eventsBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EventUploader(private val db: HistoryDb, private val pairing: Pairing, private val api: DepotApi) {
    private val lock = Mutex()

    /** Safe to call often: does nothing when unpaired or when there is nothing to send. */
    suspend fun flush() = lock.withLock {
        val token = pairing.token ?: return@withLock
        for (batch in batchesOf(db.outboxRows())) {
            when (api.postEvents(token, eventsBody(batch.map { it.second }))) {
                EventsOutcome.Accepted -> db.deleteOutbox(batch.map { it.first })
                EventsOutcome.BadBatch -> {
                    Log.w("AppDepot", "server rejected a batch of ${batch.size} events; dropping it")
                    db.deleteOutbox(batch.map { it.first })
                }
                EventsOutcome.Unauthorized -> { pairing.clearToken(); return@withLock }
                EventsOutcome.Retry -> return@withLock
            }
        }
    }
}
