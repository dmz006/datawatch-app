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
 * Auto — PRD approve / reject confirmation screen.
 *
 * v0.40.1. Two big buttons, plus a Back action. Reject ships an
 * automatic "rejected from car" reason so the daemon's
 * `request_revision` workflow has something to record without a
 * keyboard input on a moving vehicle. Full rejection-with-reason
 * stays on the phone.
 */
public class PrdActionScreen(
    carContext: CarContext,
    private val prdId: String,
    private val title: String,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private fun fire(action: String, reason: String? = null) {
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
        return MessageTemplate.Builder("Review $title")
            .setTitle("PRD review")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(approve)
                    .addAction(reject)
                    .build(),
            )
            .build()
    }
}
