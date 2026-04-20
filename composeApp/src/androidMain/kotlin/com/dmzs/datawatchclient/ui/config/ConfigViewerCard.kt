package com.dmzs.datawatchclient.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Settings → Daemon config card. Read-only view of `GET /api/config`.
 * Top-level keys render as collapsible rows; tap to expand into a
 * pretty-printed JSON snippet. Server-masked and client-masked secrets
 * arrive as "***" and render verbatim.
 *
 * Write (PUT /api/config) is deliberately out of scope for v0.12 — a
 * structured form per ADR-0019 lands in v0.13.
 */
@Composable
public fun ConfigViewerCard(vm: ConfigViewerViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PwaSectionTitle("Daemon config", modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refresh, enabled = state.supported) {
                    if (state.loading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(6.dp))
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh config",
                            tint =
                                if (state.supported) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
            ConfigViewerBody(state = state, vm = vm)
        }
    }
}

@Composable
private fun ConfigViewerBody(
    state: ConfigViewerViewModel.UiState,
    vm: ConfigViewerViewModel,
) {
    state.banner?.let { banner ->
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    banner,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = vm::dismissBanner) { Text("Dismiss") }
            }
        }
    }

    if (state.config.raw.isEmpty() && state.supported && !state.loading) {
        Text(
            "Config empty or not yet loaded.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.config.raw.toSortedMap().entries.forEachIndexed { idx, entry ->
            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ConfigRow(key = entry.key, value = entry.value)
        }
    }

    if (state.config.raw.isNotEmpty()) {
        Text(
            "Read-only. Write lands in v0.13 via a structured form (ADR-0019).",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfigRow(
    key: String,
    value: JsonElement,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                key,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Text(
                PRETTY_JSON.encodeToString(JsonElement.serializer(), value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// Module-level pretty printer. Lenient so we never blow up on a stray value.
private val PRETTY_JSON: Json =
    Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
