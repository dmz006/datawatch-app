package com.dmzs.datawatchclient.domain

import kotlinx.datetime.Instant

/**
 * A single event streamed from the datawatch server for a session. Maps the
 * `/ws?session=<id>` WebSocket frames into typed Kotlin values. Sealed so
 * the UI exhaustively handles every shape.
 *
 * Wire format (server-side, per parent datawatch source of truth): each WS
 * frame is a JSON object with `type: string` plus type-specific fields.
 * DTO → domain conversion happens in `transport/ws/EventMapper.kt`.
 */
public sealed interface SessionEvent {
    public val sessionId: String
    public val ts: Instant

    /** Plain terminal / chat output bytes. `ansi` when the output is ANSI-encoded. */
    public data class Output(
        override val sessionId: String,
        override val ts: Instant,
        val body: String,
        val stream: Stream = Stream.Stdout,
    ) : SessionEvent {
        public enum class Stream { Stdout, Stderr, System }
    }

    /** Session transitioned between states (running → waiting, etc.). */
    public data class StateChange(
        override val sessionId: String,
        override val ts: Instant,
        val from: SessionState,
        val to: SessionState,
    ) : SessionEvent

    /** A prompt waiting for user reply was detected. Pair with [Output]. */
    public data class PromptDetected(
        override val sessionId: String,
        override val ts: Instant,
        val prompt: Prompt,
    ) : SessionEvent

    /** Server-side LLM hit a rate limit. Includes retry-after if known. */
    public data class RateLimited(
        override val sessionId: String,
        override val ts: Instant,
        val retryAfter: Instant? = null,
    ) : SessionEvent

    /** Session reached a terminal state. */
    public data class Completed(
        override val sessionId: String,
        override val ts: Instant,
        val exitCode: Int? = null,
    ) : SessionEvent

    /** Unrecoverable error; session likely terminal. */
    public data class Error(
        override val sessionId: String,
        override val ts: Instant,
        val message: String,
    ) : SessionEvent

    /**
     * Full tmux pane snapshot from the server. The PWA treats this as the
     * authoritative terminal display source — `raw_output` is log-mode only.
     * [lines] is the pane contents as the server rendered it (ANSI + cursor
     * escapes preserved); [isFirst] distinguishes the first capture after
     * subscribe (write-from-scratch) from incremental redraws (clear + write).
     */
    public data class PaneCapture(
        override val sessionId: String,
        override val ts: Instant,
        val lines: List<String>,
        val isFirst: Boolean = false,
    ) : SessionEvent

    /** Unknown event kind. Forward-compat for future server versions. */
    public data class Unknown(
        override val sessionId: String,
        override val ts: Instant,
        val type: String,
    ) : SessionEvent
}
