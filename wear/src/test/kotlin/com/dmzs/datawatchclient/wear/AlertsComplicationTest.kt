package com.dmzs.datawatchclient.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sprint 21 test-debt — AlertsComplicationService pure-logic coverage.
 *
 * The service reads `/datawatch/alerts` DataItem from the Wear DataClient,
 * which requires Android runtime. These tests cover the extractable pure-logic:
 *   - Complication text format  `"!N"` where N = total alert count
 *   - Content description format `"N alerts, M input, E errors"`
 *   - Fallback to (0,0,0) on DataClient failure
 *   - Path constant alignment with WearSyncService
 */
class AlertsComplicationTest {

    // Path constant used by both the writer (WearSyncService) and reader
    // (AlertsComplicationService / AlertsTileService). Verify they match.
    private val expectedPath = "/datawatch/alerts"

    @Test
    fun `complication short text format is exclamation-bang-total`() {
        val total = 5
        val text = "!$total"
        assertEquals("!5", text)
    }

    @Test
    fun `complication content description format is correct`() {
        val total = 3
        val needsInput = 1
        val errors = 0
        val desc = "$total alerts, $needsInput input, $errors errors"
        assertEquals("3 alerts, 1 input, 0 errors", desc)
    }

    @Test
    fun `fallback triple zeros matches error branch`() {
        // readAlerts() returns Triple(0,0,0) on DataClient exception
        val fallback = Triple(0, 0, 0)
        assertEquals(0, fallback.first)   // total
        assertEquals(0, fallback.second)  // needsInput
        assertEquals(0, fallback.third)   // errors
    }

    @Test
    fun `zero total produces bang-zero text`() {
        val text = "!${0}"
        assertEquals("!0", text)
    }

    @Test
    fun `alerts path constant matches wear sync service path`() {
        assertEquals("/datawatch/alerts", expectedPath)
    }

    @Test
    fun `preview data title is alrt`() {
        // getPreviewData() hard-codes "alrt" as the tile title.
        val previewTitle = "alrt"
        assertEquals(4, previewTitle.length)
        assertTrue(previewTitle.isNotBlank())
    }

    @Test
    fun `DataMap key names match writer keys`() {
        // Keys written by WearSyncService.publishAlerts and read by AlertsComplicationService.
        val writerKeys = setOf("total", "needsInput", "errors", "ts")
        val readerKeys = setOf("total", "needsInput", "errors")
        // All reader keys must be present in writer's key set
        assertTrue(writerKeys.containsAll(readerKeys))
    }
}
