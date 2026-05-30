@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BL303-A2 — Session detail for Android Auto.
 *
 * Shows: current task, sprint ancestry, guardrail verdicts, ETA,
 * progress %, velocity badge, and action buttons.
 * Kill requires two-tap confirmation (killPending state).
 */
public class AutoSessionDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private var telemetry: SessionTelemetryDto? = null
    private var sessionState: SessionState = SessionState.New
    private var lastResponse: String? = null
    private var currentStatus: String? = null
    private var killPending: Boolean = false
    private var killFeedback: String? = null
    private var error: String? = null
    private var replyMode: Boolean = false
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // §15: track snapshot hash to skip redundant invalidate() calls.
    private var lastDetailHash: Int = -1
    private val tts: TextToSpeech = TextToSpeech(carContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }

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

            override fun onDestroy(owner: LifecycleOwner) {
                tts.stop()
                tts.shutdown()
                scope.cancel()
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            // §15: only invalidate when telemetry/state actually changed.
            val newHash = listOf(sessionState, error, telemetry?.currentTask, telemetry?.progress, killPending, lastResponse, currentStatus).hashCode()
            if (newHash != lastDetailHash) {
                lastDetailHash = newHash
                invalidate()
            }
            // BL303-A5.2: terminal sessions use cached state — slow poll, no more network calls
            val isTerminal = sessionState == SessionState.Completed || sessionState == SessionState.Killed
            delay(if (isTerminal) AMBIENT_POLL_MS else POLL_MS)
        }
    }

    private suspend fun refresh() {
        killFeedback = null
        try {
            val profile = resolveActiveProfile() ?: run {
                error = "No enabled server"
                return
            }
            val transport = AutoServiceLocator.transportFor(profile)
            transport.getSessionTelemetry(sessionId).fold(
                onSuccess = { t -> error = null; telemetry = t },
                onFailure = { err -> error = err.message ?: "Could not load telemetry" },
            )
            transport.listSessions().getOrNull()
                ?.firstOrNull { it.id == sessionId }
                ?.let { item ->
                    sessionState = item.state
                    lastResponse = item.lastResponse?.takeIf { it.isNotBlank() }
                }
            if (sessionState == SessionState.Running) {
                transport.getSessionCurrentStatus(sessionId).getOrNull()
                    ?.currentStatus?.takeIf { it.isNotBlank() }
                    ?.let { currentStatus = it }
            } else {
                currentStatus = null
            }
        } catch (e: Throwable) {
            error = e.message ?: e::class.simpleName
        }
    }

    override fun onGetTemplate(): Template {
        // Screen stack depth guard: this screen may be at depth 5 (via AutoAutomataScreen →
        // AutoSessionListScreen path). Pushing SessionReplyScreen would exceed the 5-screen
        // stack limit. Use inline reply mode (template invalidation) instead of a new push.
        if (replyMode) return buildReplyTemplate()

        val telem = telemetry
        val hasBlock = telem?.guardrailVerdicts?.any { it.outcome == "block" } == true
        val isActive = sessionState == SessionState.Running ||
            sessionState == SessionState.Waiting ||
            sessionState == SessionState.RateLimited
        // BL303-A5.2: ambient mode — terminal sessions use simplified read-only body, no actions
        val isTerminal = sessionState == SessionState.Completed || sessionState == SessionState.Killed
        val body = if (isTerminal) buildAmbientBody() else buildDetailBody(telem)

        val templateBuilder = MessageTemplate.Builder(body)
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)

        if (!isTerminal) {
            // BL303-A7.1: Drive compliance — max 2 action buttons per MessageTemplate.
            // Priority: Approve Gate > Kill > Reply (when blocked, gate takes precedence over reply).
            if (hasBlock) {
                // Blocked: Approve Gate + Kill (2 buttons)
                templateBuilder.addAction(
                    Action.Builder()
                        .setTitle("Approve Gate")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { onApproveGate() }
                        .build(),
                )
                if (killPending) {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Confirm Kill")
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener { onConfirmKill() }
                            .build(),
                    )
                } else if (isActive) {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Kill Session")
                            .setOnClickListener { onKillTap() }
                            .build(),
                    )
                }
            } else {
                // Not blocked: Send (all non-terminal) + Kill (if active) — max 2 buttons.
                // Send uses inline mode (replyMode=true) so we can inject commands into any
                // session state — Running, Waiting, RateLimited — not just Waiting.
                if (!killPending) {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Send")
                            .setOnClickListener { replyMode = true; invalidate() }
                            .build(),
                    )
                }
                if (isActive) {
                    if (killPending) {
                        templateBuilder.addAction(
                            Action.Builder()
                                .setTitle("Confirm Kill")
                                .setBackgroundColor(CarColor.RED)
                                .setOnClickListener { onConfirmKill() }
                                .build(),
                        )
                    } else {
                        templateBuilder.addAction(
                            Action.Builder()
                                .setTitle("Kill Session")
                                .setOnClickListener { onKillTap() }
                                .build(),
                        )
                    }
                }
            }
        }

        return templateBuilder.build()
    }

    private fun buildReplyTemplate(): Template {
        fun sendReply(text: String) {
            scope.launch {
                val profiles = AutoServiceLocator.profileRepository.observeAll().first()
                val profile = profiles.firstOrNull { it.enabled } ?: return@launch
                AutoServiceLocator.transportFor(profile).replyToSession(sessionId, text).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Sent: $text", CarToast.LENGTH_SHORT).show()
                        replyMode = false
                        // Return to the session detail view (same screen, not a pop).
                        invalidate()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Reply failed — ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                        replyMode = false
                        invalidate()
                    },
                )
            }
        }
        // Back action cancels reply mode and returns to the detail view rather than popping
        // the session detail screen off the stack. Action.BACK on a ListTemplate would call
        // screenManager.pop() which removes this screen entirely; we need a custom cancel action.
        val cancelAction = Action.Builder()
            .setTitle("Cancel")
            .setOnClickListener { replyMode = false; invalidate() }
            .build()
        // Drive compliance: max 6 rows per ListTemplate.
        val items = ItemList.Builder()
            .addItem(Row.Builder().setTitle(colored("Yes", CarColor.GREEN)).addText("Affirmative reply / confirm").setOnClickListener { sendReply("yes") }.build())
            .addItem(Row.Builder().setTitle(colored("No", CarColor.RED)).addText("Negative reply / decline").setOnClickListener { sendReply("no") }.build())
            .addItem(Row.Builder().setTitle("Continue").addText("Resume or proceed").setOnClickListener { sendReply("continue") }.build())
            .addItem(Row.Builder().setTitle("Skip").addText("Skip current step").setOnClickListener { sendReply("skip") }.build())
            .addItem(Row.Builder().setTitle("▶ Play").addText("Hear current status aloud").setOnClickListener { speakStatus() }.build())
            .addItem(Row.Builder().setTitle(colored("Stop", CarColor.RED)).addText("Stop the session").setOnClickListener { sendReply("stop") }.build())
            .build()
        return ListTemplate.Builder()
            .setTitle("Send Command")
            // No Action.BACK here: this template is returned from a screen already on the stack.
            // Action.BACK would call screenManager.pop() and remove the session detail screen.
            // The Cancel action in the ActionStrip is the driver-safe way to exit reply mode.
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(cancelAction)
                    .build(),
            )
            .setSingleList(items)
            .build()
    }

    // BL303-A5.2: ambient body — cached summary only, no live telemetry, no actions
    private fun buildAmbientBody(): String = buildString {
        appendLine(sessionState.name.lowercase().replaceFirstChar { it.uppercaseChar() })
        telemetry?.let { t ->
            if (t.currentTask.isNotBlank()) appendLine("Last task: ${t.currentTask.take(80)}")
            if (t.progress > 0f) appendLine("Progress: ${(t.progress * 100).toInt()}%")
        }
    }.trim().ifEmpty { sessionState.name.lowercase().replaceFirstChar { it.uppercaseChar() } }.take(BODY_CHAR_LIMIT)

    private fun buildDetailBody(telem: SessionTelemetryDto?): String = buildString {
        killFeedback?.let { appendLine("ℹ $it"); appendLine() }
        when {
            error != null -> appendLine("Error: $error")
            telem == null -> appendLine("Loading…")
            else -> {
                // Current task
                if (telem.currentTask.isNotBlank()) appendLine("▶ ${telem.currentTask}")
                // Sprint ancestry
                telem.sprint?.let { sprint ->
                    if (sprint.automata.isNotBlank() && sprint.name.isNotBlank()) {
                        appendLine("${sprint.automata} › ${sprint.name}")
                    }
                }
                // Progress + velocity badge
                if (telem.progress > 0f) {
                    val pct = (telem.progress * 100).toInt()
                    val badge = velocityBadge(telem)
                    appendLine("Progress: $pct% $badge")
                }
                // ETA
                computeEtaMinutes(telem)?.let { eta -> appendLine("ETA: ~$eta min") }
                // Running → AI current-status summary; waiting → last LLM response
                if (sessionState == SessionState.Running) {
                    currentStatus?.let { appendLine("◈ $it") }
                } else {
                    lastResponse?.let { appendLine("◀ $it") }
                }
                // Guardrail verdicts — blocks first, then warns
                val blocks = telem.guardrailVerdicts.filter { it.outcome == "block" }
                val warns = telem.guardrailVerdicts.filter { it.outcome == "warn" }
                if (blocks.isNotEmpty()) {
                    appendLine("⚠ Blocked:")
                    blocks.forEach { v ->
                        appendLine("  ${v.guardrail}: ${v.summary.take(SUMMARY_CHARS)}")
                    }
                }
                if (warns.isNotEmpty()) {
                    appendLine("⚠ Warnings: ${warns.joinToString { it.guardrail }}")
                }
                // Kill pending notice
                if (killPending) appendLine("\nTap Confirm Kill to proceed")
            }
        }
    }.trim().ifEmpty { sessionState.name.lowercase().replaceFirstChar { it.uppercaseChar() } }.take(BODY_CHAR_LIMIT)

    private fun speakStatus() {
        val text = currentStatus ?: lastResponse ?: "No status available"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "status")
        replyMode = false
        invalidate()
    }

    private fun onKillTap() {
        killPending = true
        killFeedback = "Tap Confirm Kill to kill ${sessionTitle.take(30)}"
        invalidate()
        // Auto-cancel pending after 15s
        scope.launch {
            delay(KILL_CONFIRM_TIMEOUT_MS)
            if (killPending) {
                killPending = false
                invalidate()
            }
        }
    }

    private fun onConfirmKill() {
        killPending = false
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).killSession(sessionId)
            }
            screenManager.popToRoot()
        }
    }

    private fun onApproveGate() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).runSessionGuardrail(sessionId)
                killFeedback = "Gate approval submitted"
            }
            refresh()
            invalidate()
        }
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val AMBIENT_POLL_MS: Long = 60_000L
        const val BODY_CHAR_LIMIT: Int = 500
        const val SUMMARY_CHARS: Int = 60
        const val MS_PER_MIN: Long = 60_000L
        const val KILL_CONFIRM_TIMEOUT_MS: Long = 15_000L

        fun computeEtaMinutes(telem: SessionTelemetryDto): Int? {
            val completed = telem.tasks.filter { it.status == "completed" }
            val remaining = telem.tasks.count { it.status != "completed" && it.status != "failed" }
            if (completed.isEmpty() || remaining == 0) return null
            val avgMs = completed.map { it.durationMs }.filter { it > 0 }.average()
                .takeIf { !it.isNaN() } ?: return null
            return ((avgMs * remaining) / MS_PER_MIN).toInt().coerceAtLeast(1)
        }

        fun velocityBadge(telem: SessionTelemetryDto): String {
            val completed = telem.tasks.filter { it.status == "completed" }
            val hasBlock = telem.guardrailVerdicts.any { it.outcome == "block" }
            if (hasBlock) return "🔥"
            if (completed.isEmpty()) return ""
            val avgMs = completed.map { it.durationMs }.filter { it > 0 }.average()
                .takeIf { !it.isNaN() } ?: return ""
            return when {
                avgMs < FAST_TASK_MS -> "🚀"
                avgMs > SLOW_TASK_MS -> "🐢"
                else -> ""
            }
        }

        const val FAST_TASK_MS: Double = 30_000.0   // < 30s avg = fast
        const val SLOW_TASK_MS: Double = 300_000.0  // > 5min avg = slow
    }
}
