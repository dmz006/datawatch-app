package com.dmzs.datawatchclient.ui.sessions

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
