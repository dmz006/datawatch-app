package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.transport.ws.ChannelReadyHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Session-detail connection banner readiness (PWA `state.channelReady[full_id]`).
 * Wraps [ChannelReadyHub], which the session's `/ws` stream feeds from the
 * `channel_ready` frame, `channel_ready` session fields and the output scan.
 * Callbacks run on a background thread — hop to main in Swift.
 */
public object IosChannelReady {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Current readiness for any of [sessionIds] (full id and short id). */
    public fun isReady(sessionIds: List<String>): Boolean = ChannelReadyHub.isReady(sessionIds)

    /**
     * Calls [onChange] with the current readiness for any of [sessionIds], then
     * again whenever it changes. Cancel the returned subscription on disappear.
     */
    public fun watch(
        sessionIds: List<String>,
        onChange: (Boolean) -> Unit,
    ): IosSubscription {
        val ids: List<String> = sessionIds.filter { it.isNotBlank() }
        val job =
            scope.launch {
                ChannelReadyHub.ready
                    .map<Set<String>, Boolean> { set: Set<String> -> ids.any { it in set } }
                    .distinctUntilChanged()
                    .collect { ready: Boolean -> onChange(ready) }
            }
        return IosSubscription(job)
    }
}
