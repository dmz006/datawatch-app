package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * PWA-matching voice recording overlay — shown while a VoiceRecorder is active.
 * The parent starts/owns the recorder; this composable handles Cancel vs Send only.
 *
 * Matches PWA modal: pulsing mic icon, "Recording…" label, 5-bar staggered waveform
 * (error/red, 0.9 s bounce, 120 ms stagger), Cancel + Send (red) buttons.
 */
@Composable
internal fun VoiceRecordingDialog(
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val pulse = rememberInfiniteTransition(label = "micPulse")
                val micAlpha by pulse.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "micAlpha",
                )
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = micAlpha),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    "Recording…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                VoiceWaveformBars()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(
                        onClick = onSend,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text("Send") }
                }
            }
        }
    }
}

/**
 * 5 animated bars matching the PWA waveform: center tallest, error-red fill,
 * 0.9 s RepeatMode.Reverse cycle, 120 ms stagger per bar.
 */
@Composable
internal fun VoiceWaveformBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    // [minDp, maxDp] — symmetric bell shape, center bar tallest
    val barSpecs = listOf(8f to 14f, 14f to 26f, 18f to 36f, 14f to 26f, 8f to 14f)
    val staggerMs = listOf(0, 120, 240, 360, 480)
    val heights = barSpecs.mapIndexed { i, (min, max) ->
        transition.animateFloat(
            initialValue = min,
            targetValue = max,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, delayMillis = staggerMs[i]),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    Row(
        modifier = modifier.height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { heightState ->
            val h by heightState
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}
