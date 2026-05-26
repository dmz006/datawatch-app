package com.dmzs.datawatchclient.ui.federation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.RemoteServerDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
public fun FederationPeersCard() {
    val scope = rememberCoroutineScope()
    var peers by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    suspend fun activeTransport() = run {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = profiles.firstOrNull {
            it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
        } ?: profiles.firstOrNull { it.enabled }
        profile?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        val transport = activeTransport() ?: return
        transport.listRemoteServers().fold(
            onSuccess = { peers = it; banner = null },
            onFailure = { banner = "Peers unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle("Federated peers", docsAnchor = "federated-observer", modifier = Modifier.weight(1f))
            IconButton(onClick = { addOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add peer")
            }
        }
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (peers.isEmpty() && banner == null) {
            Text(
                "No federated peers configured.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        peers.forEach { p ->
            val name = p.stringField("name") ?: "?"
            val url = p.stringField("url") ?: ""
            val enabled = p.boolField("enabled") ?: true
            val federated = p.boolField("federated") ?: false
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (enabled) "enabled" else "disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (federated) {
                            Text(
                                "federated",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    scope.launch {
                        runCatching {
                            activeTransport()?.deleteRemoteServer(name)?.onSuccess { reload() }
                                ?.onFailure { banner = it.message }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()
        }
    }

    if (addOpen) {
        AddPeerDialog(
            onDismiss = { addOpen = false },
            onSave = { server ->
                addOpen = false
                scope.launch {
                    runCatching {
                        activeTransport()?.addRemoteServer(server)?.onSuccess { reload() }
                            ?.onFailure { banner = it.message }
                    }
                }
            },
        )
    }
}

@Composable
private fun AddPeerDialog(onDismiss: () -> Unit, onSave: (RemoteServerDto) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var federated by remember { mutableStateOf(false) }
    var capabilities by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Federated Peer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL (e.g. https://host:8443)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text("Token (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Federated", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = federated, onCheckedChange = { federated = it })
                }
                if (federated) {
                    OutlinedTextField(
                        value = capabilities, onValueChange = { capabilities = it },
                        label = { Text("Capabilities (comma-separated)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        RemoteServerDto(
                            name = name.trim(),
                            url = url.trim(),
                            token = token.trim().takeIf { it.isNotBlank() },
                            enabled = enabled,
                            federated = federated,
                            capabilities = capabilities.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        ),
                    )
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolField(key: String): Boolean? = (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
