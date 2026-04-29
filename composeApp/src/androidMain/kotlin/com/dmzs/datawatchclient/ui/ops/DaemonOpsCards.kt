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
                        onSuccess = {
                            view = it
                            banner = null
                        },
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
public fun KillOrphansCard() {
    var confirmOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Kill orphaned tmux sessions")
        Text(
            "Terminate tmux sessions on the server that datawatch isn't " +
                "tracking. Useful after a crash or migration when tmux " +
                "panes were left behind.",
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
                    if (it.startsWith("Killed")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Button(
                onClick = { confirmOpen = true },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text("Kill orphans") }
        }
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Kill orphaned tmux sessions?") },
            text = {
                Text(
                    "Every tmux session on this host that datawatch doesn't " +
                        "know about will be terminated. Active datawatch " +
                        "sessions are safe.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOpen = false
                        scope.launch {
                            val profile = resolveActiveProfile() ?: return@launch
                            ServiceLocator.transportFor(profile).killOrphans().fold(
                                onSuccess = { obj ->
                                    val n =
                                        (obj["killed"] as? kotlinx.serialization.json.JsonPrimitive)
                                            ?.content?.toIntOrNull() ?: 0
                                    banner = "Killed $n orphan session(s)."
                                },
                                onFailure = {
                                    banner = "Kill failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text("Kill") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Three-state update flow:
 *   IDLE → user taps "Check for Update" → CHECKING
 *   CHECKING → GET /api/update/check returns up_to_date → IDLE (banner set)
 *   CHECKING → GET /api/update/check returns update_available → UPDATE_AVAILABLE
 *   UPDATE_AVAILABLE → user taps "Install Update" → INSTALLING
 *   INSTALLING → POST /api/update completes → IDLE (banner set)
 *
 * Older daemons (pre-v5.27.4) that 404 on GET /api/update/check fall back to
 * the original POST /api/update check-and-install flow transparently.
 */
private enum class UpdateState { IDLE, CHECKING, UPDATE_AVAILABLE, INSTALLING }

@Composable
public fun UpdateDaemonCard() {
    var updateState by remember { mutableStateOf(UpdateState.IDLE) }
    var banner by remember { mutableStateOf<String?>(null) }
    var pendingVersion by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Daemon update")
        Text(
            "Check whether a new datawatch daemon version is available on " +
                "the active server. If an update is found you can choose to " +
                "install it — the daemon downloads and re-execs; active WS " +
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
                        it.startsWith("Already") || it.startsWith("Installing") ->
                            MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
            )
        }
        if (updateState == UpdateState.CHECKING || updateState == UpdateState.INSTALLING) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            // "Check for Update" — always shown; disabled while any async op runs
            Button(
                enabled = updateState == UpdateState.IDLE || updateState == UpdateState.UPDATE_AVAILABLE,
                onClick = {
                    updateState = UpdateState.CHECKING
                    banner = null
                    pendingVersion = null
                    scope.launch {
                        val profile = resolveActiveProfile()
                        if (profile == null) {
                            banner = "No enabled server."
                            updateState = UpdateState.IDLE
                            return@launch
                        }
                        // Use GET /api/update/check (v5.27.4+); older daemons
                        // 404 → checkUpdate() falls back to POST internally.
                        ServiceLocator.transportFor(profile).checkUpdate().fold(
                            onSuccess = { obj ->
                                val status =
                                    (obj["status"] as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.content
                                val version =
                                    (obj["version"] as? kotlinx.serialization.json.JsonPrimitive)
                                        ?.content
                                when (status) {
                                    "up_to_date" -> {
                                        banner = "Already up to date (v${version ?: "?"})."
                                        updateState = UpdateState.IDLE
                                    }
                                    else -> {
                                        // update_available — surface install button
                                        pendingVersion = version
                                        updateState = UpdateState.UPDATE_AVAILABLE
                                    }
                                }
                            },
                            onFailure = {
                                banner = "Check failed — ${it.message ?: it::class.simpleName}"
                                updateState = UpdateState.IDLE
                            },
                        )
                    }
                },
            ) {
                Text(
                    when (updateState) {
                        UpdateState.CHECKING -> "Checking…"
                        else -> "Check for Update"
                    },
                )
            }

            // "Install Update" — only shown when an update was found
            if (updateState == UpdateState.UPDATE_AVAILABLE) {
                Button(
                    onClick = {
                        updateState = UpdateState.INSTALLING
                        scope.launch {
                            val profile = resolveActiveProfile()
                            if (profile == null) {
                                banner = "No enabled server."
                                updateState = UpdateState.IDLE
                                return@launch
                            }
                            ServiceLocator.transportFor(profile).updateDaemon().fold(
                                onSuccess = { obj ->
                                    val version =
                                        (obj["version"] as? kotlinx.serialization.json.JsonPrimitive)
                                            ?.content
                                    banner = "Installing v${version ?: pendingVersion ?: "?"} — daemon will restart."
                                    updateState = UpdateState.IDLE
                                },
                                onFailure = {
                                    banner = "Install failed — ${it.message ?: it::class.simpleName}"
                                    updateState = UpdateState.IDLE
                                },
                            )
                        }
                    },
                ) {
                    Text(
                        when (updateState) {
                            UpdateState.INSTALLING -> "Installing…"
                            else -> "Install Update${pendingVersion?.let { " v$it" } ?: ""}"
                        },
                    )
                }
            }
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

private val RELOAD_SUBSYSTEMS = listOf("config", "filters", "memory")

@Composable
public fun SubsystemReloadCard() {
    var selected by remember { mutableStateOf(RELOAD_SUBSYSTEMS[0]) }
    var menuOpen by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var reloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Hot-reload subsystem")
        Text(
            "Reload a subsystem on the active server without restarting the daemon. " +
                "Changes to config files, filter rules, or memory are picked up immediately.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        banner?.let {
            val isOk = it.startsWith("Reloaded")
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text(selected) }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    RELOAD_SUBSYSTEMS.forEach { sub ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = { selected = sub; menuOpen = false },
                        )
                    }
                }
            }
            Button(
                enabled = !reloading,
                onClick = {
                    reloading = true
                    banner = null
                    scope.launch {
                        val profile = resolveActiveProfile()
                        if (profile == null) {
                            banner = "No enabled server."
                            reloading = false
                            return@launch
                        }
                        ServiceLocator.transportFor(profile).reloadSubsystem(selected).fold(
                            onSuccess = { obj ->
                                val applied =
                                    (obj["applied"] as? kotlinx.serialization.json.JsonArray)
                                        ?.mapNotNull {
                                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                                        } ?: emptyList()
                                val restart =
                                    (obj["requires_restart"] as? kotlinx.serialization.json.JsonArray)
                                        ?.mapNotNull {
                                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                                        } ?: emptyList()
                                banner = buildString {
                                    append("Reloaded $selected.")
                                    if (applied.isNotEmpty()) append(" Applied: ${applied.joinToString(", ")}.")
                                    if (restart.isNotEmpty()) append(" Restart required: ${restart.joinToString(", ")}.")
                                }
                            },
                            onFailure = {
                                banner = "Reload failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                        reloading = false
                    }
                },
            ) { Text(if (reloading) "Reloading…" else "Reload") }
        }
    }
}
