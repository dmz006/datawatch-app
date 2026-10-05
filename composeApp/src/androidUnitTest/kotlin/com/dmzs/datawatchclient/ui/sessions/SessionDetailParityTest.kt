package com.dmzs.datawatchclient.ui.sessions

import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pure helpers behind the session-detail Android-missing parity items. */
class SessionDetailParityTest {
    @Test
    fun `log lines follow PWA class precedence`() {
        assertEquals(LogLineKind.AcpStatus, classifyLogLine("[opencode-acp] session started"))
        assertEquals(LogLineKind.Processing, classifyLogLine("thinking about it"))
        assertEquals(LogLineKind.Ready, classifyLogLine("[opencode-acp] server ready"))
        assertEquals(LogLineKind.Error, classifyLogLine("processing failed"))
        assertEquals(LogLineKind.Plain, classifyLogLine("hello"))
    }

    @Test
    fun `arrow repeat timings match the PWA`() {
        assertEquals(250L, ARROW_REPEAT_DELAY_MS)
        assertEquals(80L, ARROW_REPEAT_INTERVAL_MS)
    }

    @Test
    fun `chat collapses past six keeping the last four`() {
        assertEquals(6, CHAT_COLLAPSE_THRESHOLD)
        assertEquals(4, CHAT_RECENT_KEPT)
    }

    @Test
    fun `process envelope matches session kind by id prefix`() {
        val envs =
            listOf(
                StatEnvelopeDto(id = "host-ab", kind = "llm"),
                StatEnvelopeDto(id = "host-ab12", kind = "session", cpuPct = 12.0),
            )
        assertEquals(12.0, sessionEnvelopeFor("host-ab12", envs)?.cpuPct)
        assertNull(sessionEnvelopeFor("other-1", envs))
        assertNull(sessionEnvelopeFor("x", listOf(StatEnvelopeDto(id = "", kind = "session"))))
    }

    @Test
    fun `stats bar formats bytes and rates like the PWA`() {
        assertEquals("1.5GB", statsBarBytes(1_500_000_000L))
        assertEquals("250MB", statsBarBytes(250_000_000L))
        assertEquals("12KB", statsBarBytes(12_000L))
        assertEquals("2.5MB/s", statsBarRate(2_500_000L))
        assertEquals("40KB/s", statsBarRate(40_000L))
    }

    @Test
    fun `state override offers the five PWA states`() {
        assertEquals(
            listOf(SessionState.Running, SessionState.Waiting, SessionState.Completed, SessionState.Killed, SessionState.Error),
            PWA_OVERRIDE_STATES,
        )
    }
}
