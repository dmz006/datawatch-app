package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrdStatusColorTest {
    private val green = Color(0xFF10B981)  // DwSuccess — PWA var(--success)
    private val accentPurple = Color(0xFF7C3AED)  // PWA var(--accent) — approved
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
    @Test fun `revisions_asked same rank as needs_review`() = assertEquals(prdStateRank("needs_review"), prdStateRank("revisions_asked"))
    @Test fun `case-insensitive rank`() = assertEquals(prdStateRank("needs_review"), prdStateRank("NEEDS_REVIEW"))
}
