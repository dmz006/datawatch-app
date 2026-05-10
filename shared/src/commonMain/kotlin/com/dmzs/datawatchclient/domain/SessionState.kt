package com.dmzs.datawatchclient.domain

/**
 * Lifecycle state of a single datawatch session. Mirrors the parent daemon's state
 * enumeration; mapping is enforced via [fromWire] so upstream string changes cannot
 * silently mis-render on mobile.
 *
 * Sprint 3 S3-4 — channel-state classifier audit (#70):
 * Mobile derives session state EXCLUSIVELY from the server-provided `state` field
 * in WS events (`session_state`, `session_update`) and REST responses
 * (`GET /api/sessions`). There is NO client-side regex match or substring scan of
 * `lastResponse` / `rawOutput` to infer state. The `fromWire` mapping below is the
 * single translation point; all callers receive a typed [SessionState] enum — not
 * a raw string — so there is no opportunity for ad-hoc pattern matching elsewhere.
 * Issue #70 is closed: the mobile classifier is already server-authoritative.
 */
public enum class SessionState {
    New,
    Running,
    Waiting,
    RateLimited,
    Completed,
    Killed,
    Error,
    ;

    public companion object {
        /**
         * Map the parent server's wire value (per `app.js` — `running`,
         * `waiting_input`, `rate_limited`, `complete`, `failed`, `killed`)
         * to the mobile enum. Accepts legacy aliases for forward-compat
         * across server versions. Falls back to [New] on unknown values
         * rather than [Error] so a new server-side state doesn't make
         * every unrecognised session look like it failed.
         */
        public fun fromWire(value: String): SessionState =
            when (value.lowercase()) {
                "new" -> New
                "running" -> Running
                // Parent emits `waiting_input`; mobile accepts aliases for
                // forward- and backward-compat across server versions.
                "waiting_input", "waiting", "waiting_for_prompt", "needs_input" -> Waiting
                "rate_limited", "rate-limited" -> RateLimited
                // Parent emits `complete`; legacy server builds sent `completed` / `done`.
                "complete", "completed", "done" -> Completed
                "killed", "stopped" -> Killed
                // Parent emits `failed`; legacy sent `error`.
                "failed", "error" -> Error
                else -> New
            }
    }
}
