package com.dmzs.datawatchclient.diagnostics

import android.util.Log

/**
 * Log to logcat **and** the in-memory [DiagnosticsLog] ring buffer that crash
 * and bug reports include. Use it for failures worth seeing in a report
 * (transport errors, reconnects, service start failures). Messages are redacted
 * when a report is built, but still keep tokens out of them.
 */
public fun dwLog(
    tag: String,
    msg: String,
    level: LogLevel = LogLevel.WARN,
) {
    when (level) {
        LogLevel.DEBUG -> Log.d(tag, msg)
        LogLevel.INFO -> Log.i(tag, msg)
        LogLevel.WARN -> Log.w(tag, msg)
        LogLevel.ERROR -> Log.e(tag, msg)
    }
    DiagnosticsLog.log(level, tag, msg)
}
