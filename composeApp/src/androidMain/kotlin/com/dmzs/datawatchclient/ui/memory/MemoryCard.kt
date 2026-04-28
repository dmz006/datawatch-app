package com.dmzs.datawatchclient.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean

/**
 * Settings → General → Memory card. Mirrors the PWA's Memory panel:
 * stat grid + searchable + deletable list. Stats auto-refresh on
 * first mount; list refreshes after any delete and on search input.
 *
 * Create/remember + export are not yet exposed — users post memories
 * via session "remember" action (future batch) and export lives in
 * the overflow for an upcoming Sprint.
 */
@Composable
public fun MemoryCard() {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var memories by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }

    suspend fun resolveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        return profiles.firstOrNull {
            it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
        } ?: profiles.firstOrNull { it.enabled }
    }

    suspend fun refreshStats() {
        val profile = resolveProfile() ?: return
        ServiceLocator.transportFor(profile).memoryStats().fold(
            onSuccess = { obj ->
                stats = obj
                enabled = obj.bool("enabled")
            },
            onFailure = { banner = "Memory stats unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    suspend fun refreshList(q: String) {
        val profile = resolveProfile() ?: return
        val result =
            if (q.isBlank()) {
                ServiceLocator.transportFor(profile).memoryList()
            } else {
                ServiceLocator.transportFor(profile).memorySearch(q)
            }
        result.fold(
            onSuccess = { memories = it },
            onFailure = { banner = "Load failed — ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(Unit) {
        refreshStats()
        refreshList("")
    }

    // SAF-based export: user picks a destination via the system
    // file-creator, we GET /api/memory/export and write the bytes
    // through the returned ContentResolver URI. Handles the
    // "download behind bearer token" problem that ACTION_VIEW
    // can't solve (no way to attach headers to an intent-launched
    // browser download).
    val context = LocalContext.current
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val bytes = pendingBytes ?: return@rememberLauncherForActivityResult
            pendingBytes = null
            if (uri != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }.fold(
                    onSuccess = { banner = "Exported ${bytes.size} bytes." },
                    onFailure = { banner = "Export write failed — ${it.message}" },
                )
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Episodic memory")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (enabled == false) {
            Text(
                "Memory is not enabled on this server. Enable in the " +
                    "server's config → episodic_memory block.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        stats?.let { s ->
            StatsGrid(s)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val profile = resolveProfile() ?: return@launch
                        ServiceLocator.transportFor(profile).memoryTest().fold(
                            onSuccess = { banner = "Memory connection OK." },
                            onFailure = {
                                banner =
                                    "Memory test failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            ) { Text("Test", style = MaterialTheme.typography.labelMedium) }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val profile = resolveProfile() ?: return@launch
                        ServiceLocator.transportFor(profile).memoryExport().fold(
                            onSuccess = { bytes ->
                                pendingBytes = bytes
                                val ts =
                                    kotlinx.datetime.Clock.System.now().toString()
                                        .substringBefore('.')
                                        .replace(':', '-')
                                exportLauncher.launch("datawatch-memory-$ts.json")
                            },
                            onFailure = {
                                banner = "Export failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            ) { Text("Export…", style = MaterialTheme.typography.labelMedium) }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { v ->
                searchText = v
                scope.launch { refreshList(v) }
            },
            placeholder = { Text("Search memories…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
        ) {
            items(memories, key = { (it["id"] as? JsonPrimitive)?.content ?: it.hashCode().toString() }) { m ->
                MemoryRow(
                    memory = m,
                    onDelete = { id ->
                        scope.launch {
                            val profile = resolveProfile() ?: return@launch
                            ServiceLocator.transportFor(profile).memoryDelete(id).fold(
                                onSuccess = {
                                    refreshStats()
                                    refreshList(searchText)
                                },
                                onFailure = {
                                    banner = "Delete failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                    onTogglePin = { id, pinned ->
                        scope.launch {
                            val profile = resolveProfile() ?: return@launch
                            ServiceLocator.transportFor(profile).memoryPin(id, pinned).fold(
                                onSuccess = { refreshList(searchText) },
                                onFailure = {
                                    banner = "Pin failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                )
                HorizontalDivider()
            }
        }
        if (memories.isEmpty()) {
            Text(
                if (searchText.isBlank()) "No memories stored yet." else "No matches.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsGrid(s: JsonObject) {
    val cells =
        listOf(
            "Total" to s.longField("total_count"),
            "Manual" to s.longField("manual_count"),
            "Session" to s.longField("session_count"),
            "Learnings" to s.longField("learning_count"),
            "Chunks" to s.longField("chunk_count"),
            "DB" to formatBytes(s.longField("db_size_bytes") ?: 0L),
        )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cells.forEach { (label, value) ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .pwaCard()
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    value?.toString() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemoryRow(
    memory: JsonObject,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit = { _, _ -> },
) {
    val id = memory.longField("id")
    val role = memory.stringField("role").orEmpty()
    val content = memory.stringField("content").orEmpty()
    val preview = if (content.length > 200) content.take(200) + "…" else content
    val similarity = memory.doubleField("similarity")
    val pinned = (memory["pinned"] as? JsonPrimitive)?.boolean == true
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${id ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (role.isNotBlank()) {
                    Text(
                        role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                similarity?.let {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${(it * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 3,
            )
        }
        if (id != null) {
            // v0.39.2 (#21 follow-up) — pin toggle so memories can be
            // tacked into L1 retrieval regardless of recency decay.
            // Backed by /api/memory/pin (transport landed in v0.37.0).
            IconButton(onClick = { onTogglePin(id, !pinned) }) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Unpin memory" else "Pin memory",
                    tint =
                        if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(id) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete memory",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun JsonObject.stringField(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.longField(key: String): Long? =
    (get(key) as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonObject.doubleField(key: String): Double? =
    (get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun formatBytes(b: Long): String =
    when {
        b < 1024 -> "$b B"
        b < 1024L * 1024 -> "${"%.1f".format(b / 1024.0)} KB"
        b < 1024L * 1024 * 1024 -> "${"%.1f".format(b / (1024.0 * 1024))} MB"
        else -> "${"%.1f".format(b / (1024.0 * 1024 * 1024))} GB"
    }
