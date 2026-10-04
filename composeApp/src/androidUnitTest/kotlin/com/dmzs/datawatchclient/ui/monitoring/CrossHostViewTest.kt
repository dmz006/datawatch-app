package com.dmzs.datawatchclient.ui.monitoring

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PWA `showCrossHostView` cross-host caller attribution rule. */
class CrossHostViewTest {
    @Test
    fun `three colon-separated parts mark a cross-host caller`() {
        assertTrue(isCrossHostCaller("peer-a:session:ab12"))
        assertFalse(isCrossHostCaller("session:ab12"))
        assertFalse(isCrossHostCaller("local"))
    }
}
