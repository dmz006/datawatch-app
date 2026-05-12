package com.dmzs.datawatchclient.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Allows deep navigation to a specific Settings sub-tab from anywhere in the app
 * (e.g., from within SessionDetailScreen's Stats panel). HomeShell collects this
 * and drives tabNav to the Settings composable destination.
 */
public object SettingsNavChannel {
    private val _pendingTab = MutableStateFlow<String?>(null)
    public val pendingTab: StateFlow<String?> = _pendingTab.asStateFlow()

    public fun request(tab: String) {
        _pendingTab.value = tab
    }

    public fun consume() {
        _pendingTab.value = null
    }
}
