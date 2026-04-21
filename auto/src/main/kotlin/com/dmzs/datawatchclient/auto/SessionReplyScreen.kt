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
 * Driver-safe reply with Yes / No / Continue / Stop quick
 * actions. Tap → POSTs `/api/sessions/reply` via
 * [AutoServiceLocator].
 */
public class SessionReplyScreen(
    carContext: CarContext,
    private val sessionId: String,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private fun reply(text: String) {
        scope.launch {
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            val profile = profiles.firstOrNull { it.enabled } ?: return@launch
            AutoServiceLocator.transportFor(profile).replyToSession(sessionId, text).fold(
                onSuccess = {
                    CarToast.makeText(carContext, "Sent: $text", CarToast.LENGTH_SHORT).show()
                    screenManager.pop()
                },
                onFailure = { err ->
                    CarToast.makeText(
                        carContext,
                        "Reply failed — ${err.message ?: err::class.simpleName}",
                        CarToast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    override fun onGetTemplate(): Template {
        val actions =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Yes")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { reply("yes") }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("No")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { reply("no") }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Continue")
                        .setOnClickListener { reply("continue") }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Stop")
                        .setOnClickListener { reply("stop") }
                        .build(),
                )
                .build()
        return MessageTemplate.Builder("Quick reply for $sessionId")
            .setTitle("Reply")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actions)
            .build()
    }
}
