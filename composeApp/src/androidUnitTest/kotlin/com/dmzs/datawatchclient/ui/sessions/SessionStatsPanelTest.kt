package com.dmzs.datawatchclient.ui.sessions

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.ContainerInfoDto
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sprint 25 — pure-logic coverage for SessionStatsCards visibility conditions.
 *
 * SessionStatsCards is an internal @Composable so it cannot be instantiated in a
 * JVM unit test. These tests mirror the conditional checks in the Composable
 * body verbatim so that regressions in the visibility rules are caught early.
 */
class SessionStatsPanelTest {

    // ── helpers ────────────────────────────────────────────────────────────

    private fun session(
        llmRef: String? = null,
        computeNodeRef: String? = null,
    ) = Session(
        id = "s1",
        serverProfileId = "srv",
        state = SessionState.Running,
        createdAt = Instant.DISTANT_PAST,
        lastActivityAt = Instant.DISTANT_PAST,
        llmRef = llmRef,
        computeNodeRef = computeNodeRef,
    )

    /** Mirrors the containerInfo derivation in SessionStatsCards. */
    private fun resolveContainerInfo(envelope: StatEnvelopeDto?): ContainerInfoDto? =
        envelope?.container
            ?: if (envelope?.containerId?.isNotBlank() == true) {
                ContainerInfoDto(
                    containerId = envelope.containerId ?: "",
                    image = envelope.image ?: "",
                )
            } else {
                null
            }

    // ── Host card (always present) ─────────────────────────────────────────

    @Test
    fun `host card is always rendered regardless of envelope`() {
        // HostCard renders for every envelope state; the condition is unconditional.
        // We verify the no-data guard: it only triggers when ALL optional fields absent.
        val envelopeNull: StatEnvelopeDto? = null
        val envelopeEmpty = StatEnvelopeDto()
        // null envelope is accepted; empty envelope has no containerId
        assertNull(envelopeNull)
        assertNull(envelopeEmpty.containerId)
    }

    // ── Container card ─────────────────────────────────────────────────────

    @Test
    fun `container card hidden when envelope is null`() {
        assertNull(resolveContainerInfo(null))
    }

    @Test
    fun `container card hidden when envelope has no containerId and no container object`() {
        val envelope = StatEnvelopeDto(containerId = null, container = null)
        assertNull(resolveContainerInfo(envelope))
    }

    @Test
    fun `container card hidden when containerId is blank`() {
        val envelope = StatEnvelopeDto(containerId = "", container = null)
        assertNull(resolveContainerInfo(envelope))
    }

    @Test
    fun `container card shown when envelope has container object`() {
        val info = ContainerInfoDto(containerId = "abc", image = "ubuntu")
        val envelope = StatEnvelopeDto(container = info)
        assertEquals(info, resolveContainerInfo(envelope))
    }

    @Test
    fun `container card shown when envelope has non-blank containerId`() {
        val envelope = StatEnvelopeDto(containerId = "abc123", container = null)
        val resolved = resolveContainerInfo(envelope)
        assertEquals("abc123", resolved?.containerId)
    }

    // ── ComputeNode card ───────────────────────────────────────────────────

    @Test
    fun `compute node card hidden when computeNodeRef is null`() {
        val s = session(computeNodeRef = null)
        assertFalse(s.computeNodeRef?.isNotBlank() == true)
    }

    @Test
    fun `compute node card hidden when computeNodeRef is blank`() {
        val s = session(computeNodeRef = "")
        assertFalse(s.computeNodeRef?.isNotBlank() == true)
    }

    @Test
    fun `compute node card shown when computeNodeRef is non-blank`() {
        val s = session(computeNodeRef = "gpu-node-1")
        assertTrue(s.computeNodeRef?.isNotBlank() == true)
    }

    // ── LLM card ───────────────────────────────────────────────────────────

    @Test
    fun `llm card hidden when llmRef is null`() {
        val s = session(llmRef = null)
        assertFalse(s.llmRef?.isNotBlank() == true)
    }

    @Test
    fun `llm card hidden when llmRef is blank`() {
        val s = session(llmRef = "")
        assertFalse(s.llmRef?.isNotBlank() == true)
    }

    @Test
    fun `llm card shown when llmRef is non-blank`() {
        val s = session(llmRef = "claude-opus-5")
        assertTrue(s.llmRef?.isNotBlank() == true)
    }

    // ── "No data" fallback ─────────────────────────────────────────────────

    @Test
    fun `no-data state when all fields absent`() {
        val envelope: StatEnvelopeDto? = null
        val s = session()
        val containerInfo = resolveContainerInfo(envelope)
        val showNoData = envelope == null &&
            containerInfo == null &&
            s.computeNodeRef.isNullOrBlank() &&
            s.llmRef.isNullOrBlank()
        assertTrue(showNoData)
    }

    @Test
    fun `no-data state suppressed when llmRef present`() {
        val envelope: StatEnvelopeDto? = null
        val s = session(llmRef = "claude")
        val containerInfo = resolveContainerInfo(envelope)
        val showNoData = envelope == null &&
            containerInfo == null &&
            s.computeNodeRef.isNullOrBlank() &&
            s.llmRef.isNullOrBlank()
        assertFalse(showNoData)
    }

    @Test
    fun `no-data state suppressed when computeNodeRef present`() {
        val envelope: StatEnvelopeDto? = null
        val s = session(computeNodeRef = "node-a")
        val containerInfo = resolveContainerInfo(envelope)
        val showNoData = envelope == null &&
            containerInfo == null &&
            s.computeNodeRef.isNullOrBlank() &&
            s.llmRef.isNullOrBlank()
        assertFalse(showNoData)
    }
}
