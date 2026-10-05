package com.dmzs.datawatchclient.ui.schedules

import com.dmzs.datawatchclient.domain.Schedule
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** PWA loadSchedulesList row label (Android-J). */
class ScheduleRowLabelTest {
    private fun sc(
        sessionId: String? = null,
        sessionName: String? = null,
        scheduleName: String? = null,
        type: String? = null,
        deferred: String? = null,
    ) = Schedule(
        id = "s1",
        serverProfileId = "p",
        task = "run tests",
        sessionId = sessionId,
        createdAt = Instant.DISTANT_PAST,
        sessionName = sessionName,
        scheduleName = scheduleName,
        type = type,
        deferredSessionName = deferred,
    )

    @Test
    fun prefersSessionNameAndAddsScheduleName() {
        assertEquals("build [nightly]: run tests", scheduleRowLabel(sc("abc", "build", "nightly")))
    }

    @Test
    fun fallsBackToSessionId() {
        assertEquals("abc: run tests", scheduleRowLabel(sc("abc")))
    }

    @Test
    fun deferredSessionShowsNewPrefix() {
        assertEquals("NEW: worker", scheduleRowLabel(sc(type = "new_session", deferred = "worker")))
    }

    @Test
    fun noSessionShowsCommandOnly() {
        assertEquals("run tests", scheduleRowLabel(sc()))
    }
}
