package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One MCP-channel line for a session (PWA channelReplies entry). */
public data class ChannelMessage(
    val sessionId: String,
    val text: String,
    /** "incoming" (reply from the agent), "outgoing" (sent), or "notify". */
    val direction: String,
    /** RFC 3339 timestamp; empty when the frame carried none. */
    val ts: String,
)

/**
 * Global broadcast channel for `channel_reply` / `channel_notify` WS frames.
 * The daemon broadcasts them to every `/ws` client, so any open socket
 * (global stream or a per-session stream) feeds this hub. The session
 * detail Channel tab merges these with `/api/channel/history`.
 */
public object ChannelHub {
    private val _flow = MutableSharedFlow<ChannelMessage>(extraBufferCapacity = 64)
    public val flow: SharedFlow<ChannelMessage> = _flow.asSharedFlow()

    public fun emit(msg: ChannelMessage) {
        _flow.tryEmit(msg)
    }
}

/** Route a channel frame (PWA handleChannelReply; notify text gets "[notify] "). */
internal fun tryRouteChannelFrame(
    type: String,
    data: JsonElement?,
    frameTs: String?,
) {
    val obj = data as? JsonObject ?: return
    val text = obj["text"]?.jsonPrimitive?.contentOrNull ?: return
    val sid = obj["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val notify = type == "channel_notify"
    ChannelHub.emit(
        ChannelMessage(
            sessionId = sid,
            text = if (notify) "[notify] $text" else text,
            direction = if (notify) "notify" else obj["direction"]?.jsonPrimitive?.contentOrNull ?: "incoming",
            ts = frameTs.orEmpty(),
        ),
    )
}
