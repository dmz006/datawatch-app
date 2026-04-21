package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Structured per-backend config editor. Fetches `/api/config`,
 * drills into `backends.<name>`, exposes the three most-common
 * fields (model / base_url / api_key) as text inputs, and writes
 * the merged document back via `PUT /api/config`. Other fields
 * on the backend block are preserved verbatim.
 *
 * ADR-0019: only structured edits, never raw YAML — the parent's
 * full document is round-tripped but users only see the three
 * fields mobile knows how to reason about.
 */
@Composable
public fun BackendConfigDialog(
    backendName: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf<JsonObject?>(null) }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backendName) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).fetchConfig().fold(
            onSuccess = { cfg ->
                val rawMap = JsonObject(cfg.raw.toMap())
                raw = rawMap
                val backends = rawMap["backends"] as? JsonObject
                val block = backends?.get(backendName) as? JsonObject
                model = block?.stringField("model").orEmpty()
                baseUrl = block?.stringField("base_url").orEmpty()
                apiKey = block?.stringField("api_key").orEmpty()
            },
            onFailure = { banner = "Couldn't load config — ${it.message ?: it::class.simpleName}" },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure $backendName") },
        text = {
            Column {
                banner?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key (leave blank to keep)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    "Other fields on this backend are preserved. Changes take " +
                        "effect on the next new session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = raw ?: return@TextButton
                    scope.launch {
                        val profiles = ServiceLocator.profileRepository.observeAll().first()
                        val activeId = ServiceLocator.activeServerStore.get()
                        val profile =
                            profiles.firstOrNull {
                                it.id == activeId && it.enabled &&
                                    activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                            } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                        val merged = mergeConfig(base, backendName, model, baseUrl, apiKey)
                        ServiceLocator.transportFor(profile).writeConfig(merged).fold(
                            onSuccess = { onDismiss() },
                            onFailure = {
                                banner = "Save failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun mergeConfig(
    base: JsonObject,
    backendName: String,
    model: String,
    baseUrl: String,
    apiKey: String,
): JsonObject {
    val backendsEl = base["backends"] as? JsonObject ?: JsonObject(emptyMap())
    val block = (backendsEl[backendName] as? JsonObject) ?: JsonObject(emptyMap())

    val newBlock =
        buildJsonObject {
            block.forEach { (k, v) ->
                when (k) {
                    "model" -> put(k, JsonPrimitive(model.trim()))
                    "base_url" -> put(k, JsonPrimitive(baseUrl.trim()))
                    "api_key" ->
                        // Preserve existing api_key when the field is left blank
                        // (so users don't nuke a stored secret by tapping Save
                        // after a quick model edit).
                        if (apiKey.isBlank()) put(k, v) else put(k, JsonPrimitive(apiKey.trim()))
                    else -> put(k, v)
                }
            }
            // Add-if-absent: keys the block didn't have but the user filled.
            if (!block.containsKey("model") && model.isNotBlank()) {
                put("model", JsonPrimitive(model.trim()))
            }
            if (!block.containsKey("base_url") && baseUrl.isNotBlank()) {
                put("base_url", JsonPrimitive(baseUrl.trim()))
            }
            if (!block.containsKey("api_key") && apiKey.isNotBlank()) {
                put("api_key", JsonPrimitive(apiKey.trim()))
            }
        }

    val newBackends =
        buildJsonObject {
            backendsEl.forEach { (k, v) ->
                if (k == backendName) put(k, newBlock) else put(k, v)
            }
            if (!backendsEl.containsKey(backendName)) put(backendName, newBlock)
        }

    return buildJsonObject {
        base.forEach { (k, v: JsonElement) ->
            if (k == "backends") put(k, newBackends) else put(k, v)
        }
        if (!base.containsKey("backends")) put("backends", newBackends)
    }
}
