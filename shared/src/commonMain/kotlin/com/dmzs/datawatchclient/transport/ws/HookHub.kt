package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One `hook_update` WS frame: `{session_id, board}` (server BL303 S3). */
public data class HookUpdate(
    val serverProfileId: String,
    val sessionId: String,
    val board: JsonObject,
)

/**
 * Global broadcast channel for `hook_update` frames (server pushes one per
 * session hook event). The PWA dashboard feeds its EKG, live-event ticker,
 * guardrail tallies and node health from these; iOS Dashboard does the same.
 * The board is kept as raw JSON because it carries maps (`current_focus`,
 * `telemetry.sprint`) the typed status DTO doesn't model.
 */
public object HookHub {
    private val _flow = MutableSharedFlow<HookUpdate>(extraBufferCapacity = 64)

    public val flow: SharedFlow<HookUpdate> = _flow.asSharedFlow()

    public fun emit(update: HookUpdate) {
        _flow.tryEmit(update)
    }

    /** Parse a frame's `data`; drops prototype-pollution ids exactly like the PWA. */
    internal fun route(
        data: JsonElement?,
        profileId: String,
    ) {
        val obj = data as? JsonObject ?: return
        val sid = (obj["session_id"] as? JsonPrimitive)?.contentOrNull ?: return
        if (sid.isBlank() || sid == "__proto__" || sid == "constructor" || sid == "prototype") return
        val board = obj["board"] as? JsonObject ?: return
        emit(HookUpdate(serverProfileId = profileId, sessionId = sid, board = board))
    }
}
