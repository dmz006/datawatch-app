@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
 * PRD detail screen — MESSAGING-category-safe read-only reader.
 *
 * Samsung gearhead rejects ListTemplate in MESSAGING category while driving and also
 * rejects any push of a ListTemplate from a MessageTemplate-based screen. This screen
 * uses MessageTemplate exclusively so it can be reached from any MESSAGING-path parent.
 *
 * Navigation is index-based; no new screen pushes occur here:
 *   - storyIndex == -1  →  PRD overview (status + story list as body text)
 *   - storyIndex >= 0   →  story detail (story + task list as body text)
 *
 * ActionStrip (always 2 icon-only actions — Samsung MESSAGING cap):
 *   Slot 1: speaker (TTS reads current body)
 *   Slot 2: sessions (advance — overview→story0, storyN→storyN+1, last→overview)
 *
 * BACK header action pops the screen back to the automata list.
 * The close icon in story mode returns to overview (storyIndex = -1).
 *
 * No action buttons (approve/reject/run) — stripped for driving compliance.
 * Re-add action buttons only after in-car browsing is verified stable.
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

    /** -1 = PRD overview; 0..N-1 = story at that index. */
    private var storyIndex: Int = -1

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
            // If stories shrink below current index, reset to overview.
            val stories = prd?.stories ?: emptyList()
            if (storyIndex >= stories.size) storyIndex = -1
            val newHash = prd.hashCode() xor (error?.hashCode() ?: 0) xor storyIndex
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

    // ── Template building ──────────────────────────────────────────────────

    override fun onGetTemplate(): Template = try {
        val stories = prd?.stories ?: emptyList()
        val title = when {
            isLoading -> "Loading…"
            storyIndex in stories.indices ->
                "[${storyIndex + 1}/${stories.size}] ${stories[storyIndex].title.take(MAX_TITLE)}"
            else ->
                prd?.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE) ?: "Plan"
        }
        MessageTemplate.Builder(buildBody())
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(buildActionStrip())
            .build()
    } catch (e: Throwable) {
        // Fallback — always MessageTemplate so MESSAGING host accepts it.
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        MessageTemplate.Builder("Error: ${e.message ?: e::class.simpleName ?: "Unknown error"}")
            .setTitle("Plan")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {}.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    /**
     * 2-icon ActionStrip — MESSAGING driving cap.
     *
     * Overview mode: speaker (TTS) + sessions (enter story 0)
     * Story mode:    speaker (TTS) + sessions (next story; last → back to overview)
     */
    private fun buildActionStrip(): ActionStrip {
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val sessionsIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_sessions)).build()
        val stories = prd?.stories ?: emptyList()
        return ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(speakerIcon)
                    .setOnClickListener { AutoTts.speak(carContext, buildBody()) }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(sessionsIcon)
                    .setOnClickListener {
                        storyIndex = when {
                            stories.isEmpty() -> -1
                            storyIndex < 0 -> 0
                            storyIndex < stories.size - 1 -> storyIndex + 1
                            else -> -1 // past last story → back to overview
                        }
                        invalidate()
                    }
                    .build(),
            )
            .build()
    }

    // ── Body text builders ─────────────────────────────────────────────────

    private fun buildBody(): String = when {
        isLoading -> "Loading plan…"
        error != null -> "Error: $error"
        prd == null -> "Plan not found."
        storyIndex in (prd?.stories ?: emptyList()).indices -> buildStoryBody()
        else -> buildOverviewBody()
    }

    private fun buildOverviewBody(): String {
        val p = prd ?: return "No data."
        val stories = p.stories
        return buildString {
            append("Status: ${p.status}")
            p.spec?.takeIf { it.isNotBlank() }?.let { append("\n\n${it.take(MAX_SPEC_CHARS)}") }
            if (stories.isEmpty()) {
                append("\n\nNo stories yet.")
            } else {
                append("\n\nStories (${stories.size}):\n")
                stories.take(MAX_STORY_LIST).forEachIndexed { i, s ->
                    val m = storyMarker(s.status)
                    val done = s.tasks.count { it.status.lowercase() in DONE_STATUSES }
                    val total = s.tasks.size
                    val taskInfo = if (total > 0) " ($done/$total)" else ""
                    append("$m ${i + 1}. ${s.title.take(MAX_STORY_TITLE)}$taskInfo\n")
                }
                if (stories.size > MAX_STORY_LIST) append("… ${stories.size - MAX_STORY_LIST} more\n")
                append("\nTap sessions ▶ to read story 1")
            }
        }
    }

    private fun buildStoryBody(): String {
        val stories = prd?.stories ?: return "No data."
        val story = stories.getOrNull(storyIndex) ?: return "Story not found."
        val tasks = story.tasks
        val hasNext = storyIndex < stories.size - 1
        return buildString {
            val done = tasks.count { it.status.lowercase() in DONE_STATUSES }
            val running = tasks.count { it.status.lowercase() in setOf("in_progress", "running", "active") }
            val failed = tasks.count { it.status.lowercase() == "failed" }
            append("Status: ${story.status}")
            if (tasks.isNotEmpty()) {
                append("  ·  $done/${tasks.size} tasks")
                if (running > 0) append("  ·  $running running")
                if (failed > 0) append("  ·  $failed failed")
            }
            story.description?.takeIf { it.isNotBlank() }?.let {
                append("\n\n${it.take(MAX_DESC_CHARS)}")
            }
            if (tasks.isNotEmpty()) {
                append("\n\nTasks:\n")
                tasks.take(MAX_TASK_LIST).forEach { t ->
                    val m = taskMarker(t.status)
                    append("$m ${t.task.take(MAX_TASK_CHARS)}\n")
                }
                if (tasks.size > MAX_TASK_LIST) append("… ${tasks.size - MAX_TASK_LIST} more tasks\n")
            }
            append("\nTap sessions ▶ ${if (hasNext) "→ story ${storyIndex + 2}" else "→ back to overview"}")
            append("  ·  ← back")
        }
    }

    internal companion object {
        const val POLL_MS = 15_000L
        const val MAX_TITLE = 30
        const val MAX_STORY_TITLE = 36
        const val MAX_TASK_CHARS = 55
        const val MAX_DESC_CHARS = 300
        const val MAX_SPEC_CHARS = 200
        const val MAX_STORY_LIST = 12
        const val MAX_TASK_LIST = 15

        private val DONE_STATUSES = setOf("complete", "completed", "done")

        fun storyMarker(status: String): String = when (status.lowercase()) {
            "complete", "completed", "done" -> "✓"
            "running", "active", "in_progress" -> "◉"
            "failed", "error" -> "✗"
            "needs_review", "awaiting_review" -> "?"
            else -> "○"
        }

        fun taskMarker(status: String): String = when (status.lowercase()) {
            "complete", "completed", "done" -> "✓"
            "in_progress" -> "◉"
            "failed", "error" -> "✗"
            "verifying", "running_tests" -> "⟳"
            "blocked" -> "⛔"
            else -> "○"
        }

        /** Legacy helper used by [AutoPrdStoriesScreen] — returns body text for a story. */
        fun buildStoryBody(story: PrdStoryDto): String {
            val tasks = story.tasks
            return buildString {
                append("${story.status}")
                if (tasks.isNotEmpty()) {
                    val done = tasks.count { it.status.lowercase() in DONE_STATUSES }
                    append("  ·  $done/${tasks.size} tasks")
                }
                story.description?.takeIf { it.isNotBlank() }?.let { append("\n\n${it.take(400)}") }
                if (tasks.isNotEmpty()) {
                    append("\n\nTasks:\n")
                    tasks.forEach { t -> append("${taskMarker(t.status)} ${t.task.take(MAX_TASK_CHARS)}\n") }
                }
            }
        }
    }
}
