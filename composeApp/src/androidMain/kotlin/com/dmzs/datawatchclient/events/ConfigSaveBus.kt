package com.dmzs.datawatchclient.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * v0.42.9 — fired by every successful `transport.writeConfig` so
 * UI surfaces that depend on server config (the PRDs nav tab,
 * future capability-gated flags) can re-probe without polling.
 *
 * User direction 2026-04-28: *"there shouldn't be a timed
 * refresh, there should be a queue and a message saying there
 * is an event."* — daemon doesn't broadcast a config_changed
 * WS frame and the PWA simply doesn't react to it (one-shot
 * boot probe), so mobile reacts to its own config writes
 * instead. AppRoot subscribes here and re-runs `fetchConfig`
 * after every save.
 */
public object ConfigSaveBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    public val events: SharedFlow<Unit> = _events

    public suspend fun fire() {
        _events.emit(Unit)
    }
}
