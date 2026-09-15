package com.dmzs.datawatchclient.push

import kotlin.test.Test
import kotlin.test.assertEquals

class PushTierManagerTest {
    private fun reset() = PushTierManager.reset()

    @Test
    fun `initial tier is Background`() {
        reset()
        assertEquals(AlertTier.Background, PushTierManager.tier.value)
    }

    @Test
    fun `notifyRegistered sets tier to UnifiedPush`() {
        reset()
        PushTierManager.notifyRegistered()
        assertEquals(AlertTier.UnifiedPush, PushTierManager.tier.value)
    }

    @Test
    fun `notifyFailed after registered resets to Background`() {
        reset()
        PushTierManager.notifyRegistered()
        PushTierManager.notifyFailed()
        assertEquals(AlertTier.Background, PushTierManager.tier.value)
    }

    @Test
    fun `notifyFailed when already Background does not change tier`() {
        reset()
        PushTierManager.notifyFailed()
        assertEquals(AlertTier.Background, PushTierManager.tier.value)
    }

    @Test
    fun `notifyRegistered twice stays UnifiedPush`() {
        reset()
        PushTierManager.notifyRegistered()
        PushTierManager.notifyRegistered()
        assertEquals(AlertTier.UnifiedPush, PushTierManager.tier.value)
    }
}

class AlertTierDetectorLogicTest {
    @Test
    fun `isUnifiedPushActive returns true when PushTierManager is UnifiedPush`() {
        PushTierManager.reset()
        PushTierManager.notifyRegistered()
        assertEquals(true, AlertTierDetector.isUnifiedPushActive())
    }

    @Test
    fun `isUnifiedPushActive returns false when PushTierManager is Background`() {
        PushTierManager.reset()
        assertEquals(false, AlertTierDetector.isUnifiedPushActive())
    }

    @Test
    fun `isUnifiedPushActive returns false after notifyFailed`() {
        PushTierManager.reset()
        PushTierManager.notifyRegistered()
        PushTierManager.notifyFailed()
        assertEquals(false, AlertTierDetector.isUnifiedPushActive())
    }
}
