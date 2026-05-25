@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.domain.SessionState

/** Urgency score for session list sorting — lower = more urgent. */
public fun sessionUrgencyScore(
    state: SessionState,
    hasGuardrailBlock: Boolean,
): Int = when {
    hasGuardrailBlock -> 0
    state == SessionState.Error -> 1
    state == SessionState.Waiting -> 2
    state == SessionState.RateLimited -> 3
    state == SessionState.Running -> 4
    state == SessionState.New -> 5
    else -> 6
}
