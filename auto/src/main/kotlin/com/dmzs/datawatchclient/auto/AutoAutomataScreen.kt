package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = scope.launch { pollLoop() }
            }

            override fun onStop(owner: LifecycleOwner) {
                pollJob?.cancel()
                pollJob = null
            }
        })
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            invalidate()
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
                    automata = dto.prds
                        .filter { it.status == "running" }
                        .sortedWith(automataComparator)
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()

        if (automata.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle(if (error != null) "Error" else "No automata running")
                    .addText(error ?: "All automata are idle on $serverName.")
                    .build(),
            )
        } else {
            automata.take(MAX_ROWS).forEach { prd ->
                val storyPos = activeStoryPosition(prd)
                val subtitle = buildSubtitle(prd, storyPos)
                builder.addItem(
                    Row.Builder()
                        .setTitle(colored(prd.name.ifBlank { prd.id }, CarColor.GREEN))
                        .addText(subtitle)
                        .setOnClickListener {
                            // Navigate to session list filtered by automaton id
                            screenManager.push(AutoSessionListScreen(carContext))
                        }
                        .build(),
                )
            }
            if (automata.size > MAX_ROWS) {
                builder.addItem(
                    Row.Builder()
                        .setTitle("… ${automata.size - MAX_ROWS} more automata")
                        .addText("Showing top $MAX_ROWS by activity")
                        .build(),
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("$serverName Automata")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
        const val MAX_ROWS: Int = 5

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

        fun buildSubtitle(prd: PrdDto, storyPos: Int?): String = buildString {
            val totalStories = prd.stories.size
            val completedStories = prd.stories.count { it.status == "complete" }
            if (storyPos != null && totalStories > 0) {
                append("Story $storyPos/$totalStories")
                val pct = (completedStories * 100) / totalStories
                append(" · $pct%")
            } else if (totalStories > 0) {
                val pct = (completedStories * 100) / totalStories
                append("$completedStories/$totalStories stories · $pct%")
            } else {
                append(prd.status)
            }
            // Blocking flag
            val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
            if (hasBlock) append(" ⚠ awaiting approval")
        }
    }
}
