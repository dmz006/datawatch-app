package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the story stages for a PRD/automaton with Approve/Reject/Stop actions.
 *
 * Used from [AutoSessionDetailScreen] when the session's telemetry indicates it belongs
 * to an automaton (telemetry.sprint.automataId is non-blank). Each story is listed with
 * a status marker; the primary buttons perform PRD-level actions.
 *
 * Navigation depth: this screen is always pushed from SessionDetail, so it may be at
 * depth 5 (the Car App Library max). All actions here pop rather than push.
 */
public class AutoPrdStagesScreen(
    carContext: CarContext,
    private val prdId: String,
    private val prdName: String,
) : Screen(carContext) {

    private var prdStatus: String = ""
    private var stories: List<PrdStoryDto> = emptyList()
    private var isLoading: Boolean = true
    private var error: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        scope.launch { loadPrd(); invalidate() }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
        })
    }

    private suspend fun loadPrd() {
        try {
            val profile = resolveActiveProfile() ?: run { error = "No enabled server"; return }
            val result = AutoServiceLocator.transportFor(profile).listPrds().getOrNull()
            val prd = result?.prds?.firstOrNull { it.id == prdId }
            if (prd != null) {
                prdStatus = prd.status
                stories = prd.stories
            } else {
                error = "Plan not found"
            }
        } catch (e: Throwable) {
            error = e.message ?: "Unknown error"
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
                        CarToast.makeText(carContext, "${action.replaceFirstChar { it.uppercase() }} sent", CarToast.LENGTH_SHORT).show()
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

    override fun onGetTemplate(): Template {
        val body = when {
            isLoading -> "Loading plan stages…"
            error != null -> "Error: $error"
            stories.isEmpty() -> "No stages configured for this plan.\n\nStatus: $prdStatus"
            else -> buildString {
                appendLine("Status: $prdStatus\n")
                stories.forEach { s ->
                    val marker = when (s.status) {
                        "complete" -> "✓"
                        "in_progress" -> "◉"
                        "awaiting_approval" -> "⚠"
                        "rejected" -> "✗"
                        else -> "○"
                    }
                    appendLine("$marker ${s.title.take(MAX_STORY_TITLE)}")
                }
            }.trimEnd()
        }

        val statusLower = prdStatus.lowercase()
        val isReview = statusLower in setOf("needs_review", "revisions_asked", "awaiting_review")
        val isRunning = statusLower == "running" || statusLower == "active"

        val builder = MessageTemplate.Builder(body)
            .setTitle(prdName.take(MAX_TITLE_CHARS))
            .setHeaderAction(Action.BACK)

        if (!isLoading && error == null) {
            when {
                isReview -> {
                    builder.addAction(
                        Action.Builder()
                            .setTitle("Approve")
                            .setBackgroundColor(CarColor.GREEN)
                            .setOnClickListener { fire("approve") }
                            .build(),
                    )
                    builder.addAction(
                        Action.Builder()
                            .setTitle("Reject")
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener { fire("reject") }
                            .build(),
                    )
                }
                isRunning -> {
                    builder.addAction(
                        Action.Builder()
                            .setTitle("Stop Plan")
                            .setBackgroundColor(CarColor.RED)
                            .setOnClickListener { fire("cancel") }
                            .build(),
                    )
                }
            }
        }

        return builder.build()
    }

    private companion object {
        const val MAX_STORY_TITLE: Int = 45
        const val MAX_TITLE_CHARS: Int = 30
    }
}
