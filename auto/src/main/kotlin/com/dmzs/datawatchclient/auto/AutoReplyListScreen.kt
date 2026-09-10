package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
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
import kotlinx.coroutines.launch

/**
 * Quick-reply list pushed as a separate Screen from [AutoSessionDetailScreen].
 *
 * Using a pushed Screen (instead of an inline template swap) gives the
 * ListTemplate a proper Header + Action.BACK.  Without a Header the Car App
 * Library driving-mode validator on some head units (e.g. Samsung gearhead)
 * rejects the template with a "can't do that while driving" error.
 *
 * Voice reply is intentionally omitted here — the user can go back and press
 * "Voice Reply" from the session detail.  Omitting it avoids a potential
 * 6-screen-deep push when the stack already contains AutoAutomataScreen and
 * AutoMonitorScreen.
 */
internal class AutoReplyListScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {
    companion object {
        private const val MAX_TITLE_CHARS = 40
    }

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

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listOf(
            "Yes" to "yes\r",
            "No" to "no\r",
            "Continue" to "continue\r",
            "Stop" to "stop\r",
            "Enter ⏎" to "\r",
        ).forEach { (label, text) ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setOnClickListener { sendReply(text) }
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(sessionTitle.ifBlank { "Quick Reply" }.take(MAX_TITLE_CHARS))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun sendReply(text: String) {
        scope.launch {
            val profile =
                resolveActiveProfile() ?: run {
                    CarToast.makeText(carContext, "No server", CarToast.LENGTH_SHORT).show()
                    return@launch
                }
            AutoServiceLocator.transportFor(profile).replyToSession(sessionId, text).fold(
                onSuccess = {
                    CarToast.makeText(carContext, "Sent", CarToast.LENGTH_SHORT).show()
                    screenManager.pop()
                },
                onFailure = { err ->
                    CarToast.makeText(
                        carContext,
                        "Reply failed — ${err.message ?: err::class.simpleName}",
                        CarToast.LENGTH_LONG,
                    ).show()
                    screenManager.pop()
                },
            )
        }
    }
}
