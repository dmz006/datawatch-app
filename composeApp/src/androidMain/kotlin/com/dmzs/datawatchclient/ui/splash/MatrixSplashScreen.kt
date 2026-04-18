package com.dmzs.datawatchclient.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.Version
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Animated splash screen per the user's feedback: tablet frame + eye + antenna
 * arcs + matrix rain (drifting characters behind the eye, each with independent
 * opacity). Drawn entirely in Compose Canvas so it renders on every screen size
 * without an additional vector asset.
 *
 * - `replay=false` (default): the screen auto-advances after [autoAdvanceMs]
 *   (default 3200 ms) and calls [onFinished]. Used as the app's cold-launch
 *   splash via [com.dmzs.datawatchclient.ui.AppRoot].
 * - `replay=true`: stays on screen until the user dismisses via the Close
 *   button. Entered from Settings → About → "Replay splash animation".
 */
@Composable
public fun MatrixSplashScreen(
    replay: Boolean = false,
    autoAdvanceMs: Long = 3200L,
    onFinished: () -> Unit,
) {
    if (!replay) {
        LaunchedEffect(Unit) {
            delay(autoAdvanceMs)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117)),
        contentAlignment = Alignment.Center,
    ) {
        MatrixSplashArtwork()

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "datawatch",
                style = MaterialTheme.typography.displaySmall,
                color = Color(0xFFA855F7),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "v${Version.VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6D28D9),
                modifier = Modifier.padding(top = 4.dp),
            )
            if (replay) {
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Close") }
            }
        }
    }
}

// -- artwork ------------------------------------------------------------------

private object SplashPalette {
    val Bg = Color(0xFF0F1117)
    val BezelDark = Color(0xFF0D0720)
    val BezelLight = Color(0xFF241438)
    val ScreenDark = Color(0xFF08031A)
    val ScreenLit = Color(0xFF220F42)
    val Border = Color(0xFF7C3AED)
    val IrisOuter = Color(0xFF3B0764)
    val IrisMid = Color(0xFF7C3AED)
    val IrisInner = Color(0xFFA855F7)
    val Pupil = Color(0xFF04020E)
    val Crosshair = Color(0xFFE879F9)
    val Highlight = Color(0xFFF0ABFC)
    val Matrix = Color(0xFFA855F7)
    val MatrixBright = Color(0xFFC084FC)
    val MatrixLead = Color(0xFFF0ABFC)
}

private const val MATRIX_COLUMNS = 9
private val MATRIX_CHARS = ('A'..'F').toList() + ('0'..'9').toList() +
    listOf('x', 'W', 'T', 'C', 'H', 'R')

/** Stable per-column animation timing, computed once so recomposition doesn't
 *  jitter the rain. */
private data class ColumnSpec(
    val xFrac: Float,       // 0..1 across the screen width
    val durMs: Int,
    val delayFrac: Float,   // 0..1 phase offset
    val charCount: Int,
)

