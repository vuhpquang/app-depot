package com.vuhpquang.appdepot.android

import android.content.Context
import android.os.Build
import com.vuhpquang.appdepot.core.PairResult
import java.util.UUID

/** Device identity and the upload token, kept in app-private preferences. */
class Pairing(context: Context, private val api: DepotApi, private val depotVersion: String) {
    private val prefs = context.getSharedPreferences("appdepot.pairing", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }

    val token: String? get() = prefs.getString("token", null)
    val isPaired: Boolean get() = token != null

    suspend fun pair(code: String, name: String = "${Build.MANUFACTURER} ${Build.MODEL}"): PairResult {
        val r = api.pair(code.trim(), deviceId, name, depotVersion)
        if (r is PairResult.Paired) prefs.edit().putString("token", r.token).apply()
        return r
    }

    fun clearToken() = prefs.edit().remove("token").apply()
}
