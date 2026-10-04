package com.dmzs.datawatchclient.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { Dark, Light, System }

object ThemePrefs {
    const val KEY = "theme_mode"
    val DEFAULT = ThemeMode.Dark

    @Volatile private var flow: MutableStateFlow<ThemeMode>? = null

    fun load(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, DEFAULT.name) ?: DEFAULT.name
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: DEFAULT
    }

    /** Live theme mode so a Settings change re-themes the app without a restart. */
    fun modeFlow(context: Context): StateFlow<ThemeMode> =
        (flow ?: MutableStateFlow(load(context)).also { flow = it }).asStateFlow()

    fun save(
        context: Context,
        mode: ThemeMode,
    ) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
        flow?.value = mode
    }
}
