package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.rememberReducedMotion

/**
 * Per-card loading placeholder: the PWA skeleton shimmer (`style.css`
 * `.skeleton-line` + `@keyframes skeleton-shimmer`, D60) — three bars at
 * 40 % / 70 % / 90 % width, 12 dp tall, radius 4, a `--border` → `--bg3`
 * → `--border` gradient sweeping over 1.4 s. Static under reduced motion.
 * TalkBack still announces "Loading…".
 *
 * Operator 2026-10-06: card loading states must animate, not show a bare
 * "Loading…" line.
 */
@Composable
public fun PwaLoadingText(
    modifier: Modifier = Modifier,
    /** Many card bodies have no inner padding; keep the bars off the card edge. */
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val dw = LocalDatawatchColors.current
    val reduced = rememberReducedMotion()
    val shift by if (reduced) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "skeleton-shimmer").animateFloat(
            initialValue = -200f,
            targetValue = 200f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "skeleton-shimmer-x",
        )
    }
    val px = with(LocalDensity.current) { 1.dp.toPx() }
    // background-size 400px; stops 25 % / 37 % / 63 %.
    val brush =
        Brush.linearGradient(
            0.25f to dw.border,
            0.37f to dw.bg3,
            0.63f to dw.border,
            start = Offset((shift - 200f) * px, 0f),
            end = Offset((shift + 200f) * px, 0f),
        )
    val loading = stringResource(R.string.common_loading)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 8.dp)
                .semantics { contentDescription = loading },
    ) {
        listOf(0.4f, 0.7f, 0.9f).forEachIndexed { i, w ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(w)
                        .padding(bottom = if (i < 2) 8.dp else 0.dp)
                        .height(12.dp)
                        .background(brush, RoundedCornerShape(4.dp)),
            )
        }
    }
}
