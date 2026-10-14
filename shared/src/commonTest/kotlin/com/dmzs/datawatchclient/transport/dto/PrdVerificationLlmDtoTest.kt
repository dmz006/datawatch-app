package com.dmzs.datawatchclient.transport.dto

import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verification-LLM overrides, verifier record and foreign-file drift (server
 * v9.0.73 / v9.0.75). Older daemons omit every field, so each must default.
 */
class PrdVerificationLlmDtoTest {
    private val json = RestTransport.DefaultJson

    @Test
    fun `old server payload without verification fields still parses`() {
        val prd =
            json.decodeFromString(
                PrdDto.serializer(),
                """{"id":"p1","stories":[{"id":"s1","title":"S","tasks":[{"id":"t1","title":"T","verification":{"summary":"ok"}}]}]}""",
            )
        assertNull(prd.verificationBackend)
        assertNull(prd.blockOnFileDrift)
        val story = prd.stories.single()
        assertNull(story.verificationModel)
        val task = story.tasks.single()
        assertNull(task.verificationEffort)
        assertTrue(task.foreignFileDrift.isEmpty())
        assertNull(task.verification?.verifierSource)
        assertNull(PrdLlmBadge.verifierLabel(task.verification, "automaton"))
    }

    @Test
    fun `config and guardrail profile carry block_on_file_drift`() {
        val cfg = json.decodeFromString(AutonomousConfigDto.serializer(), """{"block_on_file_drift":false}""")
        assertEquals(false, cfg.blockOnFileDrift)
        assertNull(json.decodeFromString(AutonomousConfigDto.serializer(), "{}").blockOnFileDrift)
        val gp = json.decodeFromString(GuardrailProfileDto.serializer(), """{"id":"g","name":"G","block_on_file_drift":true}""")
        assertEquals(true, gp.blockOnFileDrift)
    }

    @Test
    fun `verifier label matches the PWA judged-by line plus effort`() {
        val full =
            PrdTaskVerificationDto(
                verifierBackend = "ollama",
                verifierModel = "qwen3:8b",
                verifierEffort = "low",
                verifierSource = "task",
            )
        assertEquals("ollama / qwen3:8b / low (task)", PrdLlmBadge.verifierLabel(full, "automaton"))
        assertEquals(
            "claude-code (Automat)",
            PrdLlmBadge.verifierLabel(PrdTaskVerificationDto(verifierBackend = "claude-code", verifierSource = "prd"), "Automat"),
        )
        assertEquals("pool-llm", PrdLlmBadge.verifierLabel(PrdTaskVerificationDto(verifierBackend = "pool-llm"), "automaton"))
        assertNull(PrdLlmBadge.verifierLabel(PrdTaskVerificationDto(verifierModel = "x"), "automaton"))
        assertNull(PrdLlmBadge.verifierLabel(null, "automaton"))
    }
}
