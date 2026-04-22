package com.dmzs.datawatchclient.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * Settings → Comms → Messaging channels card. Lists configured
 * channels from `/api/channels` with per-row enable/disable switch
 * and a "Send test" action that fires `/api/channel/send`. Adding
 * new channels routes through the PWA's own config UI since parent
 * returns 501 on POST /api/channels today.
 */
@Composable
public fun ChannelsCard() {
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var testChannel by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

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
        ServiceLocator.transportFor(profile).listChannels().fold(
            onSuccess = { channels = it; banner = null },
            onFailure = {
                banner = "Couldn't load channels — ${it.message ?: it::class.simpleName}"
            },
        )
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PwaSectionTitle("Communication Configuration", modifier = Modifier.weight(1f))
            IconButton(onClick = { addOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add channel")
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
        if (channels.isEmpty() && banner == null) {
            Text(
                "No channels configured. Tap + above to add one.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        channels.forEach { ch ->
            val id = ch.stringField("id") ?: ch.stringField("name") ?: "?"
            val type = ch.stringField("type")
            val enabled = ch.boolField("enabled") ?: true
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(id, style = MaterialTheme.typography.bodyMedium)
                    type?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { testChannel = id },
                    enabled = enabled,
                ) { Text("Test", style = MaterialTheme.typography.labelSmall) }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            val profiles = ServiceLocator.profileRepository.observeAll().first()
                            val activeId = ServiceLocator.activeServerStore.get()
                            val profile =
                                profiles.firstOrNull {
                                    it.id == activeId && it.enabled &&
                                        activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                                } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile)
                                .setChannelEnabled(id, on).fold(
                                    onSuccess = { refresh() },
                                    onFailure = {
                                        banner = "Toggle failed — ${it.message ?: it::class.simpleName}"
                                    },
                                )
                        }
                    },
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val profiles = ServiceLocator.profileRepository.observeAll().first()
                            val activeId = ServiceLocator.activeServerStore.get()
                            val profile =
                                profiles.firstOrNull {
                                    it.id == activeId && it.enabled &&
                                        activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                                } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                            ServiceLocator.transportFor(profile)
                                .deleteChannel(id).fold(
                                    onSuccess = { refresh() },
                                    onFailure = {
                                        banner = "Delete failed — ${it.message ?: it::class.simpleName}"
                                    },
                                )
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete channel",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
        }
    }

    if (addOpen) {
        AddChannelDialog(
            onDismiss = { addOpen = false },
            onCreate = { type, id, enabled ->
                addOpen = false
                scope.launch {
                    val profiles = ServiceLocator.profileRepository.observeAll().first()
                    val activeId = ServiceLocator.activeServerStore.get()
                    val profile =
                        profiles.firstOrNull {
                            it.id == activeId && it.enabled &&
                                activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                        } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                    ServiceLocator.transportFor(profile)
                        .createChannel(type = type, id = id, enabled = enabled, config = null)
                        .fold(
                            onSuccess = { refresh() },
                            onFailure = {
                                banner = "Create failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                }
            },
        )
    }

    testChannel?.let { channelId ->
        TestMessageDialog(
            channelId = channelId,
            onDismiss = { testChannel = null },
            onSend = { text ->
                testChannel = null
                scope.launch {
                    val profiles = ServiceLocator.profileRepository.observeAll().first()
                    val activeId = ServiceLocator.activeServerStore.get()
                    val profile =
                        profiles.firstOrNull {
                            it.id == activeId && it.enabled &&
                                activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                        } ?: profiles.firstOrNull { it.enabled } ?: return@launch
                    ServiceLocator.transportFor(profile)
                        .sendChannelTest(channelId, text).fold(
                            onSuccess = { banner = "Test message sent to $channelId." },
                            onFailure = {
                                banner = "Test failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                }
            },
        )
    }
}

@Composable
private fun TestMessageDialog(
    channelId: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("datawatch test message") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test $channelId") },
        text = {
            Column {
                Text(
                    "The server will send this message through the channel. " +
                        "Useful to confirm the messaging backend is wired.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSend(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Lean add-channel form. Type dropdown + id text + enabled toggle.
 * The server (per dmz006/datawatch#18) accepts optional `config`
 * body per backend; mobile defaults to empty and lets users fill
 * backend-specific fields via the existing BackendConfigDialog
 * after creation — matches PWA two-step UX.
 */
@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (type: String, id: String, enabled: Boolean) -> Unit,
) {
    val types =
        listOf(
            "signal", "telegram", "discord", "slack", "matrix",
            "ntfy", "email", "twilio", "webhook", "github_webhook",
        )
    var typeMenuOpen by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(types.first()) }
    var id by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add channel") },
        text = {
            Column {
                Text("Type", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    onClick = { typeMenuOpen = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) { Text(type, style = MaterialTheme.typography.bodySmall) }
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    types.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = { type = t; typeMenuOpen = false },
                        )
                    }
                }
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Channel id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Enabled on create",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(
                    "Backend-specific config (tokens, addresses) is " +
                        "set after create via the channel's edit dialog.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (id.isNotBlank()) onCreate(type, id.trim(), enabled) },
                enabled = id.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolField(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
