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
import com.dmzs.datawatchclient.transport.dto.DecisionDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full PRD detail screen for Android Auto.
 *
 * Shows a TTS-readable overview of the automaton: status, story progress arc,
 * active story + current task, pending stories, most recent decision, and a
 * spec snippet. Contextual actions (Approve/Reject/Stop/Delete) and an action
 * strip "Stories" button that pushes [AutoPrdStoriesScreen].
 *
 * Polls every 15s while the screen is visible so progress updates in real time
 * without requiring the user to navigate away and back.
 */
public class AutoPrdDetailScreen(
    carContext: CarContext,
    private val prdId: String,
) : Screen(carContext) {

    private var prd: PrdDto? = null
    private var isLoading = true
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastHash = -1

    init {
        scope.launch { load(); invalidate() }
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
                override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
            },
        )
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            load()
            val newHash = prd.hashCode() xor (error?.hashCode() ?: 0)
            if (newHash != lastHash) {
                lastHash = newHash
                invalidate()
            }
            delay(POLL_MS)
        }
    }

    private suspend fun load() {
        try {
            val profile = resolveActiveProfile() ?: run { error = "No enabled server"; return }
            AutoServiceLocator.transportFor(profile).getPrd(prdId).fold(
                onSuccess = { dto -> prd = dto; error = null },
                onFailure = { err -> error = err.message ?: err::class.simpleName ?: "error" },
            )
        } catch (e: Throwable) {
            error = e.message ?: e::class.simpleName ?: "error"
        } finally {
            isLoading = false
        }
    }

    private fun fire(action: String) {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).prdAction(prdId, action).fold(
                    onSuccess = {
                        CarToast.makeText(
                            carContext,
                            "${action.replaceFirstChar { it.uppercase() }} sent",
                            CarToast.LENGTH_SHORT,
                        ).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun fireDelete() {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).deletePrd(prdId).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Deleted", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Delete failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Throwable) {
        // Any exception from onGetTemplate() disconnects the car session.
        // Return a safe fallback so the user sees an error row instead of getting ejected.
        MessageTemplate.Builder("Error: ${e.message ?: e::class.simpleName}")
            .setTitle("Automata")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Retry")
                    .setOnClickListener {
                        isLoading = true
                        error = null
                        invalidate()
                        scope.launch { load(); invalidate() }
                    }
                    .build(),
            )
            .build()
    }

    private fun buildTemplate(): Template {
        val currentPrd = prd
        val body = when {
            isLoading -> "Loading plan details…"
            error != null -> "Error: $error"
            currentPrd == null -> "Plan not found"
            else -> buildDetailBody(currentPrd)
        }
        val prdName = currentPrd?.let {
            it.title?.takeIf { t -> t.isNotBlank() }
                ?: it.name.takeIf { n -> n.isNotBlank() }
                ?: it.id
        } ?: "Automata"

        val templateBuilder = MessageTemplate.Builder(body)
            .setTitle(prdName.take(MAX_TITLE))
            .setHeaderAction(Action.BACK)

        if (!isLoading && error == null && currentPrd != null) {
            val statusLower = currentPrd.status.lowercase()
            val isRunning = statusLower in setOf("running", "active")
            val isReview = statusLower in setOf("needs_review", "awaiting_review", "revisions_asked")
            val isTerminal = statusLower in setOf("killed", "completed", "complete", "cancelled", "rejected", "error")

            val isApproved = statusLower == "approved"
            val isPending = statusLower in setOf("pending", "decomposing", "idle", "")

            // MessageTemplate allows only 1 action with a custom title.
            // Approve/Reject needs both — Approve goes in addAction, Reject in the strip.
            var rejectInStrip = false
            when {
                isReview -> {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Approve")
                            .setBackgroundColor(CarColor.GREEN)
                            .setOnClickListener { fire("approve") }
                            .build(),
                    )
                    rejectInStrip = true
                }
                isRunning -> {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Stop")
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener { fire("cancel") }
                            .build(),
                    )
                }
                isApproved -> {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Run")
                            .setBackgroundColor(CarColor.GREEN)
                            .setOnClickListener { fire("run") }
                            .build(),
                    )
                }
                isPending -> {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Decompose")
                            .setOnClickListener { fire("decompose") }
                            .build(),
                    )
                }
                isTerminal -> {
                    templateBuilder.addAction(
                        Action.Builder()
                            .setTitle("Delete")
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener { fireDelete() }
                            .build(),
                    )
                }
            }

            // All strip actions must be icon-only (no setTitle) — titled strip actions trigger
            // the driving validator when the session was started from a MESSAGING notification.
            val closeIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_auto_close),
            ).build()
            val storiesIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_auto_sessions),
            ).build()
            val voiceIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice),
            ).build()

            val stripBuilder = ActionStrip.Builder()
            // Reject lives in the strip for review state (1-action limit on MessageTemplate).
            if (rejectInStrip) {
                stripBuilder.addAction(
                    Action.Builder()
                        .setIcon(closeIcon)
                        .setOnClickListener { fire("reject") }
                        .build(),
                )
            }
            if (currentPrd.stories.isNotEmpty()) {
                stripBuilder.addAction(
                    Action.Builder()
                        .setIcon(storiesIcon)
                        .setOnClickListener {
                            screenManager.push(AutoPrdStoriesScreen(carContext, currentPrd))
                        }
                        .build(),
                )
            }
            // Voice icon — opens VoiceRecordingScreen for a spec update via speech.
            stripBuilder.addAction(
                Action.Builder()
                    .setIcon(voiceIcon)
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Speak spec update…", CarToast.LENGTH_SHORT).show()
                        screenManager.push(
                            VoiceRecordingScreen(
                                carContext,
                                sessionId = "",
                                sessionTitle = prdName,
                                prdId = prdId,
                            ),
                        )
                    }
                    .build(),
            )
            templateBuilder.setActionStrip(stripBuilder.build())
        }

        if (!isLoading && error != null) {
            templateBuilder.addAction(
                Action.Builder()
                    .setTitle("Retry")
                    .setOnClickListener {
                        isLoading = true
                        error = null
                        invalidate()
                        scope.launch { load(); invalidate() }
                    }
                    .build(),
            )
        }

        return templateBuilder.build()
    }

    internal companion object {
        const val POLL_MS = 15_000L
        const val MAX_TITLE = 40
        const val MAX_STORY_TITLE = 52
        const val MAX_TASK_CHARS = 62
        const val MAX_SPEC_CHARS = 220
        const val MAX_PENDING_SHOWN = 4
        const val PROGRESS_WIDTH = 10

        private val DONE_STATUSES = setOf("complete", "completed", "done")

        fun buildDetailBody(prd: PrdDto): String =
            buildString {
                val totalStories = prd.stories.size
                val completedStories = prd.stories.count { it.status.lowercase() in DONE_STATUSES }
                val activeStory = prd.stories.firstOrNull {
                    it.status in setOf("in_progress", "awaiting_approval")
                }
                val pendingStories = prd.stories.filter {
                    it.status.lowercase() !in DONE_STATUSES && it.status != "in_progress" && it.status != "awaiting_approval" && it.status != "rejected"
                }

                // Conversation header: spec as [You] (what was requested), status as [datawatch]
                prd.spec?.takeIf { it.isNotBlank() }?.let { spec ->
                    appendLine("[You]: ${spec.take(MAX_SPEC_CHARS)}${if (spec.length > MAX_SPEC_CHARS) "…" else ""}")
                }
                appendLine("[datawatch]: ${prd.status.ifBlank { "unknown" }}")
                appendLine()

                // Progress arc
                if (totalStories > 0) {
                    val pct = completedStories * 100 / totalStories
                    val filled = (pct * PROGRESS_WIDTH / 100).coerceIn(0, PROGRESS_WIDTH)
                    val bar = "▓".repeat(filled) + "░".repeat(PROGRESS_WIDTH - filled)
                    appendLine("Progress: $bar $pct%  ·  $completedStories/$totalStories stories done")
                }
                appendLine()

                // Active story + current task
                if (activeStory != null) {
                    appendActiveStory(activeStory)
                    appendLine()
                }

                // Pending stories (up to MAX_PENDING_SHOWN)
                if (pendingStories.isNotEmpty()) {
                    appendLine("Up next:")
                    pendingStories.take(MAX_PENDING_SHOWN).forEach { s ->
                        appendLine("  ○ ${s.title.take(MAX_STORY_TITLE)}")
                    }
                    val more = pendingStories.size - MAX_PENDING_SHOWN
                    if (more > 0) appendLine("  … $more more")
                    appendLine()
                }

                // Most recent decision
                prd.decisions?.lastOrNull()?.let { appendDecision(it) }
            }.trimEnd()

        private fun StringBuilder.appendActiveStory(story: PrdStoryDto) {
            val marker = if (story.status == "awaiting_approval") "⚠" else "◉"
            appendLine("$marker Active: ${story.title.take(MAX_STORY_TITLE)}")
            if (story.status == "awaiting_approval") {
                appendLine("  → Awaiting your approval")
                return
            }
            val doneTasks = story.tasks.count { it.status in DONE_STATUSES }
            val totalTasks = story.tasks.size
            val failedTasks = story.tasks.count { it.status == "failed" }
            if (totalTasks > 0) {
                val taskLine = buildString {
                    append("  Tasks: $doneTasks/$totalTasks done")
                    if (failedTasks > 0) append(" · $failedTasks failed")
                }
                appendLine(taskLine)
            }
            story.tasks.firstOrNull { it.status == "in_progress" }?.let { running ->
                appendLine("  ▶ ${running.task.take(MAX_TASK_CHARS)}")
            }
            story.description?.takeIf { it.isNotBlank() && story.tasks.isEmpty() }?.let {
                appendLine("  ${it.take(80)}")
            }
        }

        private fun StringBuilder.appendDecision(d: DecisionDto) {
            val actor = d.actor?.takeIf { it.isNotBlank() } ?: "system"
            val kind = d.kind?.takeIf { it.isNotBlank() } ?: "decision"
            val note = d.note?.take(80)?.takeIf { it.isNotBlank() }
            append("Last $kind ($actor)")
            if (note != null) append(": $note")
            appendLine()
            appendLine()
        }
    }
}
