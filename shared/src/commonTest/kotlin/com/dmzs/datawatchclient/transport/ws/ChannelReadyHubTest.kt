package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PWA `markChannelReadyIfDetected` / `handleChannelReadyEvent` / `state.channelReady` parity. */
class ChannelReadyHubTest {
    private val esc = "\u001B"

    @BeforeTest
    fun reset() {
        ChannelReadyHub.clearForTest()
    }

    // ── Detector ─────────────────────────────────────────────────────────

    @Test
    fun `every PWA marker is detected`() {
        ChannelReadyDetector.MARKERS.forEach { marker ->
            assertTrue(ChannelReadyDetector.detect(listOf("prefix $marker suffix")), marker)
        }
    }

    @Test
    fun `marker split by ANSI colour codes is detected after stripping`() {
        val line = "$esc[1m$esc[32mListening$esc[0m for ${esc}[33mchannel$esc[0m messages"
        assertTrue(ChannelReadyDetector.detect(listOf(line)))
        val acp = "$esc[36m[opencode-acp]$esc[0m server ready on :4096"
        assertTrue(ChannelReadyDetector.detect(listOf(acp)))
    }

    @Test
    fun `no marker means not ready`() {
        assertFalse(ChannelReadyDetector.detect(emptyList()))
        assertFalse(ChannelReadyDetector.detect(listOf("Starting claude…", "Channel: connecting")))
        assertFalse(ChannelReadyDetector.detect(listOf("[opencode-acp] starting")))
    }

    @Test
    fun `stripAnsi removes CSI OSC and two-byte escapes`() {
        assertEquals("abc", ChannelReadyDetector.stripAnsi("$esc[2J$esc[Ha${esc}]0;title\u0007b${esc}Mc"))
        assertEquals("plain", ChannelReadyDetector.stripAnsi("plain"))
        assertEquals("", ChannelReadyDetector.stripAnsi(""))
        assertEquals("x", ChannelReadyDetector.stripAnsi("${esc}]8;;http://e\u001B\\x"))
    }

    @Test
    fun `marker across joined lines matches per line only`() {
        assertTrue(ChannelReadyDetector.detect(listOf("noise", "Channel: connected")))
        assertFalse(ChannelReadyDetector.detect(listOf("Channel:", "connected")))
    }

    // ── Hub ──────────────────────────────────────────────────────────────

    @Test
    fun `markReady is sticky and ignores blank ids`() {
        ChannelReadyHub.markReady("")
        assertTrue(ChannelReadyHub.ready.value.isEmpty())
        ChannelReadyHub.markReady("ring-abcd")
        ChannelReadyHub.markReady("ring-abcd")
        assertEquals(setOf("ring-abcd"), ChannelReadyHub.ready.value)
        assertTrue(ChannelReadyHub.isReady(listOf("ring-abcd", "abcd")))
        assertFalse(ChannelReadyHub.isReady(listOf("other-abcd", "")))
    }

    @Test
    fun `scan returns true only on the first detection`() {
        assertFalse(ChannelReadyHub.scan("ring-abcd", listOf("booting")))
        assertTrue(ChannelReadyHub.scan("ring-abcd", listOf("Listening for channel messages")))
        assertFalse(ChannelReadyHub.scan("ring-abcd", listOf("Listening for channel messages")))
        assertFalse(ChannelReadyHub.scan("", listOf("Listening for channel messages")))
    }

    @Test
    fun `isReady honours channelReady field and cached full id`() {
        val s = session(id = "abcd", host = "ring", channelReady = false)
        assertFalse(ChannelReadyHub.isReady(s))
        assertTrue(ChannelReadyHub.isReady(s.copy(channelReady = true)))
        ChannelReadyHub.markReady("ring-abcd")
        assertTrue(ChannelReadyHub.isReady(s))
    }

    @Test
    fun `observeSessions caches only channel-ready sessions by full id`() {
        ChannelReadyHub.observeSessions(
            listOf(
                session(id = "aaaa", host = "ring", channelReady = true),
                session(id = "bbbb", host = "ring", channelReady = false),
            ),
        )
        assertEquals(setOf("ring-aaaa"), ChannelReadyHub.ready.value)
    }

    // ── WS frames ────────────────────────────────────────────────────────

    @Test
    fun `channel_ready frame marks the session full id`() {
        val data = buildJsonObject { put("session_id", JsonPrimitive("ring-abcd")) }
        assertEquals("ring-abcd", ChannelReadyHub.routeFrame(data))
        assertTrue(ChannelReadyHub.isReady(listOf("ring-abcd")))
    }

    @Test
    fun `malformed channel_ready frames are ignored`() {
        assertNull(ChannelReadyHub.routeFrame(null))
        assertNull(ChannelReadyHub.routeFrame(JsonPrimitive("x")))
        assertNull(ChannelReadyHub.routeFrame(buildJsonObject { put("session_id", JsonPrimitive("")) }))
        assertNull(ChannelReadyHub.routeFrame(buildJsonObject { put("session_id", JsonPrimitive(5)) }))
        assertNull(ChannelReadyHub.routeFrame(buildJsonObject { put("other", JsonPrimitive("ring-abcd")) }))
        assertTrue(ChannelReadyHub.ready.value.isEmpty())
    }

    @Test
    fun `pane_capture frame for the session is scanned via scanEvents`() {
        val events =
            frame(
                "pane_capture",
                "ring-abcd",
                listOf("$esc[32m[opencode-acp]$esc[0m ready", "> "),
            ).toDomainEvents("ring-abcd", "abcd")
        ChannelReadyHub.scanEvents("ring-abcd", events)
        assertTrue(ChannelReadyHub.isReady(listOf("ring-abcd")))
    }

    @Test
    fun `output frame for a different session is not scanned into ours`() {
        val events =
            frame("output", "other-9999", listOf("Listening for channel messages"))
                .toDomainEvents("ring-abcd", "abcd")
        ChannelReadyHub.scanEvents("ring-abcd", events)
        assertFalse(ChannelReadyHub.isReady(listOf("ring-abcd")))
    }

    @Test
    fun `chat_message content is scanned`() {
        val data =
            buildJsonObject {
                put("session_id", JsonPrimitive("ring-abcd"))
                put("role", JsonPrimitive("system"))
                put("content", JsonPrimitive("[opencode-acp] awaiting input"))
            }
        val events = WsFrameDto(type = "chat_message", data = data, timestamp = null).toDomainEvents("ring-abcd")
        ChannelReadyHub.scanEvents("ring-abcd", events)
        assertTrue(ChannelReadyHub.isReady(listOf("ring-abcd")))
    }

    private fun frame(
        type: String,
        sid: String,
        lines: List<String>,
    ): WsFrameDto =
        WsFrameDto(
            type = type,
            data =
                buildJsonObject {
                    put("session_id", JsonPrimitive(sid))
                    put("lines", JsonArray(lines.map { JsonPrimitive(it) }))
                },
            timestamp = "2026-10-05T00:00:00Z",
        )

    private fun session(
        id: String,
        host: String,
        channelReady: Boolean,
    ): Session =
        Session(
            id = id,
            serverProfileId = "p1",
            hostnamePrefix = host,
            state = SessionState.Running,
            createdAt = Instant.fromEpochMilliseconds(0),
            lastActivityAt = Instant.fromEpochMilliseconds(0),
            channelReady = channelReady,
        )
}
