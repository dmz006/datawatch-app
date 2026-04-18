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

@Serializable
public data class SessionDto(
    val id: String,
    val state: String,
    @SerialName("task_summary") val taskSummary: String? = null,
    @SerialName("hostname_prefix") val hostnamePrefix: String? = null,
    @SerialName("created_ts") val createdTs: Long,
    @SerialName("last_activity_ts") val lastActivityTs: Long,
)

@Serializable
public data class SessionListDto(
    val sessions: List<SessionDto> = emptyList(),
)

@Serializable
public data class StartSessionDto(
    val task: String,
    @SerialName("server") val serverHint: String? = null,
    @SerialName("profile") val profile: String? = null,
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
