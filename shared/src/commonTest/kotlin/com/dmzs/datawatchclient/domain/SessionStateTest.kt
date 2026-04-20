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
    fun `unknown states degrade to New (not Error)`() {
        // Forward-compatibility: a future server state the mobile client hasn't
        // seen before is shown as New (neutral), not Error. User feedback
        // 2026-04-20: the previous Error fallback caused sessions with
        // wire-state `waiting_input` (which the earlier mapper didn't list)
        // to render as FAILED badges, confusing the user.
        assertEquals(SessionState.New, SessionState.fromWire("some_new_state"))
        assertEquals(SessionState.New, SessionState.fromWire(""))
    }

    @Test
    fun `real parent wire values map correctly`() {
        // Per internal/server/web/app.js — server emits these exact strings.
        assertEquals(SessionState.Running, SessionState.fromWire("running"))
        assertEquals(SessionState.Waiting, SessionState.fromWire("waiting_input"))
        assertEquals(SessionState.RateLimited, SessionState.fromWire("rate_limited"))
        assertEquals(SessionState.Completed, SessionState.fromWire("complete"))
        assertEquals(SessionState.Error, SessionState.fromWire("failed"))
        assertEquals(SessionState.Killed, SessionState.fromWire("killed"))
    }
}
