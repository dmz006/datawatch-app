package com.dmzs.datawatchclient.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.domain.SessionState

/**
 * Shared visual primitives that mirror the parent PWA's `style.css`.
 * These aren't a theme by themselves — they're small composables that
 * bake the PWA CSS values into Compose so every screen can use the
 * same look-and-feel without re-implementing the same gradients and
 * borders each time.
 *
 * Source: `internal/server/web/style.css` at the v4.0.3 tag.
 */

/**
 * PWA state-pill / badge. 10sp / 2dp 7dp padding / 10dp rounded /
 * uppercase / 0.3sp letter-spacing / 600 weight / tinted background
 * (0.15 alpha) with full-colour text. Byte-for-byte mirror of
 * `.state-badge-*` classes.
 */
@Composable
public fun PwaStatePill(state: SessionState) {
    val dw = LocalDatawatchColors.current
    val (bg, fg) =
        when (state) {
            SessionState.Running -> dw.success.copy(alpha = 0.15f) to dw.success
            SessionState.Waiting -> dw.waiting.copy(alpha = 0.15f) to dw.waiting
            SessionState.RateLimited -> dw.warning.copy(alpha = 0.15f) to dw.warning
            SessionState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f) to MaterialTheme.colorScheme.error
            SessionState.Completed,
            SessionState.Killed,
            SessionState.New,
            ->
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f) to
                    MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .background(color = bg, shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            state.label(),
            fontSize = 10.sp,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

/**
 * PWA wire-format labels (`internal/server/web/app.js` renders these
 * verbatim on `.state` span). We keep the exact tokens so mobile users
 * can describe a session state to someone on the web and both are
 * talking about the same badge.
 */
private fun SessionState.label(): String =
    when (this) {
        SessionState.Running -> "running"
        SessionState.Waiting -> "waiting_input"
        SessionState.RateLimited -> "rate_limited"
        SessionState.Completed -> "complete"
        SessionState.Killed -> "killed"
        SessionState.Error -> "failed"
        SessionState.New -> "new"
    }

/**
 * State-colored left edge for a session row. 4dp wide stripe painted
 * behind the row; the row itself supplies padding. Mirrors
 * `.session-card.state-*` border-left.
 */
@Composable
public fun Modifier.pwaStateEdge(state: SessionState): Modifier {
    val dw = LocalDatawatchColors.current
    val color =
        when (state) {
            SessionState.Running -> dw.success
            SessionState.Waiting -> dw.waiting
            SessionState.RateLimited -> dw.warning
            SessionState.Error -> MaterialTheme.colorScheme.error
            SessionState.Completed, SessionState.Killed, SessionState.New ->
                MaterialTheme.colorScheme.onSurfaceVariant
        }
    return drawBehind {
        drawRect(
            brush = SolidColor(color),
            topLeft = Offset.Zero,
            size = Size(4.dp.toPx(), size.height),
        )
    }
}

/**
 * Standard card surface mirroring PWA `.session-card` / `.settings-section`.
 * Rounded 12dp, `--bg2` fill, 1dp `--border` stroke. Child padding is the
 * caller's responsibility so the card can host either a dense list or a
 * single row.
 */
@Composable
public fun Modifier.pwaCard(): Modifier {
    val dw = LocalDatawatchColors.current
    return this
        .background(color = dw.bg2, shape = RoundedCornerShape(12.dp))
        .border(
            width = 1.dp,
            color = dw.border,
            shape = RoundedCornerShape(12.dp),
        )
}

/**
 * 11sp uppercase section heading used in Settings cards. Mirrors
 * `.settings-section-title` (11px, text2 color, 0.8px letter-spacing,
 * 10px 16px padding).
 */
@Composable
public fun PwaSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title.uppercase(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
    )
}

/**
 * Subtle row divider. Matches PWA `.settings-row` `border-top: 1px solid
 * var(--border)` without drawing a full-width `HorizontalDivider` — the
 * PWA inset is zero so we expose the same behaviour.
 */
public val PwaRowDividerColor: Color
    @Composable get() = LocalDatawatchColors.current.border
