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
    fun `goose and opencode config cards carry exactly the PWA keys`() {
        val s = com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas
        assertEquals(
            listOf(
                "goose.enabled", "goose.binary", "goose.provider", "goose.model",
                "goose.api_key_ref", "goose.channel_enabled",
            ),
            s.Goose.fields.map { it.key },
        )
        assertEquals(listOf("opencode.default_model"), s.OpenCode.fields.map { it.key })
    }

    @Test
    fun `automata state rank is the PWA table (unknown 9, empty is draft)`() {
        val r: (String) -> Int = { com.dmzs.datawatchclient.ui.autonomous.prdStateRank(it) }
        assertEquals(0, r("waiting_input"))
        assertEquals(0, r("revisions_asked"))
        assertEquals(1, r("blocked"))
        assertEquals(2, r("decomposing"))
        assertEquals(3, r("planning"))
        assertEquals(4, r(""))
        assertEquals(5, r("cancelled"))
        assertEquals(6, r("archived"))
        assertEquals(9, r("awaiting_approval"))
    }

    @Test
    fun `done-card dimming matches PWA state opacities`() {
        val a: (com.dmzs.datawatchclient.domain.SessionState) -> Float = { com.dmzs.datawatchclient.ui.sessions.sessionCardAlpha(it) }
        assertEquals(0.7f, a(com.dmzs.datawatchclient.domain.SessionState.Completed))
        assertEquals(0.5f, a(com.dmzs.datawatchclient.domain.SessionState.Killed))
        assertEquals(1.0f, a(com.dmzs.datawatchclient.domain.SessionState.Error))
        assertEquals(1.0f, a(com.dmzs.datawatchclient.domain.SessionState.Running))
    }

    @Test
    fun `chat bubbles use the PWA chat palette`() {
        val role = com.dmzs.datawatchclient.domain.SessionEvent.ChatMessage.Role
        val user = com.dmzs.datawatchclient.ui.sessions.chatBubbleStyle(role.User)
        val ai = com.dmzs.datawatchclient.ui.sessions.chatBubbleStyle(role.Assistant)
        val sys = com.dmzs.datawatchclient.ui.sessions.chatBubbleStyle(role.System)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3B82F6), user.avatarBg)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF10B981), ai.avatarBg)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF64748B), sys.avatarBg)
        assertEquals(listOf("U", "AI", "S"), listOf(user.avatar, ai.avatar, sys.avatar))
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
