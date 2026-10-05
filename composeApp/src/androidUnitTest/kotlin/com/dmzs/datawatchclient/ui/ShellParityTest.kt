package com.dmzs.datawatchclient.ui

import com.dmzs.datawatchclient.events.ReconnectBus
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.shell.LastViewStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Parity D84b / D40a / D38a shell helpers. */
class ShellParityTest {
    @Test
    fun `datawatch scheme is canonical and dwclient is accepted as alias`() {
        assertTrue(DeepLinks.isAppScheme("datawatch"))
        assertTrue(DeepLinks.isAppScheme("dwclient"))
        assertFalse(DeepLinks.isAppScheme("https"))
        assertFalse(DeepLinks.isAppScheme(null))
        assertEquals("datawatch://session/abc", DeepLinks.sessionUri("abc"))
    }

    @Test
    fun `alert deep links resolve to an alert target`() {
        assertEquals("a1", DeepLinks.alertTargetFor("alert", listOf("a1")))
        assertEquals("a1", DeepLinks.alertTargetFor("Alerts", listOf("a1")))
        assertEquals("", DeepLinks.alertTargetFor("alert", emptyList()))
        assertEquals(null, DeepLinks.alertTargetFor("session", listOf("a1")))
        assertEquals(null, DeepLinks.alertTargetFor(null, emptyList()))
        assertEquals("datawatch://alert/a1", DeepLinks.alertUri("a1"))
    }

    @Test
    fun `unknown or missing last tab falls back to sessions`() {
        assertEquals(Destinations.Tabs.Sessions, LastViewStore.sanitizeTab(null))
        assertEquals(Destinations.Tabs.Sessions, LastViewStore.sanitizeTab("home/channels"))
        assertEquals(Destinations.Tabs.Observer, LastViewStore.sanitizeTab(Destinations.Tabs.Observer))
        assertEquals(Destinations.Tabs.Dashboard, LastViewStore.sanitizeTab(Destinations.Tabs.Dashboard))
    }

    @Test
    fun `reconnect request bumps the tick`() {
        val before = ReconnectBus.tick.value
        ReconnectBus.request()
        assertEquals(before + 1, ReconnectBus.tick.value)
    }
}
