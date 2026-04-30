package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global broadcast channel for live stats frames received over any
 * active WebSocket connection (B10). The server emits `stats`-type
 * frames on every connected `/ws` session hub, so any live session
 * stream serves as a free stats pipe.
 *
 * StatsViewModel subscribes here and overlays WS-delivered values on
 * top of its 5 s REST poll — when WS is active the UI updates faster
 * and the REST poll acts as fallback / initial load.
 */
public object StatsHub {
    private val _flow = MutableSharedFlow<StatsDto>(extraBufferCapacity = 8)

    /** Emits whenever a `stats` WS frame arrives on any session stream. */
    public val flow: SharedFlow<StatsDto> = _flow.asSharedFlow()

    /** Called from [WebSocketTransport] when it receives a parsed stats frame. */
    public fun emit(dto: StatsDto) {
        _flow.tryEmit(dto)
    }
}
