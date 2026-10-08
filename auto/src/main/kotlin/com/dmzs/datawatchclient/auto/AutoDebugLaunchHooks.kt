package com.dmzs.datawatchclient.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.ScreenManager

/**
 * DEBUG-build-only launch hooks for Android Automotive OS emulator screenshot
 * passes against a demo server (scripts/screenshots/capture-aaos.sh). Car twin
 * of composeApp's `DebugLaunchHooks`. Inert unless the app is debuggable, so a
 * release build ignores every extra.
 *
 * The AAOS debug build declares `androidx.car.app.activity.CarAppActivity`
 * (composeApp/src/androidDebug/AndroidManifest.xml), which forwards its launch
 * intent to `Session.onCreateScreen`:
 *
 *   adb shell am start -S -n <pkg>/androidx.car.app.activity.CarAppActivity \
 *     --es dwAutoScreen sessions|session|automata|monitor|about \
 *     [--es dwAutoSession <id> --es dwAutoSessionTitle <title>]
 *
 * The server profile itself is seeded by the phone activity's `dwSeedURL`
 * hook (same package, same database). Values come from the adb command line
 * only; nothing is stored in the repo.
 */
internal object AutoDebugLaunchHooks {
    private const val TAG = "AutoDebugLaunchHooks"

    /** Returns true when [intent] carried a debug screen request that was applied. */
    fun apply(
        carContext: CarContext,
        intent: Intent?,
        screenManager: ScreenManager,
    ): Boolean {
        if (intent == null || (carContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return false
        val screen = intent.getStringExtra("dwAutoScreen")?.lowercase() ?: return false
        val target =
            when (screen) {
                "sessions" -> AutoSessionListScreen(carContext)
                "automata" -> AutoAutomataScreen(carContext)
                "monitor" -> AutoMonitorScreen(carContext)
                "about" -> AutoAboutScreen(carContext)
                "session" -> {
                    val id = intent.getStringExtra("dwAutoSession") ?: return false
                    AutoSessionDetailScreen(carContext, id, intent.getStringExtra("dwAutoSessionTitle") ?: id)
                }
                else -> null
            } ?: return false
        screenManager.popToRoot()
        screenManager.push(target)
        Log.i(TAG, "opened $screen")
        return true
    }
}
