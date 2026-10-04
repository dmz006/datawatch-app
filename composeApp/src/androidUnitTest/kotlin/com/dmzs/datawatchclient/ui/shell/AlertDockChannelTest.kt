package com.dmzs.datawatchclient.ui.shell

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Parity D41a / D47a / D51a — the dock that replaced Android toasts. */
class AlertDockChannelTest {
    @BeforeTest
    fun reset() = AlertDockChannel.resetForTest()

    @AfterTest
    fun cleanup() = AlertDockChannel.resetForTest()

    // ── open/close state machine (Sprint 20 coverage, kept) ──────────────

    @Test
    fun `initial state is closed`() = assertFalse(AlertDockChannel.open.value)

    @Test
    fun `toggle opens the dock and toggle again closes it`() {
        AlertDockChannel.toggle()
        assertTrue(AlertDockChannel.open.value)
        AlertDockChannel.toggle()
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `close after open sets dock to closed`() {
        AlertDockChannel.toggle()
        AlertDockChannel.close()
        assertFalse(AlertDockChannel.open.value)
    }

    // ── dock entries (parity D41a / D47a / D51a) ──────────────────────────

    @Test
    fun `post adds an entry without opening the dock`() {
        AlertDockChannel.post("Saved", DockLevel.Success)
        assertEquals(1, AlertDockChannel.entries.value.size)
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `same family within 60 s coalesces with a counter`() {
        AlertDockChannel.post("Recording failed: busy", DockLevel.Error, nowMs = 1_000)
        AlertDockChannel.post("Recording failed: other", DockLevel.Error, nowMs = 2_000)
        val e = AlertDockChannel.entries.value.single()
        assertEquals(2, e.count)
        assertEquals("Recording failed: other", e.message)
    }

    @Test
    fun `coalescing stops after the window`() {
        AlertDockChannel.post("Saved: a", nowMs = 0)
        AlertDockChannel.post("Saved: b", nowMs = 61_000)
        assertEquals(2, AlertDockChannel.entries.value.size)
    }

    @Test
    fun `app errors open the dock so they are never silent`() {
        AlertDockChannel.post("Upload failed", DockLevel.Error)
        assertTrue(AlertDockChannel.open.value)
    }

    @Test
    fun `server alerts never auto-open the dock`() {
        AlertDockChannel.post("session x: error", DockLevel.Error, fromServerAlert = true)
        assertFalse(AlertDockChannel.open.value)
        assertEquals(0, AlertDockChannel.localCount(AlertDockChannel.entries.value))
    }

    @Test
    fun `mute drops info and server alerts but keeps app errors`() {
        AlertDockChannel.mute()
        AlertDockChannel.post("Saved", DockLevel.Info)
        AlertDockChannel.post("alert", DockLevel.Error, fromServerAlert = true)
        assertTrue(AlertDockChannel.entries.value.isEmpty())
        AlertDockChannel.post("Upload failed", DockLevel.Error)
        assertEquals(1, AlertDockChannel.entries.value.size)
    }

    @Test
    fun `pill click while muted unmutes and opens`() {
        AlertDockChannel.mute()
        AlertDockChannel.toggle()
        assertFalse(AlertDockChannel.muted.value)
        assertTrue(AlertDockChannel.open.value)
    }

    @Test
    fun `dismiss clears entries and closes`() {
        AlertDockChannel.post("Upload failed", DockLevel.Error)
        AlertDockChannel.dismiss()
        assertTrue(AlertDockChannel.entries.value.isEmpty())
        assertFalse(AlertDockChannel.open.value)
    }

    @Test
    fun `family strips a bracket prefix and keeps text before the separator`() {
        assertEquals("Transcribe failed", AlertDockChannel.familyOf("[mic] Transcribe failed: timeout"))
    }
}
