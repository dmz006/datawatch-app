package com.dmzs.datawatchclient.diagnostics

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsLogTest {
    @BeforeTest
    fun setUp() = DiagnosticsLog.clear()

    @AfterTest
    fun tearDown() = DiagnosticsLog.clear()

    @Test
    fun keepsLinesOldestFirst() {
        DiagnosticsLog.log(LogLevel.INFO, "A", "one", timeMs = 1)
        DiagnosticsLog.log(LogLevel.ERROR, "B", "two", timeMs = 2)
        val lines = DiagnosticsLog.snapshot()
        assertEquals(listOf("one", "two"), lines.map { it.message })
        assertEquals(LogLevel.ERROR, lines[1].level)
        assertEquals("B", lines[1].tag)
    }

    @Test
    fun dropsOldestPastCapacity() {
        repeat(DiagnosticsLog.CAPACITY + 5) { DiagnosticsLog.log(LogLevel.WARN, "T", "m$it", timeMs = it.toLong()) }
        val lines = DiagnosticsLog.snapshot()
        assertEquals(DiagnosticsLog.CAPACITY, lines.size)
        assertEquals("m5", lines.first().message)
        assertEquals("m${DiagnosticsLog.CAPACITY + 4}", lines.last().message)
    }

    @Test
    fun truncatesHugeMessages() {
        DiagnosticsLog.w("T", "z".repeat(DiagnosticsLog.MAX_MESSAGE_CHARS * 2))
        val msg = DiagnosticsLog.snapshot().single().message
        assertEquals(DiagnosticsLog.MAX_MESSAGE_CHARS + 1, msg.length)
        assertTrue(msg.endsWith("…"))
    }

    @Test
    fun shorthandsSetLevel() {
        DiagnosticsLog.i("T", "i")
        DiagnosticsLog.w("T", "w")
        DiagnosticsLog.e("T", "e")
        assertEquals(listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR), DiagnosticsLog.snapshot().map { it.level })
    }

    @Test
    fun formatIsIsoTimestampLevelTagMessage() {
        val entry = LogEntry(timeMs = 1_791_970_200_123L, level = LogLevel.WARN, tag = "Ws", message = "reconnecting")
        assertEquals("2026-10-14T09:30:00.123Z W/Ws: reconnecting", entry.format())
    }

    @Test
    fun clearEmptiesBuffer() {
        DiagnosticsLog.i("T", "x")
        DiagnosticsLog.clear()
        assertTrue(DiagnosticsLog.snapshot().isEmpty())
    }
}
