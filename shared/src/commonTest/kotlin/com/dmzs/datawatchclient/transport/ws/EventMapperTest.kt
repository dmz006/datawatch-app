package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class EventMapperTest {

    @Test
    fun `output frame maps to Output event`() {
        val dto = WsFrameDto(
            type = "output",
            sessionId = "s1",
            ts = "2024-11-14T22:13:20Z",
            body = "hello",
            stream = "stdout",
        )
        val e = dto.toDomain("fallback") as SessionEvent.Output
        assertEquals("s1", e.sessionId)
        assertEquals("hello", e.body)
        assertEquals(SessionEvent.Output.Stream.Stdout, e.stream)
        assertEquals(Instant.parse("2024-11-14T22:13:20Z"), e.ts)
    }

    @Test
    fun `stderr stream tag is honored`() {
        val dto = WsFrameDto(type = "output", sessionId = "s1", body = "err", stream = "stderr")
        assertEquals(
            SessionEvent.Output.Stream.Stderr,
            (dto.toDomain("f") as SessionEvent.Output).stream,
        )
    }

    @Test
    fun `state change frame maps both states`() {
        val dto = WsFrameDto(type = "state_change", sessionId = "s1", from = "running", to = "waiting")
        val e = dto.toDomain("f") as SessionEvent.StateChange
        assertEquals(SessionState.Running, e.from)
        assertEquals(SessionState.Waiting, e.to)
    }

    @Test
    fun `prompt detected frame carries prompt text and kind`() {
        val dto = WsFrameDto(
            type = "prompt", sessionId = "s1", prompt = "continue?", promptKind = "approval",
        )
        val e = dto.toDomain("f") as SessionEvent.PromptDetected
        assertEquals("continue?", e.prompt.text)
        assertEquals(
            com.dmzs.datawatchclient.domain.Prompt.Kind.Approval,
            e.prompt.kind,
        )
    }

    @Test
    fun `rate_limited frame carries retry-after when present`() {
        val dto = WsFrameDto(
            type = "rate_limited", sessionId = "s1",
            retryAfter = "2024-11-14T22:20:00Z",
        )
        val e = dto.toDomain("f") as SessionEvent.RateLimited
        assertNotNull(e.retryAfter)
        assertEquals(Instant.parse("2024-11-14T22:20:00Z"), e.retryAfter)
    }

    @Test
    fun `completed frame with exit code`() {
        val dto = WsFrameDto(type = "completed", sessionId = "s1", exitCode = 0)
        val e = dto.toDomain("f") as SessionEvent.Completed
        assertEquals(0, e.exitCode)
    }

    @Test
    fun `error frame carries message`() {
        val dto = WsFrameDto(type = "error", sessionId = "s1", message = "boom")
        val e = dto.toDomain("f") as SessionEvent.Error
        assertEquals("boom", e.message)
    }

    @Test
    fun `unknown type falls back to Unknown event (forward-compat)`() {
        val dto = WsFrameDto(type = "future_type_xyz", sessionId = "s1")
        val e = dto.toDomain("f")
        assertIs<SessionEvent.Unknown>(e)
        assertEquals("future_type_xyz", e.type)
    }

    @Test
    fun `missing session_id uses fallback`() {
        val dto = WsFrameDto(type = "output", sessionId = null, body = "x")
        assertEquals("fallback-sid", dto.toDomain("fallback-sid").sessionId)
    }

    @Test
    fun `missing or bad timestamp falls back to now`() {
        val dto = WsFrameDto(type = "output", sessionId = "s1", body = "x", ts = "nonsense")
        val e = dto.toDomain("s1")
        // ts defaults to Clock.System.now; we can't assert exact value, but
        // it must not be DISTANT_PAST (which would signal "parse failed + no
        // fallback").
        assert(e.ts > Instant.DISTANT_PAST) { "ts fell through to DISTANT_PAST" }
    }
}
