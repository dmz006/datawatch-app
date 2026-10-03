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
 * Automata hub — PRD list, flat story+task view, and task detail in one screen (depth 2).
 *
 * Samsung MESSAGING counts steps from the ROOT screen, not just from this push:
 *   step 1 — AutoSummaryScreen (root)
 *   step 2 — Push AutoAutomataScreen
 *   step 3 — PRD tap → invalidate() (flat story+task view)
 *   step 4 — Task tap → invalidate() (task detail)  ← ListTemplate allowed through step 4
 *   step 5 would be blocked for ListTemplate
 *
 * The former two-level story navigation (story list → story tap → story detail → task tap →
 * task detail) placed task-tap at step 5 and was blocked. Flattening to one level (PRD tap →
 * flat story headers + task rows → task tap → task detail) keeps task-tap at step 4.
 *
 * Story section headers are non-tappable visual dividers. Task rows are directly tappable.
 *
 * Navigation state:
 *   selectedPrd == null               → PRD list
 *   selectedPrd != null               → flat story+task view (no intermediate story level)
 *   selectedPrd != null,
 *     selectedTask != null            → task detail
 */
public class AutoAutomataScreen(
    carContext: CarContext,
    seedPrds: List<PrdDto>? = null,
) : Screen(carContext) {
    // PRD list state — seeded from AutoSummaryScreen's cached list to avoid an init invalidate()
    private var automata: List<PrdDto> = seedPrds
        ?.filter { it.status.lowercase() !in TERMINAL_STATUSES }
        ?.sortedWith(automataComparator)
        ?: emptyList()
    private var serverName: String = "datawatch"
    private var error: String? = null
    private var isLoading: Boolean = seedPrds == null
    private var historyOn: Boolean = false
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Initialize hash from seed data so the first pollLoop iteration doesn't fire a spurious invalidate
    private var lastHash: Int = if (seedPrds != null) automata.hashCode() else -1

    // In-place PRD/story/task selection (all at depth 2 — no screenManager.push())
    private var selectedPrd: PrdDto? = null
    private var selectedStory: PrdStoryDto? = null
    private var selectedTask: PrdTaskDto? = null

    // Explicit loading/error state for PRD detail so the screen always updates
    // rather than silently staying on "Loading stories…" if getPrd() fails.
    private var prdDetailLoading: Boolean = false
    private var prdDetailError: String? = null

    init {
        // Only fetch on init when no seed data was provided.
        // With seed data the first onGetTemplate() already shows the PRD list,
        // so this invalidate() is unnecessary and would waste a Samsung MESSAGING step.
        if (seedPrds == null) {
            scope.launch { refresh(); invalidate() }
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
            val prd = selectedPrd
            if (prd != null) {
                refreshSelectedPrd(prd.id)
                // Never invalidate() from the background poll while inside a PRD.
                // Each invalidate() consumes one Samsung MESSAGING template step.
                // Step budget from root: step 2 = push this screen, step 3 = PRD tap
                // (flat view), step 4 = task tap (task detail). A background poll
                // invalidate would consume step 4, pushing task tap to step 5 (blocked).
                // Data is refreshed silently; the user sees the updated state when they
                // navigate back to the PRD list or tap in/out of the flat view.
            } else {
                refresh()
                val newHash = automata.hashCode() xor (error?.hashCode() ?: 0)
                if (newHash != lastHash) {
                    lastHash = newHash
                    invalidate()
                }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profile =
                resolveActiveProfile() ?: run {
                    error = "No enabled server"
                    automata = emptyList()
                    return
                }
            serverName = profile.displayName
            AutoServiceLocator.transportFor(profile).listPrds().fold(
                onSuccess = { dto ->
                    error = null
                    automata =
                        dto.prds
                            .filter { prd -> historyOn || prd.status.lowercase() !in TERMINAL_STATUSES }
                            .sortedWith(automataComparator)
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        } finally {
            isLoading = false
        }
    }

    private suspend fun refreshSelectedPrd(prdId: String) {
        try {
            val profile = resolveActiveProfile() ?: run {
                prdDetailError = "No enabled server"
                prdDetailLoading = false
                return
            }
            AutoServiceLocator.transportFor(profile).getPrd(prdId).fold(
                onSuccess = { dto ->
                    selectedPrd = dto
                    selectedStory = selectedStory?.let { ss -> dto.stories.find { it.id == ss.id } ?: ss }
                    selectedTask = selectedTask?.let { st -> selectedStory?.tasks?.find { it.id == st.id } ?: st }
                    prdDetailError = null
                    prdDetailLoading = false
                },
                onFailure = { err ->
                    prdDetailError = err.message ?: err::class.simpleName ?: "Error"
                    prdDetailLoading = false
                },
            )
        } catch (e: Throwable) {
            prdDetailError = e.message ?: e::class.simpleName ?: "Error"
            prdDetailLoading = false
        }
    }

    // ---- ActionStrip ----

    private fun buildActionStrip(): ActionStrip {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val filterIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_filter)).build()

        return when {
            selectedPrd != null -> {
                // Navigating within a PRD: speaker (TTS) + close (in-place back)
                val task = selectedTask
                val story = selectedStory
                val p = selectedPrd
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
                    else -> "Loading."
                }
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                        AutoTts.speak(carContext, ttsText)
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener {
                        // Story level removed — task detail closes back to flat story+task view,
                        // flat view closes back to PRD list.
                        when {
                            selectedTask != null -> { selectedTask = null; invalidate() }
                            else -> { selectedPrd = null; selectedStory = null; invalidate() }
                        }
                    }.build())
                    .build()
            }
            else -> {
                // PRD list: filter toggle + back-to-summary
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(filterIcon).setOnClickListener {
                        historyOn = !historyOn
                        isLoading = true
                        invalidate()
                        scope.launch { refresh(); invalidate() }
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener {
                        screenManager.pop()
                    }.build())
                    .build()
            }
        }
    }

    // ---- Template dispatch ----

    override fun onGetTemplate(): Template = try {
        when {
            selectedTask != null -> buildTaskDetailTemplate(selectedTask!!)
            selectedPrd != null -> buildFlatStoryTaskTemplate(selectedPrd!!)
            else -> buildPrdListTemplate()
        }
    } catch (e: Throwable) {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val errItems = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: "Unknown").build())
            .build()
        ListTemplate.Builder()
            .setTitle("$serverName Automata")
            .setHeaderAction(Action.BACK)
            .setSingleList(errItems)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                        AutoTts.speak(carContext, e.message ?: "Error")
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    // ---- PRD list ----

    private fun buildPrdListTemplate(): ListTemplate {
        val builder = ItemList.Builder()

        if (isLoading) {
            builder.addItem(
                Row.Builder()
                    .setTitle("Loading…")
                    .addText("Fetching automata from $serverName")
                    .build(),
            )
        } else if (automata.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle(if (error != null) "Error" else "No automata")
                    .addText(error ?: "No active automata on $serverName.")
                    .build(),
            )
            if (error == null && !historyOn) {
                builder.addItem(
                    Row.Builder()
                        .setTitle("Show completed automata")
                        .addText("Tap to include completed and past runs")
                        .setOnClickListener {
                            historyOn = true
                            isLoading = true
                            invalidate()
                            scope.launch { refresh(); invalidate() }
                        }
                        .build(),
                )
            }
            builder.addItem(
                Row.Builder()
                    .setTitle("⊕ New Automata")
                    .addText("Use your phone to create automata")
                    .setOnClickListener {
                        CarToast.makeText(
                            carContext,
                            "Open the phone app to create automata",
                            CarToast.LENGTH_LONG,
                        ).show()
                    }
                    .build(),
            )
        } else {
            val max =
                runCatching {
                    carContext.getCarService(ConstraintManager::class.java)
                        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
                }.getOrElse { MAX_ROWS_FALLBACK }
            val visible = automata.take((max - 1).coerceAtLeast(1))
            val overflow = automata.size - visible.size
            visible.forEach { prd ->
                val storyPos = activeStoryPosition(prd)
                val subtitle = buildSubtitle(prd, storyPos)
                val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
                val isActive = prd.status == "running" || prd.status == "active"
                val isTerminal = prd.status in setOf("killed", "completed", "complete", "cancelled", "rejected", "error")
                val isReview = prd.status in setOf("needs_review", "awaiting_review", "revisions_asked")
                val dotResId =
                    when {
                        hasBlock || isReview -> R.drawable.ic_dot_red
                        isActive -> R.drawable.ic_dot_green
                        isTerminal -> R.drawable.ic_dot_gray
                        else -> R.drawable.ic_dot_gray
                    }
                val titleColor =
                    when {
                        hasBlock || isReview -> CarColor.RED
                        isActive -> CarColor.GREEN
                        else -> CarColor.DEFAULT
                    }
                val dotIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, dotResId)).build()
                builder.addItem(
                    Row.Builder()
                        .setTitle(colored(prd.title?.takeIf { it.isNotBlank() } ?: prd.name.ifBlank { prd.id }, titleColor))
                        .setImage(dotIcon)
                        .addText(subtitle)
                        .setOnClickListener {
                            selectedPrd = prd
                            selectedStory = null
                            selectedTask = null
                            prdDetailLoading = true
                            prdDetailError = null
                            // Do NOT invalidate here before data loads — each click-handler
                            // invalidate() consumes a Samsung MESSAGING template step.
                            // Skipping the loading-state render means story-tap is step 3
                            // and task-tap is step 4, both within the 5-step limit.
                            scope.launch { refreshSelectedPrd(prd.id); invalidate() }
                        }
                        .build(),
                )
            }
            if (overflow > 0) {
                builder.addItem(
                    Row.Builder()
                        .setTitle("… $overflow more automata")
                        .addText("Showing top ${visible.size} by activity")
                        .build(),
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("$serverName Automata")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    // ---- Flat story+task view ----
    //
    // Shows story section headers (non-tappable) with their task rows directly below them.
    // Eliminates the former story-tap step so task-tap stays at step 4 (within Samsung budget).

    private fun buildFlatStoryTaskTemplate(prd: PrdDto): ListTemplate {
        val prdTitle = prd.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE) ?: "Plan"
        val items = ItemList.Builder()

        when {
            prdDetailLoading -> items.addItem(
                Row.Builder().setTitle("Loading…").addText("Fetching plan detail").build(),
            )
            prdDetailError != null -> items.addItem(
                Row.Builder().setTitle("Error").addText(prdDetailError ?: "Unknown error").build(),
            )
            prd.stories.isEmpty() -> items.addItem(
                Row.Builder().setTitle("No stories").addText("This plan has no stories yet").build(),
            )
            else -> {
                val limit = listLimit()
                // Build a flat sequence: story-header row, then task rows, repeat per story.
                // Reserve 1 slot for an overflow indicator.
                val maxContent = (limit - 1).coerceAtLeast(1)
                var rowsAdded = 0
                var overflow = 0
                outer@ for (story in prd.stories) {
                    // Story section header — not tappable, serves as a visual divider.
                    if (rowsAdded >= maxContent) { overflow++; continue }
                    val marker = storyMarker(story.status)
                    val taskDone = story.tasks.count { it.status.lowercase() in DONE_STATUSES }
                    val storyColor = when (story.status.lowercase()) {
                        "awaiting_approval", "needs_review" -> CarColor.RED
                        "in_progress", "running", "active" -> CarColor.GREEN
                        else -> CarColor.DEFAULT
                    }
                    val ttsStoryText = buildString {
                        append("${story.title.ifBlank { "Story" }}. Status: ${story.status.replace('_', ' ')}.")
                        append(" $taskDone of ${story.tasks.size} tasks done.")
                        story.tasks.take(3).forEach { t -> append(" ${t.task.take(60)}: ${t.status.replace('_', ' ')}.") }
                    }
                    items.addItem(
                        Row.Builder()
                            .setTitle(colored("$marker ${story.title.take(MAX_STORY_TITLE)}", storyColor))
                            .addText("${story.status}  ·  $taskDone/${story.tasks.size} tasks  ·  tap to hear")
                            .setOnClickListener { AutoTts.speak(carContext, ttsStoryText) }
                            .build(),
                    )
                    rowsAdded++
                    // Task rows under this story — tappable.
                    for (task in story.tasks) {
                        if (rowsAdded >= maxContent) { overflow += story.tasks.size - story.tasks.indexOf(task); continue@outer }
                        items.addItem(buildTaskRow(task) { selectedStory = story; selectedTask = task; invalidate() })
                        rowsAdded++
                    }
                }
                if (overflow > 0) {
                    items.addItem(
                        Row.Builder()
                            .setTitle("… $overflow more")
                            .addText("Showing top $rowsAdded of ${prd.stories.sumOf { 1 + it.tasks.size }}")
                            .build(),
                    )
                }
            }
        }

        // Samsung Gearhead requires a header action on every non-root template.
        // Action.BACK would pop AutoAutomataScreen entirely; use a custom back
        // arrow that navigates in-place back to the PRD list instead.
        val backIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_back)).build()
        val inPlaceBack = Action.Builder()
            .setIcon(backIcon)
            .setOnClickListener { selectedPrd = null; selectedStory = null; invalidate() }
            .build()
        return ListTemplate.Builder()
            .setTitle(prdTitle)
            .setHeaderAction(inPlaceBack)
            .setSingleList(items.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    // ---- Task detail ----

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

        val backIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_back)).build()
        val inPlaceBack = Action.Builder()
            .setIcon(backIcon)
            .setOnClickListener { selectedTask = null; invalidate() }
            .build()
        return ListTemplate.Builder()
            .setTitle(taskTitle)
            .setHeaderAction(inPlaceBack)
            .setSingleList(items.build())
            .setActionStrip(buildActionStrip())
            .build()
    }

    // ---- Task actions ----

    private fun fireRequeue(task: PrdTaskDto) {
        val prdId = selectedPrd?.id ?: return
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
        val prdId = selectedPrd?.id ?: return
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

    // ---- Row builders ----

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
            .setTitle(colored("  ↳ $marker ${task.task.take(MAX_TASK_CHARS)}", color))
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

    private fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrElse { MAX_ROWS_FALLBACK }

    // ---- Companions ----

    private companion object {
        const val POLL_MS: Long = 15_000L
        const val MAX_ROWS_FALLBACK: Int = 5
        const val PROGRESS_BAR_WIDTH: Int = 8

        // PRD/story/task display limits (mirrors AutoPrdDetailScreen)
        const val MAX_TITLE: Int = 30
        const val MAX_STORY_TITLE: Int = 36
        const val MAX_STORY_TITLE_DETAIL: Int = 50
        const val MAX_TASK_TITLE: Int = 52
        const val MAX_TASK_CHARS: Int = 55
        const val MAX_SPEC_CHARS: Int = 200
        const val MAX_ERROR_SHORT: Int = 120
        const val MAX_VERIF_CHARS: Int = 150
        const val MAX_DESC_CHARS: Int = 70

        val DONE_STATUSES = setOf("complete", "completed", "done")
        val TERMINAL_STATUSES = setOf("killed", "completed", "complete", "cancelled", "canceled", "rejected", "error")

        val automataComparator: Comparator<PrdDto> =
            compareByDescending { prd ->
                val blockedStories = prd.stories.count { it.status == "awaiting_approval" }
                blockedStories * 10 + prd.depth
            }

        fun storyMarker(status: String): String = when (status.lowercase()) {
            "complete", "completed", "done" -> "✓"
            "running", "active", "in_progress" -> "◉"
            "failed", "error" -> "✗"
            "needs_review", "awaiting_review", "awaiting_approval" -> "⚠"
            else -> "○"
        }

        fun activeStoryPosition(prd: PrdDto): Int? {
            val idx =
                prd.stories.indexOfFirst {
                    it.status == "in_progress" || it.status == "awaiting_approval"
                }
            return if (idx >= 0) idx + 1 else null
        }

        fun progressBar(completedStories: Int, totalStories: Int): String {
            val pct = if (totalStories > 0) (completedStories * 100) / totalStories else 0
            val filled = (pct * PROGRESS_BAR_WIDTH / 100).coerceIn(0, PROGRESS_BAR_WIDTH)
            return "▓".repeat(filled) + "░".repeat(PROGRESS_BAR_WIDTH - filled) + " $pct%"
        }

        fun buildSubtitle(prd: PrdDto, storyPos: Int?): String =
            buildString {
                val totalStories = prd.stories.size
                val completedStories =
                    prd.stories.count { it.status.lowercase() in setOf("complete", "completed", "done") }
                val progressNumerator = storyPos ?: completedStories
                val bar = if (totalStories > 0) progressBar(progressNumerator, totalStories) else ""
                val isActive = prd.status == "running" || prd.status == "active"
                if (!isActive) {
                    append("[${prd.status.ifBlank { "idle" }}]  ")
                }
                if (storyPos != null && totalStories > 0) {
                    append("$bar  Story $storyPos/$totalStories")
                } else if (totalStories > 0) {
                    append("$bar  $completedStories/$totalStories done")
                } else {
                    append(prd.status.ifBlank { "no stories" })
                }
                val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
                if (hasBlock) append(" ⚠ review")
            }
    }
}
