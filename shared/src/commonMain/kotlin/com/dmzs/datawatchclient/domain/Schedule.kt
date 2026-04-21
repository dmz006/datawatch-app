package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A scheduled datawatch command — a task string paired with a cron expression
 * (or a one-shot `runAt` for schedules fired once). Mirrors the parent
 * server's `ScheduledCommand` schema from `GET /api/schedules`.
 *
 * The path was corrected from `/api/schedule` (singular) to `/api/schedules`
 * (plural) on 2026-04-20 — the parent openapi.yaml was stale vs the shipped
 * server (tracked upstream at dmz006/datawatch#16).
 *
 * Timestamp fields are permissive: `createdAt` falls back to
 * [Instant.DISTANT_PAST] when the server omits the field, matching the
 * [Session] / [Alert] mapper pattern so view layers don't have to null-check.
 */
@Serializable
public data class Schedule(
    val id: String,
    val serverProfileId: String,
    /**
     * Command body. Older servers emit this as `task`; newer ones as
     * `command`. The mapper takes `command ?: task` so the domain reads one
     * field.
     */
    val task: String,
    /** Cron expression as sent by the server — we do not parse. Null for one-shot schedules. */
    val cron: String? = null,
    /** RFC3339 timestamp of the one-shot trigger. Null for cron schedules. */
    val runAt: Instant? = null,
    val enabled: Boolean = true,
    /** `pending` / `fired` / `cancelled` / etc. — null on older servers. */
    val state: String? = null,
    /**
     * Session this schedule is attached to, when created in a session
     * context (e.g. via the session-detail "Schedule reply" flow). Null
     * for server-wide schedules.
     */
    val sessionId: String? = null,
    val createdAt: Instant,
)
