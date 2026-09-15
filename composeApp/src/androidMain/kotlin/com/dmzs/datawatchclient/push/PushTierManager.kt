package com.dmzs.datawatchclient.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that tracks the active alert-delivery tier at runtime.
 *
 * Updated by [UnifiedPushSseService] when SSE registration succeeds or fails.
 * Observed by [PushNotificationsCard] to show live tier status.
 */
public object PushTierManager {
    private val _tier = MutableStateFlow(AlertTier.Background)
    public val tier: StateFlow<AlertTier> = _tier.asStateFlow()

    /** Call after a successful `registerPush` call in [UnifiedPushSseService]. */
    public fun notifyRegistered() {
        _tier.value = AlertTier.UnifiedPush
    }

    /** Call when the SSE connection cannot register (network error, auth failure, etc.). */
    public fun notifyFailed() {
        if (_tier.value == AlertTier.UnifiedPush) _tier.value = AlertTier.Background
    }

    /** Reset to background tier — used in tests only. */
    internal fun reset() {
        _tier.value = AlertTier.Background
    }
}
