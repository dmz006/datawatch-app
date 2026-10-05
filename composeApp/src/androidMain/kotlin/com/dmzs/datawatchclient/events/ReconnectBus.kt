package com.dmzs.datawatchclient.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Parity D38a — long-press on the connection status dot force-refreshes the
 * server connection (PWA `forceRefreshConnection`, app.js:15442). Each request
 * bumps [tick]; WS collectors key their `flatMapLatest` on it so the socket is
 * torn down and reopened, and REST surfaces re-probe.
 */
public object ReconnectBus {
    private val _tick = MutableStateFlow(0)
    public val tick: StateFlow<Int> = _tick

    public fun request() {
        _tick.value = _tick.value + 1
    }
}
