package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A pending prompt awaiting user reply. Produced by the server's prompt-detection
 * filters; the mobile client renders these as chat messages with a highlighted
 * "Reply" affordance (see `docs/ux-session-detail.md`).
 */
@Serializable
public data class Prompt(
    val sessionId: String,
    val text: String,
    val detectedAt: Instant,
    val kind: Kind = Kind.FreeForm,
) {
    public enum class Kind {
        FreeForm,
        Approval, // yes/no style
        Choice, // enumerated options
        RateLimit, // informational, no reply needed
    }
}
