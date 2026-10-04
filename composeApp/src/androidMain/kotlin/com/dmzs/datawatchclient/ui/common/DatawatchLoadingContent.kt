package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.ui.splash.EyeOnlyAnimated

private val Purple = Color(0xFFA855F7)

/**
 * Drop-in replacement for `CircularProgressIndicator` in card loading states.
 * Shows a small animated datawatch eye icon with an optional pulsing label.
 * Use wherever a card has nothing to show yet because the API call is in flight.
 *
 * @param label optional text below the eye (e.g. "connecting…"). Pass null to
 *   hide the label entirely.
 * @param eyeSize size of the animated eye widget.
 * @param verticalPadding outer vertical padding so the indicator has breathing room
 *   inside the card without callers adding extra padding.
 */
@Composable
public fun DatawatchLoadingContent(
    modifier: Modifier = Modifier,
    label: String? = "connecting…",
    eyeSize: Dp = 40.dp,
    verticalPadding: Dp = 24.dp,
) {
    val textAlpha by com.dmzs.datawatchclient.ui.theme.rememberDwPulse(
        initial = 0.35f,
        target = 0.90f,
        durationMs = 850,
        staticValue = 0.7f,
        easing = FastOutSlowInEasing,
        label = "dw-loading",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EyeOnlyAnimated(modifier = Modifier.size(eyeSize))
            if (label != null) {
                Text(
                    text = label,
                    color = Purple.copy(alpha = textAlpha),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                )
            }
        }
    }
}
