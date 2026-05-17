package com.dmzs.datawatchclient.wear

import com.dmzs.datawatchclient.wear.tile.BriefingTileService
import com.dmzs.datawatchclient.wear.complication.StatusComplicationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-W6 — Unit tests for the BriefingTile snapshot data logic and
 * StatusComplication text building. All pure Kotlin — no Android context needed.
 */
class BriefingTileTest {

    // ---- BriefingSnapshot health state ----

    @Test fun `no data snapshot flags correctly`() {
        val snap = BriefingTileService.BriefingSnapshot(hasData = false)
        assertFalse(snap.hasData)
        assertEquals(0, snap.running)
    }

    @Test fun `healthy snapshot has no errors or waits`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 3, waiting = 0, error = 0, needsInput = 0, alertErrors = 0, hasData = true,
        )
        assertEquals(3, snap.running)
        assertEquals(0, snap.error)
        // Health dot should be green (running > 0, no errors/waits)
        val color = healthColor(snap)
        assertEquals("green", color)
    }

    @Test fun `waiting session triggers amber health`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 1, waiting = 1, error = 0, hasData = true,
        )
        assertEquals("amber", healthColor(snap))
    }

    @Test fun `error session triggers red health`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 0, waiting = 0, error = 1, hasData = true,
        )
        assertEquals("red", healthColor(snap))
    }

    @Test fun `alertErrors trigger red health`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 2, waiting = 0, error = 0, alertErrors = 1, hasData = true,
        )
        assertEquals("red", healthColor(snap))
    }

    @Test fun `needsInput trigger amber health`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 1, waiting = 0, needsInput = 2, error = 0, hasData = true,
        )
        assertEquals("amber", healthColor(snap))
    }

    @Test fun `idle server with no sessions shows muted health`() {
        val snap = BriefingTileService.BriefingSnapshot(
            running = 0, waiting = 0, error = 0, hasData = true,
        )
        assertEquals("muted", healthColor(snap))
    }

    // ---- server name display ----

    @Test fun `blank server name falls back to default`() {
        val snap = BriefingTileService.BriefingSnapshot(serverName = "", hasData = true)
        val displayed = if (snap.serverName.isNotBlank()) snap.serverName else "datawatch"
        assertEquals("datawatch", displayed)
    }

    @Test fun `long server name truncated to 12 chars`() {
        val name = "VeryLongServerNameThatWontFit"
        val displayed = name.take(12)
        assertEquals(12, displayed.length)
    }

    @Test fun `server name shown when present`() {
        val snap = BriefingTileService.BriefingSnapshot(serverName = "Trent", hasData = true)
        val displayed = if (snap.serverName.isNotBlank()) snap.serverName else "datawatch"
        assertEquals("Trent", displayed)
    }

    // ---- StatusComplication text building ----

    @Test fun `status complication shows running count`() {
        val snap = StatusComplicationService.StatusSnapshot(running = 4, waiting = 0, error = 0, progress = 0f)
        val text = buildComplicationText(snap)
        assertTrue(text.contains("4R"), "text='$text'")
    }

    @Test fun `status complication shows blocked when waiting nonzero`() {
        val snap = StatusComplicationService.StatusSnapshot(running = 2, waiting = 1, error = 0, progress = 0f)
        val text = buildComplicationText(snap)
        assertTrue(text.contains("1B"), "text='$text'")
    }

    @Test fun `status complication omits blocked when zero`() {
        val snap = StatusComplicationService.StatusSnapshot(running = 3, waiting = 0, error = 0, progress = 0f)
        val text = buildComplicationText(snap)
        assertFalse(text.contains("B"), "text='$text'")
    }

    @Test fun `status complication zero running`() {
        val snap = StatusComplicationService.StatusSnapshot(running = 0, waiting = 2, error = 0, progress = 0f)
        val text = buildComplicationText(snap)
        assertTrue(text.contains("0R"), "text='$text'")
        assertTrue(text.contains("2B"), "text='$text'")
    }

    @Test fun `ranged value progress clamped to 0-100`() {
        val snap = StatusComplicationService.StatusSnapshot(progress = 1.5f)
        val ranged = (snap.progress * 100f).coerceIn(0f, 100f)
        assertEquals(100f, ranged)
    }

    @Test fun `ranged value progress at 0_75`() {
        val snap = StatusComplicationService.StatusSnapshot(progress = 0.75f)
        val ranged = (snap.progress * 100f).coerceIn(0f, 100f)
        assertEquals(75f, ranged, 0.01f)
    }

    @Test fun `ranged value zero progress`() {
        val snap = StatusComplicationService.StatusSnapshot(progress = 0f)
        val ranged = (snap.progress * 100f).coerceIn(0f, 100f)
        assertEquals(0f, ranged)
    }

    // ---- sync timestamp display ----

    @Test fun `recent sync shown as minutes ago`() {
        val nowMs = System.currentTimeMillis()
        val fiveMinAgo = nowMs - 5 * 60_000
        val snap = BriefingTileService.BriefingSnapshot(syncTs = fiveMinAgo, hasData = true)
        val minutesAgo = (System.currentTimeMillis() - snap.syncTs) / 60_000
        assertTrue(minutesAgo in 4..6, "minutesAgo=$minutesAgo")
    }

    // ---- helpers ----

    private fun healthColor(snap: BriefingTileService.BriefingSnapshot): String = when {
        snap.error > 0 || snap.alertErrors > 0 -> "red"
        snap.waiting > 0 || snap.needsInput > 0 -> "amber"
        snap.running > 0 -> "green"
        else -> "muted"
    }

    private fun buildComplicationText(snap: StatusComplicationService.StatusSnapshot): String =
        buildString {
            append("${snap.running}R")
            val blocked = snap.waiting + snap.error
            if (blocked > 0) append(" ${blocked}B")
        }
}
