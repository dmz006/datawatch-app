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
 * driver-safe daemon controls: Reboot (restartDaemon) + Update (when available).
 */
private const val ERROR_MSG_CHARS: Int = 40

public class AutoAboutScreen(carContext: CarContext) : Screen(carContext) {

    private enum class UpdateStatus { CHECKING, UP_TO_DATE, AVAILABLE, ERROR }

    private var updateStatus = UpdateStatus.CHECKING
    private var availableVersion: String? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { checkForUpdate() }
            }

            override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
        })
    }

    private suspend fun checkForUpdate() {
        runCatching {
            val profile = resolveActiveProfile() ?: run {
                updateStatus = UpdateStatus.UP_TO_DATE
                invalidate()
                return
            }
            AutoServiceLocator.transportFor(profile).checkUpdate().fold(
                onSuccess = { json ->
                    val status = json["status"]?.let {
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            kotlinx.serialization.json.JsonPrimitive.serializer(),
                            it
                        ).content
                    }
                    when (status) {
                        "update_available" -> {
                            updateStatus = UpdateStatus.AVAILABLE
                            availableVersion = json["version"]?.let {
                                kotlinx.serialization.json.Json.decodeFromJsonElement(
                                    kotlinx.serialization.json.JsonPrimitive.serializer(),
                                    it
                                ).content
                            }
                        }
                        else -> updateStatus = UpdateStatus.UP_TO_DATE
                    }
                },
                onFailure = {
                    // 404 or any error → fail safe, don't show Update button
                    updateStatus = UpdateStatus.UP_TO_DATE
                }
            )
        }.onFailure {
            updateStatus = UpdateStatus.UP_TO_DATE
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val updateLine = when (updateStatus) {
            UpdateStatus.CHECKING -> "\n⟳ Checking for updates…"
            UpdateStatus.UP_TO_DATE -> "\n✓ Up to date"
            UpdateStatus.AVAILABLE -> "\n⟫ Update available: ${availableVersion ?: "new version"}"
            UpdateStatus.ERROR -> "\n✓ Up to date"
        }

        val body = """
v${Version.VERSION}  (build ${Version.VERSION_CODE})
fleet observability · driver surface

⟫ Reboot — restart the daemon process
⟫ Update — pull and apply latest version$updateLine
        """.trimIndent()

        val templateBuilder = MessageTemplate.Builder(body)
            .setTitle("datawatch  v${Version.VERSION}")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Reboot")
                    .setBackgroundColor(CarColor.YELLOW)
                    .setOnClickListener { onReboot() }
                    .build(),
            )

        // Only add Update action when an update is actually available
        if (updateStatus == UpdateStatus.AVAILABLE) {
            templateBuilder.addAction(
                Action.Builder()
                    .setTitle("Update")
                    .setOnClickListener { onUpdate() }
                    .build(),
            )
        }

        return templateBuilder.build()
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
                            "Reboot failed: ${err.message?.take(ERROR_MSG_CHARS) ?: "unknown"}",
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
                            "Update failed: ${err.message?.take(ERROR_MSG_CHARS) ?: "unknown"}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }
}
