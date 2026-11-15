package com.vuhpquang.appdepot.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val MAX_MESSAGE = 2000

@Serializable
data class WireEvent(
    val type: String,
    val appSlug: String? = null,
    val fromVersion: String? = null,
    val toVersion: String? = null,
    val message: String? = null,
    val at: String,
)

private val wireJson = Json { explicitNulls = false }

fun DepotEvent.toWireJson(): String = wireJson.encodeToString(
    WireEvent.serializer(),
    WireEvent(type, appSlug, fromVersion, toVersion, message?.take(MAX_MESSAGE), at.toString()),
)

/** Splits outbox rows (id, json) into server-sized batches, keeping order. */
fun batchesOf(events: List<Pair<Long, String>>, max: Int = 200): List<List<Pair<Long, String>>> =
    events.chunked(max)

fun eventsBody(jsonEvents: List<String>): String = """{"events":[${jsonEvents.joinToString(",")}]}"""

/** Rows to drop (oldest first) so the outbox never grows past [cap]. */
fun trimOutbox(count: Int, cap: Int = 1000): Int = (count - cap).coerceAtLeast(0)

sealed interface PairResult {
    data class Paired(val token: String) : PairResult
    data object BadCode : PairResult
    data class Failed(val message: String) : PairResult
}

fun parsePairResponse(status: Int, body: String): PairResult = when (status) {
    201 -> runCatching {
        val token = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
        if (token.isNullOrEmpty()) PairResult.Failed("Pairing reply had no token") else PairResult.Paired(token)
    }.getOrElse { PairResult.Failed("Pairing reply was not understood") }
    403 -> PairResult.BadCode
    else -> PairResult.Failed("Pairing failed (HTTP $status)")
}

enum class EventsOutcome { Accepted, Unauthorized, BadBatch, Retry }

fun parseEventsStatus(status: Int): EventsOutcome = when (status) {
    in 200..299 -> EventsOutcome.Accepted
    401 -> EventsOutcome.Unauthorized
    400 -> EventsOutcome.BadBatch
    else -> EventsOutcome.Retry
}
