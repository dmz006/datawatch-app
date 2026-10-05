package com.dmzs.datawatchclient.util

import com.dmzs.datawatchclient.transport.dto.OrchestratorEdgeDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorNodeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrdDagLayoutTest {
    @Test
    fun `empty graph lays out nothing`() {
        val r = PrdDagLayout.layout(emptyList(), emptyList())
        assertTrue(r.nodes.isEmpty())
        assertEquals(0.0, r.height)
    }

    @Test
    fun `dependencies go on lower layers and edges follow nodes`() {
        val nodes =
            listOf(
                OrchestratorNodeDto(id = "s1", name = "Story one", status = "Running", kind = "story"),
                OrchestratorNodeDto(id = "t1", name = "A very long task name here", status = "pending", kind = "task"),
                OrchestratorNodeDto(id = "t2", name = null, status = "done", kind = "task"),
            )
        val edges =
            listOf(
                OrchestratorEdgeDto(from = "s1", to = "t1"),
                OrchestratorEdgeDto(from = "s1", to = "t2"),
                OrchestratorEdgeDto(from = "s1", to = "missing"),
            )
        val r = PrdDagLayout.layout(nodes, edges)
        val byId = r.nodes.associateBy { it.id }
        assertEquals("S", byId.getValue("s1").kindGlyph)
        assertEquals("T", byId.getValue("t1").kindGlyph)
        assertEquals("running", byId.getValue("s1").status)
        assertEquals("A very long t…", byId.getValue("t1").label)
        assertEquals("t2", byId.getValue("t2").label)
        assertTrue(byId.getValue("t1").y > byId.getValue("s1").y)
        assertEquals(byId.getValue("t1").y, byId.getValue("t2").y)
        // Layer of two is centred on the axis.
        assertEquals(0.0, byId.getValue("t1").x + byId.getValue("t2").x)
        assertEquals(2, r.edges.size)
        assertEquals(2 * (2 * PrdDagLayout.NODE_RADIUS + PrdDagLayout.V_SPACING), r.height)
    }
}
