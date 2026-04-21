package com.dmzs.datawatchclient.ui.ops

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.LogsView
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Settings → Monitor cards wrapping the `/api/logs`,
 * `/api/interfaces`, and `/api/restart` ops endpoints. Lightweight
 * per-card VMs would be nicer but given these are one-shot
 * read-most surfaces, an inline composable keeps the sprint short.
 * Rewrite these as proper VMs if/when we add edit actions.
 */

private suspend fun resolveActiveProfile(): ServerProfile? {
    val profiles = ServiceLocator.profileRepository.observeAll().first()
    val activeId = ServiceLocator.activeServerStore.get()
    return profiles.firstOrNull {
        it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
    } ?: profiles.firstOrNull { it.enabled }
}

@Composable
public fun DaemonLogCard() {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf<LogsView?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf<String?>(null) }

    // PWA polls /api/logs every 10 s while Monitor is visible.
    LaunchedEffect(offset) {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val profile = resolveActiveProfile()
            if (profile != null) {
                ServiceLocator.transportFor(profile)
                    .fetchLogs(lines = 50, offset = offset)
                    .fold(
                        onSuccess = { view = it; banner = null },
                        onFailure = { banner = "Logs unavailable — ${it.message ?: it::class.simpleName}" },
                    )
            }
            delay(10_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Daemon log")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val v = view
        if (v != null) {
            Text(
                "Showing ${v.lines.size} of ${v.total} lines (offset $offset)",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                v.lines.forEach { line ->
                    val color =
                        when {
                            line.contains("[error]") || line.contains("ERROR") ->
                                MaterialTheme.colorScheme.error
                            line.contains("[warn]") || line.contains("WARNING") ->
                                MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { offset = maxOf(0, offset - 50) },
                    enabled = offset > 0,
                ) { Text("Newer") }
                OutlinedButton(
                    onClick = { offset += 50 },
                    enabled = (offset + v.lines.size) < v.total,
                ) { Text("Older") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    offset = 0
                    scope.launch {
                        val profile = resolveActiveProfile() ?: return@launch
                        ServiceLocator.transportFor(profile)
                            .fetchLogs(lines = 50, offset = 0)
                            .onSuccess { view = it }
                    }
                }) { Text("Refresh") }
            }
        }
    }
}

@Composable
public fun InterfacesCard() {
    var interfaces by remember {
        mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList())
    }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val profile = resolveActiveProfile() ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listInterfaces().fold(
            onSuccess = { interfaces = it },
            onFailure = { banner = "Interfaces unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Network interfaces")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (interfaces.isEmpty() && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        interfaces.forEach { iface ->
            val name = iface.stringField("name") ?: "?"
            val mac = iface.stringField("mac")
            val ips =
                (iface["ips"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                } ?: emptyList()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    name,
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Column(modifier = Modifier.weight(1f)) {
                    if (ips.isNotEmpty()) {
                        Text(
                            ips.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    mac?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonObject.stringField(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

@Composable
public fun UpdateDaemonCard() {
    var banner by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Daemon update")
        Text(
            "Check for and install a new datawatch daemon version on " +
                "the active server. If an update is available the daemon " +
                "downloads it and re-execs automatically — active WS " +
                "connections blip but sessions survive.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    when {
                        it.startsWith("Already") ->
                            MaterialTheme.colorScheme.primary
                        it.startsWith("Installing") ->
                            MaterialTheme.colorScheme.primary
                        else ->
                            MaterialTheme.colorScheme.error
                    },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Button(
                enabled = !checking,
                onClick = {
                    checking = true
                    banner = null
                    scope.launch {
                        val profile = resolveActiveProfile()
                        if (profile == null) {
                            banner = "No enabled server."
                            checking = false
                            return@launch
                        }
                        ServiceLocator.transportFor(profile).updateDaemon().fold(
                            onSuccess = { obj ->
                                val status =
                                    (obj["status"] as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.content
                                val version =
                                    (obj["version"] as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.content
                                banner =
                                    when (status) {
                                        "up_to_date" -> "Already up to date (v${version ?: "?"})."
                                        null -> "Update requested."
                                        else -> "Installing v${version ?: "?"} — daemon will restart."
                                    }
                                checking = false
                            },
                            onFailure = {
                                banner = "Update failed — ${it.message ?: it::class.simpleName}"
                                checking = false
                            },
                        )
                    }
                },
            ) { Text(if (checking) "Checking…" else "Check for update") }
        }
    }
}

@Composable
public fun RestartDaemonCard() {
    var confirmOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Daemon")
        Text(
            "Restart the datawatch daemon on the active server. Every " +
                "running session briefly loses its WebSocket connection " +
                "during the re-exec; sessions themselves survive.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (it.startsWith("Restart requested")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Button(
                onClick = { confirmOpen = true },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text("Restart daemon") }
        }
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Restart daemon?") },
            text = {
                Text(
                    "The server process will re-exec. Active sessions drop " +
                        "their WS connection for ~1 s then reconnect. OK to proceed?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOpen = false
                        scope.launch {
                            val profile = resolveActiveProfile() ?: return@launch
                            ServiceLocator.transportFor(profile).restartDaemon().fold(
                                onSuccess = { banner = "Restart requested — reconnecting…" },
                                onFailure = {
                                    banner = "Restart failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}
