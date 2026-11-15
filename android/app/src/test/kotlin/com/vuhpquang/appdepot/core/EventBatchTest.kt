package com.vuhpquang.appdepot.core

import java.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventBatchTest {
    @Test fun `450 events split 200 200 50`() {
        val rows = (1L..450L).map { it to "{}" }
        assertEquals(listOf(200, 200, 50), batchesOf(rows).map { it.size })
        assertEquals(1L, batchesOf(rows).first().first().first)
    }

    @Test fun `empty gives no batches`() = assertTrue(batchesOf(emptyList()).isEmpty())

    @Test fun `body wraps events array`() {
        assertEquals("""{"events":[{"type":"a"},{"type":"b"}]}""", eventsBody(listOf("""{"type":"a"}""", """{"type":"b"}""")))
    }

    @Test fun `1005 rows drop 5`() {
        assertEquals(5, trimOutbox(1005))
        assertEquals(0, trimOutbox(1000))
        assertEquals(0, trimOutbox(3))
    }

    @Test fun `wire json omits nulls and uses iso utc`() {
        val json = DepotEvent("check", message = "2 updates", at = Instant.parse("2026-11-15T10:00:00Z")).toWireJson()
        assertEquals("""{"type":"check","message":"2 updates","at":"2026-11-15T10:00:00Z"}""", json)
        assertFalse(json.contains("null"))
    }

    @Test fun `wire json truncates long message to 2000`() {
        val json = DepotEvent("install_failed", appSlug = "x", message = "a".repeat(3000)).toWireJson()
        val decoded = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject["message"]!!.jsonPrimitive.content
        assertEquals(2000, decoded.length)
    }
}
