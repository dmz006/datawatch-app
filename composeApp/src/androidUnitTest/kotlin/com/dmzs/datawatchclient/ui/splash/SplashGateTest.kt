package com.dmzs.datawatchclient.ui.splash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parity D37a — PWA splash gating rule. */
class SplashGateTest {
    private val hour = 60L * 60 * 1000

    @Test
    fun `first launch shows`() = assertTrue(SplashGate.shouldShow(1_000, 0L, null, "1.0.0"))

    @Test
    fun `same version within 24 h skips`() = assertFalse(SplashGate.shouldShow(10 * hour, 9 * hour, "1.0.0", "1.0.0"))

    @Test
    fun `version change shows`() = assertTrue(SplashGate.shouldShow(10 * hour, 9 * hour, "1.0.0", "1.0.1"))

    @Test
    fun `24 h later shows`() = assertTrue(SplashGate.shouldShow(34 * hour, 10 * hour, "1.0.0", "1.0.0"))

    @Test
    fun `updated badge only on a real version change`() {
        assertNull(SplashGate.updatedTo(null, "1.0.0"))
        assertNull(SplashGate.updatedTo("1.0.0", "1.0.0"))
        assertEquals("1.0.1", SplashGate.updatedTo("1.0.0", "1.0.1"))
    }
}
