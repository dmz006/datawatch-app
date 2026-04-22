package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Active server picker for Android Auto. Lists every enabled
 * [ServerProfile] so the driver can switch which fleet the Monitor +
 * Sessions screens target without pulling out their phone. Writes
 * through `ActiveServerStore`, which the phone also reads — change
 * in the car, phone's Sessions tab reflects it next observe tick.
 */
public class AutoServerPickerScreen(carContext: CarContext) : Screen(carContext) {
    private var profiles: List<ServerProfile> = emptyList()
    private var activeId: String? = null
    private var loadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    loadJob?.cancel()
                    loadJob = scope.launch {
                        profiles =
                            AutoServiceLocator.profileRepository.observeAll().first()
                                .filter { it.enabled }
                        activeId = AutoServiceLocator.activeServerStore.get()
                        invalidate()
                    }
                }
                override fun onStop(owner: LifecycleOwner) {
                    loadJob?.cancel()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        if (profiles.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle("No enabled servers")
                    .addText("Configure on your phone.")
                    .build(),
            )
        } else {
            profiles.forEach { p ->
                val marker = if (p.id == activeId) "● " else "○ "
                val titleColor = if (p.id == activeId) CarColor.GREEN else CarColor.DEFAULT
                items.addItem(
                    Row.Builder()
                        .setTitle(colored("$marker${p.displayName}", titleColor))
                        .addText(p.baseUrl)
                        .setOnClickListener {
                            AutoServiceLocator.activeServerStore.set(p.id)
                            activeId = p.id
                            screenManager.pop()
                        }
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("Active server")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }
}
