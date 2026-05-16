package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T18 test-debt: additional round-trip coverage for SessionDto → Session that
 * complements the existing MappersTest.  Each test targets a specific
 * mapper branch that was not yet covered.
 */
class SessionMapperTest {

    @Test
    fun `agentId is propagated from dto`() {
        val dto = SessionDto(
            id = "ag1",
            state = "running",
            agentId = "worker-7af3",
        )
        val session = dto.toDomain("srv-1")
        assertEquals("worker-7af3", session.agentId)
    }

    @Test
    fun `llmRef and computeNodeRef are propagated`() {
        val dto = SessionDto(
            id = "r1",
            state = "running",
            llmRef = "my-llm",
            computeNodeRef = "gpu-node-1",
        )
        val session = dto.toDomain("srv-1")
        assertEquals("my-llm", session.llmRef)
        assertEquals("gpu-node-1", session.computeNodeRef)
    }

    @Test
    fun `outputMode and inputMode are propagated`() {
        val dto = SessionDto(
            id = "m1",
            state = "running",
            outputMode = "chat",
            inputMode = "chat",
        )
        val session = dto.toDomain("srv-1")
        assertEquals("chat", session.outputMode)
        assertEquals("chat", session.inputMode)
    }

    @Test
    fun `promptContext overrides lastPrompt when present`() {
        // The mapper exposes promptContext as a separate field; lastPrompt
        // falls back to pendingInput per existing MappersTest coverage.
        // Here we verify promptContext round-trips independently.
        val dto = SessionDto(
            id = "pc1",
            state = "waiting_input",
            lastPrompt = "Run tests?",
            promptContext = "Additional context\nLine 2",
        )
        val session = dto.toDomain("srv-1")
        assertEquals("Additional context\nLine 2", session.promptContext)
        // lastPrompt still mapped from lastPrompt field directly
        assertEquals("Run tests?", session.lastPrompt)
    }

    @Test
    fun `name field is propagated for display renaming`() {
        val dto = SessionDto(
            id = "n1",
            state = "running",
            name = "My renamed session",
        )
        val session = dto.toDomain("srv-1")
        assertEquals("My renamed session", session.name)
    }

    @Test
    fun `serverProfileId from caller is set on domain object`() {
        val dto = SessionDto(id = "x", state = "new")
        val session = dto.toDomain("profile-abc")
        assertEquals("profile-abc", session.serverProfileId)
    }

    @Test
    fun `waiting_input state parses to Waiting`() {
        val dto = SessionDto(id = "w1", state = "waiting_input")
        assertEquals(SessionState.Waiting, dto.toDomain("srv-1").state)
    }

    @Test
    fun `createdAt and updatedAt parse RFC3339 correctly`() {
        val dto = SessionDto(
            id = "ts1",
            state = "running",
            createdAt = "2026-01-01T12:00:00Z",
            updatedAt = "2026-01-01T13:00:00Z",
        )
        val session = dto.toDomain("srv-1")
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), session.createdAt)
        assertEquals(Instant.parse("2026-01-01T13:00:00Z"), session.lastActivityAt)
    }

    @Test
    fun `null agentId maps to null on session`() {
        val dto = SessionDto(id = "na1", state = "running", agentId = null)
        assertNull(dto.toDomain("srv-1").agentId)
    }
}
