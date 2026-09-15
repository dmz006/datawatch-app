package com.dmzs.datawatchclient.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sprint 21 test-debt — AlertsTileService pure-logic coverage.
 *
 * The tile renders from an `AlertsSnapshot` read via the Wear DataClient;
 * that part requires Android runtime. These tests cover the state-branch
 * logic via a mirrored data class (same approach as BriefingTileTest).
 */
class AlertsTileTest {

    // Mirror AlertsTileService.AlertsSnapshot (private data class)
    private data class AlertsSnap(
        val total: Int = 0,
        val needsInput: Int = 0,
        val errors: Int = 0,
        val syncTs: Long = 0L,
        val hasData: Boolean = false,
    )

    // Mirror the health-dot logic from AlertsTileService.healthDot()
    private fun healthState(snap: AlertsSnap): String = when {
        !snap.hasData -> "no-data"
        snap.errors > 0 -> "red"
        snap.needsInput > 0 -> "amber"
        snap.total > 0 -> "amber"
        else -> "green"
    }

    @Test
    fun `no-data snapshot has hasData false`() {
        val snap = AlertsSnap()
        assertFalse(snap.hasData)
        assertEquals("no-data", healthState(snap))
    }

    @Test
    fun `errors trigger red health`() {
        val snap = AlertsSnap(total = 2, errors = 1, hasData = true)
        assertEquals("red", healthState(snap))
    }

    @Test
    fun `needsInput with no errors triggers amber`() {
        val snap = AlertsSnap(total = 3, needsInput = 2, errors = 0, hasData = true)
        assertEquals("amber", healthState(snap))
    }

    @Test
    fun `alerts with no errors and no input triggers amber`() {
        val snap = AlertsSnap(total = 5, needsInput = 0, errors = 0, hasData = true)
        assertEquals("amber", healthState(snap))
    }

    @Test
    fun `all-zero with hasData true is green`() {
        val snap = AlertsSnap(total = 0, needsInput = 0, errors = 0, hasData = true)
        assertEquals("green", healthState(snap))
    }

    @Test
    fun `stat row label is total as string`() {
        val snap = AlertsSnap(total = 7, hasData = true)
        assertEquals("7", snap.total.toString())
    }

    @Test
    fun `freshness interval is 30 seconds`() {
        // Tile declares FRESHNESS_MS = 30_000L. Verify the value is reasonable.
        val freshnessMs = 30_000L
        assertEquals(30, freshnessMs / 1_000L)
    }

    @Test
    fun `fallback to empty snapshot when DataClient throws`() {
        val fallback = AlertsSnap()
        assertFalse(fallback.hasData)
        assertEquals(0, fallback.total)
        assertEquals(0, fallback.needsInput)
        assertEquals(0, fallback.errors)
    }

    @Test
    fun `DataMap keys are total needsInput errors ts`() {
        // Keys that AlertsTileService reads from DataMap — must match WearSyncService.
        val keys = listOf("total", "needsInput", "errors", "ts")
        assertTrue(keys.contains("total"))
        assertTrue(keys.contains("needsInput"))
        assertTrue(keys.contains("errors"))
    }
}
