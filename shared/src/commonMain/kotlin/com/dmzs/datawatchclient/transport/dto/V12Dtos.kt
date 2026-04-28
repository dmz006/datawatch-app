package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs introduced for the v0.12 schedules + files + saved commands + config
 * sprint. Grouped in one file to keep the v0.10 / v0.11 DTO modules stable.
 * Field names match the parent datawatch openapi.yaml as of the 2026-04-20
 * audit (see `docs/plans/2026-04-20-v0.12-schedules-files-config.md`).
 */

// ---- Schedules (/api/schedules — plural, per shipped server) ----
//
// The parent server actually exposes `/api/schedules` (plural); the openapi.yaml
// in the 2026-04-20 audit documented `/api/schedule` (singular) which was stale.
// ScheduledCommand objects that come back include richer fields than the spec
// claimed — `session_id` (present when created in a session context), `run_at`
// (one-shot schedules), `command` (the body; newer servers may emit this
// instead of / alongside `task`), and `state` (`pending` / `fired` /
// `cancelled`). All are optional here for forward- and back-compat: older
// servers that only emit `task` + `cron` + `enabled` still parse cleanly.
// Tracked upstream at dmz006/datawatch#16.

@Serializable
public data class ScheduleDto(
    val id: String,
    val task: String? = null,
    val command: String? = null,
    val cron: String? = null,
    @SerialName("run_at") val runAt: String? = null,
    val enabled: Boolean = true,
    val state: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class CreateScheduleDto(
    val task: String,
    val cron: String,
    val enabled: Boolean = true,
    /** Attach the schedule to a specific session so it shows up in that session's strip. */
    @SerialName("session_id") val sessionId: String? = null,
)

// ---- Files (/api/files?path=) ----

@Serializable
public data class FileEntryDto(
    val name: String,
    val path: String,
    @SerialName("is_dir") val isDir: Boolean = false,
)

@Serializable
public data class FilesListResponseDto(
    val path: String,
    val entries: List<FileEntryDto> = emptyList(),
)

/**
 * POST /api/files body for mkdir-while-browsing (PWA v5.26.46 / #14).
 * The daemon also accepts other `action` values; we only emit `mkdir`.
 */
@Serializable
public data class FilesMkdirDto(
    val path: String,
    val action: String = "mkdir",
)

// ---- Saved commands (/api/commands) ----

@Serializable
public data class SavedCommandDto(
    val name: String,
    val command: String,
)

@Serializable
public data class SaveCommandDto(
    val name: String,
    val command: String,
)
