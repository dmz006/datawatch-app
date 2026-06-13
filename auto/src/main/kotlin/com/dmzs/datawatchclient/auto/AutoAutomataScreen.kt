@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * BL303-A3 — Running automata overview for Android Auto.
 *
 * Lists all running PRDs/automata sorted by depth (deepest first — most
 * granular work at top). Shows story/task position and progress arc text.
 * Tap → [AutoSessionListScreen] filtered to sessions for that automaton.
 */
public class AutoAutomataScreen(carContext: CarContext) : Screen(carContext) {

    private var automata: List<PrdDto> = emptyList()
    private var serverName: String = "datawatch"
    private var error: String? = null
    private var isLoading: Boolean = true
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // §15: track snapshot hash to skip redundant invalidate() calls.
    private var lastHash: Int = -1

    init {
        // Eager fetch so the first onGetTemplate() render has real data.
        scope.launch { refresh(); invalidate() }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
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
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            // §15: only invalidate when the automata list actually changed.
            val newHash = automata.hashCode() xor (error?.hashCode() ?: 0)
            if (newHash != lastHash) {
                lastHash = newHash
                invalidate()
            }
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profile = resolveActiveProfile() ?: run {
                error = "No enabled server"
                automata = emptyList()
                return
            }
            serverName = profile.displayName
            AutoServiceLocator.transportFor(profile).listPrds().fold(
                onSuccess = { dto ->
                    error = null
                    // Show all automata regardless of status; blocked/active sort first.
                    automata = dto.prds.sortedWith(automataComparator)
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

    override fun onGetTemplate(): Template {
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
                    .addText(error ?: "No automata configured on $serverName.")
                    .build(),
            )
            builder.addItem(
                Row.Builder()
                    .setTitle("⊕ New Automata")
                    .addText("Use your phone to create automata")
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Open the phone app to create automata", CarToast.LENGTH_LONG).show()
                    }
                    .build(),
            )
        } else {
            val max = runCatching {
                carContext.getCarService(ConstraintManager::class.java)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            }.getOrElse { MAX_ROWS_FALLBACK }
            // Reserve 1 slot for overflow row and 1 for "New Automata" row.
            val visible = automata.take((max - 2).coerceAtLeast(1))
            val overflow = automata.size - visible.size
            visible.forEach { prd ->
                val storyPos = activeStoryPosition(prd)
                val subtitle = buildSubtitle(prd, storyPos)
                val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
                val isActive = prd.status == "running" || prd.status == "active"
                val isTerminal = prd.status in setOf("killed", "completed", "complete", "cancelled", "rejected", "error")
                val isReview = prd.status in setOf("needs_review", "awaiting_review", "revisions_asked")
                val dotResId = when {
                    hasBlock || isReview -> R.drawable.ic_dot_red
                    isActive -> R.drawable.ic_dot_green
                    isTerminal -> R.drawable.ic_dot_gray
                    else -> R.drawable.ic_dot_gray
                }
                val titleColor = when {
                    hasBlock || isReview -> CarColor.RED
                    isActive -> CarColor.GREEN
                    else -> CarColor.DEFAULT
                }
                val dotIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, dotResId)).build()
                builder.addItem(
                    Row.Builder()
                        .setTitle(colored(prd.name.ifBlank { prd.id }, titleColor))
                        .setImage(dotIcon)
                        .addText(subtitle)
                        .setOnClickListener {
                            when {
                                // Review/running/terminal → PrdActionScreen for approve/stop/delete
                                isReview || isTerminal ->
                                    screenManager.push(
                                        PrdActionScreen(
                                            carContext,
                                            prd.id,
                                            prd.name.ifBlank { prd.id },
                                            status = prd.status,
                                            automataName = prd.name.ifBlank { prd.id },
                                        ),
                                    )
                                // Running/active → PrdActionScreen (Stop + Sessions)
                                isActive ->
                                    screenManager.push(
                                        PrdActionScreen(
                                            carContext,
                                            prd.id,
                                            prd.name.ifBlank { prd.id },
                                            status = prd.status,
                                            automataName = prd.name.ifBlank { prd.id },
                                        ),
                                    )
                                // Idle/other → filtered session list
                                else ->
                                    screenManager.push(
                                        AutoSessionListScreen(carContext, automataId = prd.name.ifBlank { prd.id }),
                                    )
                            }
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
            // "New Automata" row — creating from the car requires the phone app
            builder.addItem(
                Row.Builder()
                    .setTitle("⊕ New Automata")
                    .addText("Use your phone to create automata")
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Open the phone app to create automata", CarToast.LENGTH_LONG).show()
                    }
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setTitle("$serverName Automata")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
        const val MAX_ROWS_FALLBACK: Int = 5
        const val PROGRESS_BAR_WIDTH: Int = 8

        val automataComparator: Comparator<PrdDto> = compareByDescending { prd ->
            // Gravitation: blocked stories float to top; deeper work (more depth) wins ties
            val blockedStories = prd.stories.count { it.status == "awaiting_approval" }
            blockedStories * 10 + prd.depth
        }

        fun activeStoryPosition(prd: PrdDto): Int? {
            val idx = prd.stories.indexOfFirst {
                it.status == "in_progress" || it.status == "awaiting_approval"
            }
            return if (idx >= 0) idx + 1 else null
        }

        fun progressBar(completedStories: Int, totalStories: Int): String {
            val pct = if (totalStories > 0) (completedStories * 100) / totalStories else 0
            val filled = (pct * PROGRESS_BAR_WIDTH / 100).coerceIn(0, PROGRESS_BAR_WIDTH)
            return "▓".repeat(filled) + "░".repeat(PROGRESS_BAR_WIDTH - filled) + " $pct%"
        }

        fun buildSubtitle(prd: PrdDto, storyPos: Int?): String = buildString {
            val totalStories = prd.stories.size
            val completedStories = prd.stories.count { it.status == "complete" }
            val bar = if (totalStories > 0) progressBar(completedStories, totalStories) else ""
            val isActive = prd.status == "running" || prd.status == "active"
            if (!isActive) {
                append("[${prd.status.ifBlank { "idle" }}]  ")
            }
            if (storyPos != null && totalStories > 0) {
                append("$bar  Story $storyPos/$totalStories")
            } else if (totalStories > 0) {
                append("$bar  $completedStories/$totalStories stories")
            } else {
                append(prd.status.ifBlank { "no stories" })
            }
            // Blocking flag
            val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
            if (hasBlock) append(" ⚠ awaiting approval")
        }
    }
}
