package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeerHostDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerSelfCardTest {
    @Test
    fun peerNamedAfterTheServerHostIsTheServer() {
        val peers = listOf(ObserverPeerDto(name = "Workstation"), ObserverPeerDto(name = "gpu-box"))
        assertTrue(ServerSelfCard.peerIsServer("workstation", peers))
    }

    @Test
    fun peerMatchesOnHostInfoHostname() {
        val peers = listOf(ObserverPeerDto(name = "stats-1", hostInfo = ObserverPeerHostDto(hostname = "workstation")))
        assertTrue(ServerSelfCard.peerIsServer("workstation", peers))
    }

    @Test
    fun unknownHostnameNeverHidesTheServerCard() {
        assertFalse(ServerSelfCard.peerIsServer(null, listOf(ObserverPeerDto(name = "workstation"))))
        assertFalse(ServerSelfCard.peerIsServer("workstation", listOf(ObserverPeerDto(name = "gpu-box"))))
    }

    @Test
    fun titleIsHostNameElseSavedServerNameNeverLocal() {
        assertEquals("workstation", ServerSelfCard.title("workstation", "My server"))
        assertEquals("My server", ServerSelfCard.title(null, "My server"))
        assertEquals("My server", ServerSelfCard.title(" ", "My server"))
    }
}
