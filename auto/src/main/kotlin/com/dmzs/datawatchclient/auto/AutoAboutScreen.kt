package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.dmzs.datawatchclient.Version

/**
 * About screen for Android Auto. Shows the shared [Version] and
 * brand tagline so the fleet can tell at a glance which build is
 * running in the car. ADR-0031-compliant: static text, no input.
 */
public class AutoAboutScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val items =
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(colored("datawatch", CarColor.GREEN))
                        .addText("Fleet observability — driver surface")
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Version")
                        .addText(Version.VERSION)
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Build")
                        .addText(Version.VERSION_CODE.toString())
                        .build(),
                )
                .addItem(
                    Row.Builder()
                        .setTitle("Surface")
                        .addText("Android Auto (Messaging template)")
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle("About datawatch")
            .setHeaderAction(Action.BACK)
            .setSingleList(items)
            .build()
    }
}
