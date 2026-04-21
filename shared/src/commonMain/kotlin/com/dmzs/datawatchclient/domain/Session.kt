package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A single datawatch session (tmux + LLM runner pair) as observed by the mobile client.
 * Source of truth is the server — this is a cache representation. See
 * `docs/data-model.md` and `docs/api-parity.md`.
 */
@Serializable
public data class Session(
    val id: String,
    val serverProfileId: String,
    val hostnamePrefix: String? = null,
    val state: SessionState,
    val taskSummary: String? = null,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val muted: Boolean = false,
    /**
     * Server-emitted prompt that triggered the current `waiting_input` state.
     * Populated from `SessionDto.last_prompt`. Nullable for non-waiting
     * sessions and for servers that predate the field.
     */
    val lastPrompt: String? = null,
) {
    public val needsInput: Boolean get() = state == SessionState.Waiting
    public val isTerminal: Boolean get() =
        state == SessionState.Completed ||
            state == SessionState.Killed || state == SessionState.Error
}
