package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs introduced for the v0.12 schedules + files + saved commands + config
 * sprint. Grouped in one file to keep the v0.10 / v0.11 DTO modules stable.
 * Field names match the parent datawatch openapi.yaml as of the 2026-04-20
 * audit (see `docs/plans/2026-04-20-v0.12-schedules-files-config.md`).
 */

// ---- Schedules (/api/schedule — singular) ----

@Serializable
public data class ScheduleDto(
    val id: String,
    val task: String,
    val cron: String,
    val enabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class CreateScheduleDto(
    val task: String,
    val cron: String,
    val enabled: Boolean = true,
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
