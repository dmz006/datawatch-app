package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** Outcome of one "Run guardrail" call: `pass` | `warn` | `block` | `unknown`, plus summary. */
public data class IosGuardrailRunResult(
    val outcome: String,
    val summary: String,
)

/**
 * Session guardrail actions for the Status tab (parity 03 › Guardrail verdicts card,
 * 08 › Guardrail verdicts inline). Mirrors Android SessionStatusViewModel:
 * Approve on a blocked verdict (PWA `approveGuardrailVerdict`,
 * POST /api/sessions/{id}/guardrail/{name}/approve) and "Run guardrail"
 * (POST /api/sessions/{id}/guardrail/{name}). Callbacks run on a background thread.
 */
public object IosGuardrails {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** PWA built-in guardrails offered by "Run guardrail" (same list as Android). */
    public val builtins: List<String> = listOf("sast-scan", "secrets-scan", "deps-scan")

    /** [onSuccess] receives the server's `session_unblocked` flag. */
    public fun approve(
        profile: ServerProfile,
        sessionId: String,
        guardrail: String,
        onSuccess: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile)
                .approveGuardrailVerdict(sessionId, guardrail)
                .fold(
                    onSuccess = { unblocked: Boolean -> onSuccess(unblocked) },
                    onFailure = { e: Throwable -> onError(e.message ?: (e::class.simpleName ?: "error")) },
                )
        }
    }

    public fun run(
        profile: ServerProfile,
        sessionId: String,
        guardrail: String,
        onSuccess: (IosGuardrailRunResult) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile)
                .runNamedSessionGuardrail(sessionId, guardrail)
                .fold(
                    onSuccess = { v ->
                        val outcome: String = (v["outcome"] as? JsonPrimitive)?.content ?: "unknown"
                        val summary: String = (v["summary"] as? JsonPrimitive)?.content ?: ""
                        onSuccess(IosGuardrailRunResult(outcome = outcome, summary = summary))
                    },
                    onFailure = { e: Throwable -> onError(e.message ?: (e::class.simpleName ?: "error")) },
                )
        }
    }
}
