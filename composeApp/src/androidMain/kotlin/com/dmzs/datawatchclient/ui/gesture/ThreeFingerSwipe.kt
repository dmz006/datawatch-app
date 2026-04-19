package com.dmzs.datawatchclient.ui.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Detects a three-finger upward swipe of at least [thresholdDp] dp and calls [onFired].
 *
 * - Uses [PointerEventPass.Initial] so we see touches *before* children; we do not
 *   consume them, so normal child gestures (scrolls, clicks) still work.
 * - "Upward" = net dy < 0 in Compose pointer coordinates (top-left origin).
 * - Debounced by [debounceMs] so one physical gesture fires at most once.
 * - Tracking resets whenever pointer count drops below 3.
 */
public fun Modifier.threeFingerSwipeUp(
    thresholdDp: Dp = 64.dp,
    debounceMs: Long = 500L,
    onFired: () -> Unit,
): Modifier = composed {
    pointerInput(Unit) {
        val thresholdPx = thresholdDp.toPx()
        var startY: Float? = null
        var lastFireTs = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val active = event.changes.filter { it.pressed }
                if (active.size >= 3) {
                    val avgY = active.map { it.position.y }.average().toFloat()
                    val anchor = startY
                    if (anchor == null) {
                        startY = avgY
                    } else {
                        val dy = avgY - anchor
                        val now = System.currentTimeMillis()
                        if (-dy >= thresholdPx && (now - lastFireTs) > debounceMs) {
                            lastFireTs = now
                            startY = null
                            onFired()
                        }
                    }
                } else {
                    startY = null
                }
            }
        }
    }
}
