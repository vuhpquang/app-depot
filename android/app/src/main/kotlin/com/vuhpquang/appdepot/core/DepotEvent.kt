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
