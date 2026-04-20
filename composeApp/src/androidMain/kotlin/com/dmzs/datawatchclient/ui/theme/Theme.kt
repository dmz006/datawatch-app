package com.dmzs.datawatchclient.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * datawatch mobile client theme. Matches parent datawatch palette per ADR-0030.
 * Dark default; light + Material You dynamic also supported from MVP.
 * Public display name is "datawatch" (ADR-0041); internal identifiers remain
 * com.dmzs.datawatchclient for stability.
 */
private val AccentPurple = Color(0xFF7C3AED)
private val AccentPurpleLight = Color(0xFFA855F7)
private val BgDark = Color(0xFF0F1117)
private val Bg2Dark = Color(0xFF1A1D27)
private val SurfaceDark = Color(0xFF1E2130)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextSecondary = Color(0xFF94A3B8)
private val SuccessGreen = Color(0xFF10B981)
private val WarningAmber = Color(0xFFF59E0B)
private val ErrorRed = Color(0xFFEF4444)

private val DatawatchDarkScheme =
    darkColorScheme(
        primary = AccentPurple,
        onPrimary = Color.White,
        primaryContainer = AccentPurpleLight,
        secondary = AccentPurpleLight,
        background = BgDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = Bg2Dark,
        onSurfaceVariant = TextSecondary,
        error = ErrorRed,
    )

private val DatawatchLightScheme =
    lightColorScheme(
        primary = AccentPurple,
        secondary = AccentPurpleLight,
        error = ErrorRed,
    )

@Composable
public fun DatawatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ctx = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }
            darkTheme -> DatawatchDarkScheme
            else -> DatawatchLightScheme
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
