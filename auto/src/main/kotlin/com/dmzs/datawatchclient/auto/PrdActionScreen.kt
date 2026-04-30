package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Auto — autonomous plan action screen (B69).
 *
 * v0.40.1: approve/reject for needs_review plans.
 * v0.47.0: status-aware — shows Stop/Cancel for running plans,
 * Approve/Reject for needs_review / revisions_asked.
 */
public class PrdActionScreen(
    carContext: CarContext,
    private val prdId: String,
    private val title: String,
    private val status: String = "needs_review",
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main)

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

    override fun onGetTemplate(): Template {
        val actionStrip =
            if (status.lowercase() == "running") {
                val stop =
                    Action.Builder()
                        .setTitle("Stop")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fire("cancel") }
                        .build()
                ActionStrip.Builder().addAction(stop).build()
            } else {
                val approve =
                    Action.Builder()
                        .setTitle("Approve")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { fire("approve") }
                        .build()
                val reject =
                    Action.Builder()
                        .setTitle("Reject")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fire("reject", "rejected from car") }
                        .build()
                ActionStrip.Builder().addAction(approve).addAction(reject).build()
            }
        val prompt = if (status.lowercase() == "running") "Stop plan?" else "Review $title"
        return MessageTemplate.Builder(prompt)
            .setTitle("Autonomous plan")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }
}
