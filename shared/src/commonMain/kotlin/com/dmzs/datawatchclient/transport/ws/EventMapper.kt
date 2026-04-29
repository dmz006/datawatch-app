package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Prompt
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Converts a [WsFrameDto] from the datawatch `/ws` hub into zero-or-more
 * domain [SessionEvent]s.
 *
 * Server frame format, loosely: a `type` string, a `data` object, and a
 * `timestamp` string. Per-type data shapes (source: parent
 * `internal/server/web/app.js`):
 *
 * - `sessions` — skipped, REST has the authoritative session list
 * - `session_update` — skipped, REST refresh covers it
 * - `raw_output` — one SessionEvent.Output per line in data.lines
 * - `output` — same as raw_output, fallback when raw isn't used
 * - `needs_input` — SessionEvent.PromptDetected
 * - `notification` — SessionEvent.Output (system-colored)
 * - `alert` — SessionEvent.Output (system-colored)
 * - `error` — SessionEvent.Error
 *
 * Frames whose type we don't know become [SessionEvent.Unknown] so
 * forward-compat server additions survive a round-trip through this client.
 *
 * @param forSessionId filters raw_output / output frames to the session the
 *   UI is currently showing. Non-session-filtered frames (global errors,
 *   alerts) pass through unconditionally.
 */
internal fun WsFrameDto.toDomainEvents(forSessionId: String): List<SessionEvent> {
    val ts =
        timestamp?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: Clock.System.now()
    val obj = data as? JsonObject
    return when (type) {
        // v0.42.7 — bulk `sessions` frame: scan for our session id and
        // emit a StateChange when the state differs from the last
        // bulk-emitted value. Without this, daemon state transitions
        // delivered ONLY via the bulk frame (waiting_input → running,
        // etc.) never fired SessionDetailViewModel.startStream's
        // refresh, leaving the input-required banner stale until the
        // user exited and re-entered the session. PWA v5.26.49 parity
        // (gap #2 from the 72h audit). De-duped per session so we
        // don't spam REST on every push of an unchanged state.
        "sessions" -> buildBulkStateChangeEvents(obj, ts, forSessionId)
        "pane_capture" -> buildPaneCaptureEvents(obj, ts, forSessionId)
        "raw_output", "output" -> buildOutputEvents(obj, ts, forSessionId)
        "chat_message" -> listOfNotNull(buildChatMessage(obj, ts, forSessionId))
        "needs_input", "prompt", "prompt_detected" ->
            listOfNotNull(
                buildPrompt(obj, ts, forSessionId),
            )
        "notification" -> listOfNotNull(buildNotification(obj, ts, forSessionId))
        "alert" -> listOfNotNull(buildAlert(obj, ts, forSessionId))
        "error" ->
            listOf(
                SessionEvent.Error(
                    sessionId = obj?.jsonString("session_id") ?: forSessionId,
                    ts = ts,
                    message = obj?.jsonString("message") ?: "server error",
                ),
            )
        "session_update" -> {
            val from = obj?.jsonString("state")?.let { SessionState.fromWire(it) }
            if (from == null) {
                emptyList()
            } else {
                listOf(
                    SessionEvent.StateChange(
                        sessionId = obj.jsonString("id") ?: forSessionId,
                        ts = ts,
                        from = SessionState.New,
                        to = from,
                    ),
                )
            }
        }
        "rate_limited", "rate_limit" ->
            listOf(
                SessionEvent.RateLimited(
                    sessionId = obj?.jsonString("session_id") ?: forSessionId,
                    ts = ts,
                    retryAfter =
                        obj?.jsonString("retry_after")
                            ?.let { runCatching { Instant.parse(it) }.getOrNull() },
                ),
            )
        "completed", "done" ->
            listOf(
                SessionEvent.Completed(
                    sessionId = obj?.jsonString("session_id") ?: forSessionId,
                    ts = ts,
                    exitCode = obj?.get("exit_code")?.jsonPrimitive?.longOrNull?.toInt(),
                ),
            )
        // Frames we intentionally don't forward to the per-session UI.
        "session_aware", "channel_reply", "channel_notify", "response",
        "ack",
        -> emptyList()
        else -> listOf(SessionEvent.Unknown(sessionId = forSessionId, ts = ts, type = type))
    }
}

/**
 * Tracks which session ids have already had their first pane_capture
 * routed to the UI. A WeakHashMap-equivalent isn't available in common
 * code; a simple MutableSet works because session ids are bounded in
 * practice (users don't open 10k sessions per app install).
 */
private val firstCaptureSeen: MutableSet<String> = mutableSetOf()

/**
 * Last bulk-frame state observed per session id. Used to dedupe the
 * synthetic StateChange events the bulk `sessions` frame emits — the
 * daemon ships the same state on every periodic broadcast, but we
 * only want to nudge SessionDetailViewModel.refreshFromServer() when
 * the state actually flipped.
 */
private val lastBulkStateByid: MutableMap<String, String> = mutableMapOf()

private fun buildBulkStateChangeEvents(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): List<SessionEvent> {
    if (obj == null) return emptyList()
    // Daemon ships either { sessions: [{id, state, ...}, ...] } or a
    // flat top-level array we'd have parsed as JsonArray — we only
    // handle the object shape here since WsFrameDto.data is JsonObject.
    val arr = runCatching { obj["sessions"]?.jsonArray }.getOrNull() ?: return emptyList()
    arr.forEach { el ->
        val itemObj = el as? JsonObject ?: return@forEach
        val sid = itemObj.jsonString("id") ?: itemObj.jsonString("session_id") ?: return@forEach
        // Match by short or fully-qualified id; the daemon emits whichever
        // shape it stored at session create time.
        if (!sid.contains(forSessionId) && !forSessionId.contains(sid)) return@forEach
        val stateStr = itemObj.jsonString("state") ?: return@forEach
        val previous = lastBulkStateByid[sid]
        if (previous == stateStr) return@forEach
        lastBulkStateByid[sid] = stateStr
        // Don't emit a StateChange on the very first observation —
        // SessionDetailScreen already does an initial REST refresh on
        // entry, so duplicating that would be wasteful. Only emit on
        // an actual transition.
        if (previous != null) {
            return listOf(
                SessionEvent.StateChange(
                    sessionId = sid,
                    ts = ts,
                    from = SessionState.fromWire(previous),
                    to = SessionState.fromWire(stateStr),
                ),
            )
        }
    }
    return emptyList()
}

