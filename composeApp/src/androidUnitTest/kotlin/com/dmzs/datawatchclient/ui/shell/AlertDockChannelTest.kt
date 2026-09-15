package com.dmzs.datawatchclient.ui.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sprint 20 test-debt — AlertDockChannel state machine coverage.
 *
 * AlertDockOverlay callbacks (dismiss/mute) are tested at the channel level:
 * - dismiss = AlertDockChannel.close()
 * - mute    = caller's local `dockMuted = true` (AppRoot state, not in channel)
 *
 * The channel is a singleton `object` so tests access it directly;
 * reset to closed after each test by calling close().
 */
class AlertDockChannelTest {

    @Test
    fun `initial state is closed`() {
        AlertDockChannel.close() // guard: ensure clean state
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `toggle opens the dock`() {
        AlertDockChannel.close()
        AlertDockChannel.toggle()
        assertTrue(AlertDockChannel.open.value)
        AlertDockChannel.close() // cleanup
    }

    @Test
    fun `toggle twice returns to closed`() {
        AlertDockChannel.close()
        AlertDockChannel.toggle()
        AlertDockChannel.toggle()
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `close while already closed stays closed`() {
        AlertDockChannel.close()
        AlertDockChannel.close()
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `close after open sets dock to closed`() {
        // Simulates onDismiss callback behaviour from AlertDockOverlay.
        AlertDockChannel.close()
        AlertDockChannel.toggle()
        assertTrue(AlertDockChannel.open.value, "expected open after toggle")
        AlertDockChannel.close()
        assertFalse(AlertDockChannel.open.value, "expected closed after dismiss")
    }

    @Test
    fun `mute logic is caller-side boolean independent of channel`() {
        // AppRoot tracks dockMuted as a local `var remember { false }`.
        // Muting does NOT flow through AlertDockChannel — it only suppresses
        // the overlay render in AppRoot. This test documents the contract.
        var dockMuted = false
        AlertDockChannel.toggle()
        dockMuted = true
        // Channel is still open; mute is a separate flag.
        assertTrue(AlertDockChannel.open.value)
        assertTrue(dockMuted)
        AlertDockChannel.close()
    }
}
