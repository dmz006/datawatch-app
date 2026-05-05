package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.ui.splash.EyeOnlyAnimated

private val OverlayBg = Color(0xFF0F1117)
private val Teal = Color(0xFF00E5A0)
private val Purple = Color(0xFFA855F7)
private val Pink = Color(0xFFE879F9)

/**
 * Full-screen loading state shown when navigating to a brand-new session.
 * Displays the animated datawatch eye with a pulsing lightning bolt in the
 * pupil and a "Loading" label.  Fades out as soon as [visible] becomes false
 * (first pane_capture arrives from the server).
 */
@Composable
public fun SessionLoadingOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(400)),
    ) {
        val infinite = rememberInfiniteTransition(label = "loading-overlay")

        // Slow pulse for "Loading" text alpha
        val textAlpha by infinite.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.00f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "text-pulse",
        )

        // Fast flicker for lightning bolt
        val boltAlpha by infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.00f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(220, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "bolt-alpha",
        )

        // Horizontal jitter — gives the bolt a "vibrating" feel
        val boltJitter by infinite.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(80, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "bolt-jitter",
        )

        // Glow pulse on the bolt stroke width
        val boltStroke by infinite.animateFloat(
            initialValue = 2.0f,
            targetValue = 3.5f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "bolt-stroke",
        )

        Box(
            modifier = Modifier.fillMaxSize().background(OverlayBg),
            contentAlignment = Alignment.Center,
        ) {
            // Animated eye with matrix rain
            EyeOnlyAnimated(modifier = Modifier.fillMaxSize())

            // Lightning bolt drawn over the pupil
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f + boltJitter
                val cy = size.height / 2f
                // Eye radius is 37% of the shorter dimension; pupil fits inside ~18%
                val boltH = minOf(size.width, size.height) * 0.085f
                val boltW = boltH * 0.55f

                // Classic ⚡ zigzag: top-right slant → crossbar → bottom-right slant
                val path =
                    Path().apply {
                        moveTo(cx + boltW * 0.35f, cy - boltH)           // top
                        lineTo(cx - boltW * 0.25f, cy - boltH * 0.08f)   // mid-left
                        lineTo(cx + boltW * 0.55f, cy - boltH * 0.08f)   // mid-right (crossbar)
                        lineTo(cx - boltW * 0.35f, cy + boltH)            // bottom
                    }

                // Glow halo
                drawPath(
                    path = path,
                    color = Teal.copy(alpha = boltAlpha * 0.30f),
                    style =
                        Stroke(
                            width = boltStroke * 3.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
                // Main bolt
                drawPath(
                    path = path,
                    color = Pink.copy(alpha = boltAlpha),
                    style =
                        Stroke(
                            width = boltStroke,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
                // Bright core
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = boltAlpha * 0.55f),
                    style =
                        Stroke(
                            width = boltStroke * 0.4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }

            // "Loading" label — bottom of screen, teal + pulsing
            Text(
                text = "Loading",
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                color = Teal.copy(alpha = textAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
            )
        }
    }
}
