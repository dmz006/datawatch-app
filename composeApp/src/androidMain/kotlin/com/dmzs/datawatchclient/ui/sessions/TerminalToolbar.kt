package com.dmzs.datawatchclient.ui.sessions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
@Composable
public fun TerminalToolbar(
    controller: TerminalController,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
) {
    val context: Context = LocalContext.current
    val fontPrefs =
        remember(context) {
            context.getSharedPreferences("dw.terminal.v1", Context.MODE_PRIVATE)
        }
    var fontSize by remember {
        mutableStateOf(fontPrefs.getInt("font_size_px", DEFAULT_TERM_FONT_PX))
    }
    var scrollMode by remember(sessionId) { mutableStateOf(false) }

    LaunchedEffect(fontSize) {
        controller.setFontSize(fontSize)
        fontPrefs.edit().putInt("font_size_px", fontSize).apply()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TermToolBtn(
                label = "A−",
                onClick = {
                    if (fontSize > MIN_TERM_FONT_PX) fontSize -= FONT_STEP_PX
                },
                enabled = fontSize > MIN_TERM_FONT_PX,
            )
            Text(
                "${fontSize}px",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TermToolBtn(
                label = "A+",
                onClick = {
                    if (fontSize < MAX_TERM_FONT_PX) fontSize += FONT_STEP_PX
                },
                enabled = fontSize < MAX_TERM_FONT_PX,
            )
            Separator()
            TermToolBtn(label = "Fit", onClick = { controller.autoFitToWidth() })
            Separator()
            TermToolBtn(
                label = if (scrollMode) "⏹ Exit" else "↕ Scroll",
                onClick = {
                    if (sessionId == null) return@TermToolBtn
                    if (scrollMode) {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: Escape")
                    } else {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "tmux-copy-mode $sessionId")
                    }
                    scrollMode = !scrollMode
                },
                enabled = sessionId != null,
                highlight = scrollMode,
            )
        }
    }
    // Scroll-mode navigation strip — shown only while in scroll mode,
    // mirrors PWA's `.scroll-bar` element inserted by toggleScrollMode().
    if (scrollMode && sessionId != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TermToolBtn(
                    label = "PgUp",
                    onClick = {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: PageUp")
                    },
                )
                TermToolBtn(
                    label = "PgDn",
                    onClick = {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: PageDown")
                    },
                )
                TermToolBtn(
                    label = "↑",
                    onClick = {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: Up")
                    },
                )
                TermToolBtn(
                    label = "↓",
                    onClick = {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: Down")
                    },
                )
                TermToolBtn(
                    label = "ESC — Exit",
                    onClick = {
                        com.dmzs.datawatchclient.transport.ws.WsOutbound
                            .sendCommand(sessionId, "sendkey $sessionId: Escape")
                        scrollMode = false
                    },
                    highlight = true,
                )
            }
        }
    }
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
private const val DEFAULT_TERM_FONT_PX: Int = 11
private const val MIN_TERM_FONT_PX: Int = 5
private const val MAX_TERM_FONT_PX: Int = 20
private const val FONT_STEP_PX: Int = 1
