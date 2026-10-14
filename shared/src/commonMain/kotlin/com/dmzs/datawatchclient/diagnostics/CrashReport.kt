package com.dmzs.datawatchclient.diagnostics

/** What kind of event a report describes. [label] is what the report text shows. */
public enum class ReportKind(public val label: String) {
    /** User-initiated from Settings: no crash, recent log plus a note. */
    BUG_REPORT("bug report"),

    /** Uncaught JVM / Kotlin exception (Android). */
    CRASH("crash"),

    /** Uncaught Kotlin/Native exception (iOS shared code). */
    KOTLIN_CRASH("Kotlin crash"),

    /** Uncaught Objective-C `NSException` (iOS). */
    EXCEPTION("uncaught exception"),

    /** Fatal signal (iOS: SIGABRT, SIGSEGV, ...). */
    SIGNAL("fatal signal"),

    /** Native (C/C++) crash reported by Android's exit-reason history. */
    NATIVE_CRASH("native crash"),

    /** App Not Responding, reported by Android's exit-reason history. */
    ANR("app not responding"),

    /** Crash / hang diagnostic delivered by iOS MetricKit. */
    METRICKIT("MetricKit diagnostic"),
}

/**
 * One crash or bug report before it is rendered to text by [BugReportBuilder].
 *
 * @property timeMs when the crash happened (or the report was made), epoch ms.
 * @property threadName thread that threw, when known.
 * @property stackTrace crash stack trace, ANR trace or diagnostic payload; null for bug reports.
 * @property userNote the user's "what happened" text; bug reports only.
 * @property details extra one-line facts (e.g. exit description, signal name).
 */
public data class CrashReport(
    val kind: ReportKind,
    val timeMs: Long,
    val threadName: String? = null,
    val stackTrace: String? = null,
    val userNote: String? = null,
    val details: String? = null,
)

/** Where Send points by default. From the store listings' contact address. */
public object SupportContact {
    public const val EMAIL: String = "datawatch.ai.bridge@gmail.com"

    /** Subject line for every report, e.g. `datawatch app crash report v1.28.17`. */
    public fun subject(version: String = com.dmzs.datawatchclient.Version.VERSION): String =
        "datawatch app crash report v$version"
}
