package com.dmzs.datawatchclient.wear

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-W5 — Voice query classifier and reply builder unit tests.
 * All pure Kotlin — no Android context required.
 */
class VoiceQueryDispatcherTest {

    // ---- Query classification ----

    @Test fun `status intent detected`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.STATUS,
            VoiceQueryDispatcher.classifyQuery("status"))
    }

    @Test fun `update maps to status intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.STATUS,
            VoiceQueryDispatcher.classifyQuery("give me an update"))
    }

    @Test fun `how maps to status intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.STATUS,
            VoiceQueryDispatcher.classifyQuery("how's it going"))
    }

    @Test fun `what's running maps to running intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.RUNNING,
            VoiceQueryDispatcher.classifyQuery("what's running"))
    }

    @Test fun `list maps to running intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.RUNNING,
            VoiceQueryDispatcher.classifyQuery("list sessions"))
    }

    @Test fun `any blocks maps to blocks intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.BLOCKS,
            VoiceQueryDispatcher.classifyQuery("any blocks"))
    }

    @Test fun `blocked maps to blocks intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.BLOCKS,
            VoiceQueryDispatcher.classifyQuery("blocked?"))
    }

    @Test fun `guardrail maps to blocks intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.BLOCKS,
            VoiceQueryDispatcher.classifyQuery("any guardrail hits?"))
    }

    @Test fun `unknown query returns UNKNOWN intent`() {
        assertEquals(VoiceQueryDispatcher.QueryIntent.UNKNOWN,
            VoiceQueryDispatcher.classifyQuery("banana"))
    }

    // ---- STATUS reply building ----

    @Test fun `status reply includes running and waiting counts`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.STATUS,
            running = 3, waiting = 1, error = 0,
            sessions = emptyList(), serverName = "prod",
        )
        assertTrue(reply.contains("3 running"), "reply='$reply'")
        assertTrue(reply.contains("1 waiting"), "reply='$reply'")
        assertTrue(reply.contains("prod"), "reply='$reply'")
    }

    @Test fun `status reply with no sessions says no active sessions`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.STATUS,
            running = 0, waiting = 0, error = 0,
            sessions = emptyList(), serverName = "",
        )
        assertTrue(reply.contains("no active sessions"), "reply='$reply'")
    }

    @Test fun `status reply includes error count when nonzero`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.STATUS,
            running = 0, waiting = 0, error = 2,
            sessions = emptyList(), serverName = "",
        )
        assertTrue(reply.contains("2 error"), "reply='$reply'")
    }

    // ---- RUNNING reply building ----

    @Test fun `running reply lists session titles`() {
        val sessions = listOf(
            VoiceQueryDispatcher.SessionSummary("Alpha", "Running"),
            VoiceQueryDispatcher.SessionSummary("Beta", "Running"),
        )
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.RUNNING,
            running = 2, waiting = 0, error = 0,
            sessions = sessions, serverName = "",
        )
        assertTrue(reply.contains("Alpha"), "reply='$reply'")
        assertTrue(reply.contains("Beta"), "reply='$reply'")
    }

    @Test fun `running reply caps at 3 sessions and shows overflow`() {
        val sessions = (1..5).map {
            VoiceQueryDispatcher.SessionSummary("Session $it", "Running")
        }
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.RUNNING,
            running = 5, waiting = 0, error = 0,
            sessions = sessions, serverName = "",
        )
        assertTrue(reply.contains("2 more"), "reply='$reply'")
    }

    @Test fun `running reply says no running sessions when empty`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.RUNNING,
            running = 0, waiting = 0, error = 0,
            sessions = emptyList(), serverName = "",
        )
        assertTrue(reply.contains("no running sessions"), "reply='$reply'")
    }

    // ---- BLOCKS reply building ----

    @Test fun `blocks reply says no blocks when clear`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.BLOCKS,
            running = 1, waiting = 0, error = 0,
            sessions = listOf(VoiceQueryDispatcher.SessionSummary("A", "Running")),
            serverName = "",
        )
        assertTrue(reply.contains("no blocks"), "reply='$reply'")
    }

    @Test fun `blocks reply names waiting session`() {
        val sessions = listOf(
            VoiceQueryDispatcher.SessionSummary("Codebot", "Waiting"),
        )
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.BLOCKS,
            running = 0, waiting = 1, error = 0,
            sessions = sessions, serverName = "",
        )
        assertTrue(reply.contains("Codebot"), "reply='$reply'")
    }

    // ---- Fuzzy server name matching ----

    @Test fun `exact server name matches`() {
        assertTrue(VoiceQueryDispatcher.fuzzyMatchesServer("prod", "prod"))
    }

    @Test fun `one-edit distance matches`() {
        assertTrue(VoiceQueryDispatcher.fuzzyMatchesServer("prod", "prод"))
        assertTrue(VoiceQueryDispatcher.fuzzyMatchesServer("dev1", "dev2"))
    }

    @Test fun `two-edit distance matches`() {
        assertTrue(VoiceQueryDispatcher.fuzzyMatchesServer("prod", "produ"))
    }

    @Test fun `three-edit distance does not match`() {
        assertFalse(VoiceQueryDispatcher.fuzzyMatchesServer("prod", "staging"))
    }

    @Test fun `case-insensitive match`() {
        assertTrue(VoiceQueryDispatcher.fuzzyMatchesServer("PROD", "prod"))
    }

    // ---- UNKNOWN reply ----

    @Test fun `unknown intent prompts valid options`() {
        val reply = VoiceQueryDispatcher.buildReply(
            VoiceQueryDispatcher.QueryIntent.UNKNOWN,
            running = 0, waiting = 0, error = 0,
            sessions = emptyList(), serverName = "",
        )
        assertTrue(reply.contains("status") || reply.contains("blocks"), "reply='$reply'")
    }
}
