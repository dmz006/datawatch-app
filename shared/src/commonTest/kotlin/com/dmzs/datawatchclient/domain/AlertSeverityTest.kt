package com.dmzs.datawatchclient.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies AlertSeverity.fromWire tolerates every spelling the
 * parent has emitted. Back-compat for older servers that still
 * use `severity: "critical"` or `severity: "warn"` alongside the
 * current `level: "error" | "warn" | "info"`.
 */
class AlertSeverityTest {
    @Test
    fun `error-class levels map to Error`() {
        assertEquals(AlertSeverity.Error, AlertSeverity.fromWire("error"))
        assertEquals(AlertSeverity.Error, AlertSeverity.fromWire("ERROR"))
        assertEquals(AlertSeverity.Error, AlertSeverity.fromWire("Error"))
        assertEquals(AlertSeverity.Error, AlertSeverity.fromWire("err"))
        assertEquals(AlertSeverity.Error, AlertSeverity.fromWire("critical"))
    }

    @Test
    fun `warn spellings map to Warning`() {
        assertEquals(AlertSeverity.Warning, AlertSeverity.fromWire("warn"))
        assertEquals(AlertSeverity.Warning, AlertSeverity.fromWire("warning"))
        assertEquals(AlertSeverity.Warning, AlertSeverity.fromWire("WARN"))
    }

    @Test
    fun `null empty and unknown fall back to Info`() {
        assertEquals(AlertSeverity.Info, AlertSeverity.fromWire(null))
        assertEquals(AlertSeverity.Info, AlertSeverity.fromWire(""))
        assertEquals(AlertSeverity.Info, AlertSeverity.fromWire("info"))
        assertEquals(AlertSeverity.Info, AlertSeverity.fromWire("notice"))
        assertEquals(AlertSeverity.Info, AlertSeverity.fromWire("whatever"))
    }
}
