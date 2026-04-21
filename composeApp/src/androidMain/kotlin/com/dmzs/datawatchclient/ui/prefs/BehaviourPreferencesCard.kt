package com.dmzs.datawatchclient.ui.prefs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Settings → General → Behaviour card. Reads top-level
 * `recent_window_minutes` / `max_concurrent` / `scrollback_lines`
 * from `/api/config` and lets users edit them via integer inputs;
 * Save writes back the full config via `PUT /api/config` (the
 * parent replaces the document wholesale).
 *
 * ADR-0019: raw YAML is intentionally not exposed — structured
 * fields only.
 */
@Composable
public fun BehaviourPreferencesCard() {
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf<JsonObject?>(null) }
    var recentWindow by remember { mutableStateOf("") }
    var maxConcurrent by remember { mutableStateOf("") }
    var scrollback by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).fetchConfig().fold(
            onSuccess = { cfg ->
                val rawMap =
                    kotlinx.serialization.json.JsonObject(cfg.raw.toMap())
                raw = rawMap
                recentWindow = rawMap.intField("recent_window_minutes")?.toString().orEmpty()
                maxConcurrent = rawMap.intField("max_concurrent")?.toString().orEmpty()
                scrollback = rawMap.intField("scrollback_lines")?.toString().orEmpty()
            },
            onFailure = { banner = "Couldn't load config — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Behaviour preferences")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color =
                    if (it.startsWith("Saved")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (raw == null && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        IntField(
            label = "Recent-session window (minutes)",
            value = recentWindow,
            onChange = { recentWindow = it },
        )
        IntField(
            label = "Max concurrent sessions",
            value = maxConcurrent,
            onChange = { maxConcurrent = it },
        )
        IntField(
            label = "Scrollback lines",
            value = scrollback,
            onChange = { scrollback = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    val base = raw ?: return@Button
                    scope.launch {
                        val profiles = ServiceLocator.profileRepository.observeAll().first()
                        val activeId = ServiceLocator.activeServerStore.get()
                        val profile =
                            profiles.firstOrNull {
                                it.id == activeId && it.enabled &&
                                    activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                            } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                        val merged =
                            buildJsonObject {
                                base.forEach { (k, v) ->
                                    when (k) {
                                        "recent_window_minutes" ->
                                            put(k, intOrKeep(recentWindow, v))
                                        "max_concurrent" ->
                                            put(k, intOrKeep(maxConcurrent, v))
                                        "scrollback_lines" ->
                                            put(k, intOrKeep(scrollback, v))
                                        else -> put(k, v)
                                    }
                                }
                                // Add fields that didn't exist before.
                                if (!base.containsKey("recent_window_minutes")) {
                                    recentWindow.toIntOrNull()?.let {
                                        put("recent_window_minutes", JsonPrimitive(it))
                                    }
                                }
                                if (!base.containsKey("max_concurrent")) {
                                    maxConcurrent.toIntOrNull()?.let {
                                        put("max_concurrent", JsonPrimitive(it))
                                    }
                                }
                                if (!base.containsKey("scrollback_lines")) {
                                    scrollback.toIntOrNull()?.let {
                                        put("scrollback_lines", JsonPrimitive(it))
                                    }
                                }
                            }
                        ServiceLocator.transportFor(profile).writeConfig(merged).fold(
                            onSuccess = { banner = "Saved." },
                            onFailure = {
                                banner = "Save failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { s -> onChange(s.filter { it.isDigit() }) },
            singleLine = true,
            modifier = Modifier.width(110.dp),
        )
    }
}

private fun JsonObject.intField(key: String): Int? =
    (get(key) as? JsonPrimitive)?.content?.toIntOrNull()

private fun intOrKeep(text: String, fallback: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement {
    val n = text.toIntOrNull()
    return if (n != null) JsonPrimitive(n) else fallback
}
