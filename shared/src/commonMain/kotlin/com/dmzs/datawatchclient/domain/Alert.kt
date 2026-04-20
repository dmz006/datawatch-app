package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A single alert surfaced by the datawatch server via GET /api/alerts. The
 * parent PWA consumes this endpoint for its richer per-alert metadata
 * (severity, type, timestamp, linked session) than the mobile client's earlier
 * practice of deriving alert-y state from the session list. See
 * `docs/parity-plan.md` §4.
 *
 * Fields are permissive because the parent's `Alert` schema is not fully
 * locked — unknown-field tolerance plus null defaults here avoid breaking
 * the mobile client when the server extends the type.
 */
@Serializable
public data class Alert(
    val id: String,
    val serverProfileId: String,
    /** Free-form alert category as the server sends it (e.g. "input_needed", "error"). */
    val type: String,
    val severity: AlertSeverity = AlertSeverity.Info,
    val message: String,
    val sessionId: String? = null,
    val createdAt: Instant,
    val read: Boolean = false,
)

public enum class AlertSeverity { Info, Warning, Error }
