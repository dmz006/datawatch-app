package com.dmzs.datawatchclient.ui.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Output / notification filters card mirroring PWA
 * `loadFilters()` / `createFilter()`. Each row shows
 * `[toggle] pattern → action(→value)` with ✎ edit + ✕ delete.
 * Add-filter form at the bottom.
 *
 * Actions match PWA: `send_input`, `alert`, `detect_prompt`,
 * `schedule`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FiltersCard() {
    val scope = rememberCoroutineScope()
    var filters by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: run {
                banner = "No enabled server."
                return
            }
        ServiceLocator.transportFor(profile).listFilters().fold(
            onSuccess = { filters = it; banner = null },
            onFailure = { banner = "Filters unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Output filters")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (filters.isEmpty() && banner == null) {
            Text(
                "No filters configured. Run `datawatch seed` on the server " +
                    "to populate the defaults, or add one below.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        filters.forEach { f ->
            val id = f.stringField("id") ?: return@forEach
            val pattern = f.stringField("pattern").orEmpty()
            val action = f.stringField("action").orEmpty()
            val value = f.stringField("value").orEmpty()
            val enabled = f.boolField("enabled") ?: true
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            val profile =
                                ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile)
                                .updateFilter(id, enabled = on).fold(
                                    onSuccess = { refresh() },
                                    onFailure = {
                                        banner =
                                            "Toggle failed — ${it.message ?: it::class.simpleName}"
                                    },
                                )
                        }
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pattern,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        if (value.isBlank()) action else "$action → $value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val profile =
                                ServiceLocator.profileRepository.observeAll().first()
                                    .firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile).deleteFilter(id).fold(
                                onSuccess = { refresh() },
                                onFailure = {
                                    banner =
                                        "Delete failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete filter",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
        }
        NewFilterForm(
            onCreate = { pattern, action, value ->
                scope.launch {
                    val profile =
                        ServiceLocator.profileRepository.observeAll().first()
                            .firstOrNull { it.enabled } ?: return@launch
                    ServiceLocator.transportFor(profile)
                        .createFilter(pattern = pattern, action = action, value = value).fold(
                            onSuccess = { refresh() },
                            onFailure = {
                                banner = "Create failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                }
            },
        )
    }
}

@Composable
private fun NewFilterForm(onCreate: (pattern: String, action: String, value: String?) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("send_input") }
    var value by remember { mutableStateOf("") }
    var actionMenuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            "Add filter",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            placeholder = { Text("Regex pattern") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                OutlinedButton(
                    onClick = { actionMenuOpen = true },
                    modifier = Modifier.width(170.dp),
                ) { Text(action, style = MaterialTheme.typography.labelMedium) }
                DropdownMenu(
                    expanded = actionMenuOpen,
                    onDismissRequest = { actionMenuOpen = false },
                ) {
                    listOf("send_input", "alert", "schedule", "detect_prompt").forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a) },
                            onClick = {
                                action = a
                                actionMenuOpen = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("Value (optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onCreate(pattern.trim(), action, value.trim().takeIf { it.isNotEmpty() })
                        pattern = ""
                        value = ""
                    }
                },
                enabled = pattern.isNotBlank(),
            ) { Text("Save filter") }
        }
    }
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolField(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
