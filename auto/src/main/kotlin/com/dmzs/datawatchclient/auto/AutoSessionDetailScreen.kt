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
 *   Buttons: [Voice Reply] [Quick Reply]    Strip: [Play] [Kill]
 *
 * Running — Body: currentStatus (what AI is doing right now).
 *   Buttons: [Play] [Send]    Strip: [Kill]
 *
 * Blocked — Body: block summary.
 *   Buttons: [Approve Gate] [Kill Session]
 *
 * Terminal — Body: last response.
 *   Buttons: [Play]
 *
 * Quick Reply mode — Body: prompt/context (200 chars).
 *   Buttons: [Yes] [No]    Strip: [Continue] [Skip] [🎤 Voice] [Cancel]
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
    private var currentStatusLong: String? = null
    private var promptContext: String? = null
    private var lastPrompt: String? = null
    private var guardrailVerdicts: List<GuardrailVerdictDto> = emptyList()
    private var killPending: Boolean = false
    private var error: String? = null
    private var replyMode: Boolean = false
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastDetailHash: Int = -1

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
                scope.cancel()
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            val newHash = listOf(
                sessionState, error, telemetry?.currentTask, telemetry?.progress,
                killPending, lastResponse, currentStatus, currentStatusLong, promptContext, lastPrompt,
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
        val isTerminal = sessionState == SessionState.Completed || sessionState == SessionState.Killed

        val templateBuilder = MessageTemplate.Builder(buildBody())
            .setTitle(sessionTitle.ifBlank { sessionId })
            .setHeaderAction(Action.BACK)

        // ActionStrip — labeled text for discoverability while driving.
        // Kill goes here so it doesn't occupy a primary button slot.
        val stripBuilder = ActionStrip.Builder()
        var stripCount = 0

        if (!killPending && isActive && !hasBlock) {
            stripBuilder.addAction(
                Action.Builder().setTitle("Kill").setOnClickListener { onKillTap() }.build()
            )
            stripCount++
        }
        // For waiting sessions: add "Play" to the strip so the user can hear the prompt
        // without having to enter Quick Reply mode first.
        if ((sessionState == SessionState.Waiting || sessionState == SessionState.RateLimited) && stripCount < 4) {
            val waitPlayText = lastPrompt ?: promptContext?.lines()?.firstOrNull { it.isNotBlank() } ?: lastResponse
            if (!waitPlayText.isNullOrBlank()) {
                val (shortPlay, longPlay) = splitOutputText(waitPlayText)
                stripBuilder.addAction(
                    Action.Builder().setTitle("Play").setOnClickListener {
                        screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay))
                    }.build()
                )
                stripCount++
            }
        }
        if (stripCount > 0) templateBuilder.setActionStrip(stripBuilder.build())

        // Primary action buttons (max 2 per MessageTemplate).
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
                if (isActive) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Kill Session")
                            .setOnClickListener { onKillTap() }
                            .build()
                    )
                }
            }
            sessionState == SessionState.Waiting || sessionState == SessionState.RateLimited -> {
                // Voice Reply is the primary action while driving.
                // Quick Reply opens the Yes/No/Continue/Skip/Voice strip.
                templateBuilder.addAction(
                    Action.Builder().setTitle("Voice Reply")
                        .setOnClickListener {
                            screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                        }
                        .build()
                )
                templateBuilder.addAction(
                    Action.Builder().setTitle("Quick Reply")
                        .setOnClickListener { replyMode = true; invalidate() }
                        .build()
                )
            }
            sessionState == SessionState.Running -> {
                // Play = hear what the AI is doing. Send = inject a command.
                val playText = currentStatus ?: lastResponse
                if (!playText.isNullOrBlank()) {
                    templateBuilder.addAction(
                        Action.Builder().setTitle("Play")
                            .setOnClickListener {
                                screenManager.push(
                                    LastOutputDetailScreen(carContext, sessionId, sessionTitle, playText, currentStatusLong)
                                )
                            }
                            .build()
                    )
                }
                templateBuilder.addAction(
                    Action.Builder().setTitle("Send")
                        .setOnClickListener { replyMode = true; invalidate() }
                        .build()
                )
            }
            isTerminal -> {
                // MessageTemplate requires at least one action — always add Play (LastOutputDetailScreen
                // shows "No content available" gracefully when lastResponse is null).
                val (shortResp, longResp) = splitOutputText(lastResponse)
                templateBuilder.addAction(
                    Action.Builder().setTitle("Play")
                        .setOnClickListener {
                            screenManager.push(
                                LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortResp, longResp)
                            )
                        }
                        .build()
                )
            }
            else -> {
                templateBuilder.addAction(
                    Action.Builder().setTitle("Send")
                        .setOnClickListener { replyMode = true; invalidate() }
                        .build()
                )
            }
        }

        return templateBuilder.build()
    }

    /** Body text: the most relevant content for the current session state. */
    private fun buildBody(): String {
        if (killPending) return "Tap Confirm Kill to kill ${sessionTitle.take(40)}"
        if (error != null) return "Error: $error"
        return when (sessionState) {
            SessionState.Running ->
                currentStatus?.takeIf { it.isNotBlank() }
                    ?: telemetry?.currentTask?.takeIf { it.isNotBlank() }?.let { "▶ $it" }
                    ?: "Running…"
            SessionState.Waiting, SessionState.RateLimited ->
                lastPrompt?.takeIf { it.isNotBlank() }
                    ?: promptContext?.lines()?.firstOrNull { it.isNotBlank() }
                    ?: lastResponse?.takeIf { it.isNotBlank() }
                    ?: "Waiting for your input"
            SessionState.Completed, SessionState.Killed ->
                lastResponse?.takeIf { it.isNotBlank() }
                    ?: "Session ${sessionState.name.lowercase()}"
            else -> buildString {
                telemetry?.currentTask?.takeIf { it.isNotBlank() }?.let { appendLine("▶ $it") }
                currentStatus?.let { appendLine(it) }
            }.trim().ifEmpty { sessionState.name }
        }.take(BODY_CHAR_LIMIT)
    }

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
                    .addAction(Action.Builder().setTitle("Skip").setOnClickListener { sendReply("skip\r") }.build())
                    .addAction(
                        Action.Builder()
                            .setTitle("Voice")
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)
                                ).build()
                            )
                            .setOnClickListener {
                                screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                            }
                            .build()
                    )
                    .addAction(Action.Builder().setTitle("Cancel").setOnClickListener { replyMode = false; invalidate() }.build())
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
            screenManager.popToRoot()
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
