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
    /**
     * User-assigned display name (via rename). When set, the row header
     * prefers this over [taskSummary], matching PWA behaviour.
     */
    val name: String? = null,
    /**
     * Multi-line prompt-context payload (see SessionDto.prompt_context).
     * Rendered under waiting rows so the user can see what the LLM is
     * actually asking before tapping through to detail.
     */
    val promptContext: String? = null,
    /**
     * Most-recent LLM response snippet. When present, the Sessions row
     * shows a "View last response" icon, matching the PWA.
     */
    val lastResponse: String? = null,
    /**
     * Active backend for this session (e.g. `claude-code`, `ollama`).
     * Source-of-truth for the per-row backend badge.
     */
    val backend: String? = null,
    /**
     * Server-reported output rendering mode. Values observed on datawatch
     * v4.x: `"terminal"` (default — tmux pane), `"chat"` (role-badged
     * bubble transcript fed by WS `chat_message` frames, for backends like
     * OpenWebUI / Ollama), `"log"` (read-only log viewer).
     *
     * Null = server didn't report; UI assumes `"terminal"`.
     */
    val outputMode: String? = null,
    /**
     * Server-reported input mode. Values: `"tmux"` (default, send keystrokes
     * to tmux pane), `"chat"` (submit as chat message), `"none"` (session
     * is read-only, hide composer). Null = assume `"tmux"`.
     */
    val inputMode: String? = null,
) {
    public val needsInput: Boolean get() = state == SessionState.Waiting
    public val isTerminal: Boolean get() =
        state == SessionState.Completed ||
            state == SessionState.Killed || state == SessionState.Error

    /**
     * Server-scoped session identifier the daemon uses as its store key
     * (e.g. `"ring-2db6"`). Every session mutation endpoint
     * (`kill`, `state`, `rename`, `restart`, `delete`) matches on this,
     * not the short [id]. Computed from [hostnamePrefix] + [id]; falls
     * back to [id] when the prefix is unknown (offline cold start).
     */
    public val fullId: String
        get() = hostnamePrefix?.takeIf { it.isNotBlank() }?.let { "$it-$id" } ?: id

    /** True when the session uses structured chat bubbles instead of a tmux pane. */
    public val isChatMode: Boolean get() = outputMode == "chat"
}
