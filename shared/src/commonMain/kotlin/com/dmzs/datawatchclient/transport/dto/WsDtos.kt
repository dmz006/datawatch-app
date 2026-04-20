package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Generic wire shape of a datawatch WebSocket frame. The server's frames are
 * `{ "type": "<kind>", "data": <arbitrary object>, "timestamp": "RFC3339" }`
 * — the `data` payload varies per type so we keep it as a raw [JsonElement]
 * and decode in [com.dmzs.datawatchclient.transport.ws.EventMapper].
 *
 * Known types (from parent `internal/server/web/app.js` handleMessage):
 *   sessions · session_update · output · raw_output · response · notification
 *   alert · needs_input · session_aware · channel_reply · channel_notify
 *   error
 */
@Serializable
public data class WsFrameDto(
    val type: String,
    val data: JsonElement? = null,
    @SerialName("timestamp") val timestamp: String? = null,
)

/**
 * Body of an outbound `subscribe` / `unsubscribe` frame we send to the
 * server after WS upgrade to start receiving output for a given session.
 */
@Serializable
public data class WsSubscribeDto(
    @SerialName("session_id") val sessionId: String,
)

/**
 * Body for POST /api/sessions/state (override). Mirrors parent server shape.
 */
@Serializable
public data class StateOverrideDto(
    @SerialName("session_id") val sessionId: String,
    val state: String,
)
