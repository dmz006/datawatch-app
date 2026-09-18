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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auto — autonomous plan action screen (B69).
 *
 * v0.40.1: approve/reject for needs_review plans.
 * v0.47.0: status-aware — shows Stop/Cancel for running plans,
 * Approve/Reject for needs_review / revisions_asked.
 * v1.0.60: Delete for terminal plans; "View Sessions" secondary action for all states;
 * [automataName] routes the sessions link to the filtered session list.
 */
public class PrdActionScreen(
    carContext: CarContext,
    private val prdId: String,
    private val title: String,
    private val status: String = "needs_review",
    private val automataName: String? = null,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    private fun fire(
        action: String,
        reason: String? = null,
    ) {
        scope.launch {
            try {
                val profiles = AutoServiceLocator.profileRepository.observeAll().first()
                val profile = profiles.firstOrNull { it.enabled } ?: return@launch
                val body =
                    if (reason != null) {
                        kotlinx.serialization.json.buildJsonObject {
                            put("reason", kotlinx.serialization.json.JsonPrimitive(reason))
                        }
                    } else {
                        null
                    }
                AutoServiceLocator.transportFor(profile).prdAction(prdId, action, body).fold(
                    onSuccess = {
                        CarToast.makeText(
                            carContext,
                            "PRD $action sent",
                            CarToast.LENGTH_SHORT,
                        ).show()
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
                CarToast.makeText(
                    carContext,
                    "Error: ${e.message ?: e::class.simpleName}",
                    CarToast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun fireDelete() {
        scope.launch {
            try {
                val profiles = AutoServiceLocator.profileRepository.observeAll().first()
                val profile = profiles.firstOrNull { it.enabled } ?: return@launch
                AutoServiceLocator.transportFor(profile).deletePrd(prdId).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Deleted", CarToast.LENGTH_SHORT).show()
                        // Pop twice: dismiss action screen and refresh automata list
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Delete failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(
                    carContext,
                    "Error: ${e.message ?: e::class.simpleName}",
                    CarToast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun viewSessions() {
        val nameForFilter = automataName ?: title
        screenManager.push(AutoSessionListScreen(carContext, automataId = nameForFilter))
    }

    override fun onGetTemplate(): Template {
        // MessageTemplate allows only 1 custom-title action; secondary actions go in the ActionStrip.
        val statusLower = status.lowercase()
        val isRunning = statusLower == "running" || statusLower == "active"
        val isTerminal = statusLower in setOf("killed", "completed", "complete", "cancelled", "rejected", "error")
        val isReview = statusLower in setOf("needs_review", "awaiting_review", "revisions_asked")

        val prompt =
            when {
                isRunning -> "Running: $title"
                isTerminal -> "Delete this automata?"
                isReview -> "Review: $title"
                else -> title
            }

        val builder =
            MessageTemplate.Builder(prompt)
                .setTitle("Automata")
                .setHeaderAction(Action.BACK)

        // MessageTemplate allows only 1 custom-title action; secondary actions go in the strip.
        var sessionsInStrip = false
        var rejectInStrip = false
        when {
            isRunning -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Stop")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fire("cancel") }
                        .build(),
                )
                sessionsInStrip = true
            }
            isTerminal -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Delete")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fireDelete() }
                        .build(),
                )
                sessionsInStrip = true
            }
            isReview -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Approve")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { fire("approve") }
                        .build(),
                )
                rejectInStrip = true
            }
            else -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Sessions")
                        .setOnClickListener { viewSessions() }
                        .build(),
                )
            }
        }
        if (sessionsInStrip || rejectInStrip) {
            val stripBuilder = ActionStrip.Builder()
            if (sessionsInStrip) {
                stripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Sessions")
                        .setOnClickListener { viewSessions() }
                        .build(),
                )
            }
            if (rejectInStrip) {
                stripBuilder.addAction(
                    Action.Builder()
                        .setTitle("Reject")
                        .setOnClickListener { fire("reject", "rejected from car") }
                        .build(),
                )
            }
            builder.setActionStrip(stripBuilder.build())
        }
        return builder.build()
    }
}
