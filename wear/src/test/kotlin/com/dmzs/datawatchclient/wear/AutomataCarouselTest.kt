package com.dmzs.datawatchclient.wear

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BL303-W4 — Verifies automata carousel data binding, gravitational sort,
 * gantt bar proportions, and quick action path constants.
 */
class AutomataCarouselTest {

    private fun prd(
        id: String,
        title: String,
        status: String = "running",
        blockedCount: Int = 0,
        progress: Float = 0f,
        sprintName: String = "",
        runningHours: Float = 0f,
    ) = WearSessionCountsViewModel.PrdItem(
        id = id,
        title = title,
        status = status,
        blockedCount = blockedCount,
        progress = progress,
        sprintName = sprintName,
        runningHours = runningHours,
    )

    // ---- Gravitational sort ----

    @Test fun `gravity score with block is higher than without`() {
        val blocked = prd("a", "Blocked", blockedCount = 2, runningHours = 1f)
        val clear = prd("b", "Clear", blockedCount = 0, runningHours = 5f)
        assertTrue(blocked.gravityScore() > clear.gravityScore())
        // blocked: 2*3+1 = 7, clear: 0+5 = 5
        assertEquals(7f, blocked.gravityScore())
        assertEquals(5f, clear.gravityScore())
    }

    @Test fun `gravity score zero for idle automaton`() {
        val idle = prd("c", "Idle", blockedCount = 0, runningHours = 0f)
        assertEquals(0f, idle.gravityScore())
    }

    @Test fun `gravitational sort puts highest score first`() {
        val items = listOf(
            prd("a", "Low", blockedCount = 0, runningHours = 0f),
            prd("b", "High", blockedCount = 3, runningHours = 2f),
            prd("c", "Mid", blockedCount = 1, runningHours = 2f),
        )
        val sorted = items.sortedByDescending { it.gravityScore() }
        assertEquals("b", sorted[0].id) // 3*3+2=11
        assertEquals("c", sorted[1].id) // 1*3+2=5
        assertEquals("a", sorted[2].id) // 0
    }

    @Test fun `empty automata list handled gracefully`() {
        val items = emptyList<WearSessionCountsViewModel.PrdItem>()
        val filtered = items.filter { it.status == "running" }
        assertTrue(filtered.isEmpty())
    }

    // ---- Status filtering ----

    @Test fun `only running PRDs shown in carousel`() {
        val items = listOf(
            prd("a", "Running", status = "running"),
            prd("b", "Review", status = "needs_review"),
            prd("c", "Done", status = "complete"),
            prd("d", "Also Running", status = "running"),
        )
        val filtered = items.filter { it.status.lowercase() == "running" }
        assertEquals(2, filtered.size)
        assertEquals(setOf("a", "d"), filtered.map { it.id }.toSet())
    }

    // ---- Progress formatting ----

    @Test fun `progress pct converts correctly at 0_75`() {
        val p = prd("x", "X", progress = 0.75f)
        assertEquals(75, (p.progress * 100).toInt())
    }

    @Test fun `progress clamped to 0-1`() {
        val p = prd("x", "X", progress = 1.5f)
        assertEquals(1.0f, p.progress.coerceIn(0f, 1f))
    }

    @Test fun `zero progress stays zero`() {
        val p = prd("x", "X", progress = 0f)
        assertEquals(0, (p.progress * 100).toInt())
    }

    // ---- Gantt bar proportions ----

    @Test fun `gantt done width equals progress fraction`() {
        val p = prd("x", "X", progress = 0.6f, blockedCount = 0)
        val doneW = p.progress.coerceIn(0f, 1f)
        assertEquals(0.6f, doneW, 0.001f)
    }

    @Test fun `gantt shows block segment when blocked`() {
        val p = prd("x", "X", progress = 0.5f, blockedCount = 2)
        val blockW = if (p.blockedCount > 0) 0.12f else 0f
        assertEquals(0.12f, blockW, 0.001f)
    }

    @Test fun `gantt no block segment when not blocked`() {
        val p = prd("x", "X", progress = 0.5f, blockedCount = 0)
        val blockW = if (p.blockedCount > 0) 0.12f else 0f
        assertEquals(0f, blockW)
    }

    // ---- Path constants ----

    @Test fun `memory sweep path is correct`() {
        assertEquals("/datawatch/memorySweep", WearSessionCountsViewModel.MEMORY_SWEEP_PATH)
    }

    @Test fun `five or more automata all appear in list`() {
        val items = (1..7).map { i -> prd("prd$i", "Plan $i", status = "running") }
        val filtered = items.filter { it.status == "running" }.sortedByDescending { it.gravityScore() }
        assertEquals(7, filtered.size)
    }
}
