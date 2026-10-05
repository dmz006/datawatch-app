package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Live WebSocket connection state per server profile — the PWA `state.connected`
 * (set on `ws.onopen`, cleared on `ws.onclose`) that drives its header status dot.
 *
 * [WebSocketTransport.globalStream] reports each socket's open/close here. Several
 * global sockets can be open for one profile (alert feed, dashboard, …), so this
 * keeps a count: a profile is connected while at least one is open.
 */
public object WsConnectionHub {
    private val _open = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Open global-socket count per profile id (absent = none open). */
    public val openSockets: StateFlow<Map<String, Int>> = _open.asStateFlow()

    public fun isConnected(profileId: String): Boolean = (_open.value[profileId] ?: 0) > 0

    /** Emits the current state immediately, then on every connect / disconnect. */
    public fun connected(profileId: String): Flow<Boolean> =
        _open.map { m: Map<String, Int> -> (m[profileId] ?: 0) > 0 }.distinctUntilChanged()

    internal fun opened(profileId: String) {
        _open.update { m: Map<String, Int> -> m + (profileId to ((m[profileId] ?: 0) + 1)) }
    }

    internal fun closed(profileId: String) {
        _open.update { m: Map<String, Int> ->
            val n: Int = (m[profileId] ?: 0) - 1
            if (n <= 0) m - profileId else m + (profileId to n)
        }
    }
}
