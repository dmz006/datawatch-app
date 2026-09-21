@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
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
 * Full PRD detail screen for Android Auto — always returns ListTemplate.
 *
 * All states (loading, error, normal) use ListTemplate so the MESSAGING-path
 * host never sees a template without its required 2-icon ActionStrip. Using
 * MessageTemplate for intermediate states caused "cannot do while driving"
 * because the host rejected templates that lacked a compliant ActionStrip.
 *
 * Row layout (normal state):
 *   Lifecycle action rows (Approve/Stop/Run/Decompose) — tappable, driving-safe
 *   PRD overview row (status + progress + spec snippet) — display only
 *   Story rows — tappable → AutoStoryDetailScreen (depth 4)
 *
 * ActionStrip (2 icon-only, MESSAGING limit — required on ALL templates):
 *   slot 1: speaker → TTS reads full PRD body
 *   slot 2: mic    → VoiceRecordingScreen
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
                        load()
                        invalidate()
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

    private fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrElse { MAX_ROWS_FALLBACK }

    /** 2-icon ActionStrip required on ALL templates on MESSAGING path while driving. */
    private fun buildActionStrip(prdName: String): ActionStrip {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        return ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(speakerIcon)
                    .setOnClickListener {
                        val body = prd?.let { buildDetailBody(it) } ?: prdName
                        AutoTts.speak(carContext, body)
                    }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(voiceIcon)
                    .setOnClickListener {
                        screenManager.push(
                            VoiceRecordingScreen(carContext, sessionId = "", sessionTitle = prdName, prdId = prdId),
                        )
                    }
                    .build(),
            )
            .build()
    }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Throwable) {
        // Fallback stays as ListTemplate with ActionStrip so MESSAGING host accepts it while driving.
        val items = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: e::class.simpleName ?: "Unknown error").build())
            .addItem(Row.Builder().setTitle("⟳ Retry").addText("Tap to reload").setOnClickListener {
                isLoading = true; error = null; invalidate()
                scope.launch { load(); invalidate() }
            }.build())
            .build()
        ListTemplate.Builder()
            .setTitle("Automata")
            .setHeaderAction(Action.BACK)
            .setSingleList(items)
            .setActionStrip(buildActionStrip("Automata"))
            .build()
    }

    private fun buildTemplate(): Template {
        val prdName = prd?.title?.takeIf { it.isNotBlank() }
            ?: prd?.name?.takeIf { it.isNotBlank() }
            ?: "Plan"
        val strip = buildActionStrip(prdName)
        val limit = listLimit()
        val items = ItemList.Builder()

        when {
            isLoading -> {
                items.addItem(Row.Builder().setTitle("Loading…").addText(prdId.take(24)).build())
            }
            error != null -> {
                items.addItem(Row.Builder().setTitle("Error loading plan").addText(error ?: "").build())
                items.addItem(Row.Builder().setTitle("⟳ Retry").addText("Tap to reload").setOnClickListener {
                    isLoading = true; error = null; invalidate()
                    scope.launch { load(); invalidate() }
                }.build())
            }
            prd == null -> {
                items.addItem(Row.Builder().setTitle("Plan not found").addText(prdId.take(24)).build())
            }
            else -> {
                buildPrdRows(prd!!, limit, items)
            }
        }

        return ListTemplate.Builder()
            .setTitle(prdName.take(MAX_TITLE))
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(strip)
            .build()
    }

    private fun buildPrdRows(currentPrd: PrdDto, limit: Int, items: ItemList.Builder) {
        val statusLower = currentPrd.status.lowercase()
        val isRunning = statusLower in setOf("running", "active")
        val isReview = statusLower in setOf("needs_review", "awaiting_review", "revisions_asked")
        val isApproved = statusLower == "approved"
        val isDraft = statusLower == "draft"
        val isTerminal = statusLower in setOf("killed", "completed", "complete", "cancelled",
            "rejected", "error", "done", "failed", "archived")
        var rowCount = 0

        // Lifecycle rows — row click is driving-safe (addAction is parked-only on MESSAGING path)
        if (isReview && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("✓ Approve").addText("Tap to approve this plan")
                .setOnClickListener { fire("approve") }.build())
            rowCount++
        }
        if (isReview && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("✗ Reject").addText("Tap to reject this plan")
                .setOnClickListener { fire("reject") }.build())
            rowCount++
        }
        if (isRunning && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("◼ Stop").addText("Tap to cancel this run")
                .setOnClickListener { fire("cancel") }.build())
            rowCount++
        }
        if (isApproved && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("▶ Run").addText("Tap to start running this plan")
                .setOnClickListener { fire("run") }.build())
            rowCount++
        }
        if (isDraft && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("⟳ Decompose").addText("Tap to decompose into stories")
                .setOnClickListener { fire("decompose") }.build())
            rowCount++
        }
        if (isTerminal && rowCount < limit - 1) {
            items.addItem(Row.Builder().setTitle("🗑 Delete").addText("Tap to permanently delete")
                .setOnClickListener { fireDelete() }.build())
            rowCount++
        }

        // PRD overview row (not tappable)
        val totalStories = currentPrd.stories.size
        val doneStories = currentPrd.stories.count { it.status.lowercase() in DONE_STATUSES }
        val overviewTitle = buildString {
            append(currentPrd.status.ifBlank { "unknown" })
            if (totalStories > 0) {
                val pct = doneStories * 100 / totalStories
                append("  ·  $doneStories/$totalStories stories · $pct%")
            }
        }
        val activeTask = currentPrd.stories.flatMap { it.tasks }.firstOrNull { it.status == "in_progress" }
        if (rowCount < limit) {
            val row = Row.Builder().setTitle(overviewTitle)
            currentPrd.spec?.take(MAX_SPEC_CHARS)?.takeIf { it.isNotBlank() }?.let { row.addText(it) }
            activeTask?.let { row.addText("▶ ${it.task.take(MAX_TASK_CHARS)}") }
            items.addItem(row.build())
            rowCount++
        }

        // Story rows — tappable, push AutoStoryDetailScreen (depth 4)
        if (currentPrd.stories.isEmpty() && rowCount < limit) {
            items.addItem(Row.Builder().setTitle("No stories yet").addText("Plan not yet decomposed").build())
            rowCount++
        } else {
            val remaining = (limit - rowCount).coerceAtLeast(0)
            val visible = currentPrd.stories.take(remaining.coerceAtMost(limit - 1))
            visible.forEachIndexed { idx, story ->
                if (rowCount < limit) {
                    items.addItem(
                        AutoPrdStoriesScreen.buildStoryRow(
                            position = idx + 1,
                            story = story,
                            onClick = {
                                screenManager.push(
                                    AutoStoryDetailScreen(carContext, currentPrd.id, currentPrd.status, story),
                                )
                            },
                        ),
                    )
                    rowCount++
                }
            }
            val overflow = currentPrd.stories.size - visible.size
            if (overflow > 0 && rowCount < limit) {
                items.addItem(Row.Builder().setTitle("… $overflow more stories")
                    .addText("Showing top ${visible.size}").build())
            }
        }
    }

    internal companion object {
        const val POLL_MS = 15_000L
        const val MAX_TITLE = 40
        const val MAX_STORY_TITLE = 52
        const val MAX_TASK_CHARS = 62
        const val MAX_SPEC_CHARS = 120
        const val MAX_PENDING_SHOWN = 4
        const val PROGRESS_WIDTH = 10
        const val MAX_ROWS_FALLBACK = 6

        private val DONE_STATUSES = setOf("complete", "completed", "done")

        fun buildDetailBody(prd: PrdDto): String =
            buildString {
                val totalStories = prd.stories.size
                val completedStories = prd.stories.count { it.status.lowercase() in DONE_STATUSES }
                val activeStory = prd.stories.firstOrNull {
                    it.status in setOf("in_progress", "awaiting_approval")
                }
                val pendingStories = prd.stories.filter {
                    it.status.lowercase() !in DONE_STATUSES &&
                        it.status != "in_progress" && it.status != "awaiting_approval" && it.status != "rejected"
                }
                prd.spec?.takeIf { it.isNotBlank() }?.let { spec ->
                    appendLine("[You]: ${spec.take(220)}${if (spec.length > 220) "…" else ""}")
                }
                appendLine("[datawatch]: ${prd.status.ifBlank { "unknown" }}")
                appendLine()
                if (totalStories > 0) {
                    val pct = completedStories * 100 / totalStories
                    val filled = (pct * PROGRESS_WIDTH / 100).coerceIn(0, PROGRESS_WIDTH)
                    val bar = "▓".repeat(filled) + "░".repeat(PROGRESS_WIDTH - filled)
                    appendLine("Progress: $bar $pct%  ·  $completedStories/$totalStories stories done")
                }
                appendLine()
                if (activeStory != null) {
                    appendActiveStory(activeStory)
                    appendLine()
                }
                if (pendingStories.isNotEmpty()) {
                    appendLine("Up next:")
                    pendingStories.take(MAX_PENDING_SHOWN).forEach { s ->
                        appendLine("  ○ ${s.title.take(MAX_STORY_TITLE)}")
                    }
                    val more = pendingStories.size - MAX_PENDING_SHOWN
                    if (more > 0) appendLine("  … $more more")
                    appendLine()
                }
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
