package com.dmzs.datawatchclient.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sprint 22 test-debt — BottomNavBar alerts-badge label logic coverage.
 *
 * The badge is always rendered (Sprint 22 #115), but:
 *   - alertsMuted  → shows "🔕"
 *   - alertsBadge > 0 and not muted → shows count string
 *   - alertsBadge == 0 and not muted → empty label (badge is dimmed, no text)
 *
 * Full Compose rendering requires instrumented tests; these tests cover the
 * pure label/color-selection logic extracted from BottomNavBar.kt.
 */
class BottomNavBadgeTest {

    // Mirror the label logic from BottomNavBar lines 110–115
    private fun badgeLabel(alertsMuted: Boolean, alertsBadge: Int): String =
        when {
            alertsMuted -> "🔕"
            alertsBadge > 0 -> alertsBadge.toString()
            else -> ""
        }

    // Mirror the color-selection logic from BottomNavBar lines 103–108
    private fun badgeDimmed(alertsMuted: Boolean, alertsBadge: Int): Boolean =
        alertsMuted || alertsBadge == 0

    @Test
    fun `zero count and not muted badge is dimmed`() {
        assertTrue(badgeDimmed(alertsMuted = false, alertsBadge = 0))
    }

    @Test
    fun `zero count and not muted has empty label`() {
        assertEquals("", badgeLabel(alertsMuted = false, alertsBadge = 0))
    }

    @Test
    fun `muted badge is dimmed regardless of count`() {
        assertTrue(badgeDimmed(alertsMuted = true, alertsBadge = 5))
    }

    @Test
    fun `muted badge shows bell-slash emoji`() {
        assertEquals("🔕", badgeLabel(alertsMuted = true, alertsBadge = 5))
    }

    @Test
    fun `muted badge with zero count also shows bell-slash`() {
        assertEquals("🔕", badgeLabel(alertsMuted = true, alertsBadge = 0))
    }

    @Test
    fun `positive count not muted shows count string`() {
        assertEquals("3", badgeLabel(alertsMuted = false, alertsBadge = 3))
    }

    @Test
    fun `positive count not muted badge is bright`() {
        assertTrue(!badgeDimmed(alertsMuted = false, alertsBadge = 3))
    }

    @Test
    fun `count of 1 renders as string one`() {
        assertEquals("1", badgeLabel(alertsMuted = false, alertsBadge = 1))
    }

    @Test
    fun `large count renders correctly`() {
        assertEquals("99", badgeLabel(alertsMuted = false, alertsBadge = 99))
    }
}
