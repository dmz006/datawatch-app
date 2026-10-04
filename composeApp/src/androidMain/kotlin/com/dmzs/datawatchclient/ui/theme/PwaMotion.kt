package com.dmzs.datawatchclient.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** CSS `ease-in-out` — cubic-bezier(0.42, 0, 0.58, 1). */
public val CssEaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * True when the user turned animations off (Developer options / Accessibility
 * "Remove animations" → animator duration scale 0). Android's equivalent of
 * the PWA's `prefers-reduced-motion: reduce`.
 */
@Composable
public fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * PWA `.state-badge-running` pulse (`dw-running-pulse`): opacity 0.55 → 1.0,
 * 700 ms ease-in-out, alternate, infinite; static at 1.0 under reduced motion.
 * Used for running session pills (parity D18, kept) and running / planning /
 * decomposing PRD status pills (parity D23a).
 */
@Composable
public fun rememberRunningPulseAlpha(active: Boolean): State<Float> {
    val reduced = rememberReducedMotion()
    if (!active || reduced) return remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "dw-running-pulse")
    return transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(700, easing = CssEaseInOut),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "dw-running-pulse-alpha",
    )
}
