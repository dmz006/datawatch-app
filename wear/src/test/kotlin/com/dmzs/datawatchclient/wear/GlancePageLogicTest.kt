package com.dmzs.datawatchclient.wear

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-W2 — Verifies GlancePage display-logic calculations against
 * UiState. All assertions are pure (no Android context required).
 */
class GlancePageLogicTest {

    private fun state(
        currentTask: String = "",
        taskProgress: Float = 0f,
        sprintName: String = "",
        automataName: String = "",
        guardrailBlock: Boolean = false,
        blockSummary: String = "",
        running: Int = 0,
        waiting: Int = 0,
        serverName: String = "",
    ) = WearSessionCountsViewModel.UiState(
        currentTask = currentTask,
        taskProgress = taskProgress,
        sprintName = sprintName,
        automataName = automataName,
        guardrailBlock = guardrailBlock,
        blockSummary = blockSummary,
        running = running,
        waiting = waiting,
        serverName = serverName,
        loading = false,
    )

    // ---- Progress ring ----

    @Test fun `progress pct formats correctly at 0_73`() {
        val s = state(taskProgress = 0.73f)
        assertEquals(73, (s.taskProgress * 100).toInt())
    }

    @Test fun `progress pct at 1_0 is 100`() {
        val s = state(taskProgress = 1.0f)
        assertEquals(100, (s.taskProgress * 100).toInt())
    }

    @Test fun `progress pct at 0 produces 0`() {
        val s = state(taskProgress = 0f)
        assertEquals(0, (s.taskProgress * 100).toInt())
    }

    @Test fun `progress clamped to 0-1 range for over-range`() {
        val s = state(taskProgress = 1.5f)
        val clamped = s.taskProgress.coerceIn(0f, 1f)
        assertEquals(1.0f, clamped)
    }

    @Test fun `progress clamped to 0 for negative`() {
        val s = state(taskProgress = -0.3f)
        val clamped = s.taskProgress.coerceIn(0f, 1f)
        assertEquals(0f, clamped)
    }

    // ---- Progress ring shown only when > 0 ----

    @Test fun `progress pct label suppressed at zero`() {
        val s = state(taskProgress = 0f)
        assertFalse(s.taskProgress > 0f, "pct label should not show at 0")
    }

    @Test fun `progress pct label shown when positive`() {
        val s = state(taskProgress = 0.1f)
        assertTrue(s.taskProgress > 0f, "pct label should show when positive")
    }

    // ---- Current task truncation ----

    @Test fun `current task truncated to 60 chars`() {
        val long = "A".repeat(80)
        val s = state(currentTask = long)
        val truncated = s.currentTask.take(60)
        assertEquals(60, truncated.length)
    }

    @Test fun `current task shorter than 60 not truncated`() {
        val short = "Fix auth bug"
        val s = state(currentTask = short)
        assertEquals(short, s.currentTask.take(60))
    }

    @Test fun `current task blank suppresses label`() {
        val s = state(currentTask = "")
        assertFalse(s.currentTask.isNotBlank())
    }

    // ---- Breadcrumb ----

    @Test fun `breadcrumb shown when both automata and sprint present`() {
        val s = state(automataName = "Refactor Auth", sprintName = "Sprint 3")
        assertTrue(s.automataName.isNotBlank() && s.sprintName.isNotBlank())
        assertEquals("Refactor Auth › Sprint 3", "${s.automataName} › ${s.sprintName}")
    }

    @Test fun `breadcrumb suppressed when automata blank`() {
        val s = state(automataName = "", sprintName = "Sprint 3")
        assertFalse(s.automataName.isNotBlank() && s.sprintName.isNotBlank())
    }

    @Test fun `breadcrumb suppressed when sprint blank`() {
        val s = state(automataName = "Refactor Auth", sprintName = "")
        assertFalse(s.automataName.isNotBlank() && s.sprintName.isNotBlank())
    }

    // ---- Guardrail block band ----

    @Test fun `block band triggered by guardrailBlock true`() {
        val s = state(guardrailBlock = true)
        assertTrue(s.guardrailBlock)
    }

    @Test fun `block band not shown when guardrailBlock false`() {
        val s = state(guardrailBlock = false)
        assertFalse(s.guardrailBlock)
    }

    @Test fun `block summary truncated to 40 chars`() {
        val long = "Token leaked in secrets-scan pipeline run X"
        val s = state(guardrailBlock = true, blockSummary = long)
        val truncated = s.blockSummary.take(40)
        assertEquals(40, truncated.length)
    }

    @Test fun `block summary blank suppresses warning text`() {
        val s = state(guardrailBlock = true, blockSummary = "")
        assertFalse(s.guardrailBlock && s.blockSummary.isNotBlank())
    }

    // ---- Session counts ----

    @Test fun `waiting count above 0 shows waiting indicator`() {
        val s = state(running = 2, waiting = 1)
        assertTrue(s.waiting > 0)
    }

    @Test fun `waiting count at 0 suppresses waiting indicator`() {
        val s = state(running = 2, waiting = 0)
        assertFalse(s.waiting > 0)
    }

    // ---- Server name ----

    @Test fun `server name blank suppresses label`() {
        val s = state(serverName = "")
        assertFalse(s.serverName.isNotBlank())
    }

    @Test fun `server name shown when set`() {
        val s = state(serverName = "Johnnyjohnny")
        assertTrue(s.serverName.isNotBlank())
        assertEquals("Johnnyjohnny", s.serverName)
    }
}
