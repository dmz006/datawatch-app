package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-level DTO for WebSocket `/ws?session=<id>` frames. Permissive (unknown
 * kinds survive) so server-side evolution doesn't break the mobile decoder.
 * All timestamps are RFC3339 strings.
 */
@Serializable
public data class WsFrameDto(
    val type: String,
    @SerialName("session_id") val sessionId: String? = null,
    val ts: String? = null,
    // Output frames
    val body: String? = null,
    val stream: String? = null, // "stdout" | "stderr" | "system"
    // State change frames
    val from: String? = null,
    val to: String? = null,
    // Prompt detected
    val prompt: String? = null,
    @SerialName("prompt_kind") val promptKind: String? = null,
    // Rate limited
    @SerialName("retry_after") val retryAfter: String? = null,
    // Completed
    @SerialName("exit_code") val exitCode: Int? = null,
    // Error
    val message: String? = null,
)

/**
 * Body for POST /api/sessions/state (override). Mirrors parent server shape.
 */
@Serializable
public data class StateOverrideDto(
    @SerialName("session_id") val sessionId: String,
    val state: String,
)
