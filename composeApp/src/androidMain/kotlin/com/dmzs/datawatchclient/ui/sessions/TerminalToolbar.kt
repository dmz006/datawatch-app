package com.dmzs.datawatchclient.ui.sessions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Terminal toolbar — v0.11 parity affordance. Shows a search button and a
 * copy button by default; tapping search expands an inline search bar with
 * prev/next arrows and a close button, matching the PWA's terminal chrome.
 *
 * Intentionally thin — all the search logic lives in [TerminalController]
 * which marshals calls to the xterm-addon-search bridge in host.html.
 */
@Composable
public fun TerminalToolbar(
    controller: TerminalController,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Keyed to sessionId so switching sessions re-enables the button.
    var backlogLoaded by remember(sessionId) { mutableStateOf(false) }
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Persisted terminal font size. PWA uses localStorage; mobile uses
    // SharedPreferences under `dw.terminal.v1` so the user's zoom-level
    // survives session re-opens. Clamp to a sensible range (8 … 24 sp).
    val fontPrefs =
        remember(context) {
            context.getSharedPreferences("dw.terminal.v1", Context.MODE_PRIVATE)
        }
    var fontSize by remember {
        mutableStateOf(fontPrefs.getInt("font_size_px", DEFAULT_TERM_FONT_PX))
    }
    // Apply on first composition and whenever the user bumps the size.
    androidx.compose.runtime.LaunchedEffect(fontSize) {
        controller.setFontSize(fontSize)
        fontPrefs.edit().putInt("font_size_px", fontSize).apply()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!searchOpen) {
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search terminal")
                }
                IconButton(
                    onClick = { controller.copyToClipboard(context) },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selection")
                }
                IconButton(
                    onClick = {
                        if (fontSize > MIN_TERM_FONT_PX) fontSize -= FONT_STEP_PX
                    },
                    enabled = fontSize > MIN_TERM_FONT_PX,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Smaller font")
                }
                IconButton(
                    onClick = {
                        if (fontSize < MAX_TERM_FONT_PX) fontSize += FONT_STEP_PX
                    },
                    enabled = fontSize < MAX_TERM_FONT_PX,
                ) {
                    Icon(Icons.Filled.TextIncrease, contentDescription = "Larger font")
                }
                IconButton(onClick = { controller.fit() }) {
                    Icon(Icons.Filled.FitScreen, contentDescription = "Fit terminal")
                }
                IconButton(onClick = { controller.scrollToBottom() }) {
                    Icon(
                        Icons.Filled.VerticalAlignBottom,
                        contentDescription = "Jump to live tail",
                    )
                }
                if (sessionId != null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val profiles =
                                    ServiceLocator.profileRepository.observeAll().first()
                                val activeId = ServiceLocator.activeServerStore.get()
                                val profile =
                                    profiles.firstOrNull {
                                        it.id == activeId && it.enabled &&
                                            activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                                    }
                                        ?: profiles.firstOrNull { it.enabled }
                                if (profile == null) {
                                    Toast.makeText(
                                        context,
                                        "No active server",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                                ServiceLocator.transportFor(profile)
                                    .fetchOutput(sessionId, lines = 1000)
                                    .fold(
                                        onSuccess = { backlog ->
                                            controller.prepend(backlog)
                                            backlogLoaded = true
                                            Toast.makeText(
                                                context,
                                                "Loaded backlog (${backlog.length} chars)",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        onFailure = { err ->
                                            Toast.makeText(
                                                context,
                                                "Backlog load failed — ${err.message ?: err::class.simpleName}",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        },
                                    )
                            }
                        },
                        enabled = !backlogLoaded,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Load backlog",
                            tint =
                                if (backlogLoaded) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { newValue ->
                        query = newValue
                        if (newValue.isBlank()) controller.searchClear()
                    },
                    placeholder = { Text("Search terminal") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = { if (query.isNotEmpty()) controller.searchPrev(query) },
                    enabled = query.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Previous match")
                }
                IconButton(
                    onClick = { if (query.isNotEmpty()) controller.searchNext(query) },
                    enabled = query.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Next match")
                }
                IconButton(onClick = {
                    controller.searchClear()
                    query = ""
                    searchOpen = false
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            }
        }
    }
}

/**
 * Fetches the current terminal selection via [TerminalController.copySelection]
 * and writes it to the system clipboard. Shows a toast with the copied-char
 * count (or "no selection" when empty).
 */
private fun TerminalController.copyToClipboard(context: Context) {
    copySelection { selection ->
        val trimmed = selection.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "No selection to copy", Toast.LENGTH_SHORT).show()
            return@copySelection
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("datawatch terminal", trimmed))
        Toast.makeText(
            context,
            "Copied ${trimmed.length} chars",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
private fun Text(text: String) {
    // Tiny shim so OutlinedTextField's placeholder parameter stays concise
    // without importing Text at the top — keeps the import list minimal.
    androidx.compose.material3.Text(text)
}

// PWA changeTermFontSize clamps [5, 20]; mobile goes slightly larger
// since phone users zoom up to read from arm's length. Default matches
// the existing host.html `fontSize: 15` baseline.
private const val DEFAULT_TERM_FONT_PX: Int = 15
private const val MIN_TERM_FONT_PX: Int = 10
private const val MAX_TERM_FONT_PX: Int = 28
private const val FONT_STEP_PX: Int = 2
