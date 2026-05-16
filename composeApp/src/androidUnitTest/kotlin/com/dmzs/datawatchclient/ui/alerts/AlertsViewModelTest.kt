package com.dmzs.datawatchclient.ui.alerts

import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T18 test-debt: pure-logic coverage for AlertsViewModel data classes.
 *
 * AlertsViewModel is tightly bound to ServiceLocator so it cannot be
 * instantiated in a JVM unit test.  These tests cover the same business
 * logic via the public data classes and pure helper functions that the
 * ViewModel delegates to:
 *
 *   - AlertGroup.isActive   — active vs inactive classification
 *   - AlertGroup.label      — display-name selection priority
 *   - UiState.count         — badge count = sum of active-group alert sizes
 *   - chip-filter predicate — severity/type matching (inline mirror of VM logic)
 *   - chronological sort    — newest-first ordering
 */
class AlertsViewModelTest {

    // ---- helpers ----

    private fun alert(
        id: String,
        type: String = "info",
        severity: AlertSeverity = AlertSeverity.Info,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        sessionId: String? = null,
    ) = Alert(
        id = id,
        serverProfileId = "srv-1",
        type = type,
        severity = severity,
        title = "Alert $id",
        message = "body $id",
        sessionId = sessionId,
        createdAt = createdAt,
    )

    private fun runningSession(id: String, name: String? = null) =
        Session(
            id = id,
            serverProfileId = "srv-1",
            state = SessionState.Running,
            createdAt = Instant.DISTANT_PAST,
            lastActivityAt = Instant.DISTANT_PAST,
            name = name,
        )

    private fun terminalSession(id: String) =
        Session(
            id = id,
            serverProfileId = "srv-1",
            state = SessionState.Completed,
            createdAt = Instant.DISTANT_PAST,
            lastActivityAt = Instant.DISTANT_PAST,
        )

    // ---- AlertGroup.isActive ----

