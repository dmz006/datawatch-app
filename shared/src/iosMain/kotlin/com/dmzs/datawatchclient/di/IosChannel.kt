package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ws.ChannelHub
import com.dmzs.datawatchclient.transport.ws.ChannelMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Session Channel tab data (parity B7; PWA channelReplies + /api/channel/history
 * seed). Live lines arrive via [ChannelHub] from whatever `/ws` socket is open
 * (the session detail keeps one). Callbacks run on a background thread.
 */
public object IosChannel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun history(
        profile: ServerProfile,
        sessionId: String,
        onResult: (List<ChannelMessage>) -> Unit,
    ) {
        scope.launch {
            onResult(IosServiceLocator.transportFor(profile).getChannelHistory(sessionId).getOrNull().orEmpty())
        }
    }

    /** Live lines whose session id matches any of [sessionIds] (full id and short id). */
    public fun subscribe(
        sessionIds: List<String>,
        onMessage: (ChannelMessage) -> Unit,
    ): IosSubscription {
        val ids = sessionIds.filter { it.isNotBlank() }.toSet()
        val job = scope.launch { ChannelHub.flow.collect { if (it.sessionId in ids) onMessage(it) } }
        return IosSubscription(job)
    }
}
