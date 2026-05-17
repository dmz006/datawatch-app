package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * BL303-A5.7 — Unit tests for GuardrailTtsBuilder.
 * All pure logic — no Android context needed.
 */
class GuardrailTtsBuilderTest {

    // ---- buildGuardrailVerdict: individual verdict ----

    @Test fun `pass verdict`() {
        val v = GuardrailVerdictDto(guardrail = "sast-scan", outcome = "pass", summary = "")
        assertEquals("Security scan passed.", GuardrailTtsBuilder.buildGuardrailVerdict(v))
    }

    @Test fun `warn verdict with summary`() {
        val v = GuardrailVerdictDto(guardrail = "secrets-scan", outcome = "warn", summary = "Possible token detected")
        val result = GuardrailTtsBuilder.buildGuardrailVerdict(v)
        assertTrue(result.startsWith("Secrets scan warning:"))
        assertTrue(result.contains("Possible token detected"))
    }

    @Test fun `block verdict with summary`() {
        val v = GuardrailVerdictDto(guardrail = "deps-scan", outcome = "block", summary = "CVE-2024-1234 critical")
        val result = GuardrailTtsBuilder.buildGuardrailVerdict(v)
        assertTrue(result.startsWith("Dependencies scan is blocking:"))
        assertTrue(result.contains("CVE-2024-1234"))
    }

    @Test fun `llm-grader friendly name`() {
        val v = GuardrailVerdictDto(guardrail = "llm-grader", outcome = "pass", summary = "")
        assertEquals("Quality review passed.", GuardrailTtsBuilder.buildGuardrailVerdict(v))
    }

    @Test fun `unknown guardrail name humanized`() {
        val v = GuardrailVerdictDto(guardrail = "custom-check", outcome = "pass", summary = "")
        val result = GuardrailTtsBuilder.buildGuardrailVerdict(v)
        assertTrue(result.startsWith("Custom check"))
    }

    @Test fun `unknown outcome falls back`() {
        val v = GuardrailVerdictDto(guardrail = "sast-scan", outcome = "skip", summary = "")
        assertEquals("Security scan: skip.", GuardrailTtsBuilder.buildGuardrailVerdict(v))
    }

    @Test fun `long summary is truncated to 80 chars`() {
        val longSummary = "A".repeat(200)
        val v = GuardrailVerdictDto(guardrail = "sast-scan", outcome = "block", summary = longSummary)
        val result = GuardrailTtsBuilder.buildGuardrailVerdict(v)
        // "Security scan is blocking: " + 80 chars + "."
        val extracted = result.removePrefix("Security scan is blocking: ").removeSuffix(".")
        assertTrue(extracted.length <= 80, "Summary in spoken text should be ≤ 80 chars, was ${extracted.length}")
    }

    // ---- buildAllVerdicts: list of verdicts ----

    @Test fun `empty list returns no-verdict message`() {
        val result = GuardrailTtsBuilder.buildAllVerdicts(emptyList())
        assertEquals("No guardrail verdicts.", result)
    }

    @Test fun `all pass list`() {
        val verdicts = listOf(
            GuardrailVerdictDto("sast-scan", "pass", ""),
            GuardrailVerdictDto("secrets-scan", "pass", ""),
        )
        val result = GuardrailTtsBuilder.buildAllVerdicts(verdicts)
        assertEquals("All guardrails passed.", result)
    }

    @Test fun `single block in list`() {
        val verdicts = listOf(
            GuardrailVerdictDto("secrets-scan", "block", "Token found"),
        )
        val result = GuardrailTtsBuilder.buildAllVerdicts(verdicts)
        assertTrue(result.contains("1 block"), "Expected '1 block' in: $result")
        assertTrue(result.contains("Secrets scan is blocking"))
    }

    @Test fun `multiple blocks capped at 2 spoken`() {
        val verdicts = listOf(
            GuardrailVerdictDto("sast-scan", "block", "Issue A"),
            GuardrailVerdictDto("deps-scan", "block", "Issue B"),
            GuardrailVerdictDto("llm-grader", "block", "Issue C"),
        )
        val result = GuardrailTtsBuilder.buildAllVerdicts(verdicts)
        assertTrue(result.startsWith("3 blocks."), "Expected '3 blocks.' prefix in: $result")
        // Only first 2 are spoken in detail
        assertTrue(result.contains("Security scan"))
        assertTrue(result.contains("Dependencies scan"))
    }

    @Test fun `warn list summary`() {
        val verdicts = listOf(
            GuardrailVerdictDto("sast-scan", "warn", "Minor issue"),
            GuardrailVerdictDto("deps-scan", "warn", "Outdated dep"),
        )
        val result = GuardrailTtsBuilder.buildAllVerdicts(verdicts)
        assertTrue(result.contains("2 warnings"), "Expected '2 warnings' in: $result")
        assertFalse(result.contains("block"), "Should not mention block")
    }

    @Test fun `mixed blocks and warns`() {
        val verdicts = listOf(
            GuardrailVerdictDto("secrets-scan", "block", "Token found"),
            GuardrailVerdictDto("sast-scan", "warn", "Minor"),
        )
        val result = GuardrailTtsBuilder.buildAllVerdicts(verdicts)
        assertTrue(result.contains("1 block"))
        assertTrue(result.contains("1 warning"))
    }
}
