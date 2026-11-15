package com.vuhpquang.appdepot.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vuhpquang.appdepot.core.DepotEvent
import com.vuhpquang.appdepot.core.isUploaded
import com.vuhpquang.appdepot.core.toWireJson
import com.vuhpquang.appdepot.core.trimOutbox
import java.time.Instant

const val HISTORY_CAP = 2000

data class HistoryRow(
    val id: Long,
    val type: String,
    val appSlug: String?,
    val fromVersion: String?,
    val toVersion: String?,
    val message: String?,
    val at: Instant,
)

class HistoryDb(context: Context) : SQLiteOpenHelper(context, "history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, app_slug TEXT, " +
                "from_version TEXT, to_version TEXT, message TEXT, at INTEGER NOT NULL)",
        )
        db.execSQL("CREATE TABLE outbox(id INTEGER PRIMARY KEY AUTOINCREMENT, json TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Writes the history row and the outbox row together (local-only types skip the outbox); keeps the outbox at its cap. */
    fun insert(e: DepotEvent) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insert(
                "history", null,
                ContentValues().apply {
                    put("type", e.type); put("app_slug", e.appSlug); put("from_version", e.fromVersion)
                    put("to_version", e.toVersion); put("message", e.message); put("at", e.at.toEpochMilli())
                },
            )
            if (isUploaded(e.type)) {
                db.insert(
                    "outbox", null,
                    ContentValues().apply { put("json", e.toWireJson()); put("created_at", e.at.toEpochMilli()) },
                )
            }
            db.execSQL("DELETE FROM history WHERE id <= (SELECT MAX(id) FROM history) - $HISTORY_CAP")
            val drop = trimOutbox(outboxCount(db))
            if (drop > 0) {
                db.execSQL("DELETE FROM outbox WHERE id IN (SELECT id FROM outbox ORDER BY id LIMIT $drop)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun outboxCount(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM outbox", null).use { it.moveToFirst(); it.getInt(0) }

    fun outboxRows(): List<Pair<Long, String>> =
        readableDatabase.rawQuery("SELECT id, json FROM outbox ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }

    fun deleteOutbox(ids: List<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.execSQL("DELETE FROM outbox WHERE id IN (${ids.joinToString(",")})")
    }

    fun recent(limit: Int, appSlug: String?): List<HistoryRow> {
        val sql = "SELECT id, type, app_slug, from_version, to_version, message, at FROM history" +
            (if (appSlug != null) " WHERE app_slug = ?" else "") + " ORDER BY id DESC LIMIT $limit"
        return readableDatabase.rawQuery(sql, if (appSlug != null) arrayOf(appSlug) else null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        HistoryRow(
                            c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                            c.getString(4), c.getString(5), Instant.ofEpochMilli(c.getLong(6)),
                        ),
                    )
                }
            }
        }
    }
}
