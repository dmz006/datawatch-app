@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Stories + story-detail screen for Android Auto. Stateful: starts in
 * "stories list" mode (depth 4 in the Car App Library back stack); tapping
 * a story row switches to "story detail" mode in-place, keeping the same
 * depth slot and leaving depth 5 free for [AutoTaskDetailScreen].
 *
 * Lifecycle actions (Approve, Reset Task, Cancel Story) live in the story-
 * detail ActionStrip. Each task row in story-detail mode is tappable and
 * pushes [AutoTaskDetailScreen] (depth 5).
 *
 * "◀ Stories" in the ActionStrip returns to stories-list mode without
 * popping the screen. The system-back / header BACK action pops to
 * [AutoPrdDetailScreen] (depth 3) regardless of which mode is active.
 */
public class AutoPrdStoriesScreen(
    carContext: CarContext,
    private val prd: PrdDto,
) : Screen(carContext) {

    private var selectedStory: PrdStoryDto? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val story = selectedStory
        return if (story == null) buildStoriesListTemplate() else buildStoryDetailTemplate(story)
    }

    // ---- stories-list mode ----

    private fun buildStoriesListTemplate(): ListTemplate {
        val items = ItemList.Builder()
        if (prd.stories.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle("No stories")
                    .addText("This plan has no stories yet.")
                    .build(),
            )
        } else {
            prd.stories.forEachIndexed { idx, story ->
                items.addItem(buildStoryRow(idx + 1, story) {
                    selectedStory = story
                    invalidate()
                })
            }
        }
        val prdTitle = prd.title?.takeIf { it.isNotBlank() } ?: prd.name.takeIf { it.isNotBlank() } ?: prd.id
        return ListTemplate.Builder()
            .setTitle("${prdTitle.take(28)} — Stories")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }

    // ---- story-detail mode ----

    private fun buildStoryDetailTemplate(story: PrdStoryDto): ListTemplate {
        val items = ItemList.Builder()

        // Story overview row (not tappable — conversation-format header)
        // [You]: story title (what was requested) / [datawatch]: description + status
        val overviewBuilder = Row.Builder()
            .setTitle("[You]: ${story.title.take(MAX_TITLE_CHARS)}")
        val dwResponse = buildString {
            story.description?.takeIf { it.isNotBlank() }
                ?.let { append(it.take(MAX_DESC_CHARS)) }
            val statusLine = buildStoryStatusLine(story)
            if (statusLine.isNotBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(statusLine)
            }
        }.ifBlank { buildStoryStatusLine(story) }
        overviewBuilder.addText("[datawatch]: $dwResponse")
        items.addItem(overviewBuilder.build())

        // Task rows — each pushes AutoTaskDetailScreen (depth 5)
        story.tasks.forEach { task ->
            items.addItem(buildTaskRow(task) {
                screenManager.push(AutoTaskDetailScreen(carContext, prd.id, task))
            })
        }

        // ActionStrip: back-to-stories + lifecycle actions
        val stripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("◀ Stories")
                    .setOnClickListener { selectedStory = null; invalidate() }
                    .build(),
            )

        val prdStatusLower = prd.status.lowercase()
        val isReview = prdStatusLower in REVIEW_STATUSES
        val firstFailed = story.tasks.firstOrNull { it.status == "failed" }

        if (isReview && story.status == "awaiting_approval") {
            stripBuilder.addAction(
                Action.Builder()
                    .setTitle("Approve")
                    .setOnClickListener { fireApprove() }
                    .build(),
            )
        }
        if (firstFailed != null) {
            stripBuilder.addAction(
                Action.Builder()
                    .setTitle("Reset Task")
                    .setOnClickListener { fireResetTask(firstFailed) }
                    .build(),
            )
        }
        stripBuilder.addAction(
            Action.Builder()
                .setTitle("Cancel Story")
                .setOnClickListener { fireCancelStory(story) }
                .build(),
        )

        return ListTemplate.Builder()
            .setTitle(story.title.take(38).ifBlank { "Story Detail" })
            .setHeaderAction(Action.BACK)
            .setActionStrip(stripBuilder.build())
            .setSingleList(items.build())
            .build()
    }

    private fun buildTaskRow(task: PrdTaskDto, onClick: () -> Unit): Row {
        val marker = when (task.status) {
            "complete", "completed", "done" -> "✓"
            "in_progress" -> "◉"
            "failed" -> "✗"
            else -> "○"
        }
        val color = when (task.status) {
            "failed" -> CarColor.RED
            "in_progress" -> CarColor.GREEN
            else -> CarColor.DEFAULT
        }
        val builder = Row.Builder()
            .setTitle(colored("$marker ${task.task.take(MAX_TASK_CHARS)}", color))
            .setOnClickListener(onClick)
        val detail = buildTaskDetailLine(task)
        if (detail.isNotBlank()) builder.addText(detail)
        return builder.build()
    }

    private fun buildTaskDetailLine(task: PrdTaskDto): String =
        buildString {
            when (task.status) {
                "failed" -> {
                    task.error?.takeIf { it.isNotBlank() }
                        ?.let { append("Error: ${it.take(60)}") }
                    if (task.retryCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${task.retryCount} retr${if (task.retryCount == 1) "y" else "ies"}")
                    }
                }
                "complete", "completed", "done" ->
                    task.verification?.summary?.takeIf { it.isNotBlank() }
                        ?.let { append(it.take(70)) }
                "in_progress" ->
                    task.sessionId?.takeIf { it.isNotBlank() }
                        ?.let { append("Session: $it") }
            }
        }

    // ---- fire actions ----

    private fun fireApprove() {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).prdAction(prd.id, "approve").fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Approved", CarToast.LENGTH_SHORT).show()
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

    private fun fireResetTask(task: PrdTaskDto) {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).resetPrdTask(prd.id, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task reset", CarToast.LENGTH_SHORT).show()
                        selectedStory = null
                        invalidate()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Reset failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun fireCancelStory(story: PrdStoryDto) {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).cancelPrdStory(prd.id, story.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Story cancelled", CarToast.LENGTH_SHORT).show()
                        selectedStory = null
                        invalidate()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Cancel failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    // ---- helpers ----

    private fun statusMarker(status: String): String = when (status) {
        "complete", "completed", "done" -> "✓"
        "in_progress" -> "◉"
        "awaiting_approval" -> "⚠"
        "rejected" -> "✗"
        else -> "○"
    }

    private fun buildStoryStatusLine(story: PrdStoryDto): String =
        buildString {
            append(story.status.ifBlank { "unknown" })
            if (story.tasks.isNotEmpty()) {
                val done = story.tasks.count { it.status in DONE_STATUSES }
                val failed = story.tasks.count { it.status == "failed" }
                append(" · $done/${story.tasks.size} tasks")
                if (failed > 0) append(" · $failed failed")
            }
        }

    internal companion object {
        private val DONE_STATUSES = setOf("complete", "completed", "done")
        private val REVIEW_STATUSES = setOf("needs_review", "awaiting_review", "revisions_asked")

        const val MAX_TITLE_CHARS = 50
        const val MAX_TASK_CHARS = 55
        const val MAX_DESC_CHARS = 70

        fun buildStoryRow(
            position: Int,
            story: PrdStoryDto,
            onClick: (() -> Unit)? = null,
        ): Row {
            val marker = when (story.status) {
                "complete", "completed", "done" -> "✓"
                "in_progress" -> "◉"
                "awaiting_approval" -> "⚠"
                "rejected" -> "✗"
                else -> "○"
            }
            val titleText = "$position. $marker ${story.title.take(MAX_TITLE_CHARS)}"
            val statusColor = when (story.status) {
                "awaiting_approval" -> CarColor.RED
                "in_progress" -> CarColor.GREEN
                else -> CarColor.DEFAULT
            }

            val detailLine = buildStoryDetail(story)
            val taskLine = buildTasksLine(story)

            val rowBuilder = Row.Builder().setTitle(colored(titleText, statusColor))
            if (detailLine.isNotBlank()) rowBuilder.addText(detailLine)
            if (taskLine.isNotBlank() && taskLine != detailLine) rowBuilder.addText(taskLine)
            onClick?.let { rowBuilder.setOnClickListener(it) }
            return rowBuilder.build()
        }

        fun buildStoryDetail(story: PrdStoryDto): String =
            when (story.status) {
                "awaiting_approval" -> "⚠ Awaiting your approval"
                else -> story.description?.take(MAX_DESC_CHARS)?.takeIf { it.isNotBlank() } ?: ""
            }

        fun buildTasksLine(story: PrdStoryDto): String {
            val done = setOf("complete", "completed", "done")
            if (story.tasks.isEmpty()) return story.status.ifBlank { "" }
            val doneCount = story.tasks.count { it.status in done }
            val failed = story.tasks.count { it.status == "failed" }
            val running = story.tasks.firstOrNull { it.status == "in_progress" }
            return buildString {
                append("$doneCount/${story.tasks.size} tasks")
                if (failed > 0) append(" · $failed failed")
                running?.let { append(" · ▶ ${it.task.take(MAX_TASK_CHARS)}") }
            }
        }
    }
}
