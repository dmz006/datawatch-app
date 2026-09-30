package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Payload for a full-list `sessions` WS frame. */
public data class SessionsUpdate(
    val serverProfileId: String,
    val sessions: List<Session>,
)

/** Payload for a single-session `session_state` WS frame (v8.37.0+). */
public data class SessionStateUpdate(
    val serverProfileId: String,
    val session: Session,
)

/**
 * Global broadcast channel for `sessions` and `session_state` WS frames received
 * over any active WebSocket connection. The server pushes a full `sessions` frame
 * to all connected clients immediately on connect and on every session-affecting
 * change (~15 server-side call sites). `session_state` is a lighter single-row
 * diff added in v8.37.0.
 *
 * [SessionsViewModel] subscribes here to drive real-time session-list updates
 * without REST polling.
 */
public object SessionsHub {
    private val _fullListFlow = MutableSharedFlow<SessionsUpdate>(extraBufferCapacity = 4)
    private val _singleSessionFlow = MutableSharedFlow<SessionStateUpdate>(extraBufferCapacity = 16)

    /** Emits whenever a `sessions` (full-list) WS frame arrives on any active stream. */
    public val fullListFlow: SharedFlow<SessionsUpdate> = _fullListFlow.asSharedFlow()

    /** Emits whenever a `session_state` (single-session diff) WS frame arrives. */
    public val singleSessionFlow: SharedFlow<SessionStateUpdate> = _singleSessionFlow.asSharedFlow()

    public fun emitFullList(update: SessionsUpdate) { _fullListFlow.tryEmit(update) }
    public fun emitSingle(update: SessionStateUpdate) { _singleSessionFlow.tryEmit(update) }
}
