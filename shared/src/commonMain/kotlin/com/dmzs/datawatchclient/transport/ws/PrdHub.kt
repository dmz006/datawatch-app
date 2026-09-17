package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.transport.dto.PrdDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global broadcast channel for `prd_update` WS frames received over any
 * active WebSocket connection. The server emits `prd_update` frames to
 * all connected `/ws` clients on every autonomous PRD state change, so
 * any open session stream serves as a free PRD-update pipe.
 *
 * [AutonomousViewModel] subscribes here while a PRD detail dialog is open
 * and patches its `prds` list in-place — no full re-fetch, no flicker.
 */
public object PrdHub {
    private val _flow = MutableSharedFlow<PrdDto>(extraBufferCapacity = 8)

    /** Emits whenever a `prd_update` WS frame arrives on any session stream. */
    public val flow: SharedFlow<PrdDto> = _flow.asSharedFlow()

    /** Called from [WebSocketTransport] when it receives a parsed prd_update frame. */
    public fun emit(dto: PrdDto) {
        _flow.tryEmit(dto)
    }
}
