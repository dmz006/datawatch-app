package com.dmzs.datawatchclient.ui.sessions

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sprint 29 — pure-logic coverage for SessionsViewModel UiState computed properties.
 *
 * SessionsViewModel cannot be instantiated in a JVM unit test (its constructor
 * reads SharedPreferences via ServiceLocator.context()). These tests verify the
 * computed properties on the public [SessionsViewModel.UiState] data class directly,
 * mirroring the PWA's filter/expand/badge behaviour.
 */
class SessionsViewModelTest {

    // ── helpers ────────────────────────────────────────────────────────────

    private fun session(
        id: String,
        state: SessionState = SessionState.Running,
        backend: String? = null,
        lastActivityAt: Instant = Instant.DISTANT_PAST,
    ) = Session(
        id = id,
        serverProfileId = "srv",
        state = state,
        createdAt = Instant.DISTANT_PAST,
        lastActivityAt = lastActivityAt,
        backend = backend,
    )

    // ── State chip (parity D12a) ───────────────────────────────────────────

    @Test
    fun `stateChip defaults to all`() {
        val state = SessionsViewModel.UiState()
        assertEquals(SessionsViewModel.UiState.STATE_CHIP_ALL, state.stateChip)
    }

    @Test
    fun `backendFilter defaults to null`() {
        val state = SessionsViewModel.UiState()
        assertNull(state.backendFilter)
    }

    // ── backendCounts computed property ────────────────────────────────────

    @Test
    fun `backendCounts empty when no sessions have a backend`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(session("s1"), session("s2")),
        )
        assertTrue(state.backendCounts.isEmpty())
    }

    @Test
    fun `backendCounts counts sessions per backend name`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("s1", backend = "claude"),
                session("s2", backend = "claude"),
                session("s3", backend = "openai"),
            ),
        )
        val counts = state.backendCounts.toMap()
        assertEquals(2, counts["claude"])
        assertEquals(1, counts["openai"])
    }

    @Test
    fun `backendCounts omits null backend sessions`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("s1", backend = null),
                session("s2", backend = "openai"),
            ),
        )
        assertEquals(1, state.backendCounts.size)
        assertEquals("openai", state.backendCounts.first().first)
    }

    @Test
    fun `backendCounts omits blank backend sessions`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("s1", backend = ""),
                session("s2", backend = "claude"),
            ),
        )
        assertEquals(1, state.backendCounts.size)
        assertEquals("claude", state.backendCounts.first().first)
    }

    @Test
    fun `backendCounts sorted alphabetically for stable layout`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("s1", backend = "openai"),
                session("s2", backend = "claude"),
                session("s3", backend = "anthropic"),
            ),
        )
        val names = state.backendCounts.map { it.first }
        assertEquals(listOf("anthropic", "claude", "openai"), names)
    }

    @Test
    fun `backendCounts count is N+1 total chips when N distinct backends`() {
        // The UI label shows (N+1) because the "All" chip is always prepended.
        // This test verifies the N value used in the label calculation.
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("s1", backend = "claude"),
                session("s2", backend = "openai"),
            ),
        )
        // N = backendCounts.size = 2  →  label shows "(3)"
        assertEquals(2, state.backendCounts.size)
    }

    // ── llmExpanded toggle (UI-level; test state flag behaviour) ───────────

    @Test
    fun `showHistory defaults to false`() {
        val state = SessionsViewModel.UiState()
        assertFalse(state.showHistory)
    }

    // ── State filter chips (parity D12a) ───────────────────────────────────

    @Test
    fun `stateCounts counts every real state and all`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("a", SessionState.Running),
                session("b", SessionState.Running),
                session("c", SessionState.Waiting),
                session("d", SessionState.Killed),
            ),
        )
        val c = state.stateCounts
        assertEquals(4, c["all"])
        assertEquals(2, c["running"])
        assertEquals(1, c["waiting_input"])
        assertEquals(1, c["killed"])
        assertEquals(0, c["failed"])
    }

    @Test
    fun `visibleStateChips hides zero-count chips but keeps all and the selected one`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(session("a", SessionState.Running)),
            stateChip = "failed",
        )
        assertEquals(listOf("all", "running", "failed"), state.visibleStateChips)
    }

    @Test
    fun `state chip filters to that wire state`() {
        val state = SessionsViewModel.UiState(
            sessions = listOf(
                session("a", SessionState.Running),
                session("b", SessionState.Waiting),
            ),
            stateChip = "waiting_input",
        )
        assertEquals(listOf("b"), state.visibleSessions.map { it.id })
    }

    // ── Ordering (parity D42a) ─────────────────────────────────────────────

    @Test
    fun `manual order first, then last activity descending, no state buckets`() {
        val t0 = Instant.fromEpochMilliseconds(1_000)
        val t1 = Instant.fromEpochMilliseconds(2_000)
        val t2 = Instant.fromEpochMilliseconds(3_000)
        val sessions = listOf(
            session("waiting", SessionState.Waiting, lastActivityAt = t0),
            session("old", SessionState.Running, lastActivityAt = t1),
            session("new", SessionState.Running, lastActivityAt = t2),
        )
        val sorted = SessionsViewModel.UiState.sortByManualOrder(sessions, listOf("old"))
        assertEquals(listOf("old", "new", "waiting"), sorted.map { it.id })
    }

    @Test
    fun `manual order ignores ids no longer present`() {
        val sessions = listOf(session("a"), session("b"))
        val sorted = SessionsViewModel.UiState.sortByManualOrder(sessions, listOf("gone", "b"))
        assertEquals(listOf("b", "a"), sorted.map { it.id })
    }
}
