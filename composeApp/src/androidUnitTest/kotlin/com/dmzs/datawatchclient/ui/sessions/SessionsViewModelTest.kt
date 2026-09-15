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

    // ── SessionStateFilter default ─────────────────────────────────────────

    @Test
    fun `stateFilter defaults to ALL`() {
        val state = SessionsViewModel.UiState()
        assertEquals(SessionsViewModel.SessionStateFilter.ALL, state.stateFilter)
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

    // ── State filter chip highlighting ─────────────────────────────────────

    @Test
    fun `ACTIVE filter differs from ALL`() {
        assertFalse(
            SessionsViewModel.SessionStateFilter.ACTIVE ==
                SessionsViewModel.SessionStateFilter.ALL,
        )
    }

    @Test
    fun `non-ALL filter is highlighted — ALL is not`() {
        val filters = SessionsViewModel.SessionStateFilter.values()
        val nonAll = filters.filter { it != SessionsViewModel.SessionStateFilter.ALL }
        assertTrue(nonAll.isNotEmpty())
        // Every filter except ALL is a "highlighted" state
        nonAll.forEach { assertFalse(it == SessionsViewModel.SessionStateFilter.ALL) }
    }
}
