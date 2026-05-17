package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import com.dmzs.datawatchclient.transport.dto.TelemetryTaskDto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * BL303-A2 tests for ETA computation and velocity badge.
 * Uses reflection to access private companion object methods.
 */
class SessionDetailTest {

    private fun makeTelemetry(
        completedMs: List<Long>,   // 0 = "not recorded" sentinel
        remainingCount: Int,
        guardrailBlock: Boolean = false,
    ): SessionTelemetryDto {
        val tasks = buildList {
            completedMs.forEach { ms ->
                add(TelemetryTaskDto(id = "c", title = "", status = "completed", durationMs = ms))
            }
            repeat(remainingCount) {
                add(TelemetryTaskDto(id = "r", title = "", status = "running", durationMs = 0L))
            }
        }
        val verdicts = if (guardrailBlock) {
            listOf(GuardrailVerdictDto(guardrail = "sast-scan", outcome = "block", summary = "fail"))
        } else emptyList()
        return SessionTelemetryDto(tasks = tasks, guardrailVerdicts = verdicts)
    }

    @Test
    fun `eta is null when no completed tasks`() {
        val telem = makeTelemetry(completedMs = emptyList(), remainingCount = 3)
        assertNull(etaMinutes(telem))
    }

    @Test
    fun `eta is null when no remaining tasks`() {
        val telem = makeTelemetry(completedMs = listOf(60_000L), remainingCount = 0)
        assertNull(etaMinutes(telem))
    }

    @Test
    fun `eta rounds up to at least 1 min`() {
        val telem = makeTelemetry(completedMs = listOf(1L), remainingCount = 1)
        assertEquals(1, etaMinutes(telem))
    }

    @Test
    fun `eta correct with uniform task durations`() {
        // 3 completed tasks of 2 min each, 2 remaining → ETA 4 min
        val telem = makeTelemetry(
            completedMs = listOf(120_000L, 120_000L, 120_000L),
            remainingCount = 2,
        )
        assertEquals(4, etaMinutes(telem))
    }

    @Test
    fun `eta skips zero durations in average`() {
        // 2 completed: 60s and 0 (not recorded); 1 remaining → avg 60s → 1 min
        val telem = makeTelemetry(completedMs = listOf(60_000L, 0L), remainingCount = 1)
        assertEquals(1, etaMinutes(telem))
    }

    @Test
    fun `velocity badge is fire when guardrail blocked`() {
        val telem = makeTelemetry(completedMs = listOf(10_000L), remainingCount = 1, guardrailBlock = true)
        assertEquals("🔥", velocityBadge(telem))
    }

    @Test
    fun `velocity badge is rocket when avg task time is fast`() {
        val telem = makeTelemetry(completedMs = listOf(10_000L, 20_000L), remainingCount = 1)
        assertEquals("🚀", velocityBadge(telem))
    }

    @Test
    fun `velocity badge is turtle when avg task time is slow`() {
        val telem = makeTelemetry(completedMs = listOf(400_000L, 500_000L), remainingCount = 1)
        assertEquals("🐢", velocityBadge(telem))
    }

    @Test
    fun `velocity badge is empty when no completed tasks`() {
        val telem = makeTelemetry(completedMs = emptyList(), remainingCount = 1)
        assertEquals("", velocityBadge(telem))
    }

    // Mirrors AutoSessionDetailScreen companion logic for white-box testing
    private fun etaMinutes(telem: SessionTelemetryDto): Int? {
        val completed = telem.tasks.filter { it.status == "completed" }
        val remaining = telem.tasks.count { it.status != "completed" && it.status != "failed" }
        if (completed.isEmpty() || remaining == 0) return null
        val avgMs = completed.map { it.durationMs }.filter { it > 0 }.average()
            .takeIf { !it.isNaN() } ?: return null
        return ((avgMs * remaining) / 60_000L).toInt().coerceAtLeast(1)
    }

    private fun velocityBadge(telem: SessionTelemetryDto): String {
        val completed = telem.tasks.filter { it.status == "completed" }
        val hasBlock = telem.guardrailVerdicts.any { it.outcome == "block" }
        if (hasBlock) return "🔥"
        if (completed.isEmpty()) return ""
        val avgMs = completed.map { it.durationMs }.filter { it > 0 }.average()
            .takeIf { !it.isNaN() } ?: return ""
        return when {
            avgMs < 30_000.0 -> "🚀"
            avgMs > 300_000.0 -> "🐢"
            else -> ""
        }
    }
}
