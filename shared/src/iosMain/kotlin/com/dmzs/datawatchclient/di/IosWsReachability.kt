package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.transport.ws.WsConnectionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reachability dot source (parity row 01 "Reachability source"): the live
 * WebSocket state, like the PWA `state.connected`. Reports whether any global
 * `/ws` for [profileId] is open (the foreground alert feed keeps one per enabled
 * server) — immediately, then on every connect / disconnect. Opens no socket
 * itself. Callbacks run on a background thread; cancel the handle when done.
 */
public object IosWsReachability {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    public fun watch(
        profileId: String,
        onChange: (Boolean) -> Unit,
    ): IosSubscription {
        val job: Job =
            scope.launch {
                WsConnectionHub.connected(profileId).collect { connected: Boolean -> onChange(connected) }
            }
        return IosSubscription(job)
    }
}
