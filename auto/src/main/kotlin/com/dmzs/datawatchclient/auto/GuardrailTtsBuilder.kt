package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto

/**
 * BL303-A5.4 — Builds spoken TTS strings from guardrail verdicts.
 * All functions are pure — no side effects, no Android context needed.
 * Designed for ≤15s readout at normal speaking rate.
 */
internal object GuardrailTtsBuilder {

    fun buildGuardrailVerdict(verdict: GuardrailVerdictDto): String {
        val name = friendlyName(verdict.guardrail)
        return when (verdict.outcome) {
            "pass" -> "$name passed."
            "warn" -> "$name warning: ${verdict.summary.take(MAX_SPOKEN_CHARS).trimEnd()}."
            "block" -> "$name is blocking: ${verdict.summary.take(MAX_SPOKEN_CHARS).trimEnd()}."
            else -> "$name: ${verdict.outcome}."
        }
    }

    fun buildAllVerdicts(verdicts: List<GuardrailVerdictDto>): String {
        if (verdicts.isEmpty()) return "No guardrail verdicts."
        val blocks = verdicts.filter { it.outcome == "block" }
        val warns = verdicts.filter { it.outcome == "warn" }
        return buildString {
            if (blocks.isNotEmpty()) {
                append("${blocks.size} block${if (blocks.size > 1) "s" else ""}. ")
                blocks.take(MAX_SPOKEN_VERDICTS).forEach { v ->
                    append(buildGuardrailVerdict(v))
                    append(" ")
                }
            }
            if (warns.isNotEmpty()) {
                append("${warns.size} warning${if (warns.size > 1) "s" else ""}: ")
                append(warns.take(MAX_SPOKEN_VERDICTS).joinToString(", ") { friendlyName(it.guardrail) })
                append(".")
            }
        }.trim().ifEmpty { "All guardrails passed." }
    }

    private fun friendlyName(guardrail: String): String = when (guardrail) {
        "sast-scan" -> "Security scan"
        "secrets-scan" -> "Secrets scan"
        "deps-scan" -> "Dependencies scan"
        "llm-grader" -> "Quality review"
        else -> guardrail.replace("-", " ").replaceFirstChar { it.uppercaseChar() }
    }

    private const val MAX_SPOKEN_CHARS = 80
    private const val MAX_SPOKEN_VERDICTS = 2
}
