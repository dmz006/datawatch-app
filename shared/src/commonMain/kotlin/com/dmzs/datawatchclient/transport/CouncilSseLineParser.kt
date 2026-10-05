package com.dmzs.datawatchclient.transport

/**
 * One complete `text/event-stream` frame from the council run event stream
 * (`GET /api/council/runs/{id}/events`): the `event:` name, the joined
 * `data:` lines and the optional `id:`.
 */
public data class CouncilSseFrame(
    val event: String,
    val data: String,
    val id: String?,
)

/**
 * Incremental W3C SSE line parser for the council run stream. Feed it one line
 * at a time (without the trailing newline); it returns a frame when a blank
 * line terminates one. Comment lines (`:`) are ignored, multi-line `data:` is
 * joined with `\n`, and the event name defaults to `message`.
 *
 * Deliberately self-contained (council-specific name) so it doesn't collide
 * with any other SSE helper in the transport package.
 */
public class CouncilSseLineParser {
    private var event: String = "message"
    private var id: String? = null
    private val data: StringBuilder = StringBuilder()
    private var hasData: Boolean = false

    /** Feeds one line; returns a completed frame on a dispatching blank line. */
    public fun feed(rawLine: String): CouncilSseFrame? {
        val line: String = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon: Int = line.indexOf(':')
        val field: String = if (colon < 0) line else line.substring(0, colon)
        var value: String = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value.ifEmpty { "message" }
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "id" -> id = value
            else -> Unit
        }
        return null
    }

    /** Flushes a trailing frame when the stream closes without a final blank line. */
    public fun finish(): CouncilSseFrame? = dispatch()

    private fun dispatch(): CouncilSseFrame? {
        val frame: CouncilSseFrame? =
            if (hasData) CouncilSseFrame(event = event, data = data.toString(), id = id) else null
        event = "message"
        id = null
        data.setLength(0)
        hasData = false
        return frame
    }
}
