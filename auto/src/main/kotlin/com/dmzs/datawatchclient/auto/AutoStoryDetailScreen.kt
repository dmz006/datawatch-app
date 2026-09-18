@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Full story detail for Android Auto — TTS-readable via Car Assistant.
 *
 * BL30 note: the primary navigation flow now goes through the stateful
 * [AutoPrdStoriesScreen] (depth 4), which shows story detail inline and
 * pushes [AutoTaskDetailScreen] (depth 5) for per-task actions. This class
 * is retained for its [buildStoryBody] logic and for any caller that still
 * needs a standalone story-detail screen at depth 5.
 *
 * Shows the story's description, per-task status (✓/◉/✗/○), files touched,
 * retry counts, and verification summaries; provides:
 *  - "Approve" when the parent PRD is in `needs_review` / `revisions_asked` /
 *    `awaiting_review` — approves the PRD at the server level.
 *  - "Reset Task" when a task has `failed` status.
 */
public class AutoStoryDetailScreen(
    carContext: CarContext,
    private val prdId: String,
    private val prdStatus: String,
    private val story: PrdStoryDto,
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
            },
        )
    }

    private fun fireApprove() {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).prdAction(prdId, "approve").fold(
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
                AutoServiceLocator.transportFor(profile).resetPrdTask(prdId, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task reset", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
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

    override fun onGetTemplate(): Template {
        val body = buildStoryBody(story)
        val prdStatusLower = prdStatus.lowercase()
        val isReview = prdStatusLower in setOf("needs_review", "awaiting_review", "revisions_asked")
        val firstFailed = story.tasks.firstOrNull { it.status == "failed" }

        val builder = MessageTemplate.Builder(body)
            .setTitle(story.title.take(MAX_TITLE).ifBlank { "Story" })
            .setHeaderAction(Action.BACK)

        // MessageTemplate allows only 1 custom-title action; when review + failed task both apply,
        // Reset Task goes in the ActionStrip so Approve stays as the primary button.
        var resetInStrip: PrdTaskDto? = null
        when {
            isReview -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Approve")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { fireApprove() }
                        .build(),
                )
                if (firstFailed != null) resetInStrip = firstFailed
            }
            firstFailed != null -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Reset Task")
                        .setBackgroundColor(CarColor.YELLOW)
                        .setOnClickListener { fireResetTask(firstFailed) }
                        .build(),
                )
            }
        }
        if (resetInStrip != null) {
            val task = resetInStrip
            builder.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Reset Task")
                            .setOnClickListener { fireResetTask(task) }
                            .build(),
                    )
                    .build(),
            )
        }

        return builder.build()
    }

    internal companion object {
        const val MAX_TITLE = 40
        const val MAX_DESC_CHARS = 250
        const val MAX_TASK_DESC = 60
        const val MAX_FILES = 4

        private val DONE_STATUSES = setOf("complete", "completed", "done")

        fun buildStoryBody(story: PrdStoryDto): String =
            buildString {
                // Status
                appendLine("Status: ${story.status.ifBlank { "unknown" }}")
                appendLine()

                // Description
                story.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    appendLine(desc.take(MAX_DESC_CHARS) + if (desc.length > MAX_DESC_CHARS) "…" else "")
                    appendLine()
                }

                // Tasks
                if (story.tasks.isNotEmpty()) {
                    appendLine("Tasks:")
                    story.tasks.forEach { task ->
                        val marker = when (task.status) {
                            "complete", "completed", "done" -> "✓"
                            "in_progress" -> "◉"
                            "failed" -> "✗"
                            else -> "○"
                        }
                        val taskLine = "$marker ${task.task.take(MAX_TASK_DESC)}"
                        appendLine(taskLine)
                        // Show error for failed tasks
                        task.error?.takeIf { it.isNotBlank() && task.status == "failed" }?.let { err ->
                            appendLine("  Error: ${err.take(80)}")
                        }
                        // Show retry count for failed tasks that have been retried
                        if (task.status == "failed" && task.retryCount > 0) {
                            appendLine("  Retries: ${task.retryCount}")
                        }
                        // Show verification summary for completed tasks
                        if (task.status in DONE_STATUSES) {
                            task.verification?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                                appendLine("  ✓ ${summary.take(70)}")
                            }
                        }
                    }
                    val done = story.tasks.count { it.status in DONE_STATUSES }
                    val failed = story.tasks.count { it.status == "failed" }
                    val total = story.tasks.size
                    appendLine()
                    val summaryLine = buildString {
                        append("$done/$total done")
                        if (failed > 0) append(" · $failed failed")
                    }
                    appendLine(summaryLine)
                }

                // Files touched
                val files = story.filesTouched.takeIf { it.isNotEmpty() } ?: story.files
                if (files.isNotEmpty()) {
                    appendLine()
                    appendLine("Files:")
                    files.take(MAX_FILES).forEach { f ->
                        appendLine("  ${f.take(60)}")
                    }
                    val more = files.size - MAX_FILES
                    if (more > 0) appendLine("  … $more more")
                }
            }.trimEnd()
    }
}
