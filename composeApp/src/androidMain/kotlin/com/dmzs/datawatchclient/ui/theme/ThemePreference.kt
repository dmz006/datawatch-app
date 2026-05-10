package com.dmzs.datawatchclient.ui.theme

import android.content.Context

enum class ThemeMode { Dark, Light, System }

object ThemePrefs {
    const val KEY = "theme_mode"
    val DEFAULT = ThemeMode.Dark

    fun load(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, DEFAULT.name) ?: DEFAULT.name
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: DEFAULT
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}
