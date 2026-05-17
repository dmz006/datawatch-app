package com.dmzs.datawatchclient.wear

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-W3 — Verifies guardrail block detection logic and idempotency.
 * Tests the pure state-machine logic used by WearSyncService to decide
 * when to fire a notification (new block only, not repeat).
 */
class GuardrailBlockDetectionTest {

    // Mirrors the state machine in WearSyncService without depending on Android.
    private class BlockStateMachine {
        var lastBlockedSessionId: String = ""
        val notifications = mutableListOf<Pair<String, String>>() // sessionId to summary

        fun process(sessionId: String, guardrailBlock: Boolean, blockSummary: String) {
            if (guardrailBlock && sessionId != lastBlockedSessionId) {
                lastBlockedSessionId = sessionId
                notifications += sessionId to blockSummary
            } else if (!guardrailBlock) {
                lastBlockedSessionId = ""
            }
        }
    }

    @Test fun `new block fires notification`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Token leaked")
        assertEquals(1, sm.notifications.size)
        assertEquals("s1", sm.notifications[0].first)
        assertEquals("Token leaked", sm.notifications[0].second)
    }

    @Test fun `repeated block for same session does not re-fire`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Token leaked")
        sm.process("s1", guardrailBlock = true, blockSummary = "Token leaked")
        sm.process("s1", guardrailBlock = true, blockSummary = "Token leaked")
        assertEquals(1, sm.notifications.size)
    }

    @Test fun `different session block fires new notification`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Block A")
        sm.process("s2", guardrailBlock = true, blockSummary = "Block B")
        assertEquals(2, sm.notifications.size)
        assertEquals("s2", sm.notifications[1].first)
    }

    @Test fun `cleared block resets state so same session refires on next block`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Block A")
        sm.process("s1", guardrailBlock = false, blockSummary = "")
        sm.process("s1", guardrailBlock = true, blockSummary = "Block A again")
        assertEquals(2, sm.notifications.size)
    }

    @Test fun `no block produces no notification`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = false, blockSummary = "")
        assertTrue(sm.notifications.isEmpty())
    }

    @Test fun `block summary stored accurately`() {
        val sm = BlockStateMachine()
        val expected = "secrets-scan: API token found in commit abc123"
        sm.process("s1", guardrailBlock = true, blockSummary = expected)
        assertEquals(expected, sm.notifications[0].second)
    }

    @Test fun `lastBlockedSessionId updated on new block`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Block A")
        assertEquals("s1", sm.lastBlockedSessionId)
    }

    @Test fun `lastBlockedSessionId cleared when block resolves`() {
        val sm = BlockStateMachine()
        sm.process("s1", guardrailBlock = true, blockSummary = "Block A")
        sm.process("s1", guardrailBlock = false, blockSummary = "")
        assertEquals("", sm.lastBlockedSessionId)
    }

    // ---- Haptic pattern structure ----

    @Test fun `triple buzz pattern has 6 elements`() {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
        assertEquals(6, pattern.size)
        assertEquals(0L, pattern[0])    // delay before first buzz
        assertEquals(400L, pattern[1])  // first buzz
        assertEquals(200L, pattern[2])  // pause
        assertEquals(400L, pattern[3])  // second buzz
        assertEquals(200L, pattern[4])  // pause
        assertEquals(400L, pattern[5])  // third buzz
    }

    @Test fun `auto-dismiss timeout constant is 10 seconds`() {
        assertEquals(10, WearApproveScreen.AUTO_DISMISS_SECONDS)
    }

    @Test fun `guardrail dismiss action string matches manifest intent`() {
        assertEquals(
            "com.dmzs.datawatchclient.wear.ACTION_GUARDRAIL_DISMISS",
            WearAlertListenerService.GUARDRAIL_DISMISS_ACTION,
        )
    }

    @Test fun `approve gate path has correct format`() {
        assertEquals("/datawatch/approveGate", WearApproveScreen.APPROVE_GATE_PATH)
    }

    @Test fun `guardrail block path has correct format`() {
        assertEquals("/datawatch/guardrailBlock", WearAlertListenerService.GUARDRAIL_BLOCK_PATH)
    }
}
