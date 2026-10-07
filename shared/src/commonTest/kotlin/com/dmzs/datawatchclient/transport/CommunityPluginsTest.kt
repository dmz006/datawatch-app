package com.dmzs.datawatchclient.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityPluginsTest {
    @Test
    fun pickRegistryFollowsTheWebUi() {
        assertNull(CommunityPlugins.pickRegistry(emptyList(), null))
        assertEquals("community", CommunityPlugins.pickRegistry(listOf("pai", "community"), null))
        assertEquals("pai", CommunityPlugins.pickRegistry(listOf("pai"), null))
        assertEquals("pai", CommunityPlugins.pickRegistry(listOf("pai", "community"), "pai"))
        assertEquals("community", CommunityPlugins.pickRegistry(listOf("community"), "gone"))
    }

    @Test
    fun notConnectedDetection() {
        assertTrue(CommunityPlugins.isNotConnected("registry \"community\" not connected (run ...)"))
        assertFalse(CommunityPlugins.isNotConnected("HTTP 500"))
        assertFalse(CommunityPlugins.isNotConnected(null))
    }

    @Test
    fun subtitleUsesVersionWhenNoDescription() {
        assertEquals("Fetch weather", CommunityPlugins.subtitle("Fetch weather", "1.2"))
        assertEquals("v1.2", CommunityPlugins.subtitle("", "1.2"))
        assertEquals("", CommunityPlugins.subtitle("", ""))
    }
}
