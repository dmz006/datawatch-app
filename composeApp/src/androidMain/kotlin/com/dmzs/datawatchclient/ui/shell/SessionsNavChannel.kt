package com.dmzs.datawatchclient.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-screen channel: any screen can request a jump to the Sessions tab
 * with a pre-applied text filter (e.g., the automata detail "View sessions"
 * button jumps to the Sessions tab filtered by PRD name). HomeShell collects
 * and drives tabNav; SessionsScreen collects and applies the filter.
 */
public object SessionsNavChannel {
    private val _pendingFilter = MutableStateFlow<String?>(null)
    public val pendingFilter: StateFlow<String?> = _pendingFilter.asStateFlow()

    public fun jumpTo(filter: String) {
        _pendingFilter.value = filter
    }

    public fun consume() {
        _pendingFilter.value = null
    }
}
