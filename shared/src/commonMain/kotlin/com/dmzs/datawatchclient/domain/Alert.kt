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
    /**
     * Short headline — first line of the alert (e.g. `"ring: datawatch-app
     * [17db]: waiting for input"`). Server emits this as `title`.
     */
    val title: String = "",
    /**
     * Body payload — the prompt context, error detail, or multi-line
     * info block. Server emits this as `body`.
     */
    val message: String,
    val sessionId: String? = null,
    val createdAt: Instant,
    val read: Boolean = false,
)

public enum class AlertSeverity {
    Info,
    Warning,
    Error,
    ;

    /**
     * Wire-to-domain mapping that tolerates every spelling the parent
     * has used: `"info"`, `"warn"`, `"warning"`, `"error"`, plus the
     * legacy `severity` string. Unknown values fall back to [Info].
     */
    public companion object {
        public fun fromWire(s: String?): AlertSeverity =
            when (s?.lowercase()) {
                "error", "err", "critical" -> Error
                "warn", "warning" -> Warning
                else -> Info
            }
    }
}
