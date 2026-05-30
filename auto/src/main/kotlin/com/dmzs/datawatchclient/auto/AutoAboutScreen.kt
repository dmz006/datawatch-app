package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * About screen for Android Auto. Shows version, ASCII eye art, and
 * driver-safe daemon controls: Reboot (restartDaemon) + Update (updateDaemon).
 */
public class AutoAboutScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
        })
    }

    override fun onGetTemplate(): Template {
        val body = """
◈━━━━━━━━━━━━━━━━━━━━━◈
   ◉  d a t a w a t c h  ◉
◈━━━━━━━━━━━━━━━━━━━━━◈

v${Version.VERSION}  ·  build ${Version.VERSION_CODE}
fleet observability · driver surface

⟫ Reboot — restart the daemon process
⟫ Update — pull and apply latest version
        """.trimIndent()

        return MessageTemplate.Builder(body)
            .setTitle("datawatch")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Reboot")
                    .setBackgroundColor(CarColor.YELLOW)
                    .setOnClickListener { onReboot() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Update")
                    .setOnClickListener { onUpdate() }
                    .build(),
            )
            .build()
    }

    private fun onReboot() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: run {
                    CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                    return@runCatching
                }
                AutoServiceLocator.transportFor(profile).restartDaemon().fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "◉ Daemon restart requested", CarToast.LENGTH_SHORT).show()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Reboot failed: ${err.message?.take(40) ?: "unknown"}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun onUpdate() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: run {
                    CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                    return@runCatching
                }
                AutoServiceLocator.transportFor(profile).updateDaemon().fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "⟫ Update initiated", CarToast.LENGTH_SHORT).show()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Update failed: ${err.message?.take(40) ?: "unknown"}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }
}
