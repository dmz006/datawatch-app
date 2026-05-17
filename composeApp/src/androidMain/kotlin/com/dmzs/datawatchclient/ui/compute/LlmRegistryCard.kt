package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.transport.dto.LlmModelPairDto
import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.dto.LlmSessionRefDto
import com.dmzs.datawatchclient.transport.dto.MigrationStatusDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private suspend fun resolveActiveTransport() =
    ServiceLocator.profileRepository.observeAll().first().let { profiles ->
        val activeId = ServiceLocator.activeServerStore.get()
        (
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled }
        )?.let { ServiceLocator.transportFor(it) }
    }

/** LLM kinds that require an ollama/openwebui compute node. SaaS kinds use a single model field. */
private val NODE_BASED_KINDS = setOf("ollama", "openwebui", "opencode", "opencode-acp", "opencode-prompt")

/**
 * v0.99.0 — Sprint 30: multi-node model table, LlmDetailDialog (models+sessions tabs),
 * delete-blocked reassign flow (409 Conflict), batch confirm guards.
 *
 * Endpoints: GET/POST /api/llms, GET/PUT/DELETE /api/llms/{name},
 * PATCH /api/llms/{name}/enabled, GET /api/llms/{name}/sessions,
 * POST /api/llms/{name}/reassign, GET/DELETE /api/migration/status
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
    var llmDeleteBlocked by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var detailLlm by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        loading = true
        val transport = resolveActiveTransport() ?: run {
            banner = "No enabled server."
            loading = false
            return@LaunchedEffect
        }
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
        val migCount = if (migrationStatus?.show == true) migrationStatus?.migrated?.size ?: 0 else 0
        if (migCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.llm_migration_banner, migCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D4037),
                )
                IconButton(onClick = {
                    scope.launch {
                        resolveActiveTransport()?.dismissMigration()
                        migrationStatus = migrationStatus?.copy(show = false)
                    }
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.llm_migration_dismiss), tint = Color(0xFF5D4037))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle(stringResource(R.string.settings_llm_registry_title), modifier = Modifier.weight(1f), docsAnchor = "llms")
            IconButton(onClick = { selectedLlm = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.llm_registry_add))
            }
        }
        banner?.let {
            Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (loading) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else if (llms.isEmpty() && banner == null) {
            Text(stringResource(R.string.llm_registry_empty), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            llms.forEachIndexed { idx, llm ->
                if (idx > 0) HorizontalDivider()
                LlmRegistryRow(
                    llm = llm,
                    onToggle = { enabled, onDone ->
                        scope.launch {
                            resolveActiveTransport()?.enableLlm(llm.name, enabled)?.fold(
                                onSuccess = { refreshTick++; onDone() },
                                onFailure = { banner = "Toggle failed — ${it.message ?: it::class.simpleName}"; onDone() },
                            )
                        }
                    },
                    onEdit = { selectedLlm = llm; showAddDialog = true },
                    onDelete = { llmToDelete = llm },
                    onDetails = { detailLlm = llm },
                )
            }
        }
    }

    if (showAddDialog) {
        LlmRegistryDialog(
            existing = selectedLlm,
            computeNodes = computeNodes,
            onDismiss = { showAddDialog = false; selectedLlm = null },
            onSave = { dto ->
                scope.launch {
                    val transport = resolveActiveTransport() ?: return@launch
                    val result = if (selectedLlm != null) transport.updateLlm(selectedLlm!!.name, dto) else transport.createLlm(dto)
                    result.fold(
                        onSuccess = { showAddDialog = false; selectedLlm = null; refreshTick++ },
                        onFailure = { banner = "Save failed — ${it.message ?: it::class.simpleName}" },
                    )
                }
            },
        )
    }

    // Standard delete confirm
    llmToDelete?.let { llm ->
        AlertDialog(
            onDismissRequest = { llmToDelete = null },
            title = { Text(stringResource(R.string.llm_registry_delete_confirm, llm.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val transport = resolveActiveTransport() ?: return@launch
                        transport.deleteLlm(llm.name).fold(
                            onSuccess = { llmToDelete = null; refreshTick++ },
                            onFailure = { err ->
                                llmToDelete = null
                                if (err is TransportError.Conflict) {
                                    llmDeleteBlocked = llm
                                } else {
                                    banner = "Delete failed — ${err.message ?: err::class.simpleName}"
                                }
                            },
                        )
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { llmToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Delete-blocked (409) — reassign sessions dialog
    llmDeleteBlocked?.let { blocked ->
        val otherLlms = llms.filter { it.name != blocked.name }
        var reassignTarget by remember(blocked) { mutableStateOf(otherLlms.firstOrNull()?.name ?: "") }
        var reassignDropdown by remember(blocked) { mutableStateOf(false) }
        var reassigning by remember(blocked) { mutableStateOf(false) }
        var showForceConfirm by remember(blocked) { mutableStateOf(false) }

        if (showForceConfirm) {
            AlertDialog(
                onDismissRequest = { showForceConfirm = false },
                title = { Text(stringResource(R.string.llm_force_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            reassigning = true
                            val transport = resolveActiveTransport()
                            transport?.reassignLlmSessions(blocked.name, reassignTarget, force = true)?.fold(
                                onSuccess = {
                                    transport.deleteLlm(blocked.name).fold(
                                        onSuccess = { llmDeleteBlocked = null; showForceConfirm = false; refreshTick++ },
                                        onFailure = { banner = "Force delete failed — ${it.message}"; llmDeleteBlocked = null },
                                    )
                                },
                                onFailure = { banner = "Force reassign failed — ${it.message}"; llmDeleteBlocked = null },
                            )
                            reassigning = false
                        }
                    }, enabled = !reassigning) {
                        if (reassigning) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForceConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { llmDeleteBlocked = null },
                title = { Text(stringResource(R.string.llm_delete_blocked_n, blocked.name)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.llm_reassign_to), style = MaterialTheme.typography.bodySmall)
                        Box {
                            TextButton(onClick = { reassignDropdown = true }, enabled = otherLlms.isNotEmpty()) {
                                Text(reassignTarget.ifBlank { "—" })
                            }
                            DropdownMenu(expanded = reassignDropdown, onDismissRequest = { reassignDropdown = false }) {
                                otherLlms.forEach { other ->
                                    DropdownMenuItem(text = { Text(other.name) }, onClick = { reassignTarget = other.name; reassignDropdown = false })
                                }
                            }
                        }
                        TextButton(onClick = { showForceConfirm = true }) {
                            Text(stringResource(R.string.llm_force_delete), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                reassigning = true
                                val transport = resolveActiveTransport()
                                transport?.reassignLlmSessions(blocked.name, reassignTarget)?.fold(
                                    onSuccess = {
                                        transport.deleteLlm(blocked.name).fold(
                                            onSuccess = { llmDeleteBlocked = null; refreshTick++ },
                                            onFailure = { banner = "Delete after reassign failed — ${it.message}"; llmDeleteBlocked = null },
                                        )
                                    },
                                    onFailure = { err ->
                                        if (err is TransportError.Conflict) showForceConfirm = true
                                        else { banner = "Reassign failed — ${err.message}"; llmDeleteBlocked = null }
                                    },
                                )
                                reassigning = false
                            }
                        },
                        enabled = reassignTarget.isNotBlank() && !reassigning,
                    ) {
                        if (reassigning) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text(stringResource(R.string.llm_reassign_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { llmDeleteBlocked = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }

    // LLM detail dialog (models + sessions tabs)
    detailLlm?.let { llm ->
        LlmDetailDialog(llm = llm, onDismiss = { detailLlm = null; refreshTick++ })
    }
}

@Composable
private fun LlmRegistryRow(
    llm: LlmRegistryEntryDto,
    onToggle: (Boolean, onDone: () -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    var toggling by remember { mutableStateOf(false) }
    val displayPairs = llm.models.ifEmpty {
        if (llm.computeNode.isNotBlank() || llm.model.isNotBlank())
            listOf(LlmModelPairDto(llm.computeNode, llm.model))
        else emptyList()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(llm.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(llm.kind, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
                if (llm.autoAddModels) {
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                        Text(stringResource(R.string.llm_models_auto_badge), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!llm.enabled) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("!", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (displayPairs.isEmpty()) {
                Text(stringResource(R.string.llm_models_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                displayPairs.take(3).forEach { pair ->
                    Text("${pair.computeNode} / ${pair.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (displayPairs.size > 3) {
                    Text("…+${displayPairs.size - 3} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (toggling) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(2.dp))
        } else {
            Switch(
                checked = llm.enabled,
                onCheckedChange = { newVal ->
                    toggling = true
                    onToggle(newVal) { toggling = false }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        IconButton(onClick = onDetails) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.llm_registry_edit), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** alpha.41 — session-backend kinds expose binary/console/git/claude fields. */
