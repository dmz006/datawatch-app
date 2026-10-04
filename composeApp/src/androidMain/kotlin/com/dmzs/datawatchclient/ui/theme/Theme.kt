package com.dmzs.datawatchclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * datawatch mobile client theme. Matches the parent PWA's CSS variable
 * palette byte-for-byte (`internal/server/web/style.css`). Per AGENT.md
 * "replicate, don't reinvent" — when the PWA has a design decision,
 * mirror it.
 *
 * Dark is the default; the PWA light palette (style.css `[data-theme="light"]`)
 * is available via Settings → Theme (Dark / Light / System). Material
 * You dynamic colour is explicitly DISABLED by default because it pulls
 * the user's wallpaper palette and completely overrides the datawatch
 * purple, producing a generic Material-looking UI that doesn't feel
 * like the parent at all. ADR-0030 variant pick is the brand palette,
 * not system-dynamic.
 */

// --- Parent PWA palette (internal/server/web/style.css :root vars) ---
internal val DwBg: Color = Color(0xFF0F1117) // --bg — app canvas
internal val DwBg2: Color = Color(0xFF1A1D27) // --bg2 — card surface
internal val DwBg3: Color = Color(0xFF22263A) // --bg3 — hover / pressed
internal val DwSurface: Color = Color(0xFF1E2130) // --surface — top bars
internal val DwAccent: Color = Color(0xFF7C3AED) // --accent (parity D6b: reverted from BL28's 8B5CF6 to the PWA value)
internal val DwAccent2: Color = Color(0xFFA855F7) // --accent2
internal val DwText: Color = Color(0xFFE2E8F0) // --text
internal val DwText2: Color = Color(0xFF94A3B8) // --text2
internal val DwBorder: Color = Color(0xFF2D3148) // --border
internal val DwSuccess: Color = Color(0xFF10B981) // --success
internal val DwWarning: Color = Color(0xFFF59E0B) // --warning
internal val DwError: Color = Color(0xFFEF4444) // --error
internal val DwWaiting: Color = Color(0xFF3B82F6) // --waiting

/**
 * Extended palette surfaces. The Material3 `ColorScheme` only has slots
 * for a fixed set of semantic colours; the PWA has extras (bg3, waiting,
 * explicit border). We expose them through a CompositionLocal so cards,
 * state pills, etc. can read them directly without polluting MaterialTheme.
 */
public data class DatawatchColors(
    val bg: Color,
    val bg2: Color,
    val bg3: Color,
    val border: Color,
    val waiting: Color,
    val warning: Color,
    val success: Color,
    val accent2: Color,
)

private val DefaultDatawatchColors: DatawatchColors =
    DatawatchColors(
        bg = DwBg,
        bg2 = DwBg2,
        bg3 = DwBg3,
        border = DwBorder,
        waiting = DwWaiting,
        warning = DwWarning,
        success = DwSuccess,
        accent2 = DwAccent2,
    )

public val LocalDatawatchColors: androidx.compose.runtime.ProvidableCompositionLocal<DatawatchColors> =
    compositionLocalOf { DefaultDatawatchColors }

private val DatawatchDarkScheme =
    darkColorScheme(
        primary = DwAccent,
        onPrimary = Color.White,
        primaryContainer = DwAccent2,
        onPrimaryContainer = Color.White,
        secondary = DwAccent2,
        onSecondary = Color.White,
        background = DwBg,
        onBackground = DwText,
        surface = DwBg2,
        onSurface = DwText,
        surfaceVariant = DwBg3,
        onSurfaceVariant = DwText2,
        surfaceContainer = DwSurface,
        surfaceContainerHigh = DwBg3,
        surfaceContainerHighest = DwBg3,
        surfaceContainerLow = DwBg2,
        surfaceContainerLowest = DwBg,
        outline = DwBorder,
        outlineVariant = DwBorder,
        error = DwError,
        onError = Color.White,
        errorContainer = Color(0xFF3B0F10),
        onErrorContainer = DwError,
    )

// --- Parent PWA light palette (style.css `[data-theme="light"]`, WCAG AA tuned) ---
internal val DwLightBg: Color = Color(0xFFFFFFFF) // --bg
internal val DwLightBg2: Color = Color(0xFFF1F5F9) // --bg2 (slate-100)
internal val DwLightBg3: Color = Color(0xFFE2E8F0) // --bg3 (slate-200)
internal val DwLightSurface: Color = Color(0xFFF8FAFC) // --surface (slate-50)
internal val DwLightAccent: Color = Color(0xFF2563EB) // --accent (blue-600)
internal val DwLightAccent2: Color = Color(0xFF7C3AED) // --accent2 (violet-600)
internal val DwLightText: Color = Color(0xFF0F172A) // --text (slate-900)
internal val DwLightText2: Color = Color(0xFF475569) // --text2 (slate-600)
internal val DwLightBorder: Color = Color(0xFFCBD5E1) // --border (slate-300)
internal val DwLightSuccess: Color = Color(0xFF047857) // --success (emerald-700)
internal val DwLightWarning: Color = Color(0xFFB45309) // --warning (amber-700)
internal val DwLightError: Color = Color(0xFFB91C1C) // --error (red-700)
internal val DwLightWaiting: Color = Color(0xFF1D4ED8) // --waiting (blue-700)

private val LightDatawatchColors: DatawatchColors =
    DatawatchColors(
        bg = DwLightBg,
        bg2 = DwLightBg2,
        bg3 = DwLightBg3,
        border = DwLightBorder,
        waiting = DwLightWaiting,
        warning = DwLightWarning,
        success = DwLightSuccess,
        accent2 = DwLightAccent2,
    )

/** PWA light theme (BL278) mapped onto Material3 slots, mirroring [DatawatchDarkScheme]. */
internal val LightColorScheme =
    lightColorScheme(
        primary = DwLightAccent,
        onPrimary = Color.White,
        primaryContainer = DwLightAccent2,
        onPrimaryContainer = Color.White,
        secondary = DwLightAccent2,
        onSecondary = Color.White,
        background = DwLightBg,
        onBackground = DwLightText,
        surface = DwLightBg2,
        onSurface = DwLightText,
        surfaceVariant = DwLightBg3,
        onSurfaceVariant = DwLightText2,
        surfaceContainer = DwLightSurface,
        surfaceContainerHigh = DwLightBg3,
        surfaceContainerHighest = DwLightBg3,
        surfaceContainerLow = DwLightBg2,
        surfaceContainerLowest = DwLightBg,
        outline = DwLightBorder,
        outlineVariant = DwLightBorder,
        error = DwLightError,
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = DwLightError,
    )

@Composable
public fun DatawatchTheme(
    /**
     * Material You wallpaper-derived palette. **Off by default** — turning
     * it on overrides datawatch-purple with the user's wallpaper colours
     * and the whole app stops looking like the parent. Left as an opt-in
     * toggle rather than removed entirely so future per-user preference
     * can hang off it if needed.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val dyn = dynamicColor
    val context = LocalContext.current
    val storedMode by remember { ThemePrefs.modeFlow(context) }.collectAsState()
    val darkTheme =
        when (storedMode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System -> isSystemInDarkTheme()
        }
    val colorScheme =
        when {
            darkTheme -> DatawatchDarkScheme
            else -> LightColorScheme
        }
    val dwColors = if (darkTheme) DefaultDatawatchColors else LightDatawatchColors
    CompositionLocalProvider(LocalDatawatchColors provides dwColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
