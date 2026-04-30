package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * BL14: raw server config editor behind a mandatory confirm dialog.
 * ADR-0019 restricted raw YAML editing to structured forms; this card
 * revisits that scope with a confirm gate (overwrite → explicit user
 * intent) and a read-only default view (users must tap "Save" to mutate).
 *
 * Ships on the Settings → General tab below SecurityCard.
 */
@Composable
internal fun RawConfigCard() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var rawJson by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Section(title = "Raw config") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "View or edit the full server configuration as JSON. " +
                    "Most fields require a daemon restart to take effect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                TextButton(
                    onClick = {
                        error = null
                        loading = true
                        scope.launch {
                            val profile = ServiceLocator.activeProfileFlow().first()
                            if (profile == null) {
                                error = "No active server"
                                loading = false
                                return@launch
                            }
                            ServiceLocator.transportFor(profile).fetchConfig().fold(
                                onSuccess = { cfg ->
                                    rawJson = PRETTY_JSON.encodeToString(
                                        JsonObject.serializer(),
                                        JsonObject(cfg.raw),
                                    )
                                    editing = true
                                    error = null
                                },
                                onFailure = { err ->
                                    error = err.message ?: "Failed to fetch config"
                                },
                            )
                            loading = false
                        }
                    },
                    enabled = !loading,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Edit raw config")
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Raw config") },
            text = {
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    label = { Text("JSON") },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = true }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Overwrite config?") },
            text = {
                Text(
                    "This writes the JSON directly to config.yaml on the server. " +
                        "A daemon restart may be required for most fields to take effect.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        scope.launch {
                            val profile = ServiceLocator.activeProfileFlow().first() ?: return@launch
                            runCatching { PRETTY_JSON.parseToJsonElement(rawJson).jsonObject }
                                .fold(
                                    onSuccess = { parsed ->
                                        ServiceLocator.transportFor(profile).writeConfig(parsed).fold(
                                            onSuccess = {
                                                editing = false
                                                error = null
                                            },
                                            onFailure = { err ->
                                                error = err.message ?: "Write failed"
                                            },
                                        )
                                    },
                                    onFailure = { err ->
                                        error = "Invalid JSON: ${err.message}"
                                    },
                                )
                        }
                    },
                ) {
                    Text("Overwrite", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

private val PRETTY_JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }
