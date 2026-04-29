package com.dmzs.datawatchclient.ui.sessions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Terminal toolbar — exact PWA parity (app.js:1635-1643).
 *
 * Four controls, in order:
 * 1. **A−**  — decrease font size (clamp 5..20)
 * 2. **`{size}px`** — current font size, non-interactive label
 * 3. **A+**  — increase font size
 * 4. **Fit** — [TerminalController.autoFitToWidth] shrinks font until xterm fits the
 *    container horizontally
 * 5. **↕ Scroll / ⏹ Exit Scroll** — enters / exits tmux scroll mode (copy-mode).
 *    While in scroll mode, a second row appears with Page Up / Page Down / Line Up /
 *    Line Down / ESC, matching PWA `toggleScrollMode()` (app.js:1911).
 *
 * Previous mobile-only extras (search / copy / jump-to-bottom / load-backlog)
 * were removed in v0.33.18 because they don't exist in PWA and the four-button
 * row matches user muscle-memory when switching between web and phone.
 */

/**
 * Hoisted state for [TerminalToolbarControls] + [TerminalScrollModeStrip].
 *
 * v0.42.0 (user direction 2026-04-28): the toolbar no longer renders
 * its own row — the font / scroll buttons inline next to the
 * tmux/channel pills like in the PWA. Hoisting the state lets the
 * caller place [TerminalToolbarControls] inline in the tabs row and
 * [TerminalScrollModeStrip] separately under the terminal viewport.
 */
public class TerminalToolbarState internal constructor(
    public val controller: TerminalController,
    public val sessionId: String?,
    private val fontSizeState: androidx.compose.runtime.MutableState<Int>,
    private val scrollModeState: androidx.compose.runtime.MutableState<Boolean>,
) {
    public var fontSize: Int
        get() = fontSizeState.value
        set(value) {
            fontSizeState.value = value
        }

    public var scrollMode: Boolean
        get() = scrollModeState.value
        set(value) {
            scrollModeState.value = value
            controller.setScrollMode(value)
        }
}

@Composable
public fun rememberTerminalToolbarState(
    controller: TerminalController,
    sessionId: String? = null,
): TerminalToolbarState {
    val context: Context = LocalContext.current
    val fontPrefs =
        remember(context) {
            context.getSharedPreferences("dw.terminal.v1", Context.MODE_PRIVATE)
        }
    val fontSizeState =
        remember {
            mutableStateOf(fontPrefs.getInt("font_size_px", DEFAULT_TERM_FONT_PX))
        }
    val scrollModeState = remember(sessionId) { mutableStateOf(false) }

    LaunchedEffect(fontSizeState.value) {
        controller.setFontSize(fontSizeState.value)
        fontPrefs.edit().putInt("font_size_px", fontSizeState.value).apply()
    }

    return remember(controller, sessionId) {
        TerminalToolbarState(controller, sessionId, fontSizeState, scrollModeState)
    }
}

/**
 * The font / fit / scroll buttons. v0.42.0 — inline-only (no Surface
 * wrap) so the caller can drop the controls into a tabs Row beside
 * the tmux/channel pills (PWA layout).
 */
@Composable
public fun TerminalToolbarControls(
    state: TerminalToolbarState,
    modifier: Modifier = Modifier,
) {
    val controller = state.controller
    val sessionId = state.sessionId
    // v0.42.12 — tighten the inline toolbar so the Scroll button
    // stays visible at phone widths. Drop the "{N}px" font-size
    // label (the A−/A+ buttons themselves communicate the action;
    // the size only matters when actively tweaking) and the two
    // visual `|` separators (decorative, ~20 dp saved). Compact
    // labels — "F" for Fit, "↕" for Scroll, "⏹" for Exit — keep
    // every button on one line at any phone width.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TermToolBtn(
            label = "A−",
            onClick = {
                if (state.fontSize > MIN_TERM_FONT_PX) state.fontSize -= FONT_STEP_PX
            },
            enabled = state.fontSize > MIN_TERM_FONT_PX,
        )
        TermToolBtn(
            label = "A+",
            onClick = {
                if (state.fontSize < MAX_TERM_FONT_PX) state.fontSize += FONT_STEP_PX
            },
            enabled = state.fontSize < MAX_TERM_FONT_PX,
        )
        TermToolBtn(label = "Fit", onClick = { controller.autoFitToWidth() })
        TermToolBtn(
            label = if (state.scrollMode) "⏹" else "📜",
            onClick = {
                if (sessionId == null) return@TermToolBtn
                if (state.scrollMode) {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "sendkey $sessionId: Escape")
                } else {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "tmux-copy-mode $sessionId")
                }
                state.scrollMode = !state.scrollMode
            },
            enabled = sessionId != null,
            highlight = state.scrollMode,
        )
    }
}

