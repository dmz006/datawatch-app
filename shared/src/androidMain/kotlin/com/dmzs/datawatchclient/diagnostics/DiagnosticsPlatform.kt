package com.dmzs.datawatchclient.diagnostics

import android.os.Build
import java.util.concurrent.locks.ReentrantLock

internal actual fun newDiagnosticsLock(): DiagnosticsLock =
    object : DiagnosticsLock {
        private val delegate = ReentrantLock()

        override fun lock(): Unit = delegate.lock()

        override fun unlock(): Unit = delegate.unlock()
    }

public actual fun currentDeviceInfo(): DeviceInfo {
    // Plain JVM unit tests leave the Build fields null.
    val manufacturer: String = Build.MANUFACTURER.orEmpty()
    val model: String = Build.MODEL.orEmpty()
    val name =
        when {
            model.isEmpty() -> manufacturer.ifEmpty { "unknown" }
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    return DeviceInfo(
        platform = "Android",
        osVersion = "${Build.VERSION.RELEASE.orEmpty()} (API ${Build.VERSION.SDK_INT})",
        model = name,
    )
}
