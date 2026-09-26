@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
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
 * Story detail for Android Auto — depth 4.
 *
 * Uses ListTemplate so all rows (lifecycle actions + tasks) are tappable
 * via driving-safe row click listeners. MessageTemplate.addAction() is
 * parked-only on MESSAGING path.
 *
 * Row layout:
 *   [if needs_review]  "✓ Approve" row (tappable)
 *   [if failed task]   "⟳ Reset task" row (tappable)
 *   Story overview row (status + description) — display only
 *   Task rows (tappable) → AutoTaskDetailScreen (depth 5)
 *
 * ActionStrip (2 icon-only, MESSAGING limit):
 *   slot 1: speaker → TTS story summary
 *   slot 2: mic    → VoiceRecordingScreen (depth 5)
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

    private fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrElse { MAX_ROWS_FALLBACK }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Throwable) {
        // Keep ListTemplate on error — same pattern as AutoPrdDetailScreen.
        // An uncaught exception here manifests as "can't do that while driving" on Samsung gearhead.
        val storyTitle = story.title.take(MAX_TITLE).ifBlank { "Story" }
        val errItems = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: e::class.simpleName ?: "Unknown error").build())
            .addItem(Row.Builder().setTitle("Close").addText("Tap to go back").setOnClickListener { screenManager.pop() }.build())
            .build()
        ListTemplate.Builder()
            .setTitle(storyTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(errItems)
            .build()
    }

    private fun buildTemplate(): Template {
        val storyTitle = story.title.take(MAX_TITLE).ifBlank { "Story" }
        val prdStatusLower = prdStatus.lowercase()
        val isReview = prdStatusLower in setOf("needs_review", "awaiting_review", "revisions_asked")
        val firstFailed = story.tasks.firstOrNull { it.status == "failed" }
        val limit = listLimit()
        val items = ItemList.Builder()
        var rowCount = 0

        // Lifecycle action rows — row click is driving-safe
        if (isReview && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("✓ Approve")
                    .addText("Tap to approve the full plan")
                    .setOnClickListener { fireApprove() }
                    .build(),
            )
            rowCount++
        }
        if (firstFailed != null && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("⟳ Reset failed task")
                    .addText(firstFailed.task.take(MAX_TASK_CHARS))
                    .setOnClickListener { fireResetTask(firstFailed) }
                    .build(),
            )
            rowCount++
        }

        // Story overview row (not tappable)
        if (rowCount < limit) {
            val overviewTitle = buildString {
                append(story.status.ifBlank { "unknown" })
                if (story.tasks.isNotEmpty()) {
                    val done = story.tasks.count { it.status in DONE_STATUSES }
                    append("  ·  $done/${story.tasks.size} tasks")
                    val failed = story.tasks.count { it.status == "failed" }
                    if (failed > 0) append(" · $failed failed")
                }
            }
            val descText = story.description?.take(MAX_DESC_CHARS)?.takeIf { it.isNotBlank() }
            val row = Row.Builder().setTitle(overviewTitle)
            if (descText != null) row.addText(descText)
            items.addItem(row.build())
            rowCount++
        }

        // Task rows — each tappable → AutoTaskDetailScreen (depth 5)
        if (story.tasks.isEmpty() && rowCount < limit) {
            items.addItem(Row.Builder().setTitle("No tasks").addText("Story has no tasks yet").build())
            rowCount++
        } else {
            val remaining = (limit - rowCount - 1).coerceAtLeast(0) // reserve 1 for overflow
            val visible = story.tasks.take(remaining)
            visible.forEachIndexed { idx, task ->
                if (rowCount < limit) {
                    items.addItem(buildTaskRow(idx + 1, task))
                    rowCount++
                }
            }
            val overflow = story.tasks.size - visible.size
            if (overflow > 0 && rowCount < limit) {
                items.addItem(
                    Row.Builder()
                        .setTitle("… $overflow more tasks")
                        .addText("Showing top ${visible.size}")
                        .build(),
                )
                rowCount++
            }
        }

        // ActionStrip: 2 icon-only (required while driving on MESSAGING path)
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(speakerIcon)
                    .setOnClickListener {
                        AutoTts.speak(carContext, buildStoryBody(story))
                    }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(voiceIcon)
                    .setOnClickListener {
                        // depth 4 → push VoiceRecordingScreen at depth 5 (within limit)
                        screenManager.push(
                            VoiceRecordingScreen(
                                carContext,
                                sessionId = "",
                                sessionTitle = storyTitle,
                                prdId = prdId,
                                storyId = story.id,
                            ),
                        )
                    }
                    .build(),
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(storyTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(actionStrip)
            .build()
    }

    private fun buildTaskRow(position: Int, task: PrdTaskDto): Row {
        val marker = when (task.status) {
            "complete", "completed", "done" -> "✓"
            "in_progress" -> "◉"
            "verifying", "running_tests" -> "⟳"
            "failed" -> "✗"
            "blocked" -> "⛔"
            "cancelled", "canceled" -> "○"
            else -> "○"
        }
        val title = "$position. $marker ${task.task.take(MAX_TASK_CHARS)}"
        val detail = buildString {
            append(task.status.replace('_', ' '))
            task.error?.takeIf { it.isNotBlank() && task.status == "failed" }
                ?.let { append("  ·  ${it.take(60)}") }
            task.verification?.summary?.takeIf { it.isNotBlank() && task.status in DONE_STATUSES }
                ?.let { append("  ·  ${it.take(50)}") }
            if (task.filesTouched.isNotEmpty()) append("  ·  ${task.filesTouched.size} files")
        }
        return Row.Builder()
            .setTitle(CarText.create(title))
            .addText(detail)
            .setOnClickListener {
                screenManager.push(AutoTaskDetailScreen(carContext, prdId, task))
            }
            .build()
    }

    internal companion object {
        const val MAX_TITLE = 40
        const val MAX_DESC_CHARS = 120
        const val MAX_TASK_CHARS = 52
        const val MAX_FILES = 4
        const val MAX_ROWS_FALLBACK = 6

        private val DONE_STATUSES = setOf("complete", "completed", "done")

        fun buildStoryBody(story: PrdStoryDto): String =
            buildString {
                appendLine(story.title.ifBlank { "Story" })
                appendLine("Status: ${story.status.ifBlank { "unknown" }}")
                appendLine()
                story.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    appendLine(desc.take(250) + if (desc.length > 250) "…" else "")
                    appendLine()
                }
                if (story.tasks.isNotEmpty()) {
                    val done = story.tasks.count { it.status in DONE_STATUSES }
                    val failed = story.tasks.count { it.status == "failed" }
                    appendLine("Tasks: $done/${story.tasks.size} done${if (failed > 0) " · $failed failed" else ""}")
                    story.tasks.forEach { task ->
                        val marker = when (task.status) {
                            "complete", "completed", "done" -> "✓"
                            "in_progress" -> "◉"
                            "failed" -> "✗"
                            else -> "○"
                        }
                        appendLine("$marker ${task.task.take(60)}")
                        task.error?.takeIf { it.isNotBlank() && task.status == "failed" }
                            ?.let { appendLine("  Error: ${it.take(80)}") }
                        task.retryCount.takeIf { it > 0 }
                            ?.let { appendLine("  Retries: $it") }
                        task.verification?.summary?.takeIf { it.isNotBlank() && task.status in DONE_STATUSES }
                            ?.let { appendLine("  ✓ ${it.take(70)}") }
                    }
                }
                val files = story.filesTouched.takeIf { it.isNotEmpty() } ?: story.files
                if (files.isNotEmpty()) {
                    appendLine()
                    appendLine("Files:")
                    files.take(MAX_FILES).forEach { appendLine("  $it") }
                    val more = files.size - MAX_FILES
                    if (more > 0) appendLine("  … $more more")
                }
            }.trimEnd()
    }
}
