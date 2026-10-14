package com.dmzs.datawatchclient.diagnostics

/**
 * Minimal mutual-exclusion lock for [DiagnosticsLog]. Common code has no
 * `synchronized`, so each platform supplies one (JVM `ReentrantLock`, iOS `NSLock`)
 * through [newDiagnosticsLock].
 */
internal interface DiagnosticsLock {
    fun lock()

    fun unlock()
}

internal expect fun newDiagnosticsLock(): DiagnosticsLock

internal inline fun <T> DiagnosticsLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
