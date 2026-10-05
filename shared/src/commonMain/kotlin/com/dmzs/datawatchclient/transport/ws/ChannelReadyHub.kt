package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure detection of the "MCP channel / ACP server is up" markers the
 * session backends print. Port of PWA `markChannelReadyIfDetected`
 * (app.js) — ANSI is stripped first so colour codes can't split a marker.
 */
public object ChannelReadyDetector {
    /** Marker substrings, verbatim from PWA `markChannelReadyIfDetected`. */
    public val MARKERS: List<String> =
        listOf(
            "Listening for channel",
            "Channel: connected",
            "[opencode-acp] server ready",
            "[opencode-acp] session",
            "[opencode-acp] ready",
            "[opencode-acp] awaiting input",
        )

    // Same expression as PWA `ANSI_RE`: OSC, DCS, APC, PM, then CSI / 2-byte ESC.
    private val ANSI_RE: Regex =
        Regex(
            "\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)" +
                "|\u001BP[^\u001B]*\u001B\\\\" +
                "|\u001B_[^\u001B]*\u001B\\\\" +
                "|\u001B\\^[^\u001B]*\u001B\\\\" +
                "|\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])",
        )

    /** Removes ANSI escape sequences (PWA `stripAnsi`). */
    public fun stripAnsi(text: String): String = if (text.isEmpty()) text else text.replace(ANSI_RE, "")

    /** True when any of [lines] (ANSI-stripped, joined) contains a ready marker. */
    public fun detect(lines: List<String>): Boolean {
        if (lines.isEmpty()) return false
        val text = lines.joinToString("\n") { stripAnsi(it) }
        return MARKERS.any { text.contains(it) }
    }
}

/**
 * Process-wide cache of sessions whose MCP channel / ACP server is known to be
 * connected — PWA `state.channelReady[full_id]`. Survives navigating away from
 * and back to a session. Fed by:
 *  - the WS `channel_ready` frame (`{type:"channel_ready", data:{session_id}}`),
 *  - sessions whose `channel_ready` field is true (`sessions` / `session_state` frames),
 *  - the output scan ([ChannelReadyDetector]) over output / pane_capture / chat frames.
 *
 * Ids are stored as received (normally the full `hostname-shortid` id).
 * Entries are only ever added — readiness is sticky, as in the PWA.
 */
public object ChannelReadyHub {
    private val _ready = MutableStateFlow<Set<String>>(emptySet())

    /** Ids (full ids, occasionally short ids) known to be channel-ready. */
    public val ready: StateFlow<Set<String>> = _ready.asStateFlow()

    /** Marks [sessionId] ready. No-op for blank or already-ready ids. */
    public fun markReady(sessionId: String) {
        if (sessionId.isBlank() || sessionId in _ready.value) return
        _ready.update { it + sessionId }
    }

    /** True when any of [sessionIds] (e.g. full id and short id) is marked ready. */
    public fun isReady(sessionIds: Collection<String>): Boolean {
        val snapshot = _ready.value
        return sessionIds.any { it.isNotBlank() && it in snapshot }
    }

    /** True when [session] reports `channel_ready` or its full id is cached ready. */
    public fun isReady(session: Session): Boolean =
        session.channelReady || isReady(listOf(session.fullId, session.id))

    /** Caches every session in [sessions] whose `channel_ready` is true. */
    public fun observeSessions(sessions: List<Session>) {
        sessions.forEach { if (it.channelReady) markReady(it.fullId) }
    }

    /**
     * Scans [lines] for a ready marker and marks [sessionId] on a hit.
     * Returns true only when the session newly became ready.
     */
    public fun scan(
        sessionId: String,
        lines: List<String>,
    ): Boolean {
        if (sessionId.isBlank() || sessionId in _ready.value) return false
        if (!ChannelReadyDetector.detect(lines)) return false
        markReady(sessionId)
        return true
    }

    /** Scans the text carried by session-scoped output events (already filtered to [sessionId]). */
    internal fun scanEvents(
        sessionId: String,
        events: List<SessionEvent>,
    ) {
        if (sessionId.isBlank() || sessionId in _ready.value) return
        for (ev in events) {
            val lines =
                when (ev) {
                    is SessionEvent.Output -> listOf(ev.body)
                    is SessionEvent.PaneCapture -> ev.lines
                    is SessionEvent.ChatMessage -> listOf(ev.content)
                    else -> continue
                }
            if (scan(sessionId, lines)) return
        }
    }

    /** Handles a WS `channel_ready` frame's `data` object. Returns the id marked, if any. */
    internal fun routeFrame(data: JsonElement?): String? {
        val obj = data as? JsonObject ?: return null
        val prim = obj["session_id"] as? JsonPrimitive ?: return null
        if (!prim.isString) return null
        val id = prim.content.takeIf { it.isNotBlank() } ?: return null
        markReady(id)
        return id
    }

    /** Test hook. */
    internal fun clearForTest() {
        _ready.value = emptySet()
    }
}
