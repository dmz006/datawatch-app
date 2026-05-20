package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.transport.dto.DeclaredCapacityDto
import com.dmzs.datawatchclient.transport.dto.FreeObserverPeerDto
import com.dmzs.datawatchclient.transport.dto.MigrationComputeKindsDto
import com.dmzs.datawatchclient.transport.dto.OllamaCatalogDto
import com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto
import com.dmzs.datawatchclient.transport.dto.OllamaTagDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.84.0 — Compute Nodes full CRUD overhaul (S15, #106 + #110).
 *
 * Changes from v0.74.0:
 * - COMPUTE_NODE_KINDS reduced to [ollama, openai-compat]
 * - Migration banner + per-node kind-migration modal
 * - Sliding enabled/disabled switch with auto-disabled ! badge
 * - Observer peer dropdown in Add/Edit dialog
 * - Hardware section with SaaS show/hide + computed-max suggestion
 * - Icon-only row actions
 *
 * Endpoints: GET/POST /api/compute/nodes, GET/PUT/DELETE /api/compute/nodes/{name},
 * GET /api/compute/nodes/{name}/models?kind={kind},
 * GET /api/migration/compute-kinds, PUT /api/migration/compute-kinds/{name},
 * PATCH /api/compute/nodes/{name}/enabled,
 * GET /api/observer/peers/free,
 * POST/DELETE /api/compute/nodes/{name}/observer-peer
 */
@Composable
public fun ComputeNodesCard(
    /** Optional callback fired after a node is deleted for cascade-refresh (S5-3). */
    onNodeDeleted: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var nodes by remember { mutableStateOf<List<ComputeNodeDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<ComputeNodeDto?>(null) }
    var nodeToDelete by remember { mutableStateOf<ComputeNodeDto?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // Sprint 15 migration state
    var migrationData by remember { mutableStateOf<MigrationComputeKindsDto?>(null) }
    var showMigrationModal by remember { mutableStateOf(false) }

    suspend fun resolveTransport(): TransportClient? =
        ServiceLocator.profileRepository.observeAll().first().let { profiles ->
            val activeId = ServiceLocator.activeServerStore.get()
            (
                profiles.firstOrNull {
                    it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                } ?: profiles.firstOrNull { it.enabled }
            )?.let { ServiceLocator.transportFor(it) }
        }

    LaunchedEffect(refreshTick) {
        loading = true
        val transport = resolveTransport() ?: run {
            banner = "No enabled server."
            loading = false
            return@LaunchedEffect
        }
        transport.listComputeNodes().fold(
            onSuccess = {
                nodes = it
                banner = null
            },
            onFailure = {
                banner = "Compute nodes unavailable — ${it.message ?: it::class.simpleName}"
            },
        )
        // Load migration data (best-effort)
        transport.getMigrationComputeKinds().onSuccess { migrationData = it }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PwaSectionTitle(
                stringResource(R.string.settings_compute_nodes_title),
                modifier = Modifier.weight(1f),
                docsAnchor = "compute-nodes",
            )
            IconButton(onClick = { selectedNode = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.compute_node_add))
            }
        }

        // Migration banner — shown when deprecated-kind nodes exist
        if ((migrationData?.count ?: 0) > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { showMigrationModal = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4D3000)),
            ) {
                Text(
                    stringResource(R.string.compute_migration_banner, migrationData!!.count),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B),
                )
            }
        }

        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (nodes.isEmpty() && banner == null) {
            Text(
                stringResource(R.string.compute_nodes_empty),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            nodes.forEachIndexed { idx, node ->
                if (idx > 0) HorizontalDivider()
                ComputeNodeRow(
                    node = node,
                    onEdit = { selectedNode = node; showAddDialog = true },
                    onDelete = { nodeToDelete = node },
                    onToggleEnabled = { enabled ->
                        scope.launch {
                            val transport = resolveTransport() ?: return@launch
                            transport.toggleComputeNodeEnabled(node.name, enabled).onSuccess { refreshTick++ }
                        }
                    },
                )
            }
        }
    }

    // Add / Edit dialog
    if (showAddDialog) {
        ComputeNodeDialog(
            existing = selectedNode,
            onDismiss = { showAddDialog = false; selectedNode = null },
            onSave = { dto ->
                scope.launch {
                    val transport = resolveTransport() ?: return@launch
                    val result =
                        if (selectedNode != null) {
                            transport.updateComputeNode(selectedNode!!.name, dto)
                        } else {
                            transport.createComputeNode(dto)
                        }
                    result.fold(
                        onSuccess = {
                            showAddDialog = false
                            selectedNode = null
                            refreshTick++
                        },
                        onFailure = {
                            banner = "Save failed — ${it.message ?: it::class.simpleName}"
                        },
                    )
                }
            },
            resolveTransport = ::resolveTransport,
        )
    }

    // Migration modal
    if (showMigrationModal) {
        migrationData?.let { mig ->
            MigrationModal(
                migrationData = mig,
                onDismiss = { showMigrationModal = false },
                onMigrate = { nodeName, newKind ->
                    scope.launch {
                        val transport = resolveTransport() ?: return@launch
                        transport.migrateComputeNodeKind(nodeName, newKind).onSuccess {
                            refreshTick++
                            showMigrationModal = false
                        }
                    }
                },
            )
        }
    }

    // Delete confirm dialog
    nodeToDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text(stringResource(R.string.compute_node_delete_confirm, node.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val transport = resolveTransport() ?: return@launch
                            transport.deleteComputeNode(node.name).fold(
                                onSuccess = {
                                    nodeToDelete = null
                                    refreshTick++
                                    onNodeDeleted?.invoke()
                                },
                                onFailure = {
                                    banner = "Delete failed — ${it.message ?: it::class.simpleName}"
                                    nodeToDelete = null
                                },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ComputeNodeRow(
    node: ComputeNodeDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(node.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(node.kind, style = MaterialTheme.typography.labelSmall) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                )
                if ((node.declaredCapacity?.gpus ?: 1) > 1) {
                    Text(
                        "×${node.declaredCapacity?.gpus ?: 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Auto-disabled badge
                if (!node.enabled && node.disabledReason != null) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("!", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                node.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (node.tags.isNotEmpty()) {
                Text(
                    node.tags.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Enabled/disabled sliding switch
        Switch(
            checked = node.enabled,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.size(36.dp, 20.dp),
        )
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.compute_node_edit),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MigrationModal(
    migrationData: MigrationComputeKindsDto,
    onDismiss: () -> Unit,
    onMigrate: (nodeName: String, newKind: String) -> Unit,
) {
    // Per-node selected kind state
    val selectedKinds = remember(migrationData) {
        migrationData.nodes.associate { it.name to mutableStateOf(COMPUTE_NODE_KINDS.first()) }.toMutableMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compute_migration_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                migrationData.nodes.forEach { node ->
                    val kindState = selectedKinds[node.name] ?: return@forEach
                    var kindDropdown by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(node.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                node.currentKind,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            TextButton(onClick = { kindDropdown = true }) {
                                Text(kindState.value)
                            }
                            DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                                COMPUTE_NODE_KINDS.forEach { k ->
                                    DropdownMenuItem(
                                        text = { Text(k) },
                                        onClick = { kindState.value = k; kindDropdown = false },
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { onMigrate(node.name, kindState.value) },
                        ) {
                            Text(stringResource(R.string.compute_migration_save))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        dismissButton = null,
    )
}

// Sprint 15 — reduced to 2 supported kinds only
private val COMPUTE_NODE_KINDS = listOf("ollama", "openai-compat")

private val SAAS_ADDRESS_PATTERNS = listOf(
    "api.openai.com",
    ".azure.com",
    "generativelanguage.googleapis.com",
    "api.anthropic.com",
    "api.together.xyz",
    "api.groq.com",
    "api.mistral.ai",
)

private fun isSaasAddress(address: String): Boolean =
    SAAS_ADDRESS_PATTERNS.any { address.contains(it, ignoreCase = true) }

@Composable
private fun ComputeNodeDialog(
    existing: ComputeNodeDto?,
    onDismiss: () -> Unit,
    onSave: (ComputeNodeDto) -> Unit,
    resolveTransport: suspend () -> TransportClient?,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kind by remember(existing) {
        mutableStateOf(
            existing?.kind?.let { if (it in COMPUTE_NODE_KINDS) it else COMPUTE_NODE_KINDS.first() }
                ?: COMPUTE_NODE_KINDS.first(),
        )
    }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
    var capacity by remember(existing) { mutableStateOf(existing?.declaredCapacity?.gpus?.toString() ?: "1") }
    var tagsText by remember(existing) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var kindDropdown by remember { mutableStateOf(false) }
    var hardwareExpanded by remember { mutableStateOf(false) }

    // Sprint 15 #110 — observer peer
    var observerPeer by remember(existing) { mutableStateOf(existing?.observerPeer ?: "") }
    var freePeers by remember { mutableStateOf<List<FreeObserverPeerDto>>(emptyList()) }
    var peerDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        resolveTransport()?.getFreePeers()?.onSuccess { freePeers = it }
    }

    // Sprint 27 — Ollama marketplace state
    val scope = rememberCoroutineScope()
    var installedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var installedModelsLoading by remember { mutableStateOf(false) }
    var showMarketplace by remember { mutableStateOf(false) }
    var installedRefreshTick by remember { mutableStateOf(0) }
    var pullTasks by remember { mutableStateOf<Map<String, OllamaPullTaskDto>>(emptyMap()) }
    var recentlyInstalled by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(kind, existing?.name, installedRefreshTick) {
        if (kind != "ollama" || existing == null) return@LaunchedEffect
        installedModelsLoading = true
        resolveTransport()?.getInstalledOllamaModels(existing.name)?.onSuccess { installedModels = it.models }
        installedModelsLoading = false
    }
    recentlyInstalled?.let { m -> LaunchedEffect(m) { delay(3000); recentlyInstalled = null } }

    // Determine if hardware section should be hidden (SaaS endpoint + openai-compat)
    val hideSaas = kind == "openai-compat" && isSaasAddress(address)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing != null) {
                    stringResource(R.string.compute_node_edit)
                } else {
                    stringResource(R.string.compute_node_add)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Name (immutable once created)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.compute_node_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = existing == null,
                )
                // Kind dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.compute_node_kind_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { kindDropdown = true }) { Text(kind) }
                    DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                        COMPUTE_NODE_KINDS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = { kind = k; kindDropdown = false },
                            )
                        }
                    }
                }
                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.compute_node_address_label)) },
                    singleLine = true,
                    placeholder = { Text("http://localhost:11434") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Declared capacity
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.compute_node_capacity_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Tags
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.compute_node_tags_label)) },
                    singleLine = true,
                    placeholder = { Text("gpu, fast") },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Observer peer dropdown (Sprint 15 #110)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.compute_field_observer_peer),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { peerDropdown = true }) {
                            Text(observerPeer.ifBlank { stringResource(R.string.compute_observer_none) })
                        }
                        DropdownMenu(expanded = peerDropdown, onDismissRequest = { peerDropdown = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.compute_observer_none)) },
                                onClick = { observerPeer = ""; peerDropdown = false },
                            )
                            freePeers.forEach { peer ->
                                DropdownMenuItem(
                                    text = { Text(peer.name) },
                                    onClick = { observerPeer = peer.name; peerDropdown = false },
                                )
                            }
                            // If editing and existing peer not in free list, show it as "(currently attached)"
                            val existingPeer = existing?.observerPeer
                            if (existingPeer != null && freePeers.none { it.name == existingPeer }) {
                                DropdownMenuItem(
                                    text = { Text("$existingPeer ${stringResource(R.string.compute_observer_currently_attached)}") },
                                    onClick = { observerPeer = existingPeer; peerDropdown = false },
                                )
                            }
                        }
                    }
                }
                // Hardware spec section — collapsible, kind-aware SaaS hide
                if (hideSaas) {
                    Text(
                        stringResource(R.string.compute_hardware_hidden_saas),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    existing?.hardwareSpec?.let { spec ->
                        TextButton(
                            onClick = { hardwareExpanded = !hardwareExpanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (hardwareExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.compute_hardware_section))
                        }
                        if (hardwareExpanded) {
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                spec.os?.let { HardwareRow(stringResource(R.string.compute_hardware_os), it) }
                                spec.arch?.let { HardwareRow(stringResource(R.string.compute_hardware_arch), it) }
                                if (spec.cpuCores > 0) HardwareRow(stringResource(R.string.compute_hardware_cpu_cores), "${spec.cpuCores}")
                                if (spec.memoryGb > 0) HardwareRow(stringResource(R.string.compute_hardware_ram_gb), "${spec.memoryGb} GB")
                                spec.gpuVendor?.let { HardwareRow(stringResource(R.string.compute_hardware_gpu_vendor), it) }
                                spec.gpuModel?.let { HardwareRow(stringResource(R.string.compute_hardware_gpu_model), it) }
                                if (spec.gpuCount > 0) HardwareRow(stringResource(R.string.compute_hardware_gpu_count), "${spec.gpuCount}")
                                // Computed max suggestion: floor(VRAM_per_GPU × GPU_count / 8)
                                val computedMax = if (spec.memoryGb > 0 && spec.gpuCount > 0)
                                    (spec.memoryGb * spec.gpuCount) / 8 else 0
                                if (computedMax > 0) {
                                    Text(
                                        stringResource(R.string.compute_hardware_computed_max, computedMax),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                // Sprint 27 — models sub-section (ollama nodes only, edit mode)
                if (kind == "ollama" && existing != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.compute_models_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showMarketplace = true }) {
                            Text(stringResource(R.string.ollama_browse_btn), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    recentlyInstalled?.let { model ->
                        Text(
                            stringResource(R.string.ollama_pull_done, model),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                        )
                    }
                    if (installedModelsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else if (installedModels.isEmpty()) {
                        Text(
                            stringResource(R.string.ollama_no_installed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column {
                            installedModels.forEach { model ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        model,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                resolveTransport()?.deleteOllamaModel(existing.name, model)
                                                    ?.onSuccess { installedRefreshTick++ }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.ollama_remove_btn),
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(
                        ComputeNodeDto(
                            name = name.trim(),
                            kind = kind,
                            address = address.trim(),
                            declaredCapacity = DeclaredCapacityDto(gpus = capacity.toIntOrNull() ?: 1),
                            tags = tags,
                            autoCreated = existing?.autoCreated ?: false,
                            hardwareSpec = existing?.hardwareSpec,
                            enabled = existing?.enabled ?: true,
                            autoTags = existing?.autoTags ?: emptyList(),
                            observerPeer = observerPeer.ifBlank { null },
                            disabledReason = existing?.disabledReason,
                        ),
                    )
                },
                enabled = name.isNotBlank() && address.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    // Marketplace dialog — shown when user taps "Browse marketplace"
    if (showMarketplace && existing != null) {
        OllamaMarketplaceDialog(
            nodeName = existing.name,
            installedModels = installedModels,
            pullTasks = pullTasks,
            onPull = { fullModel ->
                scope.launch {
                    val transport = resolveTransport() ?: return@launch
                    transport.pullOllamaModel(existing.name, fullModel).onSuccess { task ->
                        pullTasks = pullTasks + (fullModel to task)
                        var cur = task
                        while (cur.status != "done" && cur.status != "error") {
                            delay(2_000)
                            cur = transport.getPullTask(cur.id).getOrNull() ?: break
                            pullTasks = pullTasks + (fullModel to cur)
                        }
                        pullTasks = pullTasks - fullModel
                        if (cur.status == "done") {
                            recentlyInstalled = fullModel
                            installedRefreshTick++
                        }
                    }
                }
            },
            resolveTransport = resolveTransport,
            onDismiss = { showMarketplace = false },
        )
    }
}

@Composable
private fun HardwareRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OllamaMarketplaceDialog(
    nodeName: String,
    installedModels: List<String>,
    pullTasks: Map<String, OllamaPullTaskDto>,
    onPull: (fullModel: String) -> Unit,
    resolveTransport: suspend () -> TransportClient?,
    onDismiss: () -> Unit,
) {
    var catalog by remember { mutableStateOf<OllamaCatalogDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var expandedModels by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        resolveTransport()?.getOllamaCatalog()?.onSuccess { catalog = it }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ollama_marketplace_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Filter…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    val models = (catalog?.models ?: emptyList())
                        .filter { it.name.contains(search, ignoreCase = true) }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(models) { model ->
                            val isExpanded = model.name in expandedModels
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedModels = if (isExpanded)
                                                expandedModels - model.name
                                            else
                                                expandedModels + model.name
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            model.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (model.description.isNotBlank()) {
                                            Text(
                                                model.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    Icon(
                                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (isExpanded) {
                                    model.tags.forEach { tag ->
                                        val fullModel = "${model.name}:${tag.tag}"
                                        OllamaTagRow(
                                            tag = tag,
                                            isInstalled = fullModel in installedModels,
                                            pullTask = pullTasks[fullModel],
                                            onPull = { onPull(fullModel) },
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        dismissButton = null,
    )
}

@Composable
private fun OllamaTagRow(
    tag: OllamaTagDto,
    isInstalled: Boolean,
    pullTask: OllamaPullTaskDto?,
    onPull: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, end = 0.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tag.tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
        if (tag.size.isNotBlank()) {
            Text(
                tag.size,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
        }
        Text(
            if (tag.minRamGb > 0f) "${tag.minRamGb}G" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        Text(
            if (tag.minVramGb > 0f) "${tag.minVramGb}G" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        Text(
            if (tag.fits) stringResource(R.string.ollama_tag_fits) else stringResource(R.string.ollama_tag_tight),
            style = MaterialTheme.typography.labelSmall,
            color = if (tag.fits) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.width(36.dp),
        )
        Spacer(Modifier.weight(1f))
        when {
            isInstalled -> Text("✓", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
            pullTask != null -> Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${pullTask.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { pullTask.progress / 100f },
                    modifier = Modifier.width(50.dp),
                )
            }
            else -> TextButton(
                onClick = onPull,
                modifier = Modifier.heightIn(min = 24.dp),
            ) {
                Text(stringResource(R.string.ollama_pull_btn), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
