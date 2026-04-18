package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.dmzs.datawatchclient.Version

/**
 * Pre-MVP screen — shown only until Sprint 4 lands the ConversationItem / CarMessage
 * pipeline. Kept trivially so the manifest entry compiles and DHU testing is
 * possible on day 1.
 */
public class PreMvpPlaceholderScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("datawatch ${Version.VERSION} — scaffold")
            .setTitle("datawatch")
            .build()
}
