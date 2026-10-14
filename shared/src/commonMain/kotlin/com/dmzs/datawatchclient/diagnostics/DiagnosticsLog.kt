package com.dmzs.datawatchclient.diagnostics

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Severity of a [DiagnosticsLog] line, rendered as the logcat-style letter. */
public enum class LogLevel(public val letter: Char) {
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
}

/** One timestamped line in the [DiagnosticsLog] ring buffer. */
public data class LogEntry(
    val timeMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /** `2026-10-14T09:30:00.123Z W/Tag: message` (UTC, ISO-8601). */
    public fun format(): String = "${Instant.fromEpochMilliseconds(timeMs)} ${level.letter}/$tag: $message"
}

/**
 * Thread-safe in-memory ring buffer of the most recent app log lines. It only
 * lives in process memory: nothing is persisted or sent. A crash or bug report
 * copies a [snapshot] into its text, and [Redactor] runs over the whole report.
 */
public object DiagnosticsLog {
    /** Lines kept; the oldest line is dropped first. */
    public const val CAPACITY: Int = 300

    /** Longer messages are cut so one huge line can't crowd out the rest. */
    public const val MAX_MESSAGE_CHARS: Int = 1_000

    private val lock = newDiagnosticsLock()
    private val buffer = arrayOfNulls<LogEntry>(CAPACITY)
    private var next = 0
    private var size = 0

    /** Append one line. */
    public fun log(
        level: LogLevel,
        tag: String,
        message: String,
        timeMs: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        val msg = if (message.length > MAX_MESSAGE_CHARS) message.take(MAX_MESSAGE_CHARS) + "…" else message
        val entry = LogEntry(timeMs, level, tag, msg)
        lock.withLock {
            buffer[next] = entry
            next = (next + 1) % CAPACITY
            if (size < CAPACITY) size++
        }
    }

    public fun i(
        tag: String,
        message: String,
    ): Unit = log(LogLevel.INFO, tag, message)

    public fun w(
        tag: String,
        message: String,
    ): Unit = log(LogLevel.WARN, tag, message)

    public fun e(
        tag: String,
        message: String,
    ): Unit = log(LogLevel.ERROR, tag, message)

    /** Oldest-first copy of the buffered lines. */
    public fun snapshot(): List<LogEntry> =
        lock.withLock {
            val start = (next - size + CAPACITY) % CAPACITY
            List(size) { i -> checkNotNull(buffer[(start + i) % CAPACITY]) }
        }

    /** Drop every buffered line. */
    public fun clear() {
        lock.withLock {
            buffer.fill(null)
            next = 0
            size = 0
        }
    }
}
