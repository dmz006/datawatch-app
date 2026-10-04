package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
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
    val alpha by com.dmzs.datawatchclient.ui.theme.rememberDwPulse(
        initial = 0.4f,
        target = 1.0f,
        durationMs = 800,
        easing = FastOutSlowInEasing,
        label = "live-dot",
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
