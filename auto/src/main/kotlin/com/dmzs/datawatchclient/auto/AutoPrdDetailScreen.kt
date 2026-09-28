@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
 * PRD entry screen — ListTemplate showing selectable rows.
 *
 * Row 0: "Overview" — PRD status summary → [AutoPrdSummaryScreen] (MessageTemplate, depth 3)
 * Rows 1..N: Story rows → [AutoPrdStoriesScreen] opened to that story's detail (depth 3)
 *
 * Samsung MESSAGING category notes:
 *   - ListTemplate is safe here; it is pushed FROM AutoAutomataScreen (also ListTemplate).
 *   - The only child pushes are ListTemplate (AutoPrdStoriesScreen) and
 *     MessageTemplate (AutoPrdSummaryScreen) — both allowed from ListTemplate.
 *   - ActionStrip: 1 icon-only action (speaker TTS) — well within the 2-action cap.
 *   - Template type is always ListTemplate — no type change on invalidate().
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

    override fun onGetTemplate(): Template = try {
        val prdTitle = prd?.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE) ?: "Plan"
        val items = ItemList.Builder()
        when {
            isLoading -> items.addItem(Row.Builder().setTitle("Loading…").addText("Fetching plan…").build())
            error != null -> items.addItem(Row.Builder().setTitle("Error").addText(error ?: "").build())
            else -> buildRows(items)
        }
        val speakerIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)
        ).build()
        ListTemplate.Builder()
            .setTitle(prdTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(speakerIcon)
                            .setOnClickListener {
                                prd?.let { p ->
                                    val done = p.stories.count { it.status.lowercase() in DONE_STATUSES }
                                    AutoTts.speak(
                                        carContext,
                                        "${p.title}. Status: ${p.status}. $done of ${p.stories.size} stories done.",
                                    )
                                }
                            }
                            .build(),
                    )
                    .build(),
            )
            .build()
    } catch (e: Throwable) {
        val errItems = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Error").addText(e.message ?: "Unknown").build())
            .build()
        ListTemplate.Builder()
            .setTitle("Plan")
            .setHeaderAction(Action.BACK)
            .setSingleList(errItems)
            .build()
    }

    private fun buildRows(items: ItemList.Builder) {
        val p = prd ?: return
        val stories = p.stories
        val done = stories.count { it.status.lowercase() in DONE_STATUSES }

        // Overview row — selectable, shows PRD spec/description on tap.
        items.addItem(
            Row.Builder()
                .setTitle("Overview")
                .addText("${p.status}  ·  $done/${stories.size} done")
                .setOnClickListener { screenManager.push(AutoPrdSummaryScreen(carContext, p)) }
                .build(),
        )

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

        // Reserve 1 slot for overview and 1 for the overflow row.
        val visible = stories.take((listMax - 2).coerceAtLeast(1))
        val overflow = stories.size - visible.size

        visible.forEachIndexed { idx, story ->
            items.addItem(buildStoryRow(idx + 1, story, p))
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

    private fun buildStoryRow(num: Int, story: PrdStoryDto, prd: PrdDto): Row {
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
                // Open AutoPrdStoriesScreen directly to this story's detail view.
                screenManager.push(AutoPrdStoriesScreen(carContext, prd, story))
            }
            .build()
    }

    internal companion object {
        const val POLL_MS = 15_000L
        const val MAX_TITLE = 30
        const val MAX_STORY_TITLE = 36
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
    }
}
