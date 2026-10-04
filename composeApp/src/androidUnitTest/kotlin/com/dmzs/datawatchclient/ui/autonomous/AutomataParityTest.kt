package com.dmzs.datawatchclient.ui.autonomous

import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automata Android-missing parity helpers (PWA search + renderCurrentPosition). */
class AutomataParityTest {
    @Test
    fun `search matches title or id case-insensitively`() {
        val prd = PrdDto(id = "abc123", title = "Fix Login Flow")
        assertTrue(matchesAutomataSearch(prd, ""))
        assertTrue(matchesAutomataSearch(prd, "login"))
        assertTrue(matchesAutomataSearch(prd, "ABC1"))
        assertFalse(matchesAutomataSearch(prd, "billing"))
        assertTrue(matchesAutomataSearch(PrdDto(id = "z", name = "named only"), "named"))
    }

    @Test
    fun `current position names the first active task`() {
        val prd =
            PrdDto(
                id = "p",
                status = "running",
                stories =
                    listOf(
                        PrdStoryDto(title = "Setup", tasks = listOf(PrdTaskDto(task = "init", status = "complete"))),
                        PrdStoryDto(
                            title = "Build",
                            tasks =
                                listOf(
                                    PrdTaskDto(task = "a", status = "complete"),
                                    PrdTaskDto(task = "b", status = "verifying"),
                                ),
                        ),
                    ),
            )
        assertEquals("⟳ Story 2: Build · Task 2: b (verifying)", currentPositionLine(prd))
        assertNull(currentPositionLine(prd.copy(status = "approved")))
    }
}
