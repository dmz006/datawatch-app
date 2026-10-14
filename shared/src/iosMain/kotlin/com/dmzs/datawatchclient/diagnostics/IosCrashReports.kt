package com.dmzs.datawatchclient.diagnostics

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.experimental.ExperimentalNativeApi

/**
 * Swift-facing entry point for crash and bug reports (iosApp `CrashReporter`).
 *
 * [install] sets the Kotlin/Native unhandled-exception hook so a crash in shared
 * Kotlin code writes `kotlin-<launchId>.txt` into the pending-report directory
 * before the runtime terminates the process. The Swift side owns the directory,
 * the signal / `NSException` handlers, MetricKit, and the next-launch prompt.
 */
public object IosCrashReports {
    private var directory: String? = null
    private var launchId: String = ""

    /** Support address for the mail composer. */
    public val supportEmail: String get() = SupportContact.EMAIL

    /** Mail / share subject, e.g. `datawatch app crash report v1.28.17`. */
    public val subject: String get() = SupportContact.subject()

    /**
     * Install the Kotlin/Native unhandled-exception hook. [directory] is the
     * pending-report directory; [launchId] names this launch's report files so
     * Swift can drop the bare signal report that follows the runtime's abort().
     */
    @OptIn(ExperimentalNativeApi::class)
    public fun install(
        directory: String,
        launchId: String,
    ) {
        this.directory = directory
        this.launchId = launchId
        val previous = setUnhandledExceptionHook(null)
        setUnhandledExceptionHook { throwable ->
            writeKotlinCrash(throwable)
            previous?.invoke(throwable)
        }
    }

    private fun writeKotlinCrash(throwable: Throwable) {
        val dir = directory ?: return
        val text =
            runCatching { BugReportBuilder.crash(throwable, threadName = null, kind = ReportKind.KOTLIN_CRASH) }
                .getOrElse { "datawatch app crash report\nKind: Kotlin crash\n" + (throwable::class.simpleName ?: "") }
        writeText("$dir/kotlin-$launchId.txt", text)
    }

    /** Settings › Send bug report text: recent log plus [note]. */
    public fun buildBugReport(note: String): String = BugReportBuilder.bugReport(note)

    /** Report for an uncaught Objective-C exception. */
    public fun buildExceptionReport(
        name: String,
        reason: String,
        callStack: String,
    ): String =
        BugReportBuilder.build(
            CrashReport(
                kind = ReportKind.EXCEPTION,
                timeMs = Clock.System.now().toEpochMilliseconds(),
                details = "$name: $reason",
                stackTrace = callStack,
            ),
        )

    /** Report wrapping a MetricKit diagnostic payload (`jsonRepresentation()`). */
    public fun buildMetricKitReport(json: String): String =
        BugReportBuilder.build(
            CrashReport(
                kind = ReportKind.METRICKIT,
                timeMs = Clock.System.now().toEpochMilliseconds(),
                stackTrace = json,
            ),
            logLines = emptyList(),
        )

    /**
     * Pre-formatted header for the signal handler, ending in `Signal: ` so the
     * handler only has to append the signal name.
     */
    public fun signalHeader(): String =
        BugReportBuilder.header(ReportKind.SIGNAL, Clock.System.now().toEpochMilliseconds()) +
            "Details: the app was stopped by a fatal signal. Time is when that session started; " +
            "recent log lines are not available for this kind of crash.\n" +
            "Signal: "

    /** [Redactor.redact] for text Swift reads back from disk. */
    public fun redact(text: String): String = Redactor.redact(text)

    public fun logInfo(
        tag: String,
        message: String,
    ): Unit = DiagnosticsLog.i(tag, message)

    public fun logWarn(
        tag: String,
        message: String,
    ): Unit = DiagnosticsLog.w(tag, message)

    public fun logError(
        tag: String,
        message: String,
    ): Unit = DiagnosticsLog.e(tag, message)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun writeText(
        path: String,
        text: String,
    ): Boolean = NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}
