package com.dmzs.datawatchclient.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PWA `navigateToStalePeer`: the Settings-tab stale-peer badge opens the
 * Observer, scrolls the Federated Peers list into view and briefly
 * highlights stale rows. The value is a request token (epoch ms) so repeat
 * taps re-trigger; ObserverScreen scrolls, FederatedPeersCard flashes.
 */
public object ObserverNavChannel {
    private val _staleFlash = MutableStateFlow<Long?>(null)
    public val staleFlash: StateFlow<Long?> = _staleFlash.asStateFlow()

    public fun requestStalePeers() {
        _staleFlash.value = System.currentTimeMillis()
    }

    public fun consume() {
        _staleFlash.value = null
    }
}
