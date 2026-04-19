package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body of POST /api/voice/transcribe per parent v3.0.0
 * `internal/server/voice.go`:
 *
 *   { transcript, confidence, action, session_id, latency_ms }
 */
@Serializable
public data class VoiceTranscribeResponseDto(
    val transcript: String = "",
    val confidence: Double = 1.0,
    val action: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("latency_ms") val latencyMs: Long = 0,
)
