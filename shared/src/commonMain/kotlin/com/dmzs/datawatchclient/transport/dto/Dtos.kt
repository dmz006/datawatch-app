package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-level DTOs that track datawatch's openapi.yaml exactly. Kept flat and
 * permissive so server version skew within a minor family does not break the
 * client — unknown fields are ignored by default Json config.
 *
 * DTO → domain conversion lives in `transport/rest/Mappers.kt`.
 */

/**
 * Matches the `Session` schema in the parent datawatch openapi.yaml exactly.
 * Field names are RFC3339 / snake_case as the server emits them. Most fields
 * are nullable / defaulted because not every session populates them
 * (e.g., a brand-new session has no `last_prompt`).
 */
@Serializable
public data class SessionDto(
    val id: String,
    val state: String,
    @SerialName("full_id") val fullId: String? = null,
    val task: String? = null,
    @SerialName("tmux_session") val tmuxSession: String? = null,
    @SerialName("log_file") val logFile: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val hostname: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("pending_input") val pendingInput: String? = null,
    @SerialName("last_prompt") val lastPrompt: String? = null,
)

@Serializable
public data class StartSessionDto(
    val task: String,
    @SerialName("server") val serverHint: String? = null,
    @SerialName("profile") val profile: String? = null,
    /**
     * Optional server-side working directory. Populated from the v0.12
     * mobile file-picker. Older server builds ignore unknown fields.
     */
    @SerialName("cwd") val workingDir: String? = null,
)

@Serializable
public data class StartSessionResponseDto(
    @SerialName("session_id") val sessionId: String,
    val state: String,
)

@Serializable
public data class ReplyDto(
    @SerialName("session_id") val sessionId: String,
    val text: String,
)

@Serializable
public data class ReplyResponseDto(
    val ok: Boolean,
)

@Serializable
public data class HealthDto(
    val ok: Boolean = true,
    val version: String? = null,
)

@Serializable
public data class BackendsDto(
    val llm: List<String> = emptyList(),
    val active: String? = null,
)

@Serializable
public data class StatsDto(
    @SerialName("cpu_pct") val cpuPct: Double? = null,
    @SerialName("mem_pct") val memPct: Double? = null,
    @SerialName("disk_pct") val diskPct: Double? = null,
    @SerialName("gpu_pct") val gpuPct: Double? = null,
    @SerialName("sessions_total") val sessionsTotal: Int = 0,
    @SerialName("sessions_running") val sessionsRunning: Int = 0,
    @SerialName("sessions_waiting") val sessionsWaiting: Int = 0,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
)