/**
 * Full-width scroll-mode overlay. Replaces the composer/mic/send area
 * while [state.scrollMode] is active, matching the PWA scroll mode UX:
 * large PgUp / PgDn / Line-Up / Line-Down / ESC targets are easier to
 * tap than the tiny quick-action row, and covering the input area makes
 * the intent obvious — you're reading, not typing.
 *
 * Returns `true` if scroll mode is active (callers can skip rendering
 * the composer when this returns true).
 */
@Composable
public fun TerminalScrollModeStrip(state: TerminalToolbarState) {
    val sessionId = state.sessionId ?: return
    if (!state.scrollMode) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Single row: Page Up | Page Down | ESC / Exit — fits one line on any phone.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BigScrollBtn(
                label = "Page Up",
                modifier = Modifier.weight(1f),
                onClick = {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "sendkey $sessionId: PageUp")
                },
            )
            BigScrollBtn(
                label = "Page Down",
                modifier = Modifier.weight(1f),
                onClick = {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "sendkey $sessionId: PageDown")
                },
            )
            BigScrollBtn(
                label = "ESC / Exit",
                modifier = Modifier.weight(1f),
                highlight = true,
                onClick = {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "sendkey $sessionId: Escape")
                    state.scrollMode = false
                },
            )
        }
    }
}

@Composable
private fun BigScrollBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        colors = if (highlight) {
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Legacy wrapper kept for any caller still passing the bundled
 * toolbar shape. Renders the controls in their own Surface row.
 *
 * **Deprecated** in v0.42.0 — prefer [rememberTerminalToolbarState] +
 * [TerminalToolbarControls] + [TerminalScrollModeStrip] so the
 * controls can sit on the tmux/channel tabs row and the strip can
 * sit under the terminal viewport (PWA layout).
 */
@Composable
public fun TerminalToolbar(
    controller: TerminalController,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
) {
    val state = rememberTerminalToolbarState(controller, sessionId)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        TerminalToolbarControls(state, modifier = Modifier.padding(horizontal = 4.dp))
    }
    TerminalScrollModeStrip(state)
}

@Composable
private fun TermToolBtn(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            // v0.42.11 — `maxLines = 1, softWrap = false` keep the
            // button on a single row even when the inline toolbar is
            // squished against the screen edge. Without this, the
            // last button ("↕ Scroll" / "⏹ Exit") was wrapping into
            // ~5 stacked lines on phone widths and dragging the row
            // 154 dp tall via Compose's CenterVertically alignment —
            // surfaced as the empty band above the tabs row that
            // user reported 2026-04-29.
            maxLines = 1,
            softWrap = false,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal,
            color =
                if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun Separator() {
    Text(
        "|",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

// PWA `changeTermFontSize` clamps [5, 20]; mobile keeps the same range so
// the saved size round-trips through localStorage == SharedPreferences.
// Default matches host.html's fontSize: 11 (mobile-friendly floor vs PWA's 9).
private const val DEFAULT_TERM_FONT_PX: Int = 9
private const val MIN_TERM_FONT_PX: Int = 5
private const val MAX_TERM_FONT_PX: Int = 20
private const val FONT_STEP_PX: Int = 1
