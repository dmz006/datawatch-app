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
        public fun fromWire(value: String): SessionState =
            when (value.lowercase()) {
                "new" -> New
                "running" -> Running
                "waiting", "waiting_for_prompt", "needs_input" -> Waiting
                "rate_limited", "rate-limited" -> RateLimited
                "completed", "done" -> Completed
                "killed", "stopped" -> Killed
                "error", "failed" -> Error
                else -> Error
            }
    }
}
