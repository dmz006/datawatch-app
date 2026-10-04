package com.dmzs.datawatchclient.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.common.DocsViewerSheet

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
    // PWA dw-running-pulse (parity D18): 0.55-1.0, 700 ms ease-in-out, off under reduced motion.
    val alpha by rememberRunningPulseAlpha(state == SessionState.Running)
    Box(
        modifier =
            Modifier
                .background(color = bg.copy(alpha = bg.alpha * alpha), shape = RoundedCornerShape(10.dp))
                .border(width = 1.dp, color = fg.copy(alpha = alpha), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            state.label(),
            fontSize = 10.sp,
            color = fg.copy(alpha = alpha),
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
    // PWA `pulse-border`: waiting_input 2 s (waiting <-> #93c5fd), rate_limited 3 s
    // (warning <-> amber-300); static under reduced motion.
    val pulsing = state == SessionState.Waiting || state == SessionState.RateLimited
    val t by rememberDwPulse(
        initial = 0f,
        target = 1f,
        durationMs = if (state == SessionState.RateLimited) 1500 else 1000,
        staticValue = 0f,
        active = pulsing,
        label = "pulse-border",
    )
    val peak = if (state == SessionState.RateLimited) Color(0xFFFCD34D) else Color(0xFF93C5FD)
    val edge = if (pulsing) androidx.compose.ui.graphics.lerp(color, peak, t) else color
    return drawBehind {
        drawRect(
            brush = SolidColor(edge),
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
        // clip FIRST so pwaStateEdge's drawBehind rect is also rounded at the corners
        .clip(RoundedCornerShape(12.dp))
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
 *
 * When [docsAnchor] is non-null, the title is shown in a Row with a
 * small "?" button that opens the corresponding section in the
 * in-app docs viewer.
 */
@Composable
public fun PwaSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    docsAnchor: String? = null,
) {
    if (docsAnchor != null) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).weight(1f),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
            DocsInlineButton(anchor = docsAnchor)
        }
    } else {
        Text(
            title.uppercase(),
            modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
    }
}

/**
 * Small inline "?" button that opens the given docs [anchor] in the
 * in-app WebView sheet. Hidden when no active server is configured.
 * Used inside [PwaSectionTitle] when [docsAnchor] is non-null.
 */
@Composable
internal fun DocsInlineButton(anchor: String) {
    val profiles by ServiceLocator.profileRepository.observeAll().collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe().collectAsState(initial = null)
    val activeProfile =
        remember(profiles, activeId) {
            val enabled = profiles.filter { it.enabled }
            if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                enabled.firstOrNull()
            } else {
                (enabled.firstOrNull { it.id == activeId } ?: enabled.firstOrNull())
            }
        }
    val baseUrl = activeProfile?.baseUrl
    val allowSelfSigned = activeProfile?.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL
    var showDocs by remember { mutableStateOf(false) }

    if (baseUrl != null) {
        val url = "$baseUrl/diagrams.html#docs/datawatch-definitions.md#$anchor"
        TextButton(
            onClick = { showDocs = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text("?", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        if (showDocs) {
            DocsViewerSheet(
                url = url,
                onDismiss = { showDocs = false },
                allowSelfSigned = allowSelfSigned,
                pinSha256 = ServiceLocator.pinFor(activeProfile),
            )
        }
    }
}

/**
 * Subtle row divider. Matches PWA `.settings-row` `border-top: 1px solid
 * var(--border)` without drawing a full-width `HorizontalDivider` — the
 * PWA inset is zero so we expose the same behaviour.
 */
public val PwaRowDividerColor: Color
    @Composable get() = LocalDatawatchColors.current.border

/**
 * Tight input text style used inside Settings cards. Material3's
 * default OutlinedTextField/OutlinedButton render at 16sp which
 * looks oversized next to the PWA's 13px `.form-input`. Pass this
 * to each input's `textStyle = pwaInputTextStyle()` param so the
 * input baselines line up across Schedules, Channels, Commands,
 * Filters, Profiles and the ConfigFieldsPanel inputs.
 */
@Composable
public fun pwaInputTextStyle(): androidx.compose.ui.text.TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
