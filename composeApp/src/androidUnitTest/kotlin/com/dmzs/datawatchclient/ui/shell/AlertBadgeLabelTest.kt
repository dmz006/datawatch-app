package com.dmzs.datawatchclient.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/** PWA `updateAlertBadge` 99+ cap (01 › Alerts tab badge). */
class AlertBadgeLabelTest {
    @Test
    fun `badge caps at 99 plus`() {
        assertEquals("5", alertBadgeLabel(5))
        assertEquals("99", alertBadgeLabel(99))
        assertEquals("99+", alertBadgeLabel(100))
    }
}
