package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs introduced for the v0.11 session-power-user sprint. Grouped in one file
 * to keep the v0.10-era Dtos.kt stable. Field names match the parent datawatch
 * openapi.yaml as of the 2026-04-20 audit — see `docs/plans/2026-04-20-v0.11-session-power-user.md`.
 */

@Serializable
public data class RenameSessionDto(
    val id: String,
    val name: String,
)

@Serializable
public data class RestartSessionDto(
    val id: String,
)

/**
 * `POST /api/sessions/delete` is not in the parent's v3.0.0 openapi.yaml.
 * The mobile transport still encodes the expected body shape so when parent
 * lands the endpoint, no client code changes — only the 404 handling
 * in the UI layer goes away.
 */
@Serializable
public data class DeleteSessionDto(
    val id: String? = null,
    val ids: List<String>? = null,
)

/**
 * `POST /api/alerts` accepts either a single id or `all=true` to dismiss all.
 * Mobile emits one or the other, never both.
 */
@Serializable
public data class MarkAlertReadDto(
    val id: String? = null,
    val all: Boolean? = null,
)

/**
 * Response shape for `GET /api/alerts` per the parent spec:
 * { "alerts": [Alert...], "unread_count": int }.
 */
@Serializable
public data class AlertsListResponseDto(
    val alerts: List<AlertDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)

/**
 * Parent Alert schema is not fully locked in openapi.yaml; this is the
 * superset of fields observed across recent server versions. Missing fields
 * default to safe values so version skew doesn't break the client.
 */
@Serializable
public data class AlertDto(
    val id: String,
    val type: String,
    val severity: String? = null,
    val message: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val read: Boolean = false,
)

/**
 * Response shape for `GET /api/info` per the parent spec:
 * { hostname, version, llm_backend, messaging_backend, session_count, server: { host, port } }.
 */
@Serializable
public data class ServerInfoDto(
    val hostname: String,
    val version: String,
    @SerialName("llm_backend") val llmBackend: String? = null,
    @SerialName("messaging_backend") val messagingBackend: String? = null,
    @SerialName("session_count") val sessionCount: Int = 0,
    val server: ServerBindingDto? = null,
)

@Serializable
public data class ServerBindingDto(
    val host: String? = null,
    val port: Int? = null,
)

/**
 * `POST /api/backends/active` is not in the parent's v3.0.0 openapi.yaml.
 * Mobile transport sends `{"name": "<backend>"}` on the assumption the parent
 * adopts the same minimal body shape as other backends-* endpoints. If the
 * parent picks a different wire shape when it lands, update here.
 */
@Serializable
public data class SetActiveBackendDto(
    val name: String,
)
