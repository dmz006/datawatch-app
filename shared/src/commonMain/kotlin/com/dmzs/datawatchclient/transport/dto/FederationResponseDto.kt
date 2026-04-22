package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of GET /api/federation/sessions per parent v3.0.0
 * (see `internal/server/federation.go` → `FederationResponse`).
 *
 * - `primary` — sessions on the server we called
 * - `proxied` — map keyed by peer-server name to each peer's session list
 * - `errors` — map keyed by peer-server name to the error returned by that peer
 */
@Serializable
public data class FederationResponseDto(
    val primary: List<SessionDto> = emptyList(),
    val proxied: Map<String, List<SessionDto>> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)
