package com.dmzs.datawatchclient.diagnostics

import com.dmzs.datawatchclient.Version
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Renders a [CrashReport] plus the recent [DiagnosticsLog] lines into the
 * plain-text report the user sees under View and shares with Send. The whole
 * text goes through [Redactor] before it is returned.
 *
 * ```
 * datawatch app crash report
 * Kind: crash
 * App version: 1.28.17 (673)
 * Platform: Android
 * OS version: 14 (API 34)
 * Device: Google Pixel 8
 * Time: 2026-10-14T09:30:00.123Z
 * Thread: main
 *
 * Stack trace:
 * java.lang.IllegalStateException: ...
 *
 * Recent log (2 lines):
 * 2026-10-14T09:29:58.001Z W/Transport: ...
 * ```
 */
public object BugReportBuilder {
    /** Stack traces / ANR traces / diagnostic payloads are cut to this many chars. */
    public const val MAX_TRACE_CHARS: Int = 64 * 1024

    /** Notes from the user are cut to this many chars. */
    public const val MAX_NOTE_CHARS: Int = 4_000

    public const val TRUNCATED_MARKER: String = "\n… (truncated)"

    /** Full report text for [report], redacted. */
    public fun build(
        report: CrashReport,
        logLines: List<LogEntry> = DiagnosticsLog.snapshot(),
        device: DeviceInfo = currentDeviceInfo(),
        appVersion: String = Version.VERSION,
        versionCode: Int = Version.VERSION_CODE,
    ): String {
        val sb = StringBuilder()
        sb.append(header(report.kind, report.timeMs, device, appVersion, versionCode))
        val thread = report.threadName.orEmpty().trim()
        val details = report.details.orEmpty().trim()
        val note = report.userNote.orEmpty().trim()
        val trace = report.stackTrace.orEmpty().trimEnd()
        if (thread.isNotEmpty()) sb.append("Thread: $thread\n")
        if (details.isNotEmpty()) sb.append("Details: $details\n")
        if (note.isNotEmpty()) sb.append("\nWhat happened:\n${truncate(note, MAX_NOTE_CHARS)}\n")
        if (trace.isNotBlank()) {
            val title = if (report.kind == ReportKind.ANR) "ANR trace" else "Stack trace"
            sb.append("\n$title:\n${truncate(trace, MAX_TRACE_CHARS)}\n")
        }
        sb.append('\n')
        if (logLines.isEmpty()) {
            sb.append("Recent log: (empty)\n")
        } else {
            sb.append("Recent log (${logLines.size} lines):\n")
            logLines.forEach { sb.append(it.format()).append('\n') }
        }
        return Redactor.redact(sb.toString())
    }

    /** Settings › Send bug report: recent log plus the user's note, no crash. */
    public fun bugReport(
        userNote: String,
        timeMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): String = build(CrashReport(kind = ReportKind.BUG_REPORT, timeMs = timeMs, userNote = userNote))

    /** Report for an uncaught [throwable] on [threadName]. */
    public fun crash(
        throwable: Throwable,
        threadName: String?,
        kind: ReportKind = ReportKind.CRASH,
        timeMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): String =
        build(
            CrashReport(
                kind = kind,
                timeMs = timeMs,
                threadName = threadName,
                stackTrace = throwable.stackTraceToString(),
            ),
        )

    /**
     * Header lines (title through `Time:`), redacted, each ending in `\n`. Also
     * used on its own by the iOS signal handler, which pre-formats it at launch
     * because a signal handler must not allocate.
     */
    public fun header(
        kind: ReportKind,
        timeMs: Long,
        device: DeviceInfo = currentDeviceInfo(),
        appVersion: String = Version.VERSION,
        versionCode: Int = Version.VERSION_CODE,
    ): String {
        val title = if (kind == ReportKind.BUG_REPORT) "datawatch app bug report" else "datawatch app crash report"
        return Redactor.redact(
            buildString {
                append("$title\n")
                append("Kind: ${kind.label}\n")
                append("App version: $appVersion ($versionCode)\n")
                append("Platform: ${device.platform}\n")
                append("OS version: ${device.osVersion}\n")
                append("Device: ${device.model}\n")
                append("Time: ${Instant.fromEpochMilliseconds(timeMs)}\n")
            },
        )
    }

    internal fun truncate(
        text: String,
        max: Int,
    ): String = if (text.length <= max) text else text.take(max) + TRUNCATED_MARKER
}