/**
 * Expose a reset entry point so the UI can clear the first-capture flag
 * on session switch (so the next pane_capture for the newly-visible
 * session is treated as first — fresh terminal paint).
 */
public fun resetPaneCaptureSeen(sessionId: String) {
    firstCaptureSeen.remove(sessionId)
}

private fun buildPaneCaptureEvents(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): List<SessionEvent> {
    if (obj == null) return emptyList()
    val sid = obj.jsonString("session_id") ?: forSessionId
    // Filter by the session the UI is currently rendering — same logic as
    // raw_output so stray frames for other sessions don't land.
    if (!sid.contains(forSessionId) && !forSessionId.contains(sid)) return emptyList()
    val lines =
        runCatching {
            obj["lines"]?.jsonArray?.mapNotNull { el ->
                runCatching { el.jsonPrimitive.content }.getOrNull()
            }
        }.getOrNull().orEmpty()
    if (lines.isEmpty()) return emptyList()
    val isFirst = firstCaptureSeen.add(sid)
    return listOf(
        SessionEvent.PaneCapture(
            sessionId = sid,
            ts = ts,
            lines = lines,
            isFirst = isFirst,
        ),
    )
}

private fun buildOutputEvents(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): List<SessionEvent> {
    if (obj == null) return emptyList()
    val sid = obj.jsonString("session_id") ?: forSessionId
    // Only render frames that belong to the session the UI is showing.
    if (!sid.contains(forSessionId) && !forSessionId.contains(sid)) return emptyList()
    val lines =
        runCatching {
            obj["lines"]?.jsonArray?.mapNotNull { el ->
                runCatching { el.jsonPrimitive.content }.getOrNull()
            }
        }.getOrNull().orEmpty()
    if (lines.isEmpty()) {
        // Some server frames put a single line under "body" or "text" or "data".
        val single = obj.jsonString("body") ?: obj.jsonString("text") ?: obj.jsonString("data")
        if (single != null && single.isNotEmpty()) {
            return listOf(outputEvent(sid, ts, single))
        }
        return emptyList()
    }
    return lines.map { line -> outputEvent(sid, ts, line) }
}

private fun outputEvent(
    sid: String,
    ts: Instant,
    line: String,
): SessionEvent.Output =
    SessionEvent.Output(
        sessionId = sid,
        ts = ts,
        // Lines may or may not already carry a trailing newline; normalise to
        // CRLF so xterm renders them as distinct rows.
        body = if (line.endsWith("\n") || line.endsWith("\r\n")) line else "$line\r\n",
        stream = SessionEvent.Output.Stream.Stdout,
    )

private fun buildPrompt(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): SessionEvent? {
    if (obj == null) return null
    val sid = obj.jsonString("session_id") ?: forSessionId
    val text = obj.jsonString("prompt") ?: obj.jsonString("message") ?: return null
    return SessionEvent.PromptDetected(
        sessionId = sid,
        ts = ts,
        prompt =
            Prompt(
                sessionId = sid,
                text = text,
                detectedAt = ts,
                kind =
                    when (obj.jsonString("prompt_kind")?.lowercase()) {
                        "approval", "yes_no" -> Prompt.Kind.Approval
                        "choice" -> Prompt.Kind.Choice
                        "rate_limit" -> Prompt.Kind.RateLimit
                        else -> Prompt.Kind.FreeForm
                    },
            ),
    )
}

private fun buildNotification(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): SessionEvent? {
    val msg = obj?.jsonString("message") ?: return null
    val sid = obj.jsonString("session_id") ?: forSessionId
    return outputEvent(sid, ts, "\u001b[36m[notify] $msg\u001b[0m")
}

private fun buildAlert(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): SessionEvent? {
    if (obj == null) return null
    val sid = obj.jsonString("session_id") ?: forSessionId
    val msg = obj.jsonString("message") ?: obj.jsonString("summary") ?: return null
    return outputEvent(sid, ts, "\u001b[33m[alert] $msg\u001b[0m")
}

private fun buildChatMessage(
    obj: JsonObject?,
    ts: Instant,
    forSessionId: String,
): SessionEvent? {
    if (obj == null) return null
    val sid = obj.jsonString("session_id") ?: forSessionId
    if (!sid.contains(forSessionId) && !forSessionId.contains(sid)) return null
    val content = obj.jsonString("content") ?: ""
    val role =
        when (obj.jsonString("role")?.lowercase()) {
            "user" -> SessionEvent.ChatMessage.Role.User
            "assistant", "ai", "llm" -> SessionEvent.ChatMessage.Role.Assistant
            "system" -> SessionEvent.ChatMessage.Role.System
            else -> SessionEvent.ChatMessage.Role.System
        }
    val streaming =
        runCatching { obj["streaming"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false
    return SessionEvent.ChatMessage(
        sessionId = sid,
        ts = ts,
        role = role,
        content = content,
        streaming = streaming,
    )
}

private fun JsonObject.jsonString(key: String): String? = runCatching { get(key)?.jsonPrimitive?.content }.getOrNull()
