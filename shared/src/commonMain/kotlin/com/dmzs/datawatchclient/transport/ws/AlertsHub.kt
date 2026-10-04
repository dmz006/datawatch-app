package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Payload of a live `alert` WS frame. */
public data class AlertPush(
    val serverProfileId: String,
    val id: String?,
    val title: String,
    val level: String,
    val sessionId: String?,
)

/**
 * Global broadcast channel for `alert` WS frames received on the global
 * stream. The PWA turns each one into an alert-dock entry + badge bump
 * (`handleAlert`); the apps do the same (parity D51a).
 */
public object AlertsHub {
    private val _flow = MutableSharedFlow<AlertPush>(extraBufferCapacity = 16)

    public val flow: SharedFlow<AlertPush> = _flow.asSharedFlow()

    public fun emit(push: AlertPush) {
        _flow.tryEmit(push)
    }
}
