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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val context: Context = LocalContext.current

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