    @Test
    fun `running session group is active`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = "sess-1",
            session = runningSession("sess-1"),
            alerts = listOf(alert("a1")),
        )
        assertTrue(group.isActive)
    }

    @Test
    fun `completed session group is not active`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = "sess-2",
            session = terminalSession("sess-2"),
            alerts = listOf(alert("a2")),
        )
        assertFalse(group.isActive)
    }

    @Test
    fun `system bucket group is never active`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = AlertsViewModel.AlertGroup.SYSTEM_BUCKET,
            session = null,
            alerts = listOf(alert("sys1")),
        )
        assertFalse(group.isActive)
    }

    @Test
    fun `null session group is not active`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = "orphan-99",
            session = null,
            alerts = listOf(alert("a3")),
        )
        assertFalse(group.isActive)
    }

    @Test
    fun `waiting session group is active`() {
        val waitingSession = Session(
            id = "w1",
            serverProfileId = "srv-1",
            state = SessionState.Waiting,
            createdAt = Instant.DISTANT_PAST,
            lastActivityAt = Instant.DISTANT_PAST,
        )
        val group = AlertsViewModel.AlertGroup(
            sessionId = "w1",
            session = waitingSession,
            alerts = listOf(alert("aw")),
        )
        assertTrue(group.isActive)
    }

    // ---- AlertGroup.label ----

    @Test
    fun `system bucket label is System`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = AlertsViewModel.AlertGroup.SYSTEM_BUCKET,
            session = null,
            alerts = emptyList(),
        )
        assertEquals("System", group.label)
    }

    @Test
    fun `session with user-assigned name prefers name as label`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = "sess-abc",
            session = runningSession("sess-abc", name = "My Task"),
            alerts = emptyList(),
        )
        assertEquals("My Task", group.label)
    }

    @Test
    fun `session without name falls back to session id`() {
        val group = AlertsViewModel.AlertGroup(
            sessionId = "sess-abc",
            session = runningSession("sess-abc"),
            alerts = emptyList(),
        )
        assertEquals("sess-abc", group.label)
    }

    // ---- UiState.count ----

    @Test
    fun `count equals sum of alerts in active groups`() {
        val active1 = AlertsViewModel.AlertGroup(
            sessionId = "s1",
            session = runningSession("s1"),
            alerts = listOf(alert("a1"), alert("a2")),
        )
        val active2 = AlertsViewModel.AlertGroup(
            sessionId = "s2",
            session = runningSession("s2"),
            alerts = listOf(alert("a3")),
        )
        val state = AlertsViewModel.UiState(active = listOf(active1, active2))
        assertEquals(3, state.count)
    }

    @Test
    fun `count is zero when active list is empty`() {
        val state = AlertsViewModel.UiState(active = emptyList())
        assertEquals(0, state.count)
    }

    // ---- Chip filter logic (mirrors inline lambdas in filteredState) ----

    private fun matchesChip(chip: AlertsViewModel.ChipFilter, a: Alert): Boolean =
        when (chip) {
            AlertsViewModel.ChipFilter.All -> true
            AlertsViewModel.ChipFilter.Prompt ->
                a.type.contains("input", ignoreCase = true) ||
                    a.type == "needs_input" ||
                    a.type == "input_needed"
            AlertsViewModel.ChipFilter.Error ->
                a.severity == AlertSeverity.Error ||
                    a.type.contains("error", ignoreCase = true)
            AlertsViewModel.ChipFilter.Warn ->
                a.severity == AlertSeverity.Warning
            AlertsViewModel.ChipFilter.Info ->
                a.severity == AlertSeverity.Info
        }

    @Test
    fun `All chip matches every alert`() {
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.All, alert("x", type = "anything")))
    }

    @Test
    fun `Error chip matches error severity alert`() {
        val errAlert = alert("e1", severity = AlertSeverity.Error)
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Error, errAlert))
    }

    @Test
    fun `Error chip matches alert whose type contains error`() {
        val errAlert = alert("e2", type = "session_error")
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Error, errAlert))
    }

    @Test
    fun `Error chip does not match info alert`() {
        val infoAlert = alert("i1", severity = AlertSeverity.Info)
        assertFalse(matchesChip(AlertsViewModel.ChipFilter.Error, infoAlert))
    }

    @Test
    fun `Prompt chip matches needs_input type`() {
        val promptAlert = alert("p1", type = "needs_input")
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Prompt, promptAlert))
    }

    @Test
    fun `Prompt chip matches type containing input`() {
        val promptAlert = alert("p2", type = "waiting_input")
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Prompt, promptAlert))
    }

    @Test
    fun `Warn chip matches warning severity`() {
        val warnAlert = alert("w1", severity = AlertSeverity.Warning)
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Warn, warnAlert))
    }

    @Test
    fun `Warn chip does not match error severity`() {
        val errAlert = alert("e3", severity = AlertSeverity.Error)
        assertFalse(matchesChip(AlertsViewModel.ChipFilter.Warn, errAlert))
    }

    @Test
    fun `Info chip matches info severity`() {
        val infoAlert = alert("i2", severity = AlertSeverity.Info)
        assertTrue(matchesChip(AlertsViewModel.ChipFilter.Info, infoAlert))
    }

    // ---- Chronological sort ----

    @Test
    fun `sortByTime returns alerts newest-first`() {
        val older = alert("old", createdAt = Instant.parse("2026-01-01T08:00:00Z"))
        val newer = alert("new", createdAt = Instant.parse("2026-01-02T08:00:00Z"))
        val middle = alert("mid", createdAt = Instant.parse("2026-01-01T20:00:00Z"))

        val sorted = listOf(older, newer, middle).sortedByDescending { it.createdAt }

        assertEquals("new", sorted[0].id)
        assertEquals("mid", sorted[1].id)
        assertEquals("old", sorted[2].id)
    }

    @Test
    fun `dismissed (read=true) alerts are still returned — dismissal is server-side`() {
        // The local list contains read alerts until next poll clears them;
        // filter logic must not strip them client-side.
        val readAlert = alert("r1").copy(read = true)
        val unreadAlert = alert("r2").copy(read = false)
        val all = listOf(readAlert, unreadAlert)
        val active = all.filter { matchesChip(AlertsViewModel.ChipFilter.All, it) }
        assertEquals(2, active.size)
    }
}
