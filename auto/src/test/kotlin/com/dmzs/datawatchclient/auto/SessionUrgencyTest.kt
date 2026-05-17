package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.domain.SessionState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionUrgencyTest {

    @Test
    fun `guardrail block is highest urgency`() {
        val score = sessionUrgencyScore(SessionState.Running, hasGuardrailBlock = true)
        assertEquals(0, score)
    }

    @Test
    fun `blocked session outranks error session`() {
        val blocked = sessionUrgencyScore(SessionState.Running, hasGuardrailBlock = true)
        val error = sessionUrgencyScore(SessionState.Error, hasGuardrailBlock = false)
        assertTrue(blocked < error, "blocked ($blocked) should be more urgent than error ($error)")
    }

    @Test
    fun `error outranks waiting`() {
        val error = sessionUrgencyScore(SessionState.Error, hasGuardrailBlock = false)
        val waiting = sessionUrgencyScore(SessionState.Waiting, hasGuardrailBlock = false)
        assertTrue(error < waiting)
    }

    @Test
    fun `waiting outranks running`() {
        val waiting = sessionUrgencyScore(SessionState.Waiting, hasGuardrailBlock = false)
        val running = sessionUrgencyScore(SessionState.Running, hasGuardrailBlock = false)
        assertTrue(waiting < running)
    }

    @Test
    fun `running outranks new`() {
        val running = sessionUrgencyScore(SessionState.Running, hasGuardrailBlock = false)
        val new = sessionUrgencyScore(SessionState.New, hasGuardrailBlock = false)
        assertTrue(running < new)
    }

    @Test
    fun `terminal states have lowest urgency`() {
        val completed = sessionUrgencyScore(SessionState.Completed, hasGuardrailBlock = false)
        val killed = sessionUrgencyScore(SessionState.Killed, hasGuardrailBlock = false)
        val new = sessionUrgencyScore(SessionState.New, hasGuardrailBlock = false)
        assertTrue(completed > new)
        assertTrue(killed > new)
    }

    @Test
    fun `sort order with mixed sessions`() {
        data class FakeRow(val state: SessionState, val block: Boolean)
        val rows = listOf(
            FakeRow(SessionState.Completed, false),
            FakeRow(SessionState.Running, true),   // blocked — should be #1
            FakeRow(SessionState.Waiting, false),  // should be #2
            FakeRow(SessionState.Error, false),    // should be #2 area
            FakeRow(SessionState.Running, false),  // should be after waiting
        )
        val sorted = rows.sortedBy { sessionUrgencyScore(it.state, it.block) }
        assertEquals(true, sorted[0].block)        // blocked first
        assertEquals(SessionState.Error, sorted[1].state)
        assertEquals(SessionState.Waiting, sorted[2].state)
        assertEquals(SessionState.Running, sorted[3].state)
        assertEquals(SessionState.Completed, sorted[4].state)
    }

    @Test
    fun `progress formatting`() {
        val progress = 0.73f
        val pct = (progress * 100).toInt()
        assertEquals(73, pct)
    }
}
