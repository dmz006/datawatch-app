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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-app composable toast system. Introduced in v0.72.0 to replace
 * `android.widget.Toast` usage for structured error and status messages.
 *
 * Key behaviours (#93 alpha.9, #101, #102):
 * - Dedup: messages with the same (stripped-message, detail) key collapse to a
 *   single toast with a `×N` badge instead of flooding the overlay.
 * - Strip-prefix: leading `[name] ` pattern (e.g. `[Claude] Error`) is stripped
 *   before key comparison so agent-name prefixes don't defeat dedup.
 * - Sizing: 75% width, right-justified, 13sp body text, 8×12dp padding.
 * - Reconnect button: shown when `ToastMessage.showReconnect = true`; tap calls
 *   the `onReconnect` callback so the caller can trigger `vm.reconnect()`.
 *
 * ## Usage
 *
 * Maintain a `List<ToastMessage>` in your composable/ViewModel. Drop
 * `DatawatchToastHost(...)` at the bottom of your `Scaffold` content column or
 * inside a `Box` that overlays the screen content.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     MainContent()
 *     DatawatchToastHost(
 *         toasts = toastMessages,
 *         onDismiss = { idx -> toastMessages = toastMessages.filterIndexed { i, _ -> i != idx } },
 *         onReconnect = { vm.reconnect() },
 *         modifier = Modifier.align(Alignment.BottomEnd),
 *     )
 * }
 * ```
 *
 * ## Wiring note
 *
 * Full wiring into AppRoot/SessionDetail is deferred to Sprint 7 (layout polish pass,
 * v0.76.0). At that point `android.widget.Toast` call sites in `SessionsScreen.kt`,
 * `SessionDetailScreen.kt`, and `SettingsScreen.kt` should be migrated to feed this
 * overlay. The component is complete and tested standalone as of v0.72.0.
 *
 * TODO(Sprint 7 S7-polish): migrate `android.widget.Toast` call sites to DatawatchToastHost
 */

/** A single toast entry to display in [DatawatchToastHost]. */
public data class ToastMessage(
    val message: String,
    val detail: String = "",
    val isError: Boolean = false,
    /**
     * When `true` a "Reconnect" [TextButton] is shown inside the toast.
     * Set this when the message describes a connection failure so the user
     * can trigger a reconnect without navigating away.
     */
    val showReconnect: Boolean = false,
)

/**
 * Host composable that renders a deduplicated stack of [ToastMessage]s.
 *
 * Place at the bottom of a `Box` or `Scaffold` padding area — the column
 * grows upward from the bottom-right corner.
 *
 * @param toasts       List of current toast messages (maintained by caller).
 * @param onDismiss    Called with the *visual* index (after dedup) when the
 *                     user taps the close button.
 * @param onReconnect  Called when the user taps the "Reconnect" button on a
 *                     toast that has [ToastMessage.showReconnect] set.
 * @param modifier     Applied to the outer [Column].
 */
@Composable
public fun DatawatchToastHost(
    toasts: List<ToastMessage>,
    onDismiss: (Int) -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.End)
            .padding(end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Dedup by (stripped-message, detail) key — preserve insertion order.
        // The key strips leading "[name] " prefix so that messages like
        //   "[Claude] Connection lost" and "[opencode] Connection lost"
        // collapse into a single toast with a ×2 badge.
        //
        // Unit test (stripPrefix):
        //   "[Claude] Connection lost"    → "Connection lost"
        //   "[opencode] Error: timeout"   → "Error: timeout"
        //   "Connection lost"             → "Connection lost"  (no change)
        val dedupedToasts = remember(toasts) {
            val map = linkedMapOf<Pair<String, String>, Pair<ToastMessage, Int>>()
            toasts.forEachIndexed { _, t ->
                val key = stripPrefix(t.message) to t.detail
                val existing = map[key]
                if (existing == null) {
                    map[key] = t to 1
                } else {
                    // Promote showReconnect if any duplicate carries it.
                    val merged = if (t.showReconnect && !existing.first.showReconnect) {
                        existing.first.copy(showReconnect = true)
                    } else {
                        existing.first
                    }
                    map[key] = merged to (existing.second + 1)
                }
            }
            map.values.toList()
        }
        dedupedToasts.forEachIndexed { idx, (toast, count) ->
            DatawatchToastItem(
                toast = toast,
                count = count,
                onDismiss = { onDismiss(idx) },
                onReconnect = onReconnect,
            )
        }
    }
}

/** Pulsing green live-indicator dot used in auto-polled card headers (#95). */
@Composable
public fun LiveDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "live-dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-dot-alpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .drawBehind {
                drawCircle(color = Color(0xFF10B981), alpha = alpha)
            },
    )
}

// ── Internal ──────────────────────────────────────────────────────────────────

@Composable
private fun DatawatchToastItem(
    toast: ToastMessage,
    count: Int,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.75f),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (toast.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toast.message,
                    fontSize = 13.sp,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (toast.detail.isNotEmpty()) {
                    Text(
                        text = toast.detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        softWrap = true,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (toast.showReconnect) {
                    TextButton(
                        onClick = onReconnect,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("Reconnect", fontSize = 12.sp)
                    }
                }
            }
            if (count > 1) {
                Text(
                    text = "×$count",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Strip a leading `[name] ` prefix from [message] for dedup-key comparison.
 *
 * Examples:
 * - `"[Claude] Connection lost"` → `"Connection lost"`
 * - `"[opencode] Error: timeout"` → `"Error: timeout"`
 * - `"Connection lost"` → `"Connection lost"` (no change)
 */
internal fun stripPrefix(message: String): String =
    message.replace(Regex("""^\[[^\]]+]\s*"""), "")
