package com.vuhpquang.appdepot.core

import java.time.Instant

data class DepotEvent(
    val type: String,
    val appSlug: String? = null,
    val fromVersion: String? = null,
    val toVersion: String? = null,
    val message: String? = null,
    val at: Instant = Instant.now(),
)

fun interface EventSink {
    fun record(e: DepotEvent)
}

/**
 * Event types that stay in local history only. The server's event type list is closed, so anything
 * it doesn't know (e.g. `uninstall`) must never reach the outbox.
 */
val LOCAL_ONLY_EVENT_TYPES = setOf("uninstall")

fun isUploaded(type: String): Boolean = type !in LOCAL_ONLY_EVENT_TYPES
