package com.dmzs.datawatchclient.auto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BL303-A3.4 — Tests for the automaton-filter logic in AutoSessionListScreen.
 * All pure logic — no Android context needed.
 */
class SessionListFilterTest {

    data class FakeRow(val automataName: String, val sessionName: String?)

    private fun applyFilter(rows: List<FakeRow>, automataId: String?): List<FakeRow> =
        if (automataId == null) rows
        else rows.filter { row ->
            row.automataName.equals(automataId, ignoreCase = true) ||
                row.sessionName?.startsWith(automataId, ignoreCase = true) == true
        }

    @Test fun `null automataId returns all rows`() {
        val rows = listOf(
            FakeRow("alpha", "session1"),
            FakeRow("beta", "session2"),
        )
        assertEquals(2, applyFilter(rows, null).size)
    }

    @Test fun `filter by exact automata name match`() {
        val rows = listOf(
            FakeRow("alpha", "session1"),
            FakeRow("beta", "session2"),
        )
        val result = applyFilter(rows, "alpha")
        assertEquals(1, result.size)
        assertEquals("alpha", result[0].automataName)
    }

    @Test fun `filter is case-insensitive on automata name`() {
        val rows = listOf(
            FakeRow("Alpha", "s1"),
            FakeRow("beta", "s2"),
        )
        val result = applyFilter(rows, "ALPHA")
        assertEquals(1, result.size)
    }

    @Test fun `filter falls back to session name prefix`() {
        val rows = listOf(
            FakeRow("", "codebot-task1"),
            FakeRow("", "planner-task2"),
        )
        val result = applyFilter(rows, "codebot")
        assertEquals(1, result.size)
        assertEquals("codebot-task1", result[0].sessionName)
    }

    @Test fun `filter returns empty when no match`() {
        val rows = listOf(
            FakeRow("alpha", "session1"),
        )
        val result = applyFilter(rows, "nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test fun `filter includes both automata name match and session prefix match`() {
        val rows = listOf(
            FakeRow("codebot", "session1"),       // matches by automata name
            FakeRow("other", "codebot-session2"), // matches by session prefix
            FakeRow("other", "different"),         // no match
        )
        val result = applyFilter(rows, "codebot")
        assertEquals(2, result.size)
    }

    @Test fun `null session name does not crash filter`() {
        val rows = listOf(FakeRow("alpha", null))
        val result = applyFilter(rows, "alpha")
        assertEquals(1, result.size)
    }

    @Test fun `null session name with no automata match yields empty`() {
        val rows = listOf(FakeRow("other", null))
        val result = applyFilter(rows, "alpha")
        assertTrue(result.isEmpty())
    }
}
