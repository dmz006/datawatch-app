package com.dmzs.datawatchclient.diagnostics

import com.dmzs.datawatchclient.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportBuilderTest {
    private val device = DeviceInfo(platform = "Android", osVersion = "14 (API 34)", model = "Google Pixel 8")

    // 2026-10-14T09:30:00Z
    private val t0 = 1_791_970_200_000L

    private fun log(vararg msgs: String) = msgs.mapIndexed { i, m -> LogEntry(t0 + i, LogLevel.WARN, "Transport", m) }

    @Test
    fun crashReportHasHeaderTraceAndLog() {
        val text =
            BugReportBuilder.build(
                CrashReport(
                    kind = ReportKind.CRASH,
                    timeMs = t0,
                    threadName = "main",
                    stackTrace = "java.lang.IllegalStateException: boom\n\tat a.B.c(B.kt:1)",
                ),
                logLines = log("first", "second"),
                device = device,
                appVersion = "1.2.3",
                versionCode = 45,
            )
        val expected =
            "datawatch app crash report\n" +
                "Kind: crash\n" +
                "App version: 1.2.3 (45)\n" +
                "Platform: Android\n" +
                "OS version: 14 (API 34)\n" +
                "Device: Google Pixel 8\n" +
                "Time: 2026-10-14T09:30:00Z\n" +
                "Thread: main\n" +
                "\n" +
                "Stack trace:\n" +
                "java.lang.IllegalStateException: boom\n" +
                "\tat a.B.c(B.kt:1)\n" +
                "\n" +
                "Recent log (2 lines):\n" +
                "2026-10-14T09:30:00Z W/Transport: first\n" +
                "2026-10-14T09:30:00.001Z W/Transport: second\n"
        assertEquals(expected, text)
    }

    @Test
    fun bugReportHasNoteAndNoTrace() {
        val text =
            BugReportBuilder.build(
                CrashReport(kind = ReportKind.BUG_REPORT, timeMs = t0, userNote = "  Terminal froze after rotate  "),
                logLines = emptyList(),
                device = device,
            )
        assertTrue(text.startsWith("datawatch app bug report\nKind: bug report\n"))
        assertTrue(text.contains("App version: ${Version.VERSION} (${Version.VERSION_CODE})"))
        assertTrue(text.contains("\nWhat happened:\nTerminal froze after rotate\n"))
        assertFalse(text.contains("Stack trace"))
        assertTrue(text.endsWith("Recent log: (empty)\n"))
    }

    @Test
    fun anrTraceUsesAnrTitleAndDetails() {
        val text =
            BugReportBuilder.build(
                CrashReport(kind = ReportKind.ANR, timeMs = t0, stackTrace = "\"main\" prio=5", details = "Input dispatching timed out"),
                logLines = emptyList(),
                device = device,
            )
        assertTrue(text.contains("Kind: app not responding\n"))
        assertTrue(text.contains("Details: Input dispatching timed out\n"))
        assertTrue(text.contains("\nANR trace:\n\"main\" prio=5\n"))
    }

    @Test
    fun everySectionIsRedacted() {
        val text =
            BugReportBuilder.build(
                CrashReport(
                    kind = ReportKind.CRASH,
                    timeMs = t0,
                    stackTrace = "java.net.ConnectException: Failed to connect to /100.64.0.7:8443",
                    details = "GET https://datawatch.example.com:8443/api/sessions",
                ),
                logLines = log("Authorization: Bearer abc", "ws wss://10.0.0.2:8443/ws reconnecting"),
                device = device,
            )
        listOf("100.64.0.7", "10.0.0.2", "datawatch.example.com", "abc").forEach {
            assertFalse(text.contains(it), "leaked $it in:\n$text")
        }
        assertTrue(text.contains("Details: GET <server>/api/sessions"))
        assertTrue(text.contains("W/Transport: Authorization: <redacted>"))
        assertTrue(text.contains("ws <server>/ws reconnecting"))
    }

    @Test
    fun longTracesAndNotesAreTruncated() {
        val trace = "x".repeat(BugReportBuilder.MAX_TRACE_CHARS + 10)
        val text =
            BugReportBuilder.build(
                CrashReport(kind = ReportKind.ANR, timeMs = t0, stackTrace = trace, userNote = "n".repeat(5_000)),
                logLines = emptyList(),
                device = device,
            )
        assertTrue(text.contains("x".repeat(BugReportBuilder.MAX_TRACE_CHARS) + BugReportBuilder.TRUNCATED_MARKER))
        assertFalse(text.contains("x".repeat(BugReportBuilder.MAX_TRACE_CHARS + 1)))
        assertTrue(text.contains("n".repeat(BugReportBuilder.MAX_NOTE_CHARS) + BugReportBuilder.TRUNCATED_MARKER))
    }

    @Test
    fun crashFromThrowableIncludesItsStackTrace() {
        val text = BugReportBuilder.crash(IllegalArgumentException("bad arg"), threadName = "worker-1")
        assertTrue(text.contains("Kind: crash\n"))
        assertTrue(text.contains("Thread: worker-1\n"))
        assertTrue(text.contains("IllegalArgumentException: bad arg"))
    }

    @Test
    fun headerEndsAtTimeLine() {
        val h = BugReportBuilder.header(ReportKind.SIGNAL, t0, device, "1.0.0", 1)
        assertTrue(h.startsWith("datawatch app crash report\nKind: fatal signal\n"))
        assertTrue(h.endsWith("Time: 2026-10-14T09:30:00Z\n"))
    }

    @Test
    fun supportSubjectCarriesVersion() {
        assertEquals("datawatch app crash report v9.9.9", SupportContact.subject("9.9.9"))
        assertEquals("datawatch app crash report v${Version.VERSION}", SupportContact.subject())
    }
}
