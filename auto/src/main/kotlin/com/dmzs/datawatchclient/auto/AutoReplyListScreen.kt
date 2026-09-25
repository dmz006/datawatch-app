package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick-reply screen pushed from [AutoSessionDetailScreen].
 *
 * Uses [MessageTemplate] (not ListTemplate) so it can be pushed while driving in
 * category.MESSAGING. Samsung gearhead blocks pushing a ListTemplate from a
 * MessageTemplate screen while driving ("task can't be completed while driving").
 *
 * The [MessageTemplate.addAction] buttons (Yes / No) are parked-only in category.MESSAGING.
 * While driving, use voice reply from the session detail screen instead.
 */
internal class AutoReplyListScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {
    companion object {
        private const val MAX_TITLE_CHARS = 40
        private const val MAX_ERR_CHARS = 30
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
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        return MessageTemplate.Builder(
            "Quick replies (tap while parked):\n• Yes   • No   • Continue   • Stop   • Enter ↩\n\nWhile driving, use voice reply.",
        )
            .setTitle(sessionTitle.ifBlank { "Quick Reply" }.take(MAX_TITLE_CHARS))
            .setHeaderAction(Action.BACK)
            .addAction(Action.Builder().setTitle("Yes ↩").setOnClickListener { sendReply("yes\r") }.build())
            .addAction(Action.Builder().setTitle("No ↩").setOnClickListener { sendReply("no\r") }.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build(),
                    )
                    .build(),
            )
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
