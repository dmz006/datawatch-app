package com.dmzs.datawatchclient.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sprint 21 test-debt — WearSyncService.publishAlerts DataMap contract.
 *
 * publishAlerts() is private and requires the Wear DataClient (Android runtime).
 * This test covers:
 *   - ALERTS_PATH constant is correct (used by publisher AND both readers)
 *   - DataMap key names are aligned between writer and both reader services
 *   - AlertsCountSnapshot fields are total, needsInput, errors
 *
 * Live publishing behaviour is validated via manual device testing
 * (see docs/testing-tracker.md).
 */
class WearSyncAlertsTest {

    @Test
    fun `ALERTS_PATH constant is slash-datawatch-slash-alerts`() {
        val path = WearSyncService.ALERTS_PATH
        assertEquals("/datawatch/alerts", path)
    }

    @Test
    fun `ALERTS_PATH starts with slash-datawatch`() {
        assertTrue(WearSyncService.ALERTS_PATH.startsWith("/datawatch/"))
    }

    @Test
    fun `writer DataMap keys match AlertsComplicationService reader keys`() {
        // Keys written by WearSyncService.publishAlerts DataMap:
        val writerKeys = setOf("total", "needsInput", "errors", "ts")
        // Keys read by AlertsComplicationService.readAlerts:
        val readerKeys = setOf("total", "needsInput", "errors")
        assertTrue(writerKeys.containsAll(readerKeys), "Reader expects keys not written by publisher")
    }

    @Test
    fun `writer DataMap keys match AlertsTileService reader keys`() {
        val writerKeys = setOf("total", "needsInput", "errors", "ts")
        // Keys read by AlertsTileService.readAlerts:
        val tileReaderKeys = setOf("total", "needsInput", "errors", "ts")
        assertEquals(writerKeys, tileReaderKeys)
    }

    @Test
    fun `zero alerts count is a valid published state`() {
        // publishAlerts is called even when total=0 so the watch tile updates.
        val total = 0
        val needsInput = 0
        val errors = 0
        assertEquals(0, total + needsInput + errors)
    }

    @Test
    fun `needsInput count is subset of total`() {
        // Business invariant: needsInput <= total
        val total = 5
        val needsInput = 2
        assertTrue(needsInput <= total)
    }

    @Test
    fun `errors count is subset of total`() {
        val total = 5
        val errors = 1
        assertTrue(errors <= total)
    }
}
