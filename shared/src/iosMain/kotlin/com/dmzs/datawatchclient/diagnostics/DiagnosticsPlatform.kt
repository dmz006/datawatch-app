package com.dmzs.datawatchclient.diagnostics

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

internal actual fun newDiagnosticsLock(): DiagnosticsLock =
    object : DiagnosticsLock {
        private val delegate = NSLock()

        override fun lock(): Unit = delegate.lock()

        override fun unlock(): Unit = delegate.unlock()
    }

/**
 * `NSProcessInfo` is thread-safe (unlike `UIDevice`, which wants the main
 * thread), and `uname().machine` is the hardware model id (e.g. `iPhone15,2`).
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun currentDeviceInfo(): DeviceInfo {
    val machine =
        memScoped {
            val info = alloc<utsname>()
            if (uname(info.ptr) == 0) info.machine.toKString() else "unknown"
        }
    val simulatorModel = NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String
    return DeviceInfo(
        platform = "iOS",
        osVersion = NSProcessInfo.processInfo.operatingSystemVersionString,
        model = if (simulatorModel != null) "$simulatorModel (Simulator)" else machine,
    )
}
