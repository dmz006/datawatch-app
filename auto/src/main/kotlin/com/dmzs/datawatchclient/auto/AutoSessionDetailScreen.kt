@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BL303-A2 — Session detail for Android Auto.
 *
 * Layout by state:
 *
 * Waiting/RateLimited — Body: the prompt being asked.
 *   Buttons: [Play] [Voice Reply → VoiceRecordingScreen]    Strip: [chat-icon → AutoReplyListScreen]
 *
 * Running — Body: currentStatus (what AI is doing right now).
 *   Buttons: [Play] [Voice Reply]    Strip: [chat-icon → AutoReplyListScreen]
 *
 * Blocked — Body: block summary.
 *   Buttons: [Approve Gate] (+ [Stages] for automata sessions)
 *
 * Terminal (Completed/Killed/Error) — Body: last response.
 *   Buttons: [Play] [Restart or Stages]
 */
public class AutoSessionDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
    private val autoPlayLong: Boolean = false,
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
    private var error: String? = null
    private var isLoading: Boolean = true
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastDetailHash: Int = -1

    init {
        // Eager first fetch so onGetTemplate() has real data before onStart() fires.
        scope.launch {
            refresh()
            isLoading = false
            invalidate()
            if (autoPlayLong) {
                // "Play" notification button: always open LastOutputDetailScreen so the user
                // lands somewhere meaningful. LastOutputDetailScreen shows "No content available"
                // gracefully when all text fields are null.
                val rawText = promptContext ?: lastPrompt ?: lastSummaryLong ?: lastResponse
                val (shortPlay, splitLong) = splitOutputText(rawText)
                val longPlay = lastSummaryLong?.takeIf { it.isNotBlank() && it != rawText } ?: splitLong
                screenManager.push(LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay))
            }
        }

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
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
            },
        )
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            val newHash =
                listOf(
                    sessionState, error, telemetry?.currentTask, telemetry?.progress,
                    lastResponse, lastSummaryLong, currentStatus, currentStatusLong, promptContext, lastPrompt,
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
            val profile =
                resolveActiveProfile() ?: run {
                    error = "No enabled server"
                    return
                }
            val transport = AutoServiceLocator.transportFor(profile)
            transport.getSessionTelemetry(sessionId).fold(
                onSuccess = { t ->
                    error = null
                    telemetry = t
                },
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

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Throwable) {
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        // No addAction(.setTitle()) — those are parked-only and may cause host rejection while driving.
        // Retry is voiceIcon slot 1 in ActionStrip (icon-only, always driving-safe).
        MessageTemplate.Builder("Error: ${e.message ?: e::class.simpleName}")
            .setTitle(sessionTitle.ifBlank { sessionId })
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(voiceIcon).setOnClickListener {
                        isLoading = true; error = null; invalidate()
                        scope.launch { refresh(); isLoading = false; invalidate() }
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    private fun buildTemplate(): Template {
        val hasBlock = telemetry?.guardrailVerdicts?.any { it.outcome == "block" } == true
        val isWaiting = sessionState == SessionState.Waiting || sessionState == SessionState.RateLimited
        val isTerminal =
            sessionState == SessionState.Completed ||
                sessionState == SessionState.Killed ||
                sessionState == SessionState.Error

        val chatIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_chat)).build()
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val monitorIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_monitor)).build()
        val sessionsIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_sessions)).build()

        val templateBuilder =
            MessageTemplate.Builder(buildBody())
                .setTitle(sessionTitle.ifBlank { sessionId })
                .setHeaderAction(Action.BACK)

        // MessageTemplate requires 2 icon-only ActionStrip actions on MESSAGING path while driving.
        // Titled ActionStrip actions are rejected while driving; addAction() titled buttons are
        // parked-only but don't affect template acceptance.
        // All branches use icon-only ActionStrip — no addAction(.setTitle()) anywhere.
        // MessageTemplate.addAction() with a title is parked-only: on Samsung gearhead the host
        // may reject the ENTIRE template while driving, making even the ActionStrip icons unusable.
        // "Play" / "Review Gate" are now speaker / chat icons in the ActionStrip.
        when {
            hasBlock -> {
                val autoId = automataIdFromTelemetry()
                val thirdStripAction = if (autoId.isNotBlank()) {
                    Action.Builder().setIcon(monitorIcon).setOnClickListener {
                        screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                    }.build()
                } else {
                    Action.Builder().setIcon(sessionsIcon).setOnClickListener { screenManager.pop() }.build()
                }
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.Builder().setIcon(chatIcon).setOnClickListener {
                            screenManager.push(
                                BlockDetailsScreen(carContext, sessionId, sessionTitle, guardrailVerdicts),
                            )
                        }.build())
                        .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                            AutoTts.speak(carContext, buildBody())
                        }.build())
                        .addAction(thirdStripAction)
                        .build(),
                )
            }
            isWaiting -> {
                val waitText = promptContext ?: lastPrompt ?: lastSummaryLong ?: lastResponse
                val (shortPlay, splitLong) = splitOutputText(waitText)
                val longPlay =
                    lastSummaryLong?.takeIf { it.isNotBlank() && it != waitText }
                        ?: splitLong
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder().setIcon(speakerIcon).setOnClickListener {
                                CarToast.makeText(carContext, "Loading…", CarToast.LENGTH_SHORT).show()
                                screenManager.push(
                                    LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay),
                                )
                            }.build(),
                        )
                        .addAction(
                            Action.Builder().setIcon(voiceIcon).setOnClickListener {
                                CarToast.makeText(carContext, "Voice reply…", CarToast.LENGTH_SHORT).show()
                                screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                            }.build(),
                        )
                        .addAction(
                            Action.Builder().setIcon(chatIcon).setOnClickListener {
                                screenManager.push(AutoReplyListScreen(carContext, sessionId, sessionTitle))
                            }.build(),
                        )
                        .build(),
                )
            }
            sessionState == SessionState.Running -> {
                val playText = currentStatus ?: lastResponse
                val (shortPlay, longPlay) = splitOutputText(playText)
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder().setIcon(speakerIcon).setOnClickListener {
                                CarToast.makeText(carContext, "Loading…", CarToast.LENGTH_SHORT).show()
                                screenManager.push(
                                    LastOutputDetailScreen(
                                        carContext,
                                        sessionId,
                                        sessionTitle,
                                        shortPlay,
                                        currentStatusLong ?: longPlay,
                                    ),
                                )
                            }.build(),
                        )
                        .addAction(
                            Action.Builder().setIcon(voiceIcon).setOnClickListener {
                                CarToast.makeText(carContext, "Voice reply…", CarToast.LENGTH_SHORT).show()
                                screenManager.push(VoiceRecordingScreen(carContext, sessionId, sessionTitle))
                            }.build(),
                        )
                        .addAction(
                            Action.Builder().setIcon(chatIcon).setOnClickListener {
                                screenManager.push(AutoReplyListScreen(carContext, sessionId, sessionTitle))
                            }.build(),
                        )
                        .build(),
                )
            }
            isTerminal -> {
                val (shortResp, longResp) = splitOutputText(lastResponse)
                val termLong = longResp ?: lastSummaryLong?.takeIf { it.isNotBlank() }
                val autoId = automataIdFromTelemetry()
                val secondStripAction = if (autoId.isNotBlank()) {
                    Action.Builder().setIcon(monitorIcon).setOnClickListener {
                        CarToast.makeText(carContext, "Loading stages…", CarToast.LENGTH_SHORT).show()
                        screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                    }.build()
                } else {
                    Action.Builder().setIcon(sessionsIcon).setOnClickListener { screenManager.pop() }.build()
                }
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                            CarToast.makeText(carContext, "Loading…", CarToast.LENGTH_SHORT).show()
                            screenManager.push(
                                LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortResp, termLong),
                            )
                        }.build())
                        .addAction(secondStripAction)
                        .build(),
                )
            }
            else -> {
                // New / unknown state
                val (shortPlay, longPlay) = splitOutputText(lastResponse)
                val autoId = automataIdFromTelemetry()
                val secondStripAction = if (autoId.isNotBlank()) {
                    Action.Builder().setIcon(monitorIcon).setOnClickListener {
                        CarToast.makeText(carContext, "Loading stages…", CarToast.LENGTH_SHORT).show()
                        screenManager.push(AutoPrdStagesScreen(carContext, autoId, automataNameFromTelemetry()))
                    }.build()
                } else {
                    Action.Builder().setIcon(sessionsIcon).setOnClickListener { screenManager.pop() }.build()
                }
                templateBuilder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                            CarToast.makeText(carContext, "Loading…", CarToast.LENGTH_SHORT).show()
                            screenManager.push(
                                LastOutputDetailScreen(carContext, sessionId, sessionTitle, shortPlay, longPlay),
                            )
                        }.build())
                        .addAction(secondStripAction)
                        .build(),
                )
            }
        }

        return templateBuilder.build()
    }

    /** Body text: conversation-formatted exchange between user and datawatch session. */
    private fun buildBody(): String {
        if (isLoading) return "Loading…"
        if (error != null) return "Error: $error"
        val body = buildString {
            when (sessionState) {
                SessionState.Waiting, SessionState.RateLimited -> {
                    // promptContext overrides lastPrompt per server spec.
                    val prompt = promptContext?.lines()?.firstOrNull { it.isNotBlank() }
                        ?: lastPrompt?.takeIf { it.isNotBlank() }
                        ?: lastSummaryLong?.takeIf { it.isNotBlank() }
                        ?: lastResponse?.takeIf { it.isNotBlank() }
                    if (prompt != null) appendLine("[You]: $prompt")
                    appendLine("[datawatch]: Input needed — tap Voice Reply to respond")
                }
                SessionState.Running -> {
                    val prompt = lastPrompt?.lines()?.firstOrNull { it.isNotBlank() }
                        ?: promptContext?.lines()?.firstOrNull { it.isNotBlank() }
                    if (prompt != null) appendLine("[You]: $prompt")
                    val status = currentStatus?.takeIf { it.isNotBlank() }
                        ?: telemetry?.currentTask?.takeIf { it.isNotBlank() }?.let { "▶ $it" }
                        ?: "Running…"
                    append("[datawatch]: $status")
                }
                SessionState.Completed, SessionState.Killed, SessionState.Error -> {
                    val prompt = lastPrompt?.lines()?.firstOrNull { it.isNotBlank() }
                        ?: promptContext?.lines()?.firstOrNull { it.isNotBlank() }
                    if (prompt != null) appendLine("[You]: $prompt")
                    val response = lastResponse?.takeIf { it.isNotBlank() }
                        ?: "Session ${sessionState.name.lowercase()}"
                    append("[datawatch]: $response")
                }
                else -> {
                    val task = telemetry?.currentTask?.takeIf { it.isNotBlank() }
                    val status = currentStatus?.takeIf { it.isNotBlank() }
                    if (task != null) appendLine("▶ $task")
                    if (status != null) append(status) else append(sessionState.name)
                }
            }
            // Sprint/automata context so the user knows which plan this session belongs to.
            val sprint = telemetry?.sprint
            if (!sprint?.automataId.isNullOrBlank()) {
                append("\n\n⟫ ${sprint?.automata?.takeIf { it.isNotBlank() } ?: "Automata"}")
                sprint?.task?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            }
        }
        return body.trim().take(BODY_CHAR_LIMIT)
    }

    /** Returns the automata ID from session telemetry, or empty string if none. */
    private fun automataIdFromTelemetry(): String = telemetry?.sprint?.automataId.orEmpty()

    /** Returns the automata name from session telemetry, falling back to the ID. */
    private fun automataNameFromTelemetry(): String =
        telemetry?.sprint?.automata?.takeIf { it.isNotBlank() } ?: automataIdFromTelemetry()

    private fun onRestart() {
        scope.launch {
            runCatching {
                val profile =
                    resolveActiveProfile() ?: run {
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

    /** Splits long text into a short preview + full version for [LastOutputDetailScreen]. */
    private fun splitOutputText(text: String?): Pair<String?, String?> {
        if (text.isNullOrBlank()) return null to null
        return if (text.length > SHORT_PLAY_CHARS) {
            text.take(SHORT_PLAY_CHARS) to text
        } else {
            text to null
        }
    }

    private companion object {
        const val POLL_MS: Long = 10_000L
        const val AMBIENT_POLL_MS: Long = 60_000L
        const val BODY_CHAR_LIMIT: Int = 500
        const val SHORT_PLAY_CHARS: Int = 200
        const val ERROR_MSG_CHARS: Int = 40

        const val MS_PER_MIN: Long = 60_000L
        const val FAST_TASK_MS: Double = 30_000.0
        const val SLOW_TASK_MS: Double = 300_000.0

        fun computeEtaMinutes(telem: SessionTelemetryDto): Int? {
            val completed = telem.tasks.filter { it.status == "completed" }
            val remaining = telem.tasks.count { it.status != "completed" && it.status != "failed" }
            if (completed.isEmpty() || remaining == 0) return null
            val avgMs =
                completed.map { it.durationMs }.filter { it > 0 }.average()
                    .takeIf { !it.isNaN() } ?: return null
            return ((avgMs * remaining) / MS_PER_MIN).toInt().coerceAtLeast(1)
        }

        fun velocityBadge(telem: SessionTelemetryDto): String {
            val completed = telem.tasks.filter { it.status == "completed" }
            val hasBlock = telem.guardrailVerdicts.any { it.outcome == "block" }
            if (hasBlock) return "🔥"
            if (completed.isEmpty()) return ""
            val avgMs =
                completed.map { it.durationMs }.filter { it > 0 }.average()
                    .takeIf { !it.isNaN() } ?: return ""
            return when {
                avgMs < FAST_TASK_MS -> "🚀"
                avgMs > SLOW_TASK_MS -> "🐢"
                else -> ""
            }
        }
    }
}
