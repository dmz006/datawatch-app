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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    autoAdvance: Boolean = !replay,
    autoAdvanceMs: Long = 3200L,
    onFinished: () -> Unit,
) {
    if (autoAdvance) {
        LaunchedEffect(Unit) {
            delay(autoAdvanceMs)
            onFinished()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1117)),
        contentAlignment = Alignment.Center,
    ) {
        MatrixSplashArtwork()

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // System nav-bar inset first so gesture / 3-button bars
                    // don't swallow the bottom of our text column on phones
                    // with edge-to-edge display.
                    .systemBarsPadding()
                    // Then a generous bottom margin so the version is still
                    // clear of the gesture indicator without hugging the edge.
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 56.dp),
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
                "AI Session Monitor",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "v${Version.VERSION}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA855F7).copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
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

/**
 * Embeddable version of the animated artwork — no version text, no "Close"
 * button. Used in Settings → About so the logo is always visible as a live
 * visual element instead of hidden behind a replay action.
 */
@Composable
public fun MatrixLogoAnimated(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF0F1117)),
        contentAlignment = Alignment.Center,
    ) {
        // compact = true: tablet sits in the moon area below horizon — the
        // settings card layout the user approved.
        MatrixSplashArtwork(compact = true)
    }
}

/**
 * Standalone animated eye — no tablet/scene chrome. Fills the given modifier
 * with a large iris + matrix rain background. Used in Settings → About as the
 * card header so the eye is prominent rather than a tiny detail inside the
 * tablet frame.
 */
