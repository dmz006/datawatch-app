package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrdStatusColorTest {
    private val green = Color(0xFF10B981) // DwSuccess — PWA var(--success)
    private val accentPurple = Color(0xFF8B5CF6) // BL28: raised from 7C3AED for WCAG AA — approved
    private val amber = Color(0xFFF59E0B)
    private val red = Color(0xFFEF4444)
    private val purple = Color(0xFFA855F7)
    private val grey = Color(0xFF94A3B8)

    @Test fun `running maps to green`() = assertEquals(green, prdStatusColor("running"))

    @Test fun `approved maps to teal`() = assertEquals(accentPurple, prdStatusColor("approved"))

    @Test fun `needs_review maps to amber`() = assertEquals(amber, prdStatusColor("needs_review"))

    @Test fun `revisions_asked maps to amber`() = assertEquals(amber, prdStatusColor("revisions_asked"))

    @Test fun `awaiting_approval maps to amber`() = assertEquals(amber, prdStatusColor("awaiting_approval"))

    @Test fun `blocked maps to red`() = assertEquals(red, prdStatusColor("blocked"))

    @Test fun `rejected maps to red`() = assertEquals(red, prdStatusColor("rejected"))

    @Test fun `decomposing maps to purple`() = assertEquals(purple, prdStatusColor("decomposing"))

    @Test fun `draft maps to grey`() = assertEquals(grey, prdStatusColor("draft"))

    @Test fun `complete maps to grey`() = assertEquals(grey, prdStatusColor("complete"))

    @Test fun `completed maps to grey`() = assertEquals(grey, prdStatusColor("completed"))

    @Test fun `cancelled maps to grey`() = assertEquals(grey, prdStatusColor("cancelled"))

    @Test fun `unknown status maps to grey`() = assertEquals(grey, prdStatusColor("unknown_status"))

    @Test fun `status matching is case-insensitive`() = assertEquals(green, prdStatusColor("RUNNING"))
}

class PrdStateRankTest {
    @Test fun `needs_review ranks before running`() = assertTrue(prdStateRank("needs_review") < prdStateRank("running"))

    @Test fun `running ranks before decomposing`() = assertTrue(prdStateRank("running") < prdStateRank("decomposing"))

    @Test fun `decomposing ranks before approved`() = assertTrue(prdStateRank("decomposing") < prdStateRank("approved"))

    @Test fun `approved ranks before complete`() = assertTrue(prdStateRank("approved") < prdStateRank("complete"))

    @Test fun `revisions_asked same rank as needs_review`() =
        assertEquals(
            prdStateRank("needs_review"),
            prdStateRank("revisions_asked"),
        )

    @Test fun `case-insensitive rank`() = assertEquals(prdStateRank("needs_review"), prdStateRank("NEEDS_REVIEW"))
}

// Sprint 24 (BL293) — pin/sort + action-gate logic ─────────────────────────

/** Mirrors the [isApprovalState] private function in AutonomousScreen.kt. */
private fun isApprovalState(statusLower: String) =
    statusLower in setOf("needs_review", "awaiting_approval", "revisions_asked")

/** Mirrors the [isTerminal] derived val in PrdRow composable. */
private fun isTerminal(statusLower: String) =
    statusLower in setOf("completed", "complete", "cancelled", "canceled", "rejected", "archived")

class PrdActionGateTest {
    // Approve gate — Sprint 24
    @Test fun `needs_review triggers approve button`() = assertTrue(isApprovalState("needs_review"))
    @Test fun `awaiting_approval triggers approve button`() = assertTrue(isApprovalState("awaiting_approval"))
    @Test fun `revisions_asked triggers approve button`() = assertTrue(isApprovalState("revisions_asked"))
    @Test fun `running does not trigger approve button`() = assertTrue(!isApprovalState("running"))
    @Test fun `done does not trigger approve button`() = assertTrue(!isApprovalState("done"))

    // Cancel gate — Sprint 24
    @Test fun `running shows cancel`() = assertTrue(!isTerminal("running"))
    @Test fun `needs_review shows cancel`() = assertTrue(!isTerminal("needs_review"))
    @Test fun `completed hides cancel`() = assertTrue(isTerminal("completed"))
    @Test fun `cancelled hides cancel`() = assertTrue(isTerminal("cancelled"))
    @Test fun `rejected hides cancel`() = assertTrue(isTerminal("rejected"))
    @Test fun `archived hides cancel`() = assertTrue(isTerminal("archived"))
}

class PrdSortTest {
    private fun prd(id: String, status: String) =
        com.dmzs.datawatchclient.transport.dto.PrdDto(id = id, name = id, status = status)

    @Test
    fun `pinned PRD sorts before unpinned with same status`() {
        val pinned = prd("pinned", "running")
        val unpinned = prd("free", "running")
        val pinnedIds = setOf("pinned")
        val sorted = listOf(unpinned, pinned).sortedWith(
            compareBy({ if (it.id in pinnedIds) 0 else 1 }, { prdStateRank(it.status) })
        )
        assertEquals("pinned", sorted.first().id)
    }

    @Test
    fun `needs_review sorts before running in unpinned list`() {
        val r = prd("r", "running")
        val nr = prd("nr", "needs_review")
        val sorted = listOf(r, nr).sortedWith(
            compareBy({ 1 }, { prdStateRank(it.status) })
        )
        assertEquals("nr", sorted.first().id)
    }

    @Test
    fun `terminal PRDs sort after active ones`() {
        val done = prd("done", "completed")
        val active = prd("active", "running")
        val sorted = listOf(done, active).sortedWith(
            compareBy({ 1 }, { prdStateRank(it.status) })
        )
        assertEquals("active", sorted.first().id)
    }
}
