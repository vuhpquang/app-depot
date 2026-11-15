package com.vuhpquang.appdepot.android

import com.vuhpquang.appdepot.core.EventsOutcome
import com.vuhpquang.appdepot.core.PairResult
import com.vuhpquang.appdepot.core.parseEventsStatus
import com.vuhpquang.appdepot.core.parsePairResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

const val DEPOT_BASE_URL = "https://vuhpquang.com"

class DepotApi(private val baseUrl: () -> String, private val versionName: String) {
    private data class Reply(val status: Int, val body: String)

    private suspend fun post(path: String, json: String, bearer: String?): Reply = withContext(Dispatchers.IO) {
        val conn = URL("${baseUrl().trimEnd('/')}$path").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "AppDepot-Android/$versionName")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            Reply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    suspend fun pair(code: String, deviceId: String, name: String, depotVersion: String): PairResult = try {
        val body = buildJsonObject {
            put("code", code); put("deviceId", deviceId); put("name", name)
            put("platform", "android"); put("depotVersion", depotVersion)
        }.toString()
        val r = post("/api/depot/devices", body, null)
        parsePairResponse(r.status, r.body)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PairResult.Failed("Could not reach the server. Check your connection and try again.")
    }

    /** Network errors surface as [EventsOutcome.Retry]. */
    suspend fun postEvents(token: String, body: String): EventsOutcome = try {
        parseEventsStatus(post("/api/depot/events", body, token).status)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EventsOutcome.Retry
    }
}
