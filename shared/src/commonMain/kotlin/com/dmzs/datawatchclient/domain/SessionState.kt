package com.dmzs.datawatchclient.domain

/**
 * Lifecycle state of a single datawatch session. Mirrors the parent daemon's state
 * enumeration; mapping is enforced via [fromWire] so upstream string changes cannot
 * silently mis-render on mobile.
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
