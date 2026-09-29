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
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the story stages for a PRD/automaton — status display only while driving.
 *
 * Used from [AutoSessionDetailScreen] when the session's telemetry indicates it belongs
 * to an automaton (telemetry.sprint.automataId is non-blank). Each story is listed with
 * a status marker. Action hints (approve/reject/stop) are shown in the body text; the
 * actual actions require the phone app to avoid Samsung MESSAGING driving restrictions.
 *
 * Navigation depth: always pushed from SessionDetail (depth 3–4). All exits here pop.
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
        scope.launch {
            loadPrd()
            invalidate()
        }
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    private suspend fun loadPrd() {
        try {
            val profile =
                resolveActiveProfile() ?: run {
                    error = "No enabled server"
                    return
                }
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

    override fun onGetTemplate(): Template = try {
        val statusLower = prdStatus.lowercase()
        val isReview = statusLower in setOf("needs_review", "revisions_asked", "awaiting_review")
        val isRunning = statusLower == "running" || statusLower == "active"

        val body =
            when {
                isLoading -> "Loading plan stages…"
                error != null -> "Error: $error"
                stories.isEmpty() -> {
                    buildString {
                        appendLine("No stages configured for this plan.")
                        appendLine()
                        append("Status: $prdStatus")
                        if (isReview) append("\n\n⚠ Open app to approve or reject.")
                    }
                }
                else ->
                    buildString {
                        appendLine("Status: $prdStatus")
                        if (isReview) appendLine("⚠ Open app to approve or reject.")
                        else if (isRunning) appendLine("▶ Open app to stop the plan.")
                        appendLine()
                        stories.forEach { s ->
                            val marker =
                                when (s.status) {
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

        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()

        // No addAction() buttons — titled actions are parked-only on MESSAGING path and Samsung
        // shows "can't do that while driving" when the template contains them. Action hints are
        // encoded in the body text instead.
        MessageTemplate.Builder(body)
            .setTitle(prdName.take(MAX_TITLE_CHARS))
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                        AutoTts.speak(carContext, body)
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener {
                        screenManager.pop()
                    }.build())
                    .build(),
            )
            .build()
    } catch (e: Throwable) {
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        MessageTemplate.Builder("Error: ${e.message ?: "Unknown"}")
            .setTitle(prdName.take(MAX_TITLE_CHARS))
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    private companion object {
        const val MAX_STORY_TITLE: Int = 45
        const val MAX_TITLE_CHARS: Int = 30
    }
}
