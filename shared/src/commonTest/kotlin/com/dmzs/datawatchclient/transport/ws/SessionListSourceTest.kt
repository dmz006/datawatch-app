package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #236.5 — PWA v8.73.4 bug class: in "All servers" mode the local WebSocket's
 * own `sessions` push replaced the merged list, so remote sessions vanished
 * a second or two after showing. Pins that a single server's push never
 * replaces the merged list, while it still drives a single-server view.
 */
class SessionListSourceTest {
    private fun s(
        id: String,
        profile: String,
    ) = Session(
        id = id,
        serverProfileId = profile,
        hostnamePrefix = null,
        state = SessionState.Running,
        taskSummary = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastActivityAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun `a single server push never replaces the merged All-servers list`() {
        val merged = listOf(s("l1", "parent"), s("r1", "parent::proxy::demo"), s("b1", "other"))
        val push = SessionsUpdate("parent", listOf(s("l1", "parent")))

        // The VM's push handler only writes the per-profile cache when accepted…
        assertFalse(SessionListSource.acceptsPush(allServersMode = true, activeProfileId = null, pushProfileId = push.serverProfileId))
        assertFalse(SessionListSource.acceptsPush(allServersMode = true, activeProfileId = "parent", pushProfileId = "parent"))
        // …and even if the per-profile cache changed, All mode shows the merged list.
        val visible = SessionListSource.pick(allServersMode = true, perProfile = push.sessions, merged = merged)
        assertEquals(listOf("l1", "r1", "b1"), visible.map { it.id })
    }

    @Test
    fun `a single-server view follows its own server's push only`() {
        assertTrue(SessionListSource.acceptsPush(false, "parent::proxy::demo", "parent::proxy::demo"))
        assertFalse(SessionListSource.acceptsPush(false, "parent::proxy::demo", "parent"), "the parent's own WS must not fill a remote's view")
        assertFalse(SessionListSource.acceptsPush(false, null, "parent"))
        val own = listOf(s("r1", "parent::proxy::demo"))
        assertEquals(own, SessionListSource.pick(false, own, merged = listOf(s("x", "other"))))
    }
}
