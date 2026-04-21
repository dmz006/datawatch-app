package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Per-session waiting list. Placeholder pending ServiceLocator
 * migration (see [AutoSummaryScreen] note). Rendered structure is
 * real so the Car-App template navigation can be smoke-tested; the
 * row content is stubbed.
 */
public class WaitingSessionsScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list =
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle("Open phone to reply")
                        .addText(
                            "Auto session list wires in a follow-up sprint. " +
                                "For now, reply from the phone app.",
                        )
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle("Waiting input")
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
