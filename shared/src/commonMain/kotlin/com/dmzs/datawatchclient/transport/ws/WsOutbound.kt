package com.dmzs.datawatchclient.transport.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Outbound-frame channel for the `/ws` hub. Consumers of
 * [WebSocketTransport.events] launch a collector that forwards
 * frames tagged for their [sessionId] over the live socket.
 *
 * Introduced 2026-04-21 so session-detail can send `resize_term`
 * (xterm cols/rows sync) and `command` (scroll-mode enter/exit,
 * `sendkey`) frames matching the PWA. Prior to this the WS transport
 * was read-only.
 *
 * Shape: `WsOutboundEnvelope(sessionId, text)` where `text` is an
 * already-serialised JSON frame body (e.g. `{"type":"resize_term",
 * "data":{"session_id":"a3f2","cols":120,"rows":40}}`).
 *
 * Buffering: `replay = 0` (no replay; frames emitted before a
 * subscriber is listening are dropped — the caller is responsible
 * for only emitting after the session is subscribed),
 * `extraBufferCapacity = 64` (absorbs bursty key / resize emits
 * without suspending the producer).
 */
public data class WsOutboundEnvelope(
    public val sessionId: String,
    public val text: String,
)

public object WsOutbound {
    private val _frames: MutableSharedFlow<WsOutboundEnvelope> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 64)

    public val frames: SharedFlow<WsOutboundEnvelope> = _frames.asSharedFlow()

    /**
     * Queue a single frame for the WS connection subscribed to
     * [sessionId]. Non-blocking; drops when no subscriber is
     * currently collecting (e.g. session detail not open). Safe
     * to call from UI callbacks.
     */
    public fun tryEmit(
        sessionId: String,
        jsonText: String,
    ): Boolean = _frames.tryEmit(WsOutboundEnvelope(sessionId, jsonText))

    /**
     * Send a `resize_term` frame. Matches PWA `syncTmuxSize()`:
     * `{type: "resize_term", data: {session_id, cols, rows}}`.
     * Server resizes the tmux pane and replies with a fresh
     * `pane_capture` at the new dimensions.
     */
    public fun sendResizeTerm(
        sessionId: String,
        cols: Int,
        rows: Int,
    ): Boolean {
        val text =
            """{"type":"resize_term","data":{"session_id":"${escape(sessionId)}","cols":$cols,"rows":$rows}}"""
        return tryEmit(sessionId, text)
    }

    /**
     * Send a `send_input` frame — the PWA path for composer text
     * (app.js:2341). Wire shape:
     * `{type:"send_input", data:{session_id, text}}`.
     *
     * Important: the server does **not** expose
     * `POST /api/sessions/reply` — earlier mobile code posted there
     * and got 404. PWA has never used a REST endpoint for replies;
     * send_input over the already-open WS hub is the only path.
     */
    public fun sendInput(
        sessionId: String,
        text: String,
    ): Boolean {
        val body =
            """{"type":"send_input","data":{"session_id":"${escape(sessionId)}","text":"${escape(text)}"}}"""
        return tryEmit(sessionId, body)
    }

    /**
     * Send a generic `command` frame. PWA uses this for `sendkey`
     * (arrow/PageUp/Escape), `tmux-copy-mode`, `tmux-kill`.
     * Example: `sendCommand(id, "sendkey $id: Escape")`.
     */
    public fun sendCommand(
        sessionId: String,
        text: String,
    ): Boolean {
        val body =
            """{"type":"command","data":{"text":"${escape(text)}"}}"""
        return tryEmit(sessionId, body)
    }

    /** Minimal JSON-string escape; avoids dragging in a Json builder for one field. */
    private fun escape(s: String): String =
        buildString(s.length + 2) {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (c.code < 0x20) {
                            append("\\u")
                            append(c.code.toString(16).padStart(4, '0'))
                        } else {
                            append(c)
                        }
                }
            }
        }
}
