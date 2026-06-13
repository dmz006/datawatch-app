@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
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
 * Layout by state:
 *
 * Waiting/RateLimited — Body: the prompt being asked.
 *   Buttons: [Play] [Voice Reply]    Strip: [Kill] [chat-icon → replyMode]
 *
 * Running — Body: currentStatus (what AI is doing right now).
 *   Buttons: [Play] [Voice Reply]    Strip: [Kill] [chat-icon → replyMode]
 *
 * Blocked — Body: block summary.
 *   Buttons: [Approve Gate] [Kill Session]
 *
 * Terminal (Completed/Killed/Error) — Body: last response.
 *   Buttons: [Play] [Restart]
 *
 * Quick Reply mode — Body: prompt/context (200 chars).
 *   Buttons: [Yes] [No]    Strip: [Continue] [🎤 Voice] [✕ Cancel]
 */
public class AutoSessionDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private var telemetry: SessionTelemetryDto? = null
    private var sessionState: SessionState = SessionState.New
    private var lastResponse: String? = null
    private var lastSummaryLong: String? = null
    private var currentStatus: String? = null
    private var currentStatusLong: String? = null
    private var promptContext: String? = null
    private var lastPrompt: String? = null
    private var guardrailVerdicts: List<GuardrailVerdictDto> = emptyList()
    private var killPending: Boolean = false
    private var error: String? = null
    private var replyMode: Boolean = false
    private var isLoading: Boolean = true
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastDetailHash: Int = -1

    init {
        // Eager first fetch so onGetTemplate() has real data before onStart() fires.
        scope.launch { refresh(); isLoading = false; invalidate() }

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
                scope.cancel()
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            val newHash = listOf(
                sessionState, error, telemetry?.currentTask, telemetry?.progress,
                killPending, lastResponse, lastSummaryLong, currentStatus, currentStatusLong, promptContext, lastPrompt,
            ).hashCode()
            if (newHash != lastDetailHash) {
                lastDetailHash = newHash
                invalidate()
            }
            val isTerminal = sessionState == SessionState.Completed || sessionState == SessionState.Killed
            delay(if (isTerminal) AMBIENT_POLL_MS else POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profile = resolveActiveProfile() ?: run { error = "No enabled server"; return }
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
                    lastSummaryLong = item.lastSummaryLong?.takeIf { it.isNotBlank() }
                    promptContext = item.promptContext?.takeIf { it.isNotBlank() }
                    lastPrompt = item.lastPrompt?.takeIf { it.isNotBlank() }
                    guardrailVerdicts = telemetry?.guardrailVerdicts ?: emptyList()
                }
            if (sessionState == SessionState.Running) {
                transport.getSessionCurrentStatus(sessionId).getOrNull()?.let { dto ->
                    currentStatus = dto.currentStatus.takeIf { it.isNotBlank() }
                    currentStatusLong = dto.currentStatusLong.takeIf { it.isNotBlank() }
                }
            } else {
                currentStatus = null
                currentStatusLong = null
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message ?: e::class.simpleName
        }
    }

    override fun onGetTemplate(): Template {
        // Inline reply mode avoids a screen push (this screen may be at depth 5 via
        // AutoAutomataScreen → AutoSessionListScreen, which would exceed the 5-screen limit).
        if (replyMode) return buildReplyTemplate()

        val hasBlock = telemetry?.guardrailVerdicts?.any { it.outcome == "block" } == true
        val isActive = sessionState == SessionState.Running ||
            sessionState == SessionState.Waiting ||
            sessionState == SessionState.RateLimited
        val isWaiting = sessionState == SessionState.Waiting || sessionState == SessionState.RateLimited
        val isTerminal = sessionState == SessionState.Completed ||
            sessionState == SessionState.Killed ||
            sessionState == SessionState.Error

        val chatIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_chat)).build()
        val killIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_kill)).build()

        val templateBuilder = MessageTemplate.Builder(buildBody())
            .setTitle(sessionTitle.ifBlank { sessionId })
            .setHeaderAction(Action.BACK)

        when {
            killPending -> {
                templateBuilder.addAction(
                    Action.Builder().setTitle("Confirm Kill")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { onConfirmKill() }
                        .build()
                )
                templateBuilder.addAction(
                    Action.Builder().setTitle("Cancel")
                        .setOnClickListener { killPending = false; invalidate() }
                        .build()
                )
            }
            hasBlock -> {
                templateBuilder.addAction(
                    Action.Builder().setTitle("Approve Gate")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { onApproveGate() }
                        .build()
                )
                // When session belongs to an automaton, show "Stages" to view/approve the plan.
                // Otherwise fall back to "Kill Session" for the active state.
                val autoId = automataIdFromTelemetry()
                if (autoId.isNotBlank()) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Stages")
                            .setOnClickListener {
                                screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                            }
                            .build()
                    )
                } else if (isActive) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Kill Session")
                            .setOnClickListener { onKillTap() }
                            .build()
                    )
                }
            }
            isWaiting -> {
                // Play is always primary — LastOutputDetailScreen handles null content gracefully.
                // Voice Reply is second primary so the user can speak without looking at the screen.
                // Kill + Quick Reply text input live in the strip (1 titled strip action = Kill).
                // Full promptContext is the richest "what is it asking" content;
                // lastSummaryLong gives the AI-generated long form for Play Long.
                val waitText = promptContext ?: lastPrompt ?: lastSummaryLong ?: lastResponse
                val (shortPlay, splitLong) = splitOutputText(waitText)
                // lastSummaryLong is the AI long-form; only use it as longPlay when it differs
                // from waitText (avoids Play Long speaking identical content to Play when
                // waitText IS lastSummaryLong and both are short).
                val longPlay = lastSummaryLong?.takeIf { it.isNotBlank() && it != waitText }
                    ?: splitLong
                templateBuilder.addAction(
                    Action.Builder().setTitle("Play")
                        .setOnClickListener {
                            screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay))
                        }.build()
                )
                templateBuilder.addAction(
                    Action.Builder().setTitle("Voice Reply")
                        .setOnClickListener {
                            screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                        }.build()
                )
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        // Chat icon first (reply-like position); kill icon second with stop icon.
                        // Only 1 titled action permitted per strip (Kill).
                        .addAction(Action.Builder().setIcon(chatIcon).setOnClickListener { replyMode = true; invalidate() }.build())
                        .addAction(Action.Builder().setTitle("Kill").setIcon(killIcon).setOnClickListener { onKillTap() }.build())
                        .build()
                )
            }
            sessionState == SessionState.Running -> {
                // Play = hear what the AI is doing. Voice Reply injects input while running.
                // Quick Reply (typed) and Kill go to the strip.
                val playText = currentStatus ?: lastResponse
                val (shortPlay, longPlay) = splitOutputText(playText)
                templateBuilder.addAction(
                    Action.Builder().setTitle("Play")
                        .setOnClickListener {
                            screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, currentStatusLong ?: longPlay))
                        }.build()
                )
                templateBuilder.addAction(
                    Action.Builder().setTitle("Voice Reply")
                        .setOnClickListener {
                            screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                        }.build()
                )
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.Builder().setIcon(chatIcon).setOnClickListener { replyMode = true; invalidate() }.build())
                        .addAction(Action.Builder().setTitle("Kill").setIcon(killIcon).setOnClickListener { onKillTap() }.build())
                        .build()
                )
            }
            isTerminal -> {
                // Play shows last response; lastSummaryLong (AI summary) is the long form when available.
                val (shortResp, longResp) = splitOutputText(lastResponse)
                val termLong = lastSummaryLong?.takeIf { it.isNotBlank() } ?: longResp
                templateBuilder.addAction(
                    Action.Builder().setTitle("Play")
                        .setOnClickListener {
                            screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortResp, termLong))
                        }.build()
                )
                // For automata sessions, show plan stages so the user can see what completed;
                // for standalone sessions, offer restart.
                val autoId = automataIdFromTelemetry()
                if (autoId.isNotBlank()) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Stages")
                            .setOnClickListener {
                                screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                            }
                            .build()
                    )
                } else {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Restart")
                            .setOnClickListener { onRestart() }
                            .build()
                    )
                }
            }
            else -> {
                // New / unknown state — Play shows whatever content is available.
                val (shortPlay, longPlay) = splitOutputText(lastResponse)
                templateBuilder.addAction(
                    Action.Builder().setTitle("Play")
                        .setOnClickListener {
                            screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay))
                        }.build()
                )
                // Add Stages for automata sessions even in unknown/New state.
                val autoId = automataIdFromTelemetry()
                if (autoId.isNotBlank()) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Stages")
                            .setOnClickListener {
                                screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                            }
                            .build()
                    )
                }
            }
        }

        return templateBuilder.build()
    }

    /** Body text: the most relevant content for the current session state. */
    private fun buildBody(): String {
        if (isLoading) return "Loading…"
        if (killPending) return "Tap Confirm Kill to kill ${sessionTitle.take(40)}"
        if (error != null) return "Error: $error"
        val main = when (sessionState) {
            SessionState.Running ->
                currentStatus?.takeIf { it.isNotBlank() }
                    ?: telemetry?.currentTask?.takeIf { it.isNotBlank() }?.let { "▶ $it" }
                    ?: "Running…"
            SessionState.Waiting, SessionState.RateLimited ->
                // promptContext overrides lastPrompt per server spec: it's the pre-processed
                // last ~4 lines of conversation, not the raw LLM prompt string.
                promptContext?.lines()?.firstOrNull { it.isNotBlank() }
                    ?: lastPrompt?.takeIf { it.isNotBlank() }
                    ?: lastSummaryLong?.takeIf { it.isNotBlank() }
                    ?: lastResponse?.takeIf { it.isNotBlank() }
                    ?: "Waiting for your input"
            SessionState.Completed, SessionState.Killed, SessionState.Error ->
                lastResponse?.takeIf { it.isNotBlank() }
                    ?: "Session ${sessionState.name.lowercase()}"
            else -> buildString {
                telemetry?.currentTask?.takeIf { it.isNotBlank() }?.let { appendLine("▶ $it") }
                currentStatus?.let { appendLine(it) }
            }.trim().ifEmpty { sessionState.name }
        }
        // Append automata/sprint context so the user knows which plan this session belongs to.
        val sprint = telemetry?.sprint
        val automataCtx = if (!sprint?.automataId.isNullOrBlank()) buildString {
            append("\n\n⟫ ${sprint?.automata?.takeIf { it.isNotBlank() } ?: "Automata"}")
            sprint?.task?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        } else ""
        return (main + automataCtx).take(BODY_CHAR_LIMIT)
    }

    /** Returns the automata ID from session telemetry, or empty string if none. */
    private fun automataIdFromTelemetry(): String = telemetry?.sprint?.automataId.orEmpty()

    /** Returns the automata name from session telemetry, falling back to the ID. */
    private fun automataNameFromTelemetry(): String =
        telemetry?.sprint?.automata?.takeIf { it.isNotBlank() } ?: automataIdFromTelemetry()

    private fun buildReplyTemplate(): Template {
        fun sendReply(text: String) {
            scope.launch {
                val profiles = AutoServiceLocator.profileRepository.observeAll().first()
                val profile = profiles.firstOrNull { it.enabled } ?: return@launch
                AutoServiceLocator.transportFor(profile).replyToSession(sessionId, text).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Sent", CarToast.LENGTH_SHORT).show()
                        replyMode = false
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

        val bodyText = (lastPrompt ?: promptContext?.lines()?.firstOrNull { it.isNotBlank() } ?: currentStatus ?: "")
            .replace("\n", " ").trim().take(REPLY_BODY_CHARS)
            .ifEmpty { "What do you want to say?" }

        return MessageTemplate.Builder(bodyText)
            .setTitle("Reply")
            .addAction(Action.Builder().setTitle("Yes").setOnClickListener { sendReply("yes\r") }.build())
            .addAction(Action.Builder().setTitle("No").setOnClickListener { sendReply("no\r") }.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Continue").setOnClickListener { sendReply("continue\r") }.build())
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build())
                            .setOnClickListener {
                                screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                            }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build())
                            .setOnClickListener { replyMode = false; invalidate() }
                            .build()
                    )
                    .build(),
            )
            .build()
    }

    private fun onKillTap() {
        killPending = true
        invalidate()
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
            // pop() returns to session list; popToRoot() would skip it and land on the summary screen.
            screenManager.pop()
        }
    }

    private fun onRestart() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: run {
                    CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                    return@runCatching
                }
                AutoServiceLocator.transportFor(profile).restartSession(sessionId).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Session restarting", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Restart failed: ${err.message?.take(ERROR_MSG_CHARS) ?: "unknown"}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun onApproveGate() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).runSessionGuardrail(sessionId)
            }
            refresh()
            invalidate()
        }
    }

    /** Splits long text into a short preview + full version for [LastOutputDetailScreen]. */
    private fun splitOutputText(text: String?): Pair<String?, String?> {
        if (text.isNullOrBlank()) return null to null
        return if (text.length > SHORT_PLAY_CHARS) text.take(SHORT_PLAY_CHARS) to text
        else text to null
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val AMBIENT_POLL_MS: Long = 60_000L
        const val BODY_CHAR_LIMIT: Int = 500
        const val REPLY_BODY_CHARS: Int = 200
        const val KILL_CONFIRM_TIMEOUT_MS: Long = 15_000L
        const val SHORT_PLAY_CHARS: Int = 200
        const val ERROR_MSG_CHARS: Int = 40

        const val MS_PER_MIN: Long = 60_000L
        const val FAST_TASK_MS: Double = 30_000.0
        const val SLOW_TASK_MS: Double = 300_000.0

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
    }
}