private val SESSION_BACKEND_KINDS = setOf(
    "claude-code", "aider", "goose", "gemini", "opencode", "opencode-acp", "opencode-prompt", "shell",
)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmRegistryDialog(
    existing: LlmRegistryEntryDto?,
    computeNodes: List<ComputeNodeDto>,
    onDismiss: () -> Unit,
    onSave: (LlmRegistryEntryDto) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: LLM_KINDS.first()) }
    var singleModel by remember(existing) { mutableStateOf(if (existing != null && existing.kind !in NODE_BASED_KINDS) existing.model else "") }
    var pretestEnabled by remember(existing) { mutableStateOf(existing?.pretestEnabled ?: false) }
    var kindDropdown by remember { mutableStateOf(false) }
    var nodeModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val isNodeBased = kind in NODE_BASED_KINDS
    val isAutoAdd = existing?.autoAddModels ?: false
    // alpha.41 core fields
    var apiKeyRef by remember(existing) { mutableStateOf(existing?.apiKeyRef ?: "") }
    var timeout by remember(existing) { mutableStateOf(existing?.timeout?.toString() ?: "") }
    val tags = remember(existing) { mutableStateListOf(*(existing?.tags?.toTypedArray() ?: emptyArray())) }
    var tagInput by remember { mutableStateOf("") }
    // alpha.41 session-backend fields
    val isSessionBackend = kind in SESSION_BACKEND_KINDS
    var binary by remember(existing) { mutableStateOf(existing?.binary ?: "") }
    var consoleCols by remember(existing) { mutableStateOf(existing?.consoleCols?.toString() ?: "") }
    var consoleRows by remember(existing) { mutableStateOf(existing?.consoleRows?.toString() ?: "") }
    var outputModeDropdown by remember { mutableStateOf(false) }
    var outputMode by remember(existing) { mutableStateOf(existing?.outputMode ?: "terminal") }
    var inputModeDropdown by remember { mutableStateOf(false) }
    var inputMode by remember(existing) { mutableStateOf(existing?.inputMode ?: "tmux") }
    var autoGitInit by remember(existing) { mutableStateOf(existing?.autoGitInit ?: false) }
    var autoGitCommit by remember(existing) { mutableStateOf(existing?.autoGitCommit ?: false) }
    // alpha.41 claude-code-specific fields
    val isClaudeCode = kind == "claude-code"
    var skipPermissions by remember(existing) { mutableStateOf(existing?.skipPermissions ?: false) }
    var channelEnabled by remember(existing) { mutableStateOf(existing?.channelEnabled ?: false) }
    var autoAcceptDisclaimer by remember(existing) { mutableStateOf(existing?.autoAcceptDisclaimer ?: false) }
    var permissionModeDropdown by remember { mutableStateOf(false) }
    var permissionMode by remember(existing) { mutableStateOf(existing?.permissionMode ?: "default") }
    var defaultEffortDropdown by remember { mutableStateOf(false) }
    var defaultEffort by remember(existing) { mutableStateOf(existing?.defaultEffort ?: "normal") }
    val fallbackChain = remember(existing) { mutableStateListOf(*(existing?.fallbackChain?.toTypedArray() ?: emptyArray())) }
    var fallbackInput by remember { mutableStateOf("") }

    // Per-node model pairs: initialize from existing.models or from legacy computeNode+model
    val modelPairs = remember(existing) {
        val initial = when {
            existing == null -> mutableListOf()
            existing.models.isNotEmpty() -> existing.models.toMutableList()
            existing.computeNode.isNotBlank() -> mutableListOf(LlmModelPairDto(existing.computeNode, existing.model))
            else -> mutableListOf()
        }
        mutableStateListOf(*initial.toTypedArray())
    }

    val scope = rememberCoroutineScope()

    // Pre-load models for all currently-selected compute nodes
    LaunchedEffect(kind) {
        val transport = resolveActiveTransport() ?: return@LaunchedEffect
        val nodesToLoad = if (isNodeBased) {
            modelPairs.map { it.computeNode }.filter { it.isNotBlank() }.toSet() +
                computeNodes.map { it.name }.toSet()
        } else emptySet()
        val loaded = mutableMapOf<String, List<String>>()
        nodesToLoad.forEach { nodeName ->
            transport.getComputeNodeModels(nodeName, kind).onSuccess { loaded[nodeName] = it }
        }
        nodeModels = loaded
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) stringResource(R.string.llm_registry_edit) else stringResource(R.string.llm_registry_add)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.llm_registry_kind_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { kindDropdown = true }) { Text(kind) }
                    DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                        LLM_KINDS.forEach { k ->
                            DropdownMenuItem(text = { Text(k) }, onClick = { kind = k; kindDropdown = false })
                        }
                    }
                }

                if (isNodeBased) {
                    // Per-node model table
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_models_node_col), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.llm_models_model_col), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(32.dp))
                    }
                    HorizontalDivider()

                    if (modelPairs.isEmpty() && isAutoAdd) {
                        Text(stringResource(R.string.llm_models_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    modelPairs.forEachIndexed { idx, pair ->
                        var nodeDropdown by remember { mutableStateOf(false) }
                        var modelDropdown by remember { mutableStateOf(false) }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // Compute node picker
                            Box(modifier = Modifier.weight(1f)) {
                                TextButton(onClick = { if (!isAutoAdd) nodeDropdown = true }, enabled = !isAutoAdd) {
                                    Text(pair.computeNode.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                                }
                                DropdownMenu(expanded = nodeDropdown, onDismissRequest = { nodeDropdown = false }) {
                                    computeNodes.forEach { n ->
                                        DropdownMenuItem(text = { Text(n.name) }, onClick = {
                                            modelPairs[idx] = pair.copy(computeNode = n.name)
                                            nodeDropdown = false
                                            scope.launch {
                                                val transport = resolveActiveTransport() ?: return@launch
                                                transport.getComputeNodeModels(n.name, kind).onSuccess { models ->
                                                    nodeModels = nodeModels + (n.name to models)
                                                }
                                            }
                                        })
                                    }
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            // Model picker / text
                            val availableModels = nodeModels[pair.computeNode] ?: emptyList()
                            Box(modifier = Modifier.weight(1f)) {
                                if (availableModels.isNotEmpty() && !isAutoAdd) {
                                    TextButton(onClick = { modelDropdown = true }) {
                                        Text(pair.model.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                                    }
                                    DropdownMenu(expanded = modelDropdown, onDismissRequest = { modelDropdown = false }) {
                                        availableModels.forEach { m ->
                                            DropdownMenuItem(text = { Text(m) }, onClick = { modelPairs[idx] = pair.copy(model = m); modelDropdown = false })
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = pair.model,
                                        onValueChange = { if (!isAutoAdd) modelPairs[idx] = pair.copy(model = it) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isAutoAdd,
                                        placeholder = { Text("model", style = MaterialTheme.typography.bodySmall) },
                                    )
                                }
                            }
                            if (!isAutoAdd) {
                                IconButton(onClick = { modelPairs.removeAt(idx) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Spacer(Modifier.width(32.dp))
                            }
                        }
                    }

                    if (!isAutoAdd) {
                        TextButton(
                            onClick = { modelPairs.add(LlmModelPairDto("", "")) },
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.llm_models_add_row), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    // SaaS: single model text input
                    OutlinedTextField(
                        value = singleModel,
                        onValueChange = { singleModel = it },
                        label = { Text(stringResource(R.string.llm_registry_model_label)) },
                        singleLine = true,
                        placeholder = { Text("e.g. gemini-2.0-flash") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Pretest enabled (G20: Switch replaces Checkbox)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.llm_registry_pretest_label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(checked = pretestEnabled, onCheckedChange = { pretestEnabled = it })
                }

                // alpha.41 core: API key ref, timeout, tags
                HorizontalDivider()
                OutlinedTextField(
                    value = apiKeyRef,
                    onValueChange = { apiKeyRef = it },
                    label = { Text(stringResource(R.string.llm_field_api_key_ref)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeout,
                        onValueChange = { if (it.all { c -> c.isDigit() }) timeout = it },
                        label = { Text(stringResource(R.string.llm_field_timeout)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Tags chip input
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick = { tags.remove(tag) },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        label = { Text(stringResource(R.string.llm_field_tags)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { if (tagInput.isNotBlank()) { tags.add(tagInput.trim()); tagInput = "" } }) { Text("+") }
                }

                // alpha.41 session-backend section
                if (isSessionBackend) {
                    HorizontalDivider()
                    Text(stringResource(R.string.llm_section_session_backend), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = binary, onValueChange = { binary = it }, label = { Text(stringResource(R.string.llm_field_binary)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = consoleCols, onValueChange = { if (it.all { c -> c.isDigit() }) consoleCols = it }, label = { Text(stringResource(R.string.llm_field_console_cols)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = consoleRows, onValueChange = { if (it.all { c -> c.isDigit() }) consoleRows = it }, label = { Text(stringResource(R.string.llm_field_console_rows)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    // Output mode
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_output_mode), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { outputModeDropdown = true }) { Text(outputMode) }
                            DropdownMenu(expanded = outputModeDropdown, onDismissRequest = { outputModeDropdown = false }) {
                                listOf("terminal", "log", "chat").forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { outputMode = m; outputModeDropdown = false })
                                }
                            }
                        }
                    }
                    // Input mode
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_input_mode), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { inputModeDropdown = true }) { Text(inputMode) }
                            DropdownMenu(expanded = inputModeDropdown, onDismissRequest = { inputModeDropdown = false }) {
                                listOf("tmux", "chat", "none").forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { inputMode = m; inputModeDropdown = false })
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_auto_git_init), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = autoGitInit, onCheckedChange = { autoGitInit = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_auto_git_commit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = autoGitCommit, onCheckedChange = { autoGitCommit = it })
                    }
                }

                // alpha.41 claude-code-specific section
                if (isClaudeCode) {
                    HorizontalDivider()
                    Text(stringResource(R.string.llm_section_claude), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_skip_permissions), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = skipPermissions, onCheckedChange = { skipPermissions = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_channel_enabled), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = channelEnabled, onCheckedChange = { channelEnabled = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_auto_accept), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = autoAcceptDisclaimer, onCheckedChange = { autoAcceptDisclaimer = it })
                    }
                    // Permission mode
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_permission_mode), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { permissionModeDropdown = true }) { Text(permissionMode) }
                            DropdownMenu(expanded = permissionModeDropdown, onDismissRequest = { permissionModeDropdown = false }) {
                                listOf("default", "acceptEdits", "bypassPermissions").forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { permissionMode = m; permissionModeDropdown = false })
                                }
                            }
                        }
                    }
                    // Default effort
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.llm_field_default_effort), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { defaultEffortDropdown = true }) { Text(defaultEffort) }
                            DropdownMenu(expanded = defaultEffortDropdown, onDismissRequest = { defaultEffortDropdown = false }) {
                                listOf("low", "normal", "high").forEach { e ->
                                    DropdownMenuItem(text = { Text(e) }, onClick = { defaultEffort = e; defaultEffortDropdown = false })
                                }
                            }
                        }
                    }
                    // Fallback chain
                    if (fallbackChain.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            fallbackChain.forEach { llm ->
                                AssistChip(onClick = { fallbackChain.remove(llm) }, label = { Text(llm, style = MaterialTheme.typography.labelSmall) }, trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) })
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = fallbackInput, onValueChange = { fallbackInput = it }, label = { Text(stringResource(R.string.llm_field_fallback_chain)) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (fallbackInput.isNotBlank()) { fallbackChain.add(fallbackInput.trim()); fallbackInput = "" } }) { Text("+") }
                    }
                }
            }
        },
        confirmButton = {
            val saveEnabled = name.isNotBlank() && when {
                isNodeBased && !isAutoAdd -> modelPairs.isNotEmpty() && modelPairs.all { it.model.isNotBlank() }
                isNodeBased && isAutoAdd -> true
                else -> singleModel.isNotBlank()
            }
            TextButton(
                onClick = {
                    val commonFields = { base: LlmRegistryEntryDto ->
                        base.copy(
                            apiKeyRef = apiKeyRef.trim().ifBlank { null },
                            timeout = timeout.trim().toIntOrNull(),
                            tags = tags.toList().ifEmpty { null },
                            binary = if (isSessionBackend) binary.trim().ifBlank { null } else null,
                            consoleCols = if (isSessionBackend) consoleCols.trim().toIntOrNull() else null,
                            consoleRows = if (isSessionBackend) consoleRows.trim().toIntOrNull() else null,
                            outputMode = if (isSessionBackend) outputMode.ifBlank { null } else null,
                            inputMode = if (isSessionBackend) inputMode.ifBlank { null } else null,
                            autoGitInit = if (isSessionBackend) autoGitInit else null,
                            autoGitCommit = if (isSessionBackend) autoGitCommit else null,
                            skipPermissions = if (isClaudeCode) skipPermissions else null,
                            channelEnabled = if (isClaudeCode) channelEnabled else null,
                            autoAcceptDisclaimer = if (isClaudeCode) autoAcceptDisclaimer else null,
                            permissionMode = if (isClaudeCode) permissionMode.ifBlank { null } else null,
                            defaultEffort = if (isClaudeCode) defaultEffort.ifBlank { null } else null,
                            fallbackChain = if (isClaudeCode) fallbackChain.toList().ifEmpty { null } else null,
                        )
                    }
                    val dto = if (isNodeBased) {
                        commonFields(
                            LlmRegistryEntryDto(
                                name = name.trim(),
                                kind = kind,
                                computeNode = modelPairs.firstOrNull()?.computeNode ?: "",
                                computeNodes = modelPairs.drop(1).map { it.computeNode },
                                model = modelPairs.firstOrNull()?.model ?: "",
                                models = modelPairs.toList(),
                                enabled = existing?.enabled ?: true,
                                pretestEnabled = pretestEnabled,
                                autoAddModels = isAutoAdd,
                            ),
                        )
                    } else {
                        commonFields(
                            LlmRegistryEntryDto(
                                name = name.trim(),
                                kind = kind,
                                computeNode = "",
                                model = singleModel.trim(),
                                enabled = existing?.enabled ?: true,
                                pretestEnabled = pretestEnabled,
                            ),
                        )
                    }
                    onSave(dto)
                },
                enabled = saveEnabled,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LlmDetailDialog(
    llm: LlmRegistryEntryDto,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var refreshingModels by remember { mutableStateOf(false) }
    var modelRefreshBanner by remember { mutableStateOf<String?>(null) }

    var sessions by remember { mutableStateOf<List<LlmSessionRefDto>>(emptyList()) }
    var sessionsTotal by remember { mutableStateOf(0) }
    var sessionsPage by remember { mutableStateOf(1) }
    var sessionsSize by remember { mutableStateOf(10) }
    var sessionsLoading by remember { mutableStateOf(false) }
    var sessionsSizeDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, sessionsPage, sessionsSize) {
        if (selectedTab == 1) {
            sessionsLoading = true
            val transport = resolveActiveTransport() ?: run { sessionsLoading = false; return@LaunchedEffect }
            transport.getLlmSessions(llm.name, sessionsPage, sessionsSize).fold(
                onSuccess = { sessions = it.sessions; sessionsTotal = it.total },
                onFailure = {},
            )
            sessionsLoading = false
        }
    }

    val displayPairs = llm.models.ifEmpty {
        if (llm.computeNode.isNotBlank() || llm.model.isNotBlank())
            listOf(LlmModelPairDto(llm.computeNode, llm.model))
        else emptyList()
    }
    val totalPages = if (sessionsSize > 0) (sessionsTotal + sessionsSize - 1) / sessionsSize else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(llm.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.llm_models_tab)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.llm_in_use_tab)) })
                }
                when (selectedTab) {
                    0 -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.llm_models_tab), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            if (refreshingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        refreshingModels = true
                                        modelRefreshBanner = null
                                        val transport = resolveActiveTransport()
                                        transport?.updateLlm(llm.name, llm)?.fold(
                                            onSuccess = { modelRefreshBanner = null },
                                            onFailure = { modelRefreshBanner = it.message },
                                        )
                                        refreshingModels = false
                                    }
                                }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        modelRefreshBanner?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (displayPairs.isEmpty()) {
                            Text(stringResource(R.string.llm_models_none), modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(stringResource(R.string.llm_models_node_col), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.llm_models_model_col), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(displayPairs) { pair ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(pair.computeNode, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(pair.model, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    1 -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${sessionsTotal} ${stringResource(R.string.llm_in_use_tab)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Box {
                                TextButton(onClick = { sessionsSizeDropdown = true }) { Text("$sessionsSize / page") }
                                DropdownMenu(expanded = sessionsSizeDropdown, onDismissRequest = { sessionsSizeDropdown = false }) {
                                    listOf(5, 10, 50).forEach { sz ->
                                        DropdownMenuItem(text = { Text("$sz") }, onClick = { sessionsSize = sz; sessionsPage = 1; sessionsSizeDropdown = false })
                                    }
                                }
                            }
                        }
                        if (sessionsLoading) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (sessions.isEmpty()) {
                            Text(stringResource(R.string.llm_in_use_none), modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(stringResource(R.string.llm_in_use_task_col), modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("State", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(sessions) { session ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(session.task, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                                        Text(session.state, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                }
                            }
                            // Pagination controls
                            if (totalPages > 1) {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { if (sessionsPage > 1) sessionsPage-- }, enabled = sessionsPage > 1) { Text("<") }
                                    Text("$sessionsPage / $totalPages", style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = { if (sessionsPage < totalPages) sessionsPage++ }, enabled = sessionsPage < totalPages) { Text(">") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
