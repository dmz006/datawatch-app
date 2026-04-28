package com.dmzs.datawatchclient.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for the response-noise filter ported from
 * datawatch v5.26.31 (issue #15). Each test names the predicate it
 * exercises so a regression makes it obvious which branch broke.
 */
class ResponseNoiseFilterTest {
    @Test
    fun `blank input returns empty`() {
        assertEquals("", ResponseNoiseFilter.strip(""))
        assertEquals("", ResponseNoiseFilter.strip("   \n\n  "))
    }

    @Test
    fun `pure prose passes through unchanged`() {
        val src = "Hello, here is the analysis you asked for.\nIt looks fine."
        assertEquals(src, ResponseNoiseFilter.strip(src))
    }

    @Test
    fun `pure box-drawing line is dropped`() {
        assertTrue(ResponseNoiseFilter.isPureBoxDrawing("─────────────"))
        assertTrue(ResponseNoiseFilter.isPureBoxDrawing("╭───────────╮"))
        assertFalse(ResponseNoiseFilter.isPureBoxDrawing("─── Hello ───"))
    }

    @Test
    fun `labeled border with one-word label is dropped`() {
        assertTrue(ResponseNoiseFilter.isLabeledBorder("─── Status ───"))
        assertTrue(ResponseNoiseFilter.isLabeledBorder("╭─ Tool ─╮"))
        assertFalse(
            ResponseNoiseFilter.isLabeledBorder(
                "─── Here is a long sentence that happens to be in a frame ───",
            ),
        )
    }

    @Test
    fun `embedded status timer line is dropped`() {
        assertTrue(ResponseNoiseFilter.hasEmbeddedStatusTimer("elapsed 0:01:23"))
        assertTrue(ResponseNoiseFilter.hasEmbeddedStatusTimer("Working… 12s"))
        assertTrue(ResponseNoiseFilter.hasEmbeddedStatusTimer("00:00:42 elapsed"))
        assertFalse(ResponseNoiseFilter.hasEmbeddedStatusTimer("Plain prose."))
    }

    @Test
    fun `spinner counter line is dropped`() {
        assertTrue(ResponseNoiseFilter.isSpinnerCounter("⠋ Thinking…"))
        assertTrue(ResponseNoiseFilter.isSpinnerCounter("⠹"))
        assertFalse(
            ResponseNoiseFilter.isSpinnerCounter(
                "⠋ Thinking about what to do next, this is real prose really",
            ),
        )
    }

    @Test
    fun `pure digit line is dropped`() {
        assertTrue(ResponseNoiseFilter.isPureDigitLine("42"))
        assertTrue(ResponseNoiseFilter.isPureDigitLine("12 / 100"))
        assertTrue(ResponseNoiseFilter.isPureDigitLine("3.14, 2.71"))
        assertFalse(ResponseNoiseFilter.isPureDigitLine("score: 42"))
    }

    @Test
    fun `coalesce keeps prose body when noise frames it`() {
        val src =
            """
            ─── Tool result ───
            ⠋
            The migration ran cleanly.
            All 5 tables updated.
            ─── End ───
            elapsed 0:00:42
            """.trimIndent()
        val out = ResponseNoiseFilter.strip(src)
        assertTrue(out.contains("The migration ran cleanly."), "prose preserved")
        assertTrue(out.contains("All 5 tables updated."), "prose preserved")
        assertFalse(out.contains("─── Tool result ───"), "border dropped")
        assertFalse(out.contains("elapsed"), "timer dropped")
    }

    @Test
    fun `noise pattern catches token + context lines`() {
        assertTrue(ResponseNoiseFilter.matchesNoisePattern("tokens: 14523"))
        assertTrue(ResponseNoiseFilter.matchesNoisePattern("Context: 8"))
        assertTrue(ResponseNoiseFilter.matchesNoisePattern("(esc to interrupt)"))
        assertFalse(ResponseNoiseFilter.matchesNoisePattern("normal sentence"))
    }
}
