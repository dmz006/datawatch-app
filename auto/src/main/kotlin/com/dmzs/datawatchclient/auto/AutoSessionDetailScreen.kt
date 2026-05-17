package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BL303-A2 — Session detail screen for Android Auto.
 *
 * Shows current task, sprint ancestry, guardrail verdicts, ETA,
 * and action buttons (Approve Gate / Kill Session).
 * Fully implemented in Sprint A2; A1 lands this stub for navigation.
 */
public class AutoSessionDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private var telemetry: SessionTelemetryDto? = null
    private var sessionState: SessionState = SessionState.New
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = scope.launch { pollLoop() }
            }

            override fun onStop(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = null
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            invalidate()
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profile = resolveActiveProfile() ?: run {
                error = "No enabled server"
                return
            }
            val transport = AutoServiceLocator.transportFor(profile)
            transport.getSessionTelemetry(sessionId).fold(
                onSuccess = { t ->
                    error = null
                    telemetry = t
                },
                onFailure = { err ->
                    error = err.message ?: "Could not load telemetry"
                },
            )
            transport.listSessions().getOrNull()
                ?.firstOrNull { it.id == sessionId }
                ?.let { sessionState = it.state }
        } catch (e: Throwable) {
            error = e.message ?: e::class.simpleName
        }
    }

    override fun onGetTemplate(): Template {
        val telem = telemetry
        val body = buildString {
            if (error != null) {
                appendLine("Error: $error")
            } else if (telem == null) {
                appendLine("Loading…")
            } else {
                // Current task
                if (telem.currentTask.isNotBlank()) appendLine("▶ ${telem.currentTask}")
                // Sprint ancestry
                telem.sprint?.let { sprint ->
                    if (sprint.automata.isNotBlank()) {
                        appendLine("${sprint.automata} › ${sprint.name}")
                    }
                }
                // Progress
                if (telem.progress > 0f) appendLine("Progress: ${(telem.progress * 100).toInt()}%")
                // Guardrail verdicts
                val blocks = telem.guardrailVerdicts.filter { it.outcome == "block" }
                if (blocks.isNotEmpty()) {
                    appendLine("⚠ Blocked:")
                    blocks.forEach { v -> appendLine("  ${v.guardrail}: ${v.summary.take(SUMMARY_CHARS)}") }
                }
                // ETA
                val eta = computeEta(telem)
                if (eta != null) appendLine("ETA: ~$eta min")
            }
        }.trim().take(BODY_CHAR_LIMIT)

        val hasBlock = telemetry?.guardrailVerdicts?.any { it.outcome == "block" } == true

        val templateBuilder = MessageTemplate.Builder(body)
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)

        // Action: Approve Gate — only shown when guardrail is blocking
        if (hasBlock) {
            templateBuilder.addAction(
                Action.Builder()
                    .setTitle("Approve Gate")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener { onApproveGate() }
                    .build(),
            )
        }

        // Action: Kill Session
        if (sessionState == SessionState.Running || sessionState == SessionState.Waiting) {
            templateBuilder.addAction(
                Action.Builder()
                    .setTitle("Kill Session")
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener { onKillSession() }
                    .build(),
            )
        }

        return templateBuilder.build()
    }

    private fun onApproveGate() {
        val blockVerdict = telemetry?.guardrailVerdicts?.firstOrNull { it.outcome == "block" }
            ?: return
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).runSessionGuardrail(sessionId)
            }
            refresh()
            invalidate()
        }
    }

    private fun onKillSession() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).killSession(sessionId)
            }
            screenManager.popToRoot()
        }
    }

    private fun computeEta(telem: SessionTelemetryDto): Int? {
        val completed = telem.tasks.filter { it.status == "completed" }
        val remaining = telem.tasks.filter { it.status != "completed" && it.status != "failed" }
        if (completed.isEmpty() || remaining.isEmpty()) return null
        val avgMs = completed.mapNotNull { it.durationMs }.average().takeIf { !it.isNaN() }
            ?: return null
        return ((avgMs * remaining.size) / MS_PER_MIN).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val BODY_CHAR_LIMIT: Int = 500
        const val SUMMARY_CHARS: Int = 60
        const val MS_PER_MIN: Long = 60_000L
    }
}
