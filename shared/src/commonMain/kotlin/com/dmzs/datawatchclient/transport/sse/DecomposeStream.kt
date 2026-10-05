package com.dmzs.datawatchclient.transport.sse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Live planning stream (server BL328, PWA `_startDecomposeStream` app.js ~11123).
 *
 * `POST /api/autonomous/prds/{id}/decompose` → 202 `{task_id, stream_url}`, then
 * `GET /api/autonomous/prds/{id}/decompose/stream` (text/event-stream) emits one
 * `id: N` + `data: <json>` frame per event:
 *
 * ```
 * {"type":"story","index":0,"title":"…","description":"…","id":"…"}
 * {"type":"progress","done":3,"total":7}
 * {"type":"complete","story_count":7}
 * {"type":"error","message":"…"}
 * ```
 *
 * The server replays every event after `Last-Event-ID` on reconnect and closes
 * the stream once the job is terminal. This file is deliberately self-contained
 * (own tiny line reader) so it doesn't collide with other SSE readers.
 */
public data class DecomposeStreamEvent(
    /** story | progress | complete | error (anything else is dropped by the parser). */
    val type: String,
    val index: Int = 0,
    val storyId: String = "",
    val title: String = "",
    val description: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val storyCount: Int = 0,
    val message: String = "",
    /** SSE `id:` of the frame — used as Last-Event-ID on reconnect. */
    val eventId: String? = null,
) {
    val isTerminal: Boolean get() = type == TYPE_COMPLETE || type == TYPE_ERROR

    public companion object {
        public const val TYPE_STORY: String = "story"
        public const val TYPE_PROGRESS: String = "progress"
        public const val TYPE_COMPLETE: String = "complete"
        public const val TYPE_ERROR: String = "error"
    }
}

/** One dispatched SSE frame (W3C event-stream). */
public data class DecomposeSseFrame(
    val id: String?,
    val event: String,
    val data: String,
)

/**
 * Minimal W3C event-stream line reader: feed lines (without the trailing
 * newline); a blank line dispatches the accumulated frame. Handles `id:`,
 * `event:`, multi-line `data:`, `retry:` (ignored) and `:` comments.
 */
public class DecomposeSseLineReader {
    private var id: String? = null
    private var event: String = "message"
    private val data = StringBuilder()
    private var hasData = false

    public fun feed(rawLine: String): DecomposeSseFrame? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "id" -> id = value
            "event" -> event = value.ifEmpty { "message" }
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            else -> Unit // retry: and unknown fields
        }
        return null
    }

    /** Dispatch a trailing frame when the stream closes without a final blank line. */
    public fun flush(): DecomposeSseFrame? = dispatch()

    private fun dispatch(): DecomposeSseFrame? {
        val frame: DecomposeSseFrame? = if (hasData) DecomposeSseFrame(id = id, event = event, data = data.toString()) else null
        event = "message"
        data.setLength(0)
        hasData = false
        // `id` persists across frames per the SSE spec (last event id).
        return frame
    }
}

public object DecomposeStreamParser {
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val known: Set<String> =
        setOf(
            DecomposeStreamEvent.TYPE_STORY,
            DecomposeStreamEvent.TYPE_PROGRESS,
            DecomposeStreamEvent.TYPE_COMPLETE,
            DecomposeStreamEvent.TYPE_ERROR,
        )

    /** Parse one frame's JSON payload; null for malformed or unknown events. */
    public fun parse(frame: DecomposeSseFrame): DecomposeStreamEvent? = parse(frame.data, frame.id)

    public fun parse(
        data: String,
        eventId: String?,
    ): DecomposeStreamEvent? {
        val obj: JsonObject = (runCatching { json.parseToJsonElement(data) }.getOrNull() as? JsonObject) ?: return null
        val type: String = str(obj, "type")
        if (type !in known) return null
        return DecomposeStreamEvent(
            type = type,
            index = int(obj, "index"),
            storyId = str(obj, "id"),
            title = str(obj, "title"),
            description = str(obj, "description"),
            done = int(obj, "done"),
            total = int(obj, "total"),
            storyCount = int(obj, "story_count"),
            message = str(obj, "message"),
            eventId = eventId,
        )
    }

    private fun str(
        obj: JsonObject,
        key: String,
    ): String {
        val p = obj[key] as? JsonPrimitive ?: return ""
        return if (p.isString) p.content else if (p.content == "null") "" else p.content
    }

    private fun int(
        obj: JsonObject,
        key: String,
    ): Int = (obj[key] as? JsonPrimitive)?.intOrNull ?: 0
}

/**
 * Accumulated view of a planning run for the detail screens: the stories
 * received so far (deduped by index — the server replays after reconnect),
 * progress counters and the terminal outcome.
 */
public data class DecomposeLiveState(
    val stories: List<DecomposeStreamEvent> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val finished: Boolean = false,
    val storyCount: Int = 0,
    val error: String? = null,
) {
    val isActive: Boolean get() = !finished && error == null

    public fun apply(ev: DecomposeStreamEvent): DecomposeLiveState =
        when (ev.type) {
            DecomposeStreamEvent.TYPE_STORY -> {
                val kept = stories.filter { it.index != ev.index }
                copy(stories = (kept + ev).sortedBy { it.index })
            }
            DecomposeStreamEvent.TYPE_PROGRESS -> copy(done = ev.done, total = ev.total)
            DecomposeStreamEvent.TYPE_COMPLETE -> copy(finished = true, storyCount = ev.storyCount)
            DecomposeStreamEvent.TYPE_ERROR -> copy(error = ev.message.ifEmpty { "error" })
            else -> this
        }

    public companion object {
        /** Fold a batch of events from a fresh state (used by tests + iOS bridge). */
        public fun fold(events: List<DecomposeStreamEvent>): DecomposeLiveState =
            events.fold(DecomposeLiveState()) { acc, e -> acc.apply(e) }
    }
}
