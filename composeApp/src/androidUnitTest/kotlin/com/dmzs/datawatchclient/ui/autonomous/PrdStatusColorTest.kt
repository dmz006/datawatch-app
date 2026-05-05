package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class PrdStatusColorTest {
    private val green = Color(0xFF22C55E)
    private val teal = Color(0xFF14B8A6)
    private val amber = Color(0xFFF59E0B)
    private val red = Color(0xFFEF4444)
    private val purple = Color(0xFFA855F7)
    private val grey = Color(0xFF94A3B8)

    @Test fun `running maps to green`() = assertEquals(green, prdStatusColor("running"))
    @Test fun `approved maps to teal`() = assertEquals(teal, prdStatusColor("approved"))
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
