package com.dmzs.datawatchclient.ui.compute

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.ComputeHardwareSpec
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.74.0 — Compute Nodes full CRUD (S5-1, #98).
 *
 * Manages the registry of compute workers (ollama, opencode, aider, goose,
 * gemini, shell, remote, etc.). openwebui is NOT a ComputeNode kind — it is
 * an LLM kind (see LlmRegistryCard) that references an ollama node.
 *
 * Endpoints: GET/POST /api/compute/nodes, GET/PUT/DELETE /api/compute/nodes/{name},
 * GET /api/compute/nodes/{name}/models?kind={kind}
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

    suspend fun resolveTransport() =
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
            )
            IconButton(onClick = { selectedNode = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.compute_node_add))
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
        )
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
                if (node.declaredCapacity > 1) {
                    Text(
                        "×${node.declaredCapacity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

private val COMPUTE_NODE_KINDS = listOf(
    "ollama",
    "opencode",
    "opencode-acp",
    "opencode-prompt",
    "claude-code",
    "aider",
    "goose",
    "gemini",
    "shell",
    "remote",
)

@Composable
private fun ComputeNodeDialog(
    existing: ComputeNodeDto?,
    onDismiss: () -> Unit,
    onSave: (ComputeNodeDto) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: COMPUTE_NODE_KINDS.first()) }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }
    var capacity by remember(existing) { mutableStateOf(existing?.declaredCapacity?.toString() ?: "1") }
    var tagsText by remember(existing) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var kindDropdown by remember { mutableStateOf(false) }
    var hardwareExpanded by remember { mutableStateOf(false) }

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
                // Hardware spec (read-only collapsible section, shown only when editing)
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
                        Text(stringResource(R.string.compute_node_hardware_spec))
                    }
                    if (hardwareExpanded) {
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            spec.os?.let { HardwareRow("OS", it) }
                            spec.arch?.let { HardwareRow("Arch", it) }
                            if (spec.cpuCores > 0) HardwareRow("CPU cores", "${spec.cpuCores}")
                            if (spec.memoryGb > 0) HardwareRow("Memory", "${spec.memoryGb} GB")
                            spec.gpuVendor?.let { HardwareRow("GPU vendor", it) }
                            spec.gpuModel?.let { HardwareRow("GPU model", it) }
                            if (spec.gpuCount > 0) HardwareRow("GPU count", "${spec.gpuCount}")
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
                            declaredCapacity = capacity.toIntOrNull() ?: 1,
                            tags = tags,
                            autoCreated = existing?.autoCreated ?: false,
                            hardwareSpec = existing?.hardwareSpec,
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
