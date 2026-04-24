package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the Session computed properties added in v0.34.6 / v0.34.7.
 * fullId is the key the server uses to identify sessions across every
 * mutation endpoint; a regression here re-introduces the kill-404
 * bug the P0 release fixed.
 */
class SessionTest {
    private fun newSession(
        id: String = "2db6",
        hostnamePrefix: String? = "ring",
        state: SessionState = SessionState.Running,
        outputMode: String? = null,
        inputMode: String? = null,
    ) = Session(
        id = id,
        serverProfileId = "profile-1",
        hostnamePrefix = hostnamePrefix,
        state = state,
        taskSummary = "task",
        createdAt = Instant.parse("2026-04-24T00:00:00Z"),
        lastActivityAt = Instant.parse("2026-04-24T00:01:00Z"),
        outputMode = outputMode,
        inputMode = inputMode,
    )

    @Test
    fun `fullId joins hostname prefix and id for normal servers`() {
        val s = newSession(id = "2db6", hostnamePrefix = "ring")
        assertEquals("ring-2db6", s.fullId)
    }

    @Test
    fun `fullId falls back to short id when hostname prefix is null`() {
        val s = newSession(id = "2db6", hostnamePrefix = null)
        assertEquals("2db6", s.fullId)
    }

    @Test
    fun `fullId falls back to short id when hostname prefix is blank`() {
        val s = newSession(id = "2db6", hostnamePrefix = "   ")
        assertEquals("2db6", s.fullId)
    }

    @Test
    fun `fullId handles hostnames with dashes`() {
        val s = newSession(id = "abcd", hostnamePrefix = "worker-01")
        assertEquals("worker-01-abcd", s.fullId)
    }

    @Test
    fun `isChatMode is true only when outputMode equals chat`() {
        assertTrue(newSession(outputMode = "chat").isChatMode)
        assertFalse(newSession(outputMode = "terminal").isChatMode)
        assertFalse(newSession(outputMode = "log").isChatMode)
        assertFalse(newSession(outputMode = null).isChatMode)
        // Case-sensitive — server emits lowercase, anything else is rejected.
        assertFalse(newSession(outputMode = "Chat").isChatMode)
    }

    @Test
    fun `needsInput is true when state equals Waiting`() {
        assertTrue(newSession(state = SessionState.Waiting).needsInput)
        assertFalse(newSession(state = SessionState.Running).needsInput)
        assertFalse(newSession(state = SessionState.Completed).needsInput)
    }

    @Test
    fun `isTerminal covers Completed Killed and Error`() {
        assertTrue(newSession(state = SessionState.Completed).isTerminal)
        assertTrue(newSession(state = SessionState.Killed).isTerminal)
        assertTrue(newSession(state = SessionState.Error).isTerminal)
        assertFalse(newSession(state = SessionState.Running).isTerminal)
        assertFalse(newSession(state = SessionState.Waiting).isTerminal)
        assertFalse(newSession(state = SessionState.New).isTerminal)
    }
}
