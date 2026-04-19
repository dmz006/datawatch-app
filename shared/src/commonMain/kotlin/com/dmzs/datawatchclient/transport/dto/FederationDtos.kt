package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of GET /api/federation/sessions per parent v3.0.0
 * `internal/server/federation.go` FederationResponse:
 *
 *   { "primary": [Session...], "proxied": { "<server-name>": [Session...] }, "errors": {...} }
 */
@Serializable
public data class FederationResponseDto(
    val primary: List<SessionDto> = emptyList(),
    val proxied: Map<String, List<SessionDto>> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)