@Composable
public fun EyeOnlyAnimated(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "eye-only")

    val pupilScale by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pupil",
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glow",
    )
    val rainTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rain",
    )
    val flickers: List<Float> =
        (0 until 16).map { i ->
            infinite.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.60f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = 900 + (i * 73 % 900),
                                delayMillis = (i * 41) % 500,
                                easing = FastOutSlowInEasing,
                            ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "flicker-$i",
            ).value
        }

    val eyeColumns: List<ColumnSpec> =
        remember {
            val rng = kotlin.random.Random(7979)
            List(7) {
                ColumnSpec(
                    xFrac = 0.08f + it * (0.84f / 6f),
                    durMs = 4200 + rng.nextInt(2400),
                    delayFrac = rng.nextFloat(),
                    charCount = 6 + rng.nextInt(4),
                )
            }
        }
    val eyeColumnChars: List<List<Char>> =
        remember(eyeColumns) {
            val rng = kotlin.random.Random(31)
            eyeColumns.map { col -> List(col.charCount) { MATRIX_CHARS[rng.nextInt(MATRIX_CHARS.size)] } }
        }

    Box(modifier = modifier.background(SplashPalette.Bg), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Sparse matrix rain behind the eye
            eyeColumns.forEachIndexed { colIdx, col ->
                val phase = ((rainTime + col.delayFrac) % 1f)
                val colY = -20f + phase * (size.height + 40f)
                val colX = col.xFrac * size.width
                col.charCount.let {
                    eyeColumnChars[colIdx].forEachIndexed { rowIdx, ch ->
                        val y = colY + rowIdx * 18f
                        if (y < -16f || y > size.height) return@forEachIndexed
                        val flickerIdx = (colIdx * 5 + rowIdx) % flickers.size
                        val alpha =
                            (flickers[flickerIdx] * (0.4f + 0.6f * (1f - rowIdx.toFloat() / col.charCount)))
                                .coerceIn(0f, 1f)
                        drawCircle(
                            color = SplashPalette.Matrix.copy(alpha = alpha * 0.6f),
                            radius = 3f,
                            center = Offset(colX, y),
                        )
                    }
                }
            }

            // Draw the eye large and centered
            val radius = minOf(size.width, size.height) * 0.37f
            drawEye(center = Offset(cx, cy), radius = radius, pupilScale = pupilScale, glowAlpha = glowPulse)
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
private val MATRIX_CHARS =
    ('A'..'F').toList() + ('0'..'9').toList() +
        listOf('x', 'W', 'T', 'C', 'H', 'R')

/** Stable per-column animation timing, computed once so recomposition doesn't
 *  jitter the rain. */
private data class ColumnSpec(
    val xFrac: Float, // 0..1 across the screen width
    val durMs: Int,
    val delayFrac: Float, // 0..1 phase offset
    val charCount: Int,
)

/**
 * @param compact when `true` (Settings → About card), tablet sits inside the
 *   moon area below the horizon — the layout the user approved. When `false`
 *   (full-screen splash), the tablet is centered on the canvas so the eye
 *   sits at the optical center of the screen.
 */
@Composable
private fun MatrixSplashArtwork(compact: Boolean = false) {
    val textMeasurer = rememberTextMeasurer()
    val infinite = rememberInfiniteTransition(label = "matrix")

    // Pulsing pupil — a slow sine-ish breathing so the eye feels alive.
    val pupilScale by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pupil",
    )

    // Global rain clock. Every column derives its Y from this value + its own phase.
    val rainTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rain",
    )

    // Slow scan-line sweep across the screen.
    val scanY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scan",
    )

    // Per-character opacity flicker — 64 staggered flicker tracks, each column
    // consumes N of them. rememberInfiniteTransition only gives us one value per
    // call so we build a list.
    val flickers: List<Float> =
        (0 until 32).map { i ->
            infinite.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.85f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
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
    val columns: List<ColumnSpec> =
        remember {
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
    val columnChars: List<List<Char>> =
        remember(columns) {
            val rng = Random(13)
            columns.map { col ->
                List(col.charCount) { MATRIX_CHARS[rng.nextInt(MATRIX_CHARS.size)] }
            }
        }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val discRadius = minOf(size.width, size.height) / 2.2f

        // SCENE — Apollo 8 Earthrise composition (B8 v2).
        //   • Space + stars background (drawn by Box, but we add subtle stars here)
        //   • Earth (centered above midline, with halo)
        //   • Moon surface filling lower 60% with pronounced craters
        //   • Tablet on lunar surface (showing matrix + eye)

        // Subtle starfield in upper area (deterministic positions).
        val starPositions =
            listOf(
                0.05f to 0.06f, 0.12f to 0.10f, 0.20f to 0.04f, 0.32f to 0.08f,
                0.42f to 0.05f, 0.62f to 0.07f, 0.72f to 0.04f, 0.84f to 0.10f,
                0.92f to 0.06f, 0.08f to 0.18f, 0.94f to 0.20f, 0.34f to 0.16f,
                0.66f to 0.18f,
            )
        starPositions.forEach { (xf, yf) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 1.2f,
                center = Offset(size.width * xf, size.height * yf),
            )
        }

        // Earth — slightly smaller and higher so the tablet has dominant
        // vertical real estate below the horizon.
        val earthRadius = size.width * 0.065f
        val earthCenter = Offset(cx, size.height * 0.16f)
        // Atmospheric halo
        drawCircle(
            color = Color(0xFF7AB8E8).copy(alpha = 0.30f),
            radius = earthRadius * 1.28f,
            center = earthCenter,
        )
        // Earth disc — layered for sphere illusion
        drawCircle(color = Color(0xFF0B2A55), radius = earthRadius, center = earthCenter)
        drawCircle(
            color = Color(0xFF4988C8),
            radius = earthRadius * 0.78f,
            center = Offset(earthCenter.x - earthRadius * 0.10f, earthCenter.y - earthRadius * 0.10f),
        )
        drawCircle(
            color = Color(0xFFA8D4F2),
            radius = earthRadius * 0.40f,
            center = Offset(earthCenter.x - earthRadius * 0.20f, earthCenter.y - earthRadius * 0.20f),
        )
        // Continent hint
        drawCircle(
            color = Color(0xFF2F5E36).copy(alpha = 0.7f),
            radius = earthRadius * 0.30f,
            center = Offset(earthCenter.x + earthRadius * 0.20f, earthCenter.y + earthRadius * 0.10f),
        )
        // Specular highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = earthRadius * 0.18f,
            center = Offset(earthCenter.x - earthRadius * 0.30f, earthCenter.y - earthRadius * 0.30f),
        )

        // Moon surface — horizon raised to 28% of height so the moon fills
        // ~72% of the canvas. Tablet sits firmly below the horizon with
        // moon visible above and below it.
        val horizonY = size.height * 0.28f
        // Moon body rectangle (filled solid; clipping to icon shape happens via
        // Compose's Canvas clip—we use a simple rect since the disc clip is
        // implicit at higher level).
        drawRect(
            color = Color(0xFF332B25),
            topLeft = Offset(0f, horizonY),
            size = Size(size.width, size.height - horizonY),
        )
        // Moon gradient overlay for depth
        drawRect(
            brush =
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFF7A6B5F),
                            Color(0xFF332B25),
                            Color(0xFF0F0A08),
                        ),
                    startY = horizonY,
                    endY = size.height,
                ),
            topLeft = Offset(0f, horizonY),
            size = Size(size.width, size.height - horizonY),
        )
        // Earthshine rim
        drawLine(
            color = Color(0xFF7AB8E8).copy(alpha = 0.4f),
            start = Offset(0f, horizonY),
            end = Offset(size.width, horizonY),
            strokeWidth = 2f,
        )

        // Pronounced craters on moon surface
        val craters =
            listOf(
                // x_frac, y_frac (within bottom 60%), rx_frac, ry_frac
                Triple(0.12f, 0.78f, 0.08f),
                Triple(0.88f, 0.80f, 0.09f),
                Triple(0.32f, 0.55f, 0.06f),
                Triple(0.68f, 0.55f, 0.06f),
                Triple(0.06f, 0.58f, 0.05f),
                Triple(0.94f, 0.58f, 0.04f),
                Triple(0.22f, 0.94f, 0.04f),
                Triple(0.78f, 0.94f, 0.05f),
            )
        craters.forEach { (xf, yf, sizeFrac) ->
            val crCx = size.width * xf
            val crCy = size.height * yf
            val rx = size.width * sizeFrac
            val ry = rx * 0.28f
            // Dark inner ellipse
            drawOval(
                color = Color(0xFF1A1310),
                topLeft = Offset(crCx - rx, crCy - ry),
                size = Size(rx * 2f, ry * 2f),
            )
            // Lit rim (slightly above center)
            drawOval(
                color = Color(0xFF8B7B6E).copy(alpha = 0.6f),
                topLeft = Offset(crCx - rx, crCy - ry - 1f),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 1.2f),
            )
        }

        // 3. Tablet placement depends on `compact`:
        //   - compact (settings card): centered in the moon area, below horizon
        //     — the layout user approved for the Settings render
        //   - !compact (full-screen splash): centered on the canvas so the eye
        //     ends up at the optical center of the device screen
        val tabletWidth = discRadius * 1.8f
        val tabletHeight = discRadius * 1.4f
        val tabletLeft = cx - tabletWidth / 2f
        val moonAreaCenter = (horizonY + size.height) / 2f
        val tabletTop =
            if (compact) {
                moonAreaCenter - tabletHeight / 2f
            } else {
                cy - tabletHeight / 2f
            }
        // Shadow on regolith
        drawOval(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(tabletLeft - 6f, tabletTop + tabletHeight - 6f),
            size = Size(tabletWidth + 12f, 18f),
        )
        drawRoundedRect(
            tabletLeft,
            tabletTop,
            tabletWidth,
            tabletHeight,
            cornerRadius = 28f,
            fillColor = SplashPalette.BezelDark,
            strokeColor = SplashPalette.Border,
            strokeWidth = 3f,
        )
        // Speaker slit
        drawRoundedRect(
            cx - 20f,
            tabletTop + 8f,
            40f,
            4f,
            cornerRadius = 2f,
            fillColor = Color(0xFF2D1B4E),
        )

        // 4. Screen recess — clipped matrix rain + eye go inside.
        val padding = 14f
        val screenLeft = tabletLeft + padding
        val screenTop = tabletTop + padding + 14f
        val screenWidth = tabletWidth - 2 * padding
        val screenHeight = tabletHeight - 2 * padding - 14f
        drawRoundedRect(
            screenLeft,
            screenTop,
            screenWidth,
            screenHeight,
            cornerRadius = 18f,
            fillColor = SplashPalette.ScreenDark,
            strokeColor = Color(0xFF4C1D95).copy(alpha = 0.8f),
            strokeWidth = 1f,
        )

        clipRect(
            left = screenLeft,
            top = screenTop,
            right = screenLeft + screenWidth,
            bottom = screenTop + screenHeight,
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
                    val tint =
                        when {
                            rowIdx == 0 -> SplashPalette.MatrixLead
                            rowIdx % 3 == 0 -> SplashPalette.MatrixBright
                            else -> SplashPalette.Matrix
                        }
                    val measured =
                        textMeasurer.measure(
                            text = ch.toString(),
                            style =
                                TextStyle(
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

        // 5. Eye — centered on the SCREEN recess (not on the bezel above it).
        // screenTop / screenHeight were computed during step 4. Centering on
        // the screen rectangle ensures the eye lands inside the device-screen
        // area rather than overlapping the bezel chrome above it.
        val eyeCenterY = screenTop + screenHeight / 2f
        drawEye(
            center = Offset(cx, eyeCenterY),
            radius = discRadius * 0.44f,
            pupilScale = pupilScale,
        )
    }
}

// -- drawing helpers ---------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcGroup(
    center: Offset,
    baseRadius: Float,
) {
    val arcs =
        listOf(
            Triple(baseRadius * 1.7f, 0.45f, 6f),
            Triple(baseRadius * 1.3f, 0.70f, 8f),
            Triple(baseRadius * 0.9f, 0.95f, 10f),
        )
    arcs.forEach { (r, alpha, strokeWidth) ->
        val path =
            Path().apply {
                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            left = center.x - r,
                            top = center.y - r,
                            right = center.x + r,
                            bottom = center.y + r,
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
    glowAlpha: Float = 0f,
) {
    // Outer glow halo (used by EyeOnlyAnimated; 0 on splash so it doesn't bleed over the tablet)
    if (glowAlpha > 0f) {
        drawCircle(
            color = SplashPalette.IrisMid.copy(alpha = glowAlpha * 0.55f),
            radius = radius * 2.3f,
            center = center,
        )
        drawCircle(
            color = SplashPalette.IrisInner.copy(alpha = glowAlpha * 0.28f),
            radius = radius * 1.75f,
            center = center,
        )
    }

    // Sclera ellipse — slightly wider and taller than before
    val ellipseRx = radius * 1.92f
    val ellipseRy = radius * 1.20f
    drawOval(
        color = Color(0xFF080518),
        topLeft = Offset(center.x - ellipseRx, center.y - ellipseRy),
        size = Size(ellipseRx * 2f, ellipseRy * 2f),
    )
    // Bold outer border
    drawOval(
        color = SplashPalette.Border,
        topLeft = Offset(center.x - ellipseRx, center.y - ellipseRy),
        size = Size(ellipseRx * 2f, ellipseRy * 2f),
        style = Stroke(width = 3.5f),
    )
    // Inner glow ring just inside the sclera
    drawOval(
        color = SplashPalette.IrisMid.copy(alpha = 0.40f),
        topLeft = Offset(center.x - ellipseRx * 0.91f, center.y - ellipseRy * 0.91f),
        size = Size(ellipseRx * 1.82f, ellipseRy * 1.82f),
        style = Stroke(width = 1.5f),
    )

    // Iris — layered circles for gradient depth
    drawCircle(color = SplashPalette.IrisOuter, radius = radius, center = center)
    drawCircle(color = SplashPalette.IrisMid, radius = radius * 0.82f, center = center)
    drawCircle(color = SplashPalette.IrisInner.copy(alpha = 0.75f), radius = radius * 0.52f, center = center)
    // Visible iris ring boundary
    drawCircle(
        color = SplashPalette.IrisInner.copy(alpha = 0.55f),
        radius = radius * 0.80f,
        center = center,
        style = Stroke(width = 1.8f),
    )

    // Pupil (pulsing)
    drawCircle(color = SplashPalette.Pupil, radius = radius * 0.38f * pupilScale, center = center)
    // Pupil ring
    drawCircle(
        color = SplashPalette.IrisOuter.copy(alpha = 0.65f),
        radius = radius * 0.42f * pupilScale,
        center = center,
        style = Stroke(width = 1.2f),
    )

    // Crosshair — bolder, extends further toward sclera edge
    val cr = radius * 0.40f
    val crGap = radius * 0.13f
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x, center.y - cr),
        end = Offset(center.x, center.y - crGap),
        strokeWidth = 4.5f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x, center.y + crGap),
        end = Offset(center.x, center.y + cr),
        strokeWidth = 4.5f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x - cr, center.y),
        end = Offset(center.x - crGap, center.y),
        strokeWidth = 4.5f,
    )
    drawLine(
        color = SplashPalette.Crosshair,
        start = Offset(center.x + crGap, center.y),
        end = Offset(center.x + cr, center.y),
        strokeWidth = 4.5f,
    )

    // Center highlight (pulses with pupil)
    drawCircle(
        color = SplashPalette.Highlight,
        radius = radius * 0.09f * pupilScale,
        center = center,
    )
    // Lens highlight (upper-left, static)
    rotate(-30f, Offset(center.x - radius * 0.42f, center.y - radius * 0.28f)) {
        drawOval(
            color = Color.White.copy(alpha = 0.13f),
            topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.32f),
            size = Size(radius * 0.28f, radius * 0.13f),
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
