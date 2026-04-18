package com.dmzs.datawatchclient.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStateTest {

    @Test
    fun `maps canonical wire states`() {
        assertEquals(SessionState.New, SessionState.fromWire("new"))
        assertEquals(SessionState.Running, SessionState.fromWire("running"))
        assertEquals(SessionState.Waiting, SessionState.fromWire("waiting"))
        assertEquals(SessionState.RateLimited, SessionState.fromWire("rate_limited"))
        assertEquals(SessionState.Completed, SessionState.fromWire("completed"))
        assertEquals(SessionState.Killed, SessionState.fromWire("killed"))
        assertEquals(SessionState.Error, SessionState.fromWire("error"))
    }

    @Test
    fun `maps synonyms the daemon sometimes emits`() {
        assertEquals(SessionState.Waiting, SessionState.fromWire("needs_input"))
        assertEquals(SessionState.Waiting, SessionState.fromWire("waiting_for_prompt"))
        assertEquals(SessionState.RateLimited, SessionState.fromWire("rate-limited"))
        assertEquals(SessionState.Completed, SessionState.fromWire("done"))
        assertEquals(SessionState.Killed, SessionState.fromWire("stopped"))
        assertEquals(SessionState.Error, SessionState.fromWire("failed"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals(SessionState.Running, SessionState.fromWire("RUNNING"))
        assertEquals(SessionState.Completed, SessionState.fromWire("Completed"))
    }

    @Test
    fun `unknown states degrade to Error (not a crash)`() {
        // Forward-compatibility: a future server state the mobile client hasn't seen
        // before is shown as Error in the UI, not a silent drop or crash.
        assertEquals(SessionState.Error, SessionState.fromWire("some_new_state"))
        assertEquals(SessionState.Error, SessionState.fromWire(""))
    }
}
