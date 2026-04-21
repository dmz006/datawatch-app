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
    /**
     * User-assigned display name (via rename). Parent PWA prefers this over
     * [task] for the row header. When null, the row falls back to [task].
     */
    val name: String? = null,
    @SerialName("tmux_session") val tmuxSession: String? = null,
    @SerialName("log_file") val logFile: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val hostname: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("pending_input") val pendingInput: String? = null,
    @SerialName("last_prompt") val lastPrompt: String? = null,
    /**
     * Multi-line prompt context surfaced by the server when a session is in
     * `waiting_input`. PWA renders the last 4 lines under the card so users
     * know what the LLM is actually asking before they tap through.
     * Overrides [lastPrompt] when present.
     */
    @SerialName("prompt_context") val promptContext: String? = null,
    /**
     * Most-recent LLM response snippet. When present, the PWA shows a
     * "View last response" icon on the row; mobile does the same.
     */
    @SerialName("last_response") val lastResponse: String? = null,
    /**
     * Active backend name for this session. Populates the per-row
     * backend badge; was previously fetched from `/api/info` which only
     * returns the server's current backend, not the session's.
     */
    @SerialName("llm_backend") val llmBackend: String? = null,
    /**
     * Federation-only: source server name when the row came from a
     * proxied fan-out call. `"local"` for rows on the user's own server.
     */
    val server: String? = null,
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
    /**
     * User-assigned session name (distinct from the task prompt).
     * Matches PWA `submitNewSession` payload. Older servers ignore.
     */
    val name: String? = null,
    /** Backend override — PWA passes the picked /api/backends name. */
    val backend: String? = null,
    /**
     * Resume a previous session by full id (matches PWA
     * `resume_id`). Server warm-restarts the named session rather
     * than starting fresh.
     */
    @SerialName("resume_id") val resumeId: String? = null,
    @SerialName("auto_git_init") val autoGitInit: Boolean? = null,
    @SerialName("auto_git_commit") val autoGitCommit: Boolean? = null,
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
