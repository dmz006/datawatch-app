package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shell-level probes for the iOS root view.
 *
 * [autonomousEnabled] mirrors the PWA nav gating (app.js: `navBtnAutonomous` /
 * `navBtnDashboard` are unhidden only when the autonomous subsystem is enabled)
 * and Android `AppRoot.probeAutonomous` (reads `autonomous.enabled` from
 * `/api/config`). Result codes keep the Swift interop trivial:
 * 1 = enabled, 0 = disabled, -1 = unknown (request failed — leave tabs shown,
 * same default as Android). Callback runs on a background thread.
 */
public object IosShellProbe {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun autonomousEnabled(
        profile: ServerProfile,
        onResult: (Int) -> Unit,
    ) {
        scope.launch {
            val result = IosServiceLocator.transportFor(profile).fetchConfig()
            val code: Int =
                result.fold(
                    onSuccess = { cfg ->
                        val auto: JsonObject? = cfg.raw["autonomous"] as? JsonObject
                        val flag: String? = (auto?.get("enabled") as? JsonPrimitive)?.content?.lowercase()
                        if (flag == "true") 1 else 0
                    },
                    onFailure = { -1 },
                )
            onResult(code)
        }
    }
}
