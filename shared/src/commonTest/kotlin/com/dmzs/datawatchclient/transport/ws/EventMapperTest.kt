package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventMapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private fun decodeData(raw: String): JsonElement = json.parseToJsonElement(raw)

    @Test
    fun `raw_output with lines yields one Output event per line`() {
        val dto =
            WsFrameDto(
                type = "raw_output",
                data = decodeData("""{"session_id":"s1","lines":["foo","bar"]}"""),
                timestamp = "2024-11-14T22:13:20Z",
            )
        val events = dto.toDomainEvents("s1")
        assertEquals(2, events.size)
        val first = events[0] as SessionEvent.Output
        assertEquals("s1", first.sessionId)
        assertTrue(first.body.startsWith("foo"), "body was ${first.body}")
        assertEquals(Instant.parse("2024-11-14T22:13:20Z"), first.ts)
    }

    @Test
    fun `output frames are scoped to the current session`() {
        val dto =
            WsFrameDto(
                type = "raw_output",
                data = decodeData("""{"session_id":"other","lines":["nope"]}"""),
            )
        assertEquals(0, dto.toDomainEvents("s1").size)
    }

    @Test
    fun `needs_input yields a PromptDetected`() {
        val dto =
            WsFrameDto(
                type = "needs_input",
                data = decodeData("""{"session_id":"s1","prompt":"continue?"}"""),
            )
        val e = dto.toDomainEvents("s1").single() as SessionEvent.PromptDetected
        assertEquals("continue?", e.prompt.text)
    }

    @Test
    fun `notification becomes a styled Output event`() {
        val dto =
            WsFrameDto(
                type = "notification",
                data = decodeData("""{"session_id":"s1","message":"hello"}"""),
            )
        val e = dto.toDomainEvents("s1").single() as SessionEvent.Output
        assertTrue(e.body.contains("[notify] hello"), e.body)
    }

    @Test
    fun `alert becomes a styled Output event`() {
        val dto =
            WsFrameDto(
                type = "alert",
                data = decodeData("""{"session_id":"s1","message":"look"}"""),
            )
        val e = dto.toDomainEvents("s1").single() as SessionEvent.Output
        assertTrue(e.body.contains("[alert] look"), e.body)
    }

    @Test
    fun `error frame carries the server message`() {
        val dto =
            WsFrameDto(
                type = "error",
                data = decodeData("""{"message":"boom"}"""),
            )
        val e = dto.toDomainEvents("s1").single() as SessionEvent.Error
        assertEquals("boom", e.message)
    }

    @Test
    fun `unknown type falls back to Unknown event (forward-compat)`() {
        val dto =
            WsFrameDto(
                type = "future_type_xyz",
                data = decodeData("""{"any":"thing"}"""),
            )
        val e = dto.toDomainEvents("s1").single()
        assertIs<SessionEvent.Unknown>(e)
        assertEquals("future_type_xyz", e.type)
    }

    @Test
    fun `sessions frame is skipped (UI sources it from REST)`() {
        val dto =
            WsFrameDto(
                type = "sessions",
                data = decodeData("""{"sessions":[{"id":"s1","state":"running"}]}"""),
            )
        assertEquals(0, dto.toDomainEvents("s1").size)
    }

    @Test
    fun `missing timestamp falls back to now`() {
        val dto =
            WsFrameDto(
                type = "raw_output",
                data = decodeData("""{"session_id":"s1","lines":["x"]}"""),
                timestamp = null,
            )
        // Not asserting an exact time — just that it parses without throwing
        // and produces an event with a non-epoch-0 instant.
        val e = dto.toDomainEvents("s1").first()
        assertTrue(e.ts.toEpochMilliseconds() > 0)
    }

    @Test
    fun `lines without trailing newline are normalised to CRLF`() {
        val dto =
            WsFrameDto(
                type = "raw_output",
                data = decodeData("""{"session_id":"s1","lines":["bare"]}"""),
            )
        val e = dto.toDomainEvents("s1").first() as SessionEvent.Output
        assertTrue(e.body.endsWith("\r\n"), "expected CRLF tail, got: ${e.body}")
    }
}
