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
 * Sprint 4 Auto screen — glanceable session summary for drivers. Uses the
 * car-app [ListTemplate] (Play-compliant for Messaging-category apps when
 * items aren't tappable into free-form UI; here each row is a static
 * count display). Pre-MVP the counts are zero because the Auto module
 * doesn't yet have its own transport — Sprint 5 Phase 2 pipes them
 * from the phone app via the shared SessionRepository's cross-process
 * ContentProvider.
 */
public class PreMvpPlaceholderScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Running").addText("0 sessions").build())
            .addItem(Row.Builder().setTitle("Waiting input").addText("0 sessions").build())
            .addItem(Row.Builder().setTitle("Total").addText("0 sessions").build())
            .build()
        return ListTemplate.Builder()
            .setTitle("datawatch ${Version.VERSION}")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
            .build()
    }
}
