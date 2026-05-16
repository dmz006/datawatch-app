package com.dmzs.datawatchclient.transport.dto

import com.dmzs.datawatchclient.transport.rest.toDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T18 test-debt: verifies the backendFamily / llmBackend migration logic
 * in SessionDto → Session mapping.  The v7 alpha.27 rename means both
 * wire keys must still route to Session.backend correctly.
 */
class DtoRoundTripTest {

    @Test
    fun `backendFamily set and llmBackend null maps to backendFamily`() {
        val dto = SessionDto(
            id = "s1",
            state = "running",
            backendFamily = "ollama",
            llmBackend = null,
        )
        val session = dto.toDomain(serverProfileId = "srv-1")
        assertEquals("ollama", session.backend)
    }

    @Test
    fun `backendFamily null and llmBackend set maps to llmBackend`() {
        val dto = SessionDto(
            id = "s2",
            state = "running",
            backendFamily = null,
            llmBackend = "claude-code",
        )
        val session = dto.toDomain(serverProfileId = "srv-1")
        assertEquals("claude-code", session.backend)
    }

    @Test
    fun `both backendFamily and llmBackend null yields null backend`() {
        val dto = SessionDto(
            id = "s3",
            state = "new",
            backendFamily = null,
            llmBackend = null,
        )
        val session = dto.toDomain(serverProfileId = "srv-1")
        assertNull(session.backend)
    }

    @Test
    fun `backendFamily wins over llmBackend when both are set`() {
        // Mapper: backendFamily ?: llmBackend — backendFamily takes priority.
        val dto = SessionDto(
            id = "s4",
            state = "running",
            backendFamily = "openai-compat",
            llmBackend = "legacy-key",
        )
        val session = dto.toDomain(serverProfileId = "srv-1")
        assertEquals("openai-compat", session.backend)
    }
}
