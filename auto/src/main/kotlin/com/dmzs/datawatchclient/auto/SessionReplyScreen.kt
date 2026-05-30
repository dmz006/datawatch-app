package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
 * Driver-safe reply with Yes / No / Continue / Stop quick actions.
 * Uses a ListTemplate with tappable rows (no ActionStrip) so all four
 * options are available without violating Car App Library's 1-custom-title
 * ActionStrip constraint. Tap → POSTs `/api/sessions/reply` via
 * [AutoServiceLocator].
 */
public class SessionReplyScreen(
    carContext: CarContext,
    private val sessionId: String,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

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
        val items = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(colored("Yes", CarColor.GREEN))
                    .addText("Send affirmative reply")
                    .setOnClickListener { reply("yes") }
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle(colored("No", CarColor.RED))
                    .addText("Send negative reply")
                    .setOnClickListener { reply("no") }
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle("Continue")
                    .addText("Resume the session")
                    .setOnClickListener { reply("continue") }
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle(colored("Stop", CarColor.RED))
                    .addText("Stop the session")
                    .setOnClickListener { reply("stop") }
                    .build(),
            )
            .build()
        return ListTemplate.Builder()
            .setTitle("Quick Reply")
            .setHeaderAction(Action.BACK)
            .setSingleList(items)
            .build()
    }
}
