package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WsConnectionHubTest {
    @Test
    fun connectedWhileAnySocketIsOpen() =
        runTest {
            val id = "hub-test-a"
            assertFalse(WsConnectionHub.isConnected(id))
            assertFalse(WsConnectionHub.connected(id).first())
            WsConnectionHub.opened(id)
            WsConnectionHub.opened(id)
            assertTrue(WsConnectionHub.connected(id).first())
            WsConnectionHub.closed(id)
            assertTrue(WsConnectionHub.isConnected(id))
            WsConnectionHub.closed(id)
            assertFalse(WsConnectionHub.isConnected(id))
            assertEquals(null, WsConnectionHub.openSockets.value[id])
        }

    @Test
    fun extraCloseNeverGoesNegative() {
        val id = "hub-test-b"
        WsConnectionHub.closed(id)
        assertFalse(WsConnectionHub.isConnected(id))
        WsConnectionHub.opened(id)
        assertTrue(WsConnectionHub.isConnected(id))
        WsConnectionHub.closed(id)
        assertFalse(WsConnectionHub.isConnected(id))
    }
}
