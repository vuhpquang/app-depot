package com.vuhpquang.appdepot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingResponseTest {
    @Test fun `201 gives token`() {
        val r = parsePairResponse(201, """{"data":{"deviceId":"d","token":"t"},"error":null,"status":201}""")
        assertEquals(PairResult.Paired("t"), r)
    }

    @Test fun `403 is bad code`() =
        assertEquals(PairResult.BadCode, parsePairResponse(403, """{"data":null,"error":"bad","status":403}"""))

    @Test fun `500 fails`() = assertTrue(parsePairResponse(500, "oops") is PairResult.Failed)

    @Test fun `201 with garbage fails`() = assertTrue(parsePairResponse(201, "nope") is PairResult.Failed)

    @Test fun `400 fails`() = assertTrue(parsePairResponse(400, "{}") is PairResult.Failed)

    @Test fun `events status mapping`() {
        assertEquals(EventsOutcome.Accepted, parseEventsStatus(201))
        assertEquals(EventsOutcome.Unauthorized, parseEventsStatus(401))
        assertEquals(EventsOutcome.BadBatch, parseEventsStatus(400))
        assertEquals(EventsOutcome.Retry, parseEventsStatus(503))
    }
}
