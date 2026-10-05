package com.dmzs.datawatchclient.ui

import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.ui.monitoring.countStalePeers
import com.dmzs.datawatchclient.ui.monitoring.isPeerRowFlashable
import com.dmzs.datawatchclient.ui.monitoring.shapeBadgeLabel
import com.dmzs.datawatchclient.ui.observer.commBackendLabel
import com.dmzs.datawatchclient.ui.observer.enabledCommBackends
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android-I parity sweep: helpers that mirror PWA behaviour verbatim. */
class AndroidParitySweepTest {
    private val now = kotlinx.datetime.Instant.parse("2026-10-05T12:00:00Z").toEpochMilliseconds()

    private fun peer(lastPush: String?) = ObserverPeerDto(name = "p", lastPushAt = lastPush)

    @Test
    fun `stale peer count follows PWA updatePeerStaleBadge (never pushed or older than 60s)`() {
        val peers =
            listOf(
                peer(null),
                peer("2026-10-05T11:59:30Z"),
                peer("2026-10-05T11:58:00Z"),
                peer("garbage"),
            )
        assertEquals(2, countStalePeers(peers, now))
        assertEquals(0, countStalePeers(emptyList(), now))
    }

    @Test
    fun `flash targets only red-dot rows`() {
        assertTrue(isPeerRowFlashable("2026-10-05T11:58:00Z", now))
        assertFalse(isPeerRowFlashable("2026-10-05T11:59:30Z", now))
        assertFalse(isPeerRowFlashable(null, now))
    }

    @Test
    fun `shape badge uses PWA word labels`() {
        assertEquals("agent", shapeBadgeLabel("A"))
        assertEquals("standalone", shapeBadgeLabel("b"))
        assertEquals("cluster", shapeBadgeLabel("cluster"))
        assertEquals("shape X", shapeBadgeLabel("X"))
        assertEquals("shape ?", shapeBadgeLabel(""))
    }

    @Test
    fun `comm backends keep PWA order and capitalised labels`() {
        val cfg =
            Json.parseToJsonElement(
                """{"webhook":{"enabled":true},"matrix":{"enabled":true},"slack":{"enabled":false},"telegram":{"enabled":true}}""",
            ).jsonObject
        assertEquals(listOf("telegram", "matrix", "webhook"), enabledCommBackends(cfg))
        assertEquals("Github Webhook", commBackendLabel("github_webhook"))
        assertEquals("Dns Channel", commBackendLabel("dns_channel"))
    }
}
