package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A scheduled datawatch command — a task string paired with a cron expression.
 * Mirrors the parent server's `ScheduledCommand` schema from
 * `GET /api/schedule` (path is singular, not `/schedules` — verified against
 * the parent openapi.yaml audit 2026-04-20).
 *
 * Timestamp fields are permissive: `createdAt` falls back to
 * [Instant.DISTANT_PAST] when the server omits the field, matching the
 * [Session] / [Alert] mapper pattern so view layers don't have to null-check.
 */
@Serializable
public data class Schedule(
    val id: String,
    val serverProfileId: String,
    val task: String,
    /** Cron expression as sent by the server — we do not parse. */
    val cron: String,
    val enabled: Boolean = true,
    val createdAt: Instant,
)
