package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.dmzs.datawatchclient.Version

/**
 * Glanceable Auto session summary. Current state (v0.24.0):
 * placeholder counts — the phone app's ServiceLocator lives in
 * `composeApp/androidMain` and is not visible to this `:auto`
 * library module. A proper Sprint T migration needs either
 *  (a) move ServiceLocator / DatabaseFactory / RestTransport
 *      construction to `:shared`, or
 *  (b) add an Auto-only slim DI reader that opens SQLCipher with
 *      the same key + instantiates RestTransport from the first
 *      enabled profile.
 *
 * Both options are scoped for a Sprint T follow-up. The
 * Messaging-template service and screen navigation is ready; only
 * the data source is stubbed.
 */
public class AutoSummaryScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list =
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle("Running")
                        .addText("— (open phone)")
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Waiting input")
                        .addText("— (open phone)")
                        .setOnClickListener {
                            screenManager.push(WaitingSessionsScreen(carContext))
                        }
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Total")
                        .addText("— (open phone)")
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Status")
                        .addText(
                            "datawatch-app ${Version.VERSION}. Auto integration placeholder " +
                                "pending DI migration — see docs/plans/2026-04-21-auto-audit.md.",
                        )
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle("datawatch ${Version.VERSION}")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
            .build()
    }
}
