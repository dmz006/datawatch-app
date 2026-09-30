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
import com.dmzs.datawatchclient.transport.dto.DecisionDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * PRD screen — story list, story detail, and task detail all in one screen (depth 3).
 *
 * Samsung MESSAGING category rejects ListTemplate at depth ≥ 4 with "can't do that while
 * driving." Story and task detail are shown in-place via [selectedStory]/[selectedTask] so
 * the depth stays at 3. The ActionStrip close button performs in-place back navigation:
 *   task detail   → story detail  (selectedTask = null)
 *   story detail  → story list    (selectedStory = null)
 *   story list    → screenManager.pop()
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

    private var selectedStory: PrdStoryDto? = null
    private var selectedTask: PrdTaskDto? = null

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
            val freshPrd = prd
            if (freshPrd != null) {
                selectedStory = selectedStory?.let { ss -> freshPrd.stories.find { it.id == ss.id } ?: ss }
                selectedTask = selectedTask?.let { st -> selectedStory?.tasks?.find { it.id == st.id } ?: st }
            }
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

    private fun buildActionStrip(): ActionStrip {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val task = selectedTask
        val story = selectedStory
        val p = prd
        val ttsText = when {
            task != null ->
                "${task.task.take(50).ifBlank { "Task" }}. Status: ${task.status.replace('_', ' ')}."
            story != null -> {
                val done = story.tasks.count { it.status.lowercase() in DONE_STATUSES }
                "${story.title.ifBlank { "Story" }}. ${story.status}. $done of ${story.tasks.size} tasks done."
            }
            p != null -> {
                val done = p.stories.count { it.status.lowercase() in DONE_STATUSES }
                "${p.title?.ifBlank { null } ?: p.name}. ${p.status}. $done of ${p.stories.size} stories done."
            }
            else -> "Plan loading."
        }
        return ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                AutoTts.speak(carContext, ttsText)
            }.build())
            .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener {
                when {
                    selectedTask != null -> { selectedTask = null; invalidate() }
                    selectedStory != null -> { selectedStory = null; invalidate() }
                    else -> screenManager.pop()
                }
            }.build())
            .build()
    }

    override fun onGetTemplate(): Template = try {
        when {
            selectedTask != null -> buildTaskDetailTemplate(selectedTask!!)
            selectedStory != null -> buildStoryDetailTemplate(selectedStory!!)
            else -> buildStoryListTemplate()
        }
    } catch (e: Throwable) {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val errStrip = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                AutoTts.speak(carContext, e.message ?: "Error")
            }.build())
            .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
            .build()
        val errItems = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: "Unknown").build())
            .build()
        ListTemplate.Builder()
            .setTitle("Plan")
            .setHeaderAction(Action.BACK)
            .setSingleList(errItems)
            .setActionStrip(errStrip)
            .build()
    }

    // ---- Story list mode ----

    private fun buildStoryListTemplate(): ListTemplate {
        val prdTitle = prd?.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE) ?: "Plan"
        val items = ItemList.Builder()
        when {
            isLoading -> items.addItem(Row.Builder().setTitle("Loading…").addText("Fetching plan…").build())
            error != null -> items.addItem(Row.Builder().setTitle("Error").addText(error ?: "").build())
            else -> buildStoryRows(items)
        }
        return ListTemplate.Builder()
            .setTitle(prdTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    private fun buildStoryRows(items: ItemList.Builder) {
        val p = prd ?: return
        val stories = p.stories

        if (stories.isEmpty()) {
            items.addItem(
                Row.Builder().setTitle("No stories yet").addText("Check back after planning completes.").build(),
            )
            return
        }

        val listMax = runCatching {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        }.getOrElse { MAX_ROWS_FALLBACK }

        val visible = stories.take((listMax - 1).coerceAtLeast(1))
        val overflow = stories.size - visible.size

        visible.forEachIndexed { idx, story ->
            items.addItem(buildStoryRow(idx + 1, story))
        }
        if (overflow > 0) {
            items.addItem(
                Row.Builder()
                    .setTitle("… $overflow more stories")
                    .addText("Showing top ${visible.size}")
                    .build(),
            )
        }
    }

    private fun buildStoryRow(num: Int, story: PrdStoryDto): Row {
        val marker = storyMarker(story.status)
        val taskDone = story.tasks.count { it.status.lowercase() in DONE_STATUSES }
        val color = when (story.status.lowercase()) {
            "awaiting_approval", "needs_review" -> CarColor.RED
            "in_progress", "running", "active" -> CarColor.GREEN
            else -> CarColor.DEFAULT
        }
        return Row.Builder()
            .setTitle(colored("$marker $num. ${story.title.take(MAX_STORY_TITLE)}", color))
            .addText("${story.status}  ·  $taskDone/${story.tasks.size} tasks")
            .setOnClickListener {
                selectedStory = story
                selectedTask = null
                invalidate()
            }
            .build()
    }

    // ---- Story detail mode ----

    private fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrElse { MAX_ROWS_FALLBACK }

    private fun buildStoryDetailTemplate(story: PrdStoryDto): ListTemplate {
        val items = ItemList.Builder()

        val overviewBuilder = Row.Builder()
            .setTitle("[You]: ${story.title.take(MAX_STORY_TITLE_DETAIL)}")
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

        val taskMax = (listLimit() - 2).coerceAtLeast(1)
        val visibleTasks = story.tasks.take(taskMax)
        val taskOverflow = story.tasks.size - visibleTasks.size
        visibleTasks.forEach { task ->
            items.addItem(
                buildTaskRow(task) {
                    selectedTask = task
                    invalidate()
                },
            )
        }
        if (taskOverflow > 0) {
            items.addItem(
                Row.Builder()
                    .setTitle("… $taskOverflow more tasks")
                    .addText("Showing top ${visibleTasks.size}")
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setTitle(story.title.take(38).ifBlank { "Story" })
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    // ---- Task detail mode ----

    private fun buildTaskDetailTemplate(task: PrdTaskDto): ListTemplate {
        val taskTitle = task.task.take(MAX_TASK_TITLE).ifBlank { "Task" }
        val limit = listLimit()
        val items = ItemList.Builder()
        var rowCount = 0

        if (task.status == "failed" && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("⟳ Requeue task")
                    .addText("Tap to requeue this failed task")
                    .setOnClickListener { fireRequeue(task) }
                    .build(),
            )
            rowCount++
        }
        if (task.status in setOf("pending", "in_progress") && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("✗ Cancel task")
                    .addText("Tap to cancel this task")
                    .setOnClickListener { fireCancelTask(task) }
                    .build(),
            )
            rowCount++
        }

        if (rowCount < limit) {
            val statusLine = buildString {
                append("Status: ${task.status.replace('_', ' ')}")
                if (task.retryCount > 0) append("  ·  Retries: ${task.retryCount}")
            }
            val row = Row.Builder().setTitle(CarText.create(statusLine))
            task.spec.takeIf { it.isNotBlank() }?.let { row.addText(it.take(MAX_SPEC_CHARS)) }
            task.error?.takeIf { it.isNotBlank() && task.status == "failed" }
                ?.let { row.addText("Error: ${it.take(MAX_ERROR_SHORT)}") }
            items.addItem(row.build())
            rowCount++
        }

        task.verification?.let { v ->
            if (rowCount < limit) {
                val verTitle = buildString {
                    append("Verification")
                    v.severity?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                }
                val verText = v.summary?.take(MAX_VERIF_CHARS)?.takeIf { it.isNotBlank() }
                val issueText = v.issues.take(3).joinToString("  ·  ") { it.take(40) }
                val row = Row.Builder().setTitle(verTitle)
                if (verText != null) row.addText(verText)
                if (issueText.isNotBlank()) row.addText(issueText)
                items.addItem(row.build())
                rowCount++
            }
        }

        if (task.filesTouched.isNotEmpty() && rowCount < limit) {
            val fileTitle = "${task.filesTouched.size} file${if (task.filesTouched.size == 1) "" else "s"} touched"
            val fileList = task.filesTouched.take(3).joinToString("  ") { it.substringAfterLast('/') }
            val more = (task.filesTouched.size - 3).takeIf { it > 0 }
            items.addItem(
                Row.Builder()
                    .setTitle(fileTitle)
                    .addText(fileList + (more?.let { " … +$it" } ?: ""))
                    .build(),
            )
            rowCount++
        }

        return ListTemplate.Builder()
            .setTitle(taskTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    private fun fireRequeue(task: PrdTaskDto) {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).requeuePrdTask(prdId, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task requeued", CarToast.LENGTH_SHORT).show()
                        selectedTask = null
                        invalidate()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Requeue failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun fireCancelTask(task: PrdTaskDto) {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).cancelPrdTask(prdId, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task cancelled", CarToast.LENGTH_SHORT).show()
                        selectedTask = null
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

    // ---- Helpers ----

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
        const val POLL_MS = 15_000L
        const val MAX_TITLE = 30
        const val MAX_STORY_TITLE = 36
        const val MAX_STORY_TITLE_DETAIL = 50
        const val MAX_TASK_TITLE = 52
        const val MAX_TASK_CHARS = 55
        const val MAX_SPEC_CHARS = 200
        const val MAX_ERROR_SHORT = 120
        const val MAX_VERIF_CHARS = 150
        const val MAX_DESC_CHARS = 70
        const val MAX_ROWS_FALLBACK = 5

        val DONE_STATUSES = setOf("complete", "completed", "done")

        fun storyMarker(status: String): String = when (status.lowercase()) {
            "complete", "completed", "done" -> "✓"
            "running", "active", "in_progress" -> "◉"
            "failed", "error" -> "✗"
            "needs_review", "awaiting_review", "awaiting_approval" -> "⚠"
            else -> "○"
        }

        fun taskMarker(status: String): String = when (status.lowercase()) {
            "complete", "completed", "done" -> "✓"
            "in_progress" -> "◉"
            "failed", "error" -> "✗"
            else -> "○"
        }

        private const val MAX_SPEC_CHARS_DETAIL = 200
        private const val MAX_PENDING_PREVIEW = 4

        fun buildDetailBody(prd: PrdDto): String = buildString {
            appendLine("[datawatch]: ${prd.status}")
            appendLine()

            val stories = prd.stories
            if (stories.isNotEmpty()) {
                val total = stories.size
                val done = stories.count { it.status.lowercase() in DONE_STATUSES }
                val pct = (done * 100 / total)
                appendLine("$done/$total stories done  ($pct%)")
                appendLine()
            }

            val spec = prd.spec
            if (!spec.isNullOrBlank()) {
                val snippet = if (spec.length > MAX_SPEC_CHARS_DETAIL) spec.take(MAX_SPEC_CHARS_DETAIL) + "…" else spec
                appendLine("[You]: $snippet")
                appendLine()
            }

            val active = stories.firstOrNull {
                it.status.lowercase() in setOf("in_progress", "running", "active", "awaiting_approval", "needs_review", "awaiting_review")
            }
            if (active != null) {
                val prefix = when (active.status.lowercase()) {
                    "awaiting_approval", "needs_review", "awaiting_review" -> "⚠"
                    else -> "◉"
                }
                appendLine("$prefix Active: ${active.title}")

                if (active.status.lowercase() in setOf("awaiting_approval", "needs_review", "awaiting_review")) {
                    appendLine("Awaiting your approval")
                } else {
                    val tasks = active.tasks
                    val taskDone = tasks.count { it.status.lowercase() in DONE_STATUSES }
                    val failed = tasks.count { it.status.lowercase() in setOf("failed", "error") }
                    val running = tasks.firstOrNull { it.status.lowercase() == "in_progress" }
                    if (running != null) appendLine("▶ ${running.task}")
                    if (tasks.isNotEmpty()) append("$taskDone/${tasks.size} done")
                    if (failed > 0) append("  ·  $failed failed")
                    if (tasks.isNotEmpty() || failed > 0) appendLine()
                }
                appendLine()
            }

            val pending = stories.filter { it.status.lowercase() == "pending" }
            if (pending.isNotEmpty()) {
                appendLine("Up next:")
                pending.take(MAX_PENDING_PREVIEW).forEach { appendLine("○ ${it.title}") }
                val overflow = pending.size - MAX_PENDING_PREVIEW
                if (overflow > 0) appendLine("… $overflow more")
                appendLine()
            }

            val decisions = prd.decisions
            if (!decisions.isNullOrEmpty()) {
                val last = decisions.last()
                val kind = last.kind ?: "decision"
                val actor = last.actor ?: "unknown"
                val note = last.note?.let { ": $it" } ?: ""
                appendLine("Last $kind ($actor)$note")
            }
        }.trimEnd()
    }
}
