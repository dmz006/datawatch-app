package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto

/**
 * Observer › System Statistics shows each machine once, by name — never a
 * separate "local" card (operator, 2026-10-08). The server's own observer peer
 * (datawatch-stats, named after its host) already represents it, so the
 * `/api/stats` card is shown only when no peer is the server itself.
 */
public object ServerSelfCard {
    /** True when one of [peers] is the server whose host name is [hostname]. */
    public fun peerIsServer(
        hostname: String?,
        peers: List<ObserverPeerDto>,
    ): Boolean {
        val h = hostname?.trim()?.lowercase().orEmpty()
        if (h.isEmpty()) return false
        return peers.any { it.name.trim().lowercase() == h || it.hostInfo?.hostname?.trim()?.lowercase() == h }
    }

    /** Title for the server's own card: its host name, else the saved server name. */
    public fun title(
        hostname: String?,
        serverName: String,
    ): String = hostname?.takeIf { it.isNotBlank() } ?: serverName
}
