package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Placeholder reply screen. Full voice/quick-reply flow lands in a
 * Sprint T follow-up once DI is unified. For the v0.24 test train,
 * replies happen on the phone.
 */
public class SessionReplyScreen(
    carContext: CarContext,
    @Suppress("UNUSED_PARAMETER") sessionId: String,
) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(
            "Reply from the phone app while this Auto integration is " +
                "placeholder-only. Voice + quick-reply lands in a follow-up sprint.",
        )
            .setTitle("Reply")
            .setHeaderAction(Action.BACK)
            .build()
}
