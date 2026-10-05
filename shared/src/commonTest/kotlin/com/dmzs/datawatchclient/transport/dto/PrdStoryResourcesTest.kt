package com.dmzs.datawatchclient.transport.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrdStoryResourcesTest {
    @Test
    fun `rows join task sessions to envelopes for cpu and rss`() {
        val prd =
            PrdDto(
                id = "p",
                stories =
                    listOf(
                        PrdStoryDto(
                            id = "s1",
                            title = "One",
                            tasks =
                                listOf(
                                    PrdTaskDto(id = "a", status = "completed", sessionId = "h-1"),
                                    PrdTaskDto(id = "b", status = "running", sessionId = "h-2"),
                                ),
                        ),
                        PrdStoryDto(id = "s2", title = "", tasks = listOf(PrdTaskDto(id = "c", status = "pending"))),
                    ),
            )
        val envs =
            listOf(
                StatEnvelopeDto(sessionId = "h-1", cpuPct = 10.0, rssBytes = 100L * 1_048_576),
                StatEnvelopeDto(sessionId = "h-2", cpuPct = 30.0, rssBytes = 50L * 1_048_576),
                StatEnvelopeDto(sessionId = null, cpuPct = 99.0),
            )
        val rows = PrdStoryResources.rows(prd, envs)
        assertEquals(1, rows[0].done)
        assertEquals(2, rows[0].total)
        assertTrue(rows[0].active)
        assertEquals("CPU 20% · 150 MB", rows[0].resourceLabel)
        assertEquals(0.5, rows[0].fraction)
        assertEquals("s2", rows[1].title)
        assertFalse(rows[1].hasCpu)
        assertEquals("", rows[1].resourceLabel)
    }
}
