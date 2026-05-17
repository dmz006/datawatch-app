package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import com.dmzs.datawatchclient.transport.dto.TelemetrySprintDto
import com.dmzs.datawatchclient.transport.dto.TelemetryTaskDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-W1 — Verifies SessionTelemetryDto fields serialize/deserialize
 * correctly so WearSyncService can publish them to the DataLayer safely.
 */
class TelemetryDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `telemetry dto has default values when empty`() {
        val dto = SessionTelemetryDto()
        assertEquals("", dto.currentTask)
        assertEquals(0f, dto.progress)
        assertTrue(dto.tasks.isEmpty())
        assertTrue(dto.guardrailVerdicts.isEmpty())
    }

    @Test
    fun `guardrail block detection from verdicts`() {
        val dto = SessionTelemetryDto(
            guardrailVerdicts = listOf(
                GuardrailVerdictDto(guardrail = "sast-scan", outcome = "pass", summary = ""),
                GuardrailVerdictDto(guardrail = "secrets-scan", outcome = "block", summary = "Token found"),
            ),
        )
        val hasBlock = dto.guardrailVerdicts.any { it.outcome == "block" }
        assertTrue(hasBlock)
        val blockSummary = dto.guardrailVerdicts.firstOrNull { it.outcome == "block" }?.summary
        assertEquals("Token found", blockSummary)
    }

    @Test
    fun `no block when all verdicts pass`() {
        val dto = SessionTelemetryDto(
            guardrailVerdicts = listOf(
                GuardrailVerdictDto(guardrail = "sast-scan", outcome = "pass", summary = ""),
            ),
        )
        val hasBlock = dto.guardrailVerdicts.any { it.outcome == "block" }
        assertFalse(hasBlock)
    }

    @Test
    fun `sprint fields serialize correctly`() {
        val dto = SessionTelemetryDto(
            sprint = TelemetrySprintDto(
                name = "Sprint 12",
                id = "s12",
                automata = "Refactor Auth",
                automataId = "a1",
                task = "Update middleware",
                taskId = "t7",
            ),
            progress = 0.73f,
        )
        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<SessionTelemetryDto>(encoded)
        assertEquals("Sprint 12", decoded.sprint?.name)
        assertEquals("Refactor Auth", decoded.sprint?.automata)
        assertEquals(0.73f, decoded.progress, 0.001f)
    }

    @Test
    fun `task list serializes with duration`() {
        val dto = SessionTelemetryDto(
            tasks = listOf(
                TelemetryTaskDto(id = "t1", title = "Write tests", status = "completed", durationMs = 45_000L),
                TelemetryTaskDto(id = "t2", title = "Fix bug", status = "running", durationMs = 0L),
            ),
        )
        val encoded = json.encodeToString(dto)
        val decoded = json.decodeFromString<SessionTelemetryDto>(encoded)
        assertEquals(2, decoded.tasks.size)
        assertEquals(45_000L, decoded.tasks[0].durationMs)
        assertEquals("completed", decoded.tasks[0].status)
        assertEquals("running", decoded.tasks[1].status)
    }

    @Test
    fun `progress field is float between 0 and 1`() {
        val dto = SessionTelemetryDto(progress = 0.5f)
        assertEquals(0.5f, dto.progress)
        assertTrue(dto.progress in 0f..1f)
    }

    @Test
    fun `telemetry from empty json uses defaults`() {
        val decoded = json.decodeFromString<SessionTelemetryDto>("{}")
        assertEquals("", decoded.currentTask)
        assertEquals(0f, decoded.progress)
        assertTrue(decoded.guardrailVerdicts.isEmpty())
    }
}
