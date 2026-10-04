package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Pulsing green live-indicator dot used in auto-polled card headers (#95). */
@Composable
public fun LiveDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live-dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "live-dot-alpha",
    )
    Box(
        modifier =
            modifier
                .size(8.dp)
                .drawBehind {
                    drawCircle(color = Color(0xFF10B981), alpha = alpha)
                },
    )
}
