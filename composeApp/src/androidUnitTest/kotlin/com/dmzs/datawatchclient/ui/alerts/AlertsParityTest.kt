package com.dmzs.datawatchclient.ui.alerts

import com.dmzs.datawatchclient.domain.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PWA alerts group ordering (04 › Group ordering). */
class AlertsParityTest {
    @Test
    fun `waiting groups rank before running before others`() {
        assertTrue(alertGroupStateRank(SessionState.Waiting) < alertGroupStateRank(SessionState.Running))
        assertTrue(alertGroupStateRank(SessionState.Running) < alertGroupStateRank(SessionState.Completed))
        assertEquals(alertGroupStateRank(SessionState.Killed), alertGroupStateRank(null))
    }
}
