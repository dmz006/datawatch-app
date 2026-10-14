package com.dmzs.datawatchclient.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import kotlin.system.exitProcess

/**
 * On-device crash reporter. No SDK, no network: reports are text files in
 * `filesDir/crash/` and only leave the device when the user picks Send in
 * [CrashReportPrompt] (see `docs/crash-reports.md`).
 *
 * - [install] (from `DatawatchApp.onCreate`) chains a default uncaught-exception
 *   handler that writes the report synchronously, then calls the previous handler.
 * - On API 30+ it also reads `ActivityManager.getHistoricalProcessExitReasons`
 *   for ANRs and native crashes newer than the last check, so each one is offered once.
 */
public object CrashReporter {
    private const val DIR = "crash"
    private const val PREFS = "dw_crash_reporter"
    private const val KEY_LAST_EXIT_TS = "last_exit_ts"
    private const val MAX_PENDING = 10
    private const val MAX_TRACE_BYTES = 64 * 1024
    private const val EXIT_STATUS_CRASH = 10

    private val pendingState = MutableStateFlow<List<File>>(emptyList())

    /** Pending report files, newest first. */
    public val pending: StateFlow<List<File>> = pendingState.asStateFlow()

    @Volatile private var dir: File? = null

    public fun install(context: Context) {
        val app = context.applicationContext
        dir = File(app.filesDir, DIR).apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(thread, throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(EXIT_STATUS_CRASH)
            }
        }
        refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Thread({
                runCatching { collectExitReasons(app) }
                    .onFailure { dwLog("CrashReporter", "exit-reason scan failed: ${it::class.simpleName}") }
                refresh()
            }, "dw-exit-reasons").start()
        }
    }

    /** Synchronous: the process is about to die. Never throws. */
    private fun writeCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        val target = dir ?: return
        runCatching {
            val text =
                runCatching { BugReportBuilder.crash(throwable, thread.name) }
                    .getOrElse { "datawatch app crash report\nKind: crash\n${throwable::class.java.name}\n" }
            File(target, "${System.currentTimeMillis()}-crash.txt").writeText(text)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun collectExitReasons(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT_TS, -1L)
        val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 0)
        val newest = infos.maxOfOrNull { it.timestamp } ?: 0L
        val offered = setOf(ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH_NATIVE)
        // First run with the reporter: record a baseline instead of offering
        // exits that happened before this feature existed.
        if (lastSeen >= 0) {
            infos
                .filter { it.timestamp > lastSeen }
                .filter { it.reason in offered }
                .forEach { writeExitReport(it) }
        }
        prefs.edit { putLong(KEY_LAST_EXIT_TS, maxOf(newest, lastSeen, 0L)) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun writeExitReport(info: ApplicationExitInfo) {
        val target = dir ?: return
        val anr = info.reason == ApplicationExitInfo.REASON_ANR
        // Native-crash traces are binary tombstone protos; only ANR traces are text.
        val trace = if (anr) runCatching { info.traceInputStream?.use { readCapped(it) } }.getOrNull() else null
        val details =
            buildString {
                info.description?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
                if (!anr) append("(signal ").append(info.status).append(')')
            }
        val text =
            BugReportBuilder.build(
                CrashReport(
                    kind = if (anr) ReportKind.ANR else ReportKind.NATIVE_CRASH,
                    timeMs = info.timestamp,
                    stackTrace = trace,
                    details = details.trim(),
                ),
                // The log buffer belongs to this process, not the one that exited.
                logLines = emptyList(),
            )
        File(target, "${info.timestamp}-${if (anr) "anr" else "native"}.txt").writeText(text)
    }

    private fun readCapped(input: InputStream): String {
        val buf = ByteArray(MAX_TRACE_BYTES)
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return String(buf, 0, total, Charsets.UTF_8)
    }

    /** Re-list `filesDir/crash/`, keeping only the [MAX_PENDING] newest reports. */
    public fun refresh() {
        val target = dir ?: return
        val files =
            target
                .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
                .orEmpty()
                .sortedByDescending { it.name }
        files.drop(MAX_PENDING).forEach { it.delete() }
        pendingState.value = files.take(MAX_PENDING)
    }

    /** Report text for display / sharing. Redacted again in case of an older file. */
    public fun read(file: File): String = Redactor.redact(runCatching { file.readText() }.getOrDefault(""))

    /** Remove a report after Send or Dismiss. */
    public fun delete(file: File) {
        file.delete()
        refresh()
    }
}
