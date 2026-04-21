package com.dmzs.datawatchclient.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Daemon config viewer — PWA Settings/Config parity (read-only for v0.12;
 * editable form lands v0.13 per ADR-0019).
 *
 * The PWA renders `/api/config` as one card per top-level section (server,
 * session, telegram, slack, ollama, openwebui, anthropic, …) with the
 * key=value pairs shown inline. This mirror renders the same layout:
 * one `pwaCard()` per section, rows of `key value`, nested objects
 * indented one level. Masked secrets (server sends `***` or mobile's
 * client-side secondary mask) render as `***` in monospace.
 */
@Composable
public fun ConfigViewerCard(vm: ConfigViewerViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // Header card with refresh action + banner. Each config section
    // renders as its own card below, mirroring the PWA's per-section
    // grouping.
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
            }
            if (state.config.raw.isNotEmpty()) {
                Text(
                    "Read-only. Editable form lands v0.13 (ADR-0019).",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // One card per top-level config section. Sorted alphabetically so the
    // layout is stable across refreshes.
    state.config.raw.toSortedMap().forEach { (sectionName, sectionValue) ->
        ConfigSectionCard(name = sectionName, value = sectionValue)
    }
}

@Composable
private fun ConfigSectionCard(
    name: String,
    value: JsonElement,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle(name)
            ConfigSectionBody(value)
        }
    }
}

/**
 * Render the body of a section. JsonObject flattens into key=value rows;
 * primitives render as a single row; arrays render as a bullet list.
 */
@Composable
private fun ConfigSectionBody(
    value: JsonElement,
    indent: Int = 0,
) {
    when (value) {
        is JsonObject -> {
            value.entries.forEachIndexed { idx, (key, v) ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                when (v) {
                    is JsonObject -> {
                        // Nested section — render an inset sub-header + body.
                        Text(
                            key,
                            modifier =
                                Modifier.padding(
                                    start = (16 + indent * 12).dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 2.dp,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ConfigSectionBody(v, indent = indent + 1)
                    }
                    else -> ConfigKeyValueRow(key = key, value = v, indent = indent)
                }
            }
        }
        is JsonPrimitive -> {
            Text(
                value.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        is JsonArray -> {
            value.forEach { el ->
                Text(
                    "• ${primitiveOrRepr(el)}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        is JsonNull -> {
            Text(
                "—",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigKeyValueRow(
    key: String,
    value: JsonElement,
    indent: Int,
) {
    val startPadding = (16 + indent * 12).dp
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = startPadding, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            key,
            modifier = Modifier.weight(1f, fill = true).padding(end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val (display, isSecret) = formatValue(value)
        Text(
            display,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color =
                if (isSecret) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

private fun formatValue(value: JsonElement): Pair<String, Boolean> =
    when (value) {
        is JsonPrimitive -> {
            val s = value.content
            val isSecret = s == "***"
            s to isSecret
        }
        is JsonNull -> "—" to false
        is JsonArray -> value.joinToString(", ") { primitiveOrRepr(it) } to false
        is JsonObject -> "{…}" to false
    }

private fun primitiveOrRepr(el: JsonElement): String =
    when (el) {
        is JsonPrimitive -> el.content
        is JsonNull -> "null"
        else -> el.toString()
    }
