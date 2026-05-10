package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.dto.MigrationStatusDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * v0.74.0 — LLM Registry full CRUD (S5-2, S5-3, S5-4, S5-5, #98, #99, #100, #101, #102).
 *
 * Manages registered LLM entries. Unlike ComputeNodes, openwebui IS a valid LLM kind
 * (it references an ollama ComputeNode under the hood).
 *
 * Includes:
 * - Amber migration banner when v7 daemon auto-migrated legacy configs (S5-3)
 * - Per-row enabled/disabled Switch (S5-5, teal=on)
 * - Multi-select compute node in add/edit dialog (S5-2)
 * - Kind-aware model dropdown via /api/compute/nodes/{name}/models?kind (S5-2)
 *
 * Endpoints: GET/POST /api/llms, GET/PUT/DELETE /api/llms/{name},
 * PATCH /api/llms/{name}/enabled, GET/DELETE /api/migration/status
 */
@Composable
public fun LlmRegistryCard() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var llms by remember { mutableStateOf<List<LlmRegistryEntryDto>>(emptyList()) }
    var computeNodes by remember { mutableStateOf<List<ComputeNodeDto>>(emptyList()) }
    var migrationStatus by remember { mutableStateOf<MigrationStatusDto?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedLlm by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var llmToDelete by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
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
        // Fetch LLMs + compute nodes + migration status in parallel (best-effort)
        transport.listLlms().fold(
            onSuccess = { llms = it; banner = null },
            onFailure = { banner = "LLMs unavailable — ${it.message ?: it::class.simpleName}" },
        )
        transport.listComputeNodes().onSuccess { computeNodes = it }
        transport.getMigrationStatus().onSuccess { migrationStatus = it }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        // Migration banner (amber, dismissible) — shown when count > 0
        val migCount = migrationStatus?.count ?: 0
        if (migCount > 0) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(
                            color = Color(0xFFFFF8E1),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.llm_migration_banner, migCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D4037),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            resolveTransport()?.dismissMigration()
                            migrationStatus = migrationStatus?.copy(count = 0)
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.llm_migration_dismiss),
                        tint = Color(0xFF5D4037),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PwaSectionTitle(
                stringResource(R.string.settings_llm_registry_title),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { selectedLlm = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.llm_registry_add))
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
        } else if (llms.isEmpty() && banner == null) {
            Text(
                stringResource(R.string.llm_registry_empty),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            llms.forEachIndexed { idx, llm ->
                if (idx > 0) HorizontalDivider()
                LlmRegistryRow(
                    llm = llm,
                    onToggle = { enabled ->
                        scope.launch {
                            resolveTransport()?.enableLlm(llm.name, enabled)?.fold(
                                onSuccess = { refreshTick++ },
                                onFailure = {
                                    banner = "Toggle failed — ${it.message ?: it::class.simpleName}"
                                },
                            )
                        }
                    },
                    onEdit = { selectedLlm = llm; showAddDialog = true },
                    onDelete = { llmToDelete = llm },
                )
            }
        }
    }

    // Add / Edit dialog
    if (showAddDialog) {
        LlmRegistryDialog(
            existing = selectedLlm,
            computeNodes = computeNodes,
            onDismiss = { showAddDialog = false; selectedLlm = null },
            onSave = { dto ->
                scope.launch {
                    val transport = resolveTransport() ?: return@launch
                    val result =
                        if (selectedLlm != null) {
                            transport.updateLlm(selectedLlm!!.name, dto)
                        } else {
                            transport.createLlm(dto)
                        }
                    result.fold(
                        onSuccess = {
                            showAddDialog = false
                            selectedLlm = null
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
    llmToDelete?.let { llm ->
        AlertDialog(
            onDismissRequest = { llmToDelete = null },
            title = { Text(stringResource(R.string.llm_registry_delete_confirm, llm.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val transport = resolveTransport() ?: return@launch
                            transport.deleteLlm(llm.name).fold(
                                onSuccess = {
                                    llmToDelete = null
                                    refreshTick++
                                },
                                onFailure = {
                                    banner = "Delete failed — ${it.message ?: it::class.simpleName}"
                                    llmToDelete = null
                                },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { llmToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LlmRegistryRow(
    llm: LlmRegistryEntryDto,
    onToggle: (Boolean) -> Unit,
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
                Text(llm.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(llm.kind, style = MaterialTheme.typography.labelSmall) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                )
            }
            Text(
                "${llm.computeNode} / ${llm.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Enabled toggle — teal when on, grey when off
        Switch(
            checked = llm.enabled,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
        )
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.llm_registry_edit),
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

/** The 10 valid LLM kinds. openwebui IS valid here (references an ollama ComputeNode). */
private val LLM_KINDS = listOf(
    "ollama",
    "openwebui",
    "opencode",
    "opencode-acp",
    "opencode-prompt",
    "claude-code",
    "aider",
    "goose",
    "gemini",
    "shell",
)

@Composable
private fun LlmRegistryDialog(
    existing: LlmRegistryEntryDto?,
    computeNodes: List<ComputeNodeDto>,
    onDismiss: () -> Unit,
    onSave: (LlmRegistryEntryDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: LLM_KINDS.first()) }
    var computeNode by remember(existing) { mutableStateOf(existing?.computeNode ?: computeNodes.firstOrNull()?.name ?: "") }
    var model by remember(existing) { mutableStateOf(existing?.model ?: "") }
    var pretestEnabled by remember(existing) { mutableStateOf(existing?.pretestEnabled ?: false) }
    var kindDropdown by remember { mutableStateOf(false) }
    var nodeDropdown by remember { mutableStateOf(false) }
    var modelDropdown by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // Reload models whenever compute node or kind changes
    LaunchedEffect(computeNode, kind) {
        if (computeNode.isNotBlank()) {
            val transport =
                ServiceLocator.profileRepository.observeAll().first().let { profiles ->
                    val activeId = ServiceLocator.activeServerStore.get()
                    (
                        profiles.firstOrNull {
                            it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
                        } ?: profiles.firstOrNull { it.enabled }
                    )?.let { ServiceLocator.transportFor(it) }
                }
            transport?.getComputeNodeModels(computeNode, kind)?.onSuccess { availableModels = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing != null) {
                    stringResource(R.string.llm_registry_edit)
                } else {
                    stringResource(R.string.llm_registry_add)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.compute_node_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = existing == null,
                )
                // Kind
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.llm_registry_kind_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { kindDropdown = true }) { Text(kind) }
                    DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                        LLM_KINDS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k) },
                                onClick = { kind = k; kindDropdown = false },
                            )
                        }
                    }
                }
                // Compute node
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.llm_registry_compute_node_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { nodeDropdown = true }) {
                        Text(computeNode.ifBlank { "—" })
                    }
                    DropdownMenu(expanded = nodeDropdown, onDismissRequest = { nodeDropdown = false }) {
                        computeNodes.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n.name) },
                                onClick = { computeNode = n.name; nodeDropdown = false },
                            )
                        }
                        if (computeNodes.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No nodes found") },
                                onClick = { nodeDropdown = false },
                            )
                        }
                    }
                }
                // Model — dropdown from getComputeNodeModels, or freetext if empty
                if (availableModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.llm_registry_model_label),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { modelDropdown = true }) {
                            Text(model.ifBlank { "Select model" })
                        }
                        DropdownMenu(expanded = modelDropdown, onDismissRequest = { modelDropdown = false }) {
                            availableModels.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = { model = m; modelDropdown = false },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.llm_registry_model_label)) },
                        singleLine = true,
                        placeholder = { Text("e.g. llama3.2") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Pretest enabled
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = pretestEnabled,
                        onCheckedChange = { pretestEnabled = it },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.llm_registry_pretest_label),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        LlmRegistryEntryDto(
                            name = name.trim(),
                            kind = kind,
                            computeNode = computeNode,
                            model = model.trim(),
                            enabled = existing?.enabled ?: true,
                            pretestEnabled = pretestEnabled,
                        ),
                    )
                },
                enabled = name.isNotBlank() && model.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
