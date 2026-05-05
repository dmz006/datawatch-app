package com.dmzs.datawatchclient.ui.memory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
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

private enum class MemoryTab { List, Timeline, Research }

/**
 * Settings → General → Memory card. Mirrors the PWA's Memory panel:
 * stat grid + 3-tab layout (List / Timeline / Research). Stats auto-refresh
 * on first mount; list refreshes after any mutating action and on search input.
 *
 * BL12 additions (v0.69.0):
 *  - [+] Add memory button → AddMemoryDialog → POST /api/memory/remember
 *  - Timeline tab: chronological view grouped by date with left-side time marker
 *  - Research tab: dedicated search field with similarity scores visible
 */
@Composable
public fun MemoryCard() {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var memories by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(MemoryTab.List) }
    var addOpen by remember { mutableStateOf(false) }

    // Research tab state
    var researchQuery by remember { mutableStateOf("") }
    var researchResults by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    // Timeline tab state — separate list loaded with higher limit
    var timelineMemories by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

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

    suspend fun refreshTimeline() {
        val profile = resolveProfile() ?: return
        ServiceLocator.transportFor(profile).memoryList(limit = 100).fold(
            onSuccess = { list ->
                // Sort descending by created_at string (ISO 8601 sorts lexicographically)
                timelineMemories = list.sortedByDescending { it.stringField("created_at").orEmpty() }
            },
            onFailure = { banner = "Timeline load failed — ${it.message ?: it::class.simpleName}" },
        )
    }

    suspend fun runResearch(q: String) {
        if (q.isBlank()) return
        val profile = resolveProfile() ?: return
        ServiceLocator.transportFor(profile).memorySearch(q).fold(
            onSuccess = { researchResults = it },
            onFailure = { banner = "Research failed — ${it.message ?: it::class.simpleName}" },
        )
    }

    LaunchedEffect(Unit) {
        refreshStats()
        refreshList("")
    }

    // Load timeline on tab switch
    LaunchedEffect(activeTab) {
        if (activeTab == MemoryTab.Timeline && timelineMemories.isEmpty()) {
            scope.launch { refreshTimeline() }
        }
    }

    // SAF-based export
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
        // Header row: section title + [+] Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PwaSectionTitle("Episodic memory")
            }
            IconButton(onClick = { addOpen = true }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add memory",
                    tint = MaterialTheme.colorScheme.primary,
                )
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

        // Action buttons row
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

        // ── Tab bar ──────────────────────────────────────────────────
        TabRow(selectedTabIndex = activeTab.ordinal) {
            MemoryTab.values().forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = {
                        Text(
                            stringResource(
                                when (tab) {
                                    MemoryTab.List -> R.string.memory_tab_list
                                    MemoryTab.Timeline -> R.string.memory_tab_timeline
                                    MemoryTab.Research -> R.string.memory_tab_research
                                },
                            ),
                        )
                    },
                )
            }
        }

        when (activeTab) {
            MemoryTab.List -> ListTab(
                memories = memories,
                searchText = searchText,
                stats = stats,
                onSearchChange = { v ->
                    searchText = v
                    scope.launch { refreshList(v) }
                },
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

            MemoryTab.Timeline -> TimelineTab(
                memories = timelineMemories,
                onDelete = { id ->
                    scope.launch {
                        val profile = resolveProfile() ?: return@launch
                        ServiceLocator.transportFor(profile).memoryDelete(id).fold(
                            onSuccess = {
                                refreshStats()
                                refreshTimeline()
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
                            onSuccess = { refreshTimeline() },
                            onFailure = {
                                banner = "Pin failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            )

            MemoryTab.Research -> ResearchTab(
                query = researchQuery,
                results = researchResults,
                onQueryChange = { researchQuery = it },
                onSearch = { scope.launch { runResearch(researchQuery) } },
                onDelete = { id ->
                    scope.launch {
                        val profile = resolveProfile() ?: return@launch
                        ServiceLocator.transportFor(profile).memoryDelete(id).fold(
                            onSuccess = {
                                refreshStats()
                                runResearch(researchQuery)
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
                            onSuccess = { runResearch(researchQuery) },
                            onFailure = {
                                banner = "Pin failed — ${it.message ?: it::class.simpleName}"
                            },
                        )
                    }
                },
            )
        }
    }

    // Add memory dialog
    if (addOpen) {
        AddMemoryDialog(
            onDismiss = { addOpen = false },
            onSave = { text, tags ->
                addOpen = false
                scope.launch {
                    val profile = resolveProfile() ?: return@launch
                    ServiceLocator.transportFor(profile).memoryRemember(text = text, tags = tags)
                        .onSuccess {
                            refreshStats()
                            refreshList(searchText)
                            if (timelineMemories.isNotEmpty()) refreshTimeline()
                        }
                        .onFailure {
                            banner = "Add failed — ${it.message ?: it::class.simpleName}"
                        }
                }
            },
        )
    }
}

// ── Tab composables ───────────────────────────────────────────────────────────

@Composable
private fun ListTab(
    memories: List<JsonObject>,
    searchText: String,
    stats: JsonObject?,
    onSearchChange: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchChange,
        placeholder = { Text(stringResource(R.string.memory_research_query_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
    ) {
        items(
            memories,
            key = { (it["id"] as? JsonPrimitive)?.content ?: it.hashCode().toString() },
        ) { m ->
            MemoryRow(
                memory = m,
                onDelete = onDelete,
                onTogglePin = onTogglePin,
            )
            HorizontalDivider()
        }
    }
    if (memories.isEmpty()) {
        val totalCount = stats?.longField("total_count") ?: 0L
        val emptyMsg = when {
            searchText.isNotBlank() -> "No matches."
            totalCount > 0L -> "No manually saved memories yet — $totalCount session memories are searchable above."
            else -> "No memories stored yet."
        }
        Text(
            emptyMsg,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimelineTab(
    memories: List<JsonObject>,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    if (memories.isEmpty()) {
        Text(
            stringResource(R.string.memory_timeline_empty),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Group by date prefix (first 10 chars of created_at: "YYYY-MM-DD")
    val grouped = memories.groupBy { m ->
        m.stringField("created_at")?.take(10) ?: "Unknown date"
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
    ) {
        grouped.forEach { (dateLabel, group) ->
            item(key = "header_$dateLabel") {
                Text(
                    dateLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            }
            items(group, key = { (it["id"] as? JsonPrimitive)?.content ?: it.hashCode().toString() }) { m ->
                TimelineRow(
                    memory = m,
                    onDelete = onDelete,
                    onTogglePin = onTogglePin,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    memory: JsonObject,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    val id = memory.longField("id")
    val role = memory.stringField("role").orEmpty()
    val content = memory.stringField("content").orEmpty()
    val createdAt = memory.stringField("created_at").orEmpty()
    // Extract HH:mm from ISO timestamp (chars 11-16)
    val timePart = if (createdAt.length >= 16) createdAt.substring(11, 16) else ""
    val pinned = (memory["pinned"] as? JsonPrimitive)?.boolean == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left time marker column
        Column(
            modifier = Modifier.width(52.dp).padding(top = 2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                timePart,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (role.isNotBlank()) {
                Text(
                    role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                if (content.length > 200) content.take(200) + "…" else content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
            )
        }
        if (id != null) {
            IconButton(
                onClick = { onTogglePin(id, !pinned) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Unpin memory" else "Pin memory",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { onDelete(id) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete memory",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun ResearchTab(
    query: String,
    results: List<JsonObject>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.memory_research_query_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        trailingIcon = {
            TextButton(onClick = onSearch) {
                Text("Search", style = MaterialTheme.typography.labelMedium)
            }
        },
    )

    if (results.isEmpty() && query.isNotBlank()) {
        Text(
            stringResource(R.string.memory_research_empty),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
    ) {
        items(
            results,
            key = { (it["id"] as? JsonPrimitive)?.content ?: it.hashCode().toString() },
        ) { m ->
            ResearchRow(
                memory = m,
                onDelete = onDelete,
                onTogglePin = onTogglePin,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ResearchRow(
    memory: JsonObject,
    onDelete: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
) {
    val id = memory.longField("id")
    val role = memory.stringField("role").orEmpty()
    val content = memory.stringField("content").orEmpty()
    val similarity = memory.doubleField("similarity")
    val pinned = (memory["pinned"] as? JsonPrimitive)?.boolean == true

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (role.isNotBlank()) {
                    Text(
                        role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                similarity?.let { sim ->
                    Text(
                        "${stringResource(R.string.memory_similarity_label)}: ${"%.2f".format(sim)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                if (content.length > 300) content.take(300) + "…" else content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 3,
            )
        }
        if (id != null) {
            IconButton(onClick = { onTogglePin(id, !pinned) }) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Unpin memory" else "Pin memory",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, tags: List<String>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_add_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.memory_add_text_label)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    maxLines = 8,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.memory_add_tags_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.memory_add_tags_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(text.trim(), tagList)
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

// ── Shared MemoryRow (List tab) ───────────────────────────────────────────────

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
            IconButton(onClick = { onTogglePin(id, !pinned) }) {
                Icon(
                    if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (pinned) "Unpin memory" else "Pin memory",
                    tint =
                        if (pinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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

// ── StatsGrid ─────────────────────────────────────────────────────────────────

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

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.longField(key: String): Long? = (get(key) as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonObject.doubleField(key: String): Double? = (get(key) as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun JsonObject.bool(key: String): Boolean? = (get(key) as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun formatBytes(b: Long): String =
    when {
        b < 1024 -> "$b B"
        b < 1024L * 1024 -> "${"%.1f".format(b / 1024.0)} KB"
        b < 1024L * 1024 * 1024 -> "${"%.1f".format(b / (1024.0 * 1024))} MB"
        else -> "${"%.1f".format(b / (1024.0 * 1024 * 1024))} GB"
    }
