package com.dmzs.datawatchclient.ui.tailscale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * v0.80.0 Sprint 11 — Tailscale config card.
 * Reads cfg.tailscale.* from GET /api/config; saves via PUT /api/config
 * dotted-key patch (same mechanism as ConfigFieldsPanel).
 */
@Composable
public fun TailscaleSettingsCard() {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var coordinatorUrl by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var authKey by remember { mutableStateOf("") }
    var authKeyHasValue by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyHasValue by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun resolveTransport() = run {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
            ?: profiles.firstOrNull { it.enabled }
        profile?.let { ServiceLocator.transportFor(it) }
    }

    fun load() {
        scope.launch {
            val transport = resolveTransport() ?: return@launch
            transport.fetchConfig().fold(
                onSuccess = { cfg ->
                    val raw = cfg.raw
                    val tsObj = (raw["tailscale"] as? kotlinx.serialization.json.JsonObject)
                    enabled = tsObj?.get("enabled")?.jsonPrimitive?.content?.toBoolean() ?: false
                    coordinatorUrl = tsObj?.get("coordinator_url")?.jsonPrimitive?.content ?: ""
                    image = tsObj?.get("image")?.jsonPrimitive?.content ?: ""
                    val ak = tsObj?.get("auth_key")?.jsonPrimitive?.content ?: ""
                    authKeyHasValue = ak.isNotEmpty() && ak != "***"
                    authKey = ""
                    val apk = tsObj?.get("api_key")?.jsonPrimitive?.content ?: ""
                    apiKeyHasValue = apk.isNotEmpty() && apk != "***"
                    apiKey = ""
                    loadError = null
                },
                onFailure = { loadError = it.message },
            )
        }
    }
    LaunchedEffect(Unit) { load() }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.tailscale_section_config), docsAnchor = "tailscale-configuration")
            if (loadError != null) {
                Text(
                    loadError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // Enabled toggle
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tailscale_enabled_label),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = coordinatorUrl,
                onValueChange = { coordinatorUrl = it },
                label = { Text(stringResource(R.string.tailscale_coordinator_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = image,
                onValueChange = { image = it },
                label = { Text(stringResource(R.string.tailscale_image_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = authKey,
                onValueChange = { authKey = it },
                label = { Text(stringResource(R.string.tailscale_authkey_label)) },
                placeholder = {
                    if (authKeyHasValue) {
                        Text(stringResource(R.string.tailscale_authkey_placeholder), style = MaterialTheme.typography.bodySmall)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                visualTransformation = if (authKey.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.tailscale_apikey_label)) },
                placeholder = {
                    if (apiKeyHasValue) {
                        Text(stringResource(R.string.tailscale_apikey_placeholder), style = MaterialTheme.typography.bodySmall)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                visualTransformation = if (apiKey.isEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val transport = resolveTransport() ?: return@launch
                    val patch = buildJsonObject {
                        put("tailscale.enabled", JsonPrimitive(enabled))
                        if (coordinatorUrl.isNotBlank()) {
                            put("tailscale.coordinator_url", JsonPrimitive(coordinatorUrl))
                        }
                        if (image.isNotBlank()) {
                            put("tailscale.image", JsonPrimitive(image))
                        }
                        if (authKey.isNotBlank()) {
                            put("tailscale.auth_key", JsonPrimitive(authKey))
                        }
                        if (apiKey.isNotBlank()) {
                            put("tailscale.api_key", JsonPrimitive(apiKey))
                        }
                    }
                    transport.writeConfig(patch).fold(
                        onSuccess = { saveStatus = "Saved"; load() },
                        onFailure = { saveStatus = it.message ?: "Error" },
                    )
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
            if (saveStatus.isNotEmpty()) {
                Text(
                    saveStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