@Composable
private fun MatrixSplashArtwork() {
    val textMeasurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "matrix")

    // Pulsing pupil — a slow sine-ish breathing so the eye feels alive.
    val pupilScale by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pupil",
    )

    // Global rain clock. Every column derives its Y from this value + its own phase.
    val rainTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rain",
    )

    // Slow scan-line sweep across the screen.
    val scanY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan",
    )

    // Per-character opacity flicker — 64 staggered flicker tracks, each column
    // consumes N of them. rememberInfiniteTransition only gives us one value per
    // call so we build a list.
    val flickers: List<Float> = (0 until 32).map { i ->
        infinite.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 900 + (i * 73 % 900),
                    delayMillis = (i * 41) % 500,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "flicker-$i",
        ).value
    }

    // Deterministic column specs (seeded so the pattern is reproducible build-to-build).
    val columns: List<ColumnSpec> = remember {
        val rng = Random(4242)
        List(MATRIX_COLUMNS) {
            ColumnSpec(
                xFrac = 0.12f + it * (0.76f / (MATRIX_COLUMNS - 1)),
                durMs = 4000 + rng.nextInt(2800),
                delayFrac = rng.nextFloat(),
                charCount = 7 + rng.nextInt(4),
            )
        }
    }

    // Deterministic character assignments per (column, row).
    val columnChars: List<List<Char>> = remember(columns) {
        val rng = Random(13)
        columns.map { col ->
            List(col.charCount) { MATRIX_CHARS[rng.nextInt(MATRIX_CHARS.size)] }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val discRadius = minOf(size.width, size.height) / 2.2f

        // 1. Dark disc background (centered, slightly smaller than screen).
        drawCircle(color = Color(0xFF1A0A2E), radius = discRadius, center = Offset(cx, cy))
        drawCircle(
            color = SplashPalette.Border.copy(alpha = 0.35f),
            radius = discRadius * 0.98f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f),
        )

        // 2. Antenna arcs (B1 crown) above the tablet.
        val arcTop = cy - discRadius * 0.72f
        val arcCenter = Offset(cx, arcTop)
        drawArcGroup(arcCenter, baseRadius = discRadius * 0.28f)

        // 3. Tablet bezel — rounded rectangle landscape.
        val tabletWidth = discRadius * 1.50f
        val tabletHeight = discRadius * 1.10f
        val tabletLeft = cx - tabletWidth / 2f
        val tabletTop = cy - tabletHeight / 2f + discRadius * 0.05f
        drawRoundedRect(
            tabletLeft, tabletTop, tabletWidth, tabletHeight,
            cornerRadius = 28f, fillColor = SplashPalette.BezelDark,
            strokeColor = SplashPalette.Border, strokeWidth = 3f,
        )
        // Speaker slit
        drawRoundedRect(
            cx - 20f, tabletTop + 8f, 40f, 4f,
            cornerRadius = 2f, fillColor = Color(0xFF2D1B4E),
        )

        // 4. Screen recess — clipped matrix rain + eye go inside.
        val padding = 14f
        val screenLeft = tabletLeft + padding
        val screenTop = tabletTop + padding + 14f
        val screenWidth = tabletWidth - 2 * padding
        val screenHeight = tabletHeight - 2 * padding - 14f
        drawRoundedRect(
            screenLeft, screenTop, screenWidth, screenHeight,
            cornerRadius = 18f, fillColor = SplashPalette.ScreenDark,
            strokeColor = Color(0xFF4C1D95).copy(alpha = 0.8f), strokeWidth = 1f,
        )

        clipRect(
            left = screenLeft, top = screenTop,
            right = screenLeft + screenWidth, bottom = screenTop + screenHeight,
        ) {
            // Matrix rain — each column offset by its own phase.
            columns.forEachIndexed { colIdx, col ->
                val phase = ((rainTime + col.delayFrac) % 1f)
                val colY = screenTop - 20f + phase * (screenHeight + 40f)
                val colX = screenLeft + col.xFrac * screenWidth
                val chars = columnChars[colIdx]
                val fontSize = 13.sp
                chars.forEachIndexed { rowIdx, ch ->
                    val y = colY + rowIdx * 16f
                    if (y < screenTop - 16f || y > screenTop + screenHeight) return@forEachIndexed
                    val flickerIdx = (colIdx * 7 + rowIdx) % flickers.size
                    val baseAlpha = flickers[flickerIdx]
                    // Head of the column is brighter, tail fades.
                    val positionWeight = 1f - rowIdx.toFloat() / col.charCount
                    val alpha = (baseAlpha * (0.45f + 0.55f * positionWeight)).coerceIn(0f, 1f)
                    val tint = when {
                        rowIdx == 0 -> SplashPalette.MatrixLead
                        rowIdx % 3 == 0 -> SplashPalette.MatrixBright
                        else -> SplashPalette.Matrix
                    }
                    val measured = textMeasurer.measure(
                        text = ch.toString(),
                        style = TextStyle(
                            color = tint.copy(alpha = alpha),
                            fontSize = fontSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(colX - measured.size.width / 2f, y),
                    )
                }
            }

            // Scan-line sweep
            val scanPx = screenTop + scanY * screenHeight
            drawRect(
                color = SplashPalette.Border.copy(alpha = 0.30f),
                topLeft = Offset(screenLeft, scanPx),
                size = Size(screenWidth, 2f),
            )
        }

        // 5. Eye — layered on top of matrix rain.
        drawEye(center = Offset(cx, cy + discRadius * 0.06f), radius = discRadius * 0.34f,
                pupilScale = pupilScale)
    }
}

// -- drawing helpers ---------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcGroup(
    center: Offset,
    baseRadius: Float,
) {
    val arcs = listOf(
        Triple(baseRadius * 1.7f, 0.45f, 6f),
        Triple(baseRadius * 1.3f, 0.70f, 8f),
        Triple(baseRadius * 0.9f, 0.95f, 10f),
    )
    arcs.forEach { (r, alpha, strokeWidth) ->
        val path = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = center.x - r, top = center.y - r,
                    right = center.x + r, bottom = center.y + r,
                ),
                startAngleDegrees = 200f,
                sweepAngleDegrees = 140f,
                forceMoveTo = true,
            )
        }
        drawPath(
            path = path,
            color = SplashPalette.Border.copy(alpha = alpha),
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEye(
    center: Offset,
    radius: Float,
    pupilScale: Float,
) {
    // Sclera ellipse
    val ellipseRx = radius * 1.85f
    val ellipseRy = radius * 1.15f
    drawOval(
        color = Color(0xFF080518),
        topLeft = Offset(center.x - ellipseRx, center.y - ellipseRy),
        size = Size(ellipseRx * 2f, ellipseRy * 2f),
    )
    drawOval(
        color = SplashPalette.Border,
        topLeft = Offset(center.x - ellipseRx, center.y - ellipseRy),
        size = Size(ellipseRx * 2f, ellipseRy * 2f),
        style = Stroke(width = 2f),
    )
    // Iris (solid gradient-ish by layered circles)
    drawCircle(color = SplashPalette.IrisOuter, radius = radius, center = center)
    drawCircle(color = SplashPalette.IrisMid, radius = radius * 0.85f, center = center)
    drawCircle(color = SplashPalette.IrisInner.copy(alpha = 0.6f), radius = radius * 0.55f, center = center)
    // Pupil (pulsing)
    drawCircle(
        color = SplashPalette.Pupil,
        radius = radius * 0.40f * pupilScale,
        center = center,
    )
    // Crosshair
    val cr = radius * 0.32f
    val crGap = radius * 0.15f
    val strokeCrosshair = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x, center.y - cr),
        end = Offset(center.x, center.y - crGap),
        strokeWidth = 3f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x, center.y + crGap),
        end = Offset(center.x, center.y + cr),
        strokeWidth = 3f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x - cr, center.y),
        end = Offset(center.x - crGap, center.y),
        strokeWidth = 3f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x + crGap, center.y),
        end = Offset(center.x + cr, center.y),
        strokeWidth = 3f,
    )
    // Center highlight (pulses with pupil)
    drawCircle(
        color = SplashPalette.Highlight,
        radius = radius * 0.07f * pupilScale,
        center = center,
    )
    // Lens highlight (static, subtle)
    rotate(-30f, Offset(center.x - radius * 0.42f, center.y - radius * 0.28f)) {
        drawOval(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.32f),
            size = Size(radius * 0.25f, radius * 0.11f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundedRect(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cornerRadius: Float,
    fillColor: Color,
    strokeColor: Color? = null,
    strokeWidth: Float = 0f,
) {
    val cr = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = cr,
    )
    if (strokeColor != null) {
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = cr,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Suppress("UnusedReceiverParameter")
private val TextUnit.unused: Unit get() = Unit
