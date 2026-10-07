package com.dmzs.datawatchclient.ui.compute

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent
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
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal suspend fun resolveActiveTransport() =
    ServiceLocator.profileRepository.observeAll().first().let { profiles ->
        val activeId = ServiceLocator.activeServerStore.get()
        (
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled }
        )?.let { ServiceLocator.transportFor(it) }
    }


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
    var warningBanner by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedLlm by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var llmToDelete by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var llmDeleteBlocked by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var detailLlm by remember { mutableStateOf<LlmRegistryEntryDto?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    // PWA "</> YAML": raw editor for this LLM; after save the form reopens.
    var yamlLlmName by remember { mutableStateOf<String?>(null) }
    var reopenAfterYaml by remember { mutableStateOf<String?>(null) }
    val yamlAfterSaveMsg = stringResource(R.string.llm_yaml_after_save)
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(refreshTick) {
        loading = true
        val transport =
            resolveActiveTransport() ?: run {
                banner = "No enabled server."
                loading = false
                return@LaunchedEffect
            }
        transport.listLlms().fold(
            onSuccess = {
                llms = it
                banner = null
            },
            onFailure = { banner = "LLMs unavailable — ${it.message ?: it::class.simpleName}" },
        )
        transport.listComputeNodes().onSuccess { computeNodes = it }
        transport.getMigrationStatus().onSuccess { migrationStatus = it }
        loading = false
        reopenAfterYaml?.let { n ->
            reopenAfterYaml = null
            llms.firstOrNull { it.name == n }?.let {
                selectedLlm = it
                showAddDialog = true
            }
        }
    }

    PwaCard(
        id = "llms",
        title = stringResource(R.string.settings_llm_registry_title),
        headerActions = {
            IconButton(onClick = {
                selectedLlm = null
                showAddDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.llm_registry_add))
            }
        },
    ) {
        val migCount = if (migrationStatus?.show == true) migrationStatus?.migrated?.size ?: 0 else 0
        if (migCount > 0) {
            Row(
                modifier =
                    Modifier
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
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.llm_migration_dismiss),
                        tint = Color(0xFF5D4037),
                    )
                }
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
        warningBanner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF59E0B),
            )
        }
        if (loading) {
            DatawatchLoadingContent(modifier = Modifier.padding(horizontal = 12.dp), label = androidx.compose.ui.res.stringResource(com.dmzs.datawatchclient.R.string.common_loading))
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
                    onToggle = { enabled, onDone ->
                        scope.launch {
                            resolveActiveTransport()?.enableLlm(llm.name, enabled)?.fold(
                                onSuccess = {
                                    warningBanner = null
                                    refreshTick++
                                    onDone()
                                },
                                onFailure = { err ->
                                    val msg = err.message ?: err::class.simpleName ?: ""
                                    if (msg.contains("unsupported", ignoreCase = true) ||
                                        msg.contains("auto-created", ignoreCase = true) ||
                                        msg.contains("kind", ignoreCase = true)
                                    ) {
                                        warningBanner = "This LLM type doesn't support enable/disable — it remains available for session selection"
                                        banner = null
                                    } else {
                                        banner = "Toggle failed — $msg"
                                    }
                                    onDone()
                                },
                            )
                        }
                    },
                    onEdit = {
                        selectedLlm = llm
                        showAddDialog = true
                    },
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
            onDismiss = {
                showAddDialog = false
                selectedLlm = null
            },
            onOpenYaml = {
                val n = selectedLlm?.name
                if (n == null) {
                    // PWA: "YAML editor available after first save" toast.
                    android.widget.Toast.makeText(context, yamlAfterSaveMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showAddDialog = false
                    selectedLlm = null
                    yamlLlmName = n
                }
            },
            onSave = { dto ->
                scope.launch {
                    val transport = resolveActiveTransport() ?: return@launch
                    val result =
                        if (selectedLlm != null) {
                            transport.updateLlm(
                                selectedLlm!!.name,
                                dto,
                            )
                        } else {
                            transport.createLlm(dto)
                        }
                    result.fold(
                        onSuccess = {
                            showAddDialog = false
                            selectedLlm = null
                            refreshTick++
                        },
                        onFailure = { banner = "Save failed — ${it.message ?: it::class.simpleName}" },
                    )
                }
            },
        )
    }

    yamlLlmName?.let { n ->
        LlmYamlDialog(
            name = n,
            onDismiss = { yamlLlmName = null },
            onSaved = {
                yamlLlmName = null
                reopenAfterYaml = n
                refreshTick++
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
                            onSuccess = {
                                llmToDelete = null
                                refreshTick++
                            },
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
                                        onSuccess = {
                                            llmDeleteBlocked = null
                                            showForceConfirm = false
                                            refreshTick++
                                        },
                                        onFailure = {
                                            banner = "Force delete failed — ${it.message}"
                                            llmDeleteBlocked = null
                                        },
                                    )
                                },
                                onFailure = {
                                    banner = "Force reassign failed — ${it.message}"
                                    llmDeleteBlocked = null
                                },
                            )
                            reassigning = false
                        }
                    }, enabled = !reassigning) {
                        if (reassigning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(R.string.action_delete))
                        }
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
                                    DropdownMenuItem(text = { Text(other.name) }, onClick = {
                                        reassignTarget = other.name
                                        reassignDropdown = false
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { showForceConfirm = true }) {
                            Text(
                                stringResource(R.string.llm_force_delete),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
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
                                            onSuccess = {
                                                llmDeleteBlocked = null
                                                refreshTick++
                                            },
                                            onFailure = {
                                                banner = "Delete after reassign failed — ${it.message}"
                                                llmDeleteBlocked = null
                                            },
                                        )
                                    },
                                    onFailure = { err ->
                                        if (err is TransportError.Conflict) {
                                            showForceConfirm = true
                                        } else {
                                            banner = "Reassign failed — ${err.message}"
                                            llmDeleteBlocked = null
                                        }
                                    },
                                )
                                reassigning = false
                            }
                        },
                        enabled = reassignTarget.isNotBlank() && !reassigning,
                    ) {
                        if (reassigning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(R.string.llm_reassign_btn))
                        }
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
        LlmDetailDialog(llm = llm, onDismiss = {
            detailLlm = null
            refreshTick++
        })
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
    var menuOpen by remember { mutableStateOf(false) }
    val displayPairs =
        llm.models.ifEmpty {
            if (llm.computeNode.isNotBlank() || llm.model.isNotBlank()) {
                listOf(LlmModelPairDto(llm.computeNode, llm.model))
            } else {
                emptyList()
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    llm.name,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // PWA `llm_auto` pill (auto_created LLMs) — same style as the compute-node pill.
                if (llm.autoCreated) {
                    Text(
                        stringResource(R.string.llm_auto),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                ).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(llm.kind, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                )
                if (llm.autoAddModels) {
                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            stringResource(R.string.llm_models_auto_badge),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!llm.enabled) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("!", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (displayPairs.isEmpty()) {
                Text(
                    stringResource(R.string.llm_models_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                displayPairs.take(3).forEach { pair ->
                    val label = if (pair.computeNode.isNotBlank()) "${pair.computeNode} / ${pair.model}" else pair.model
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (displayPairs.size > 3) {
                    Text(
                        "…+${displayPairs.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.llm_in_use_tab)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDetails()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.llm_registry_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** alpha.41 — session-backend kinds expose binary/console/git/claude fields. */
private val SESSION_BACKEND_KINDS =
    setOf(
        "claude-code",
        "aider",
        "goose",
        "gemini",
        "opencode",
        "opencode-acp",
        "opencode-prompt",
        "shell",
    )

/** PWA `_llmSaasKinds` — no ComputeNodes / node column / auto-add. */
private val LLM_SAAS_KINDS = setOf("claude-code", "aider", "goose", "gemini")

/** PWA `_renderLLMEditPanel` allKinds, in the same order. */
private val LLM_KINDS =
    listOf(
        "ollama",
        "openwebui",
        "opencode",
        "claude-code",
        "opencode-acp",
        "opencode-prompt",
        "aider",
        "goose",
        "gemini",
        "council",
        "shell",
    )

/** PWA llmEditOutputMode / llmEditInputMode / llmEditPermMode / llmEditEffort options ("" = default/none). */
private val LLM_OUTPUT_MODES = listOf("", "terminal", "log", "chat")
private val LLM_INPUT_MODES = listOf("", "tmux", "none")
private val LLM_PERMISSION_MODES = listOf("", "plan", "acceptEdits", "auto", "bypassPermissions", "dontAsk", "default")
private val LLM_EFFORTS = listOf("", "quick", "normal", "thorough")

/** One labelled dropdown row; "" renders as [emptyLabel]. */
@Composable
private fun LlmChoiceRow(
    label: String,
    value: String,
    options: List<String>,
    emptyLabel: String,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(value.ifBlank { emptyLabel }) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(o.ifBlank { emptyLabel }) }, onClick = {
                        onChange(o)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun LlmSwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Chip list + text field + "+" (PWA renderBadgeInput, freeform). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmChipInput(
    label: String,
    items: MutableList<String>,
    placeholder: String,
) {
    var input by remember { mutableStateOf("") }
    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.toList().forEach { item ->
                AssistChip(
                    onClick = { items.remove(item) },
                    label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) },
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            // Accept comma-separated input like the PWA badge input.
            input.split(',').map { it.trim() }.filter { it.isNotEmpty() && it !in items }.forEach { items.add(it) }
            input = ""
        }) { Text("+") }
    }
}

/**
 * PWA `_renderLLMEditPanel` (§8.9) — field order, labels and kind-dependent sections
 * (`_llmKindChanged`): Name · Kind · ComputeNodes (local kinds) · Enabled Models
 * (node column hidden for SaaS kinds) · Auto-enable new models (local kinds) · API key
 * reference · Timeout · Max in-flight · Tags · session-backend section · claude-code
 * section · Test model + Test · `</> YAML` / Cancel / Save|Add. A literal API key is
 * never shown; blank keeps it (SecretMask rule). `auto_created` is never sent
 * (LlmSaveBody).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmRegistryDialog(
    existing: LlmRegistryEntryDto?,
    computeNodes: List<ComputeNodeDto>,
    onDismiss: () -> Unit,
    onOpenYaml: () -> Unit,
    onSave: (LlmRegistryEntryDto) -> Unit,
) {
    val isEdit = existing != null
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: LLM_KINDS.first()) }
    var kindDropdown by remember { mutableStateOf(false) }
    var nodeModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val isSaas = kind in LLM_SAAS_KINDS
    val isSessionBackend = kind in SESSION_BACKEND_KINDS
    val isClaudeCode = kind == "claude-code"
    // ComputeNodes multi-select; selection order = failover order.
    val selectedNodes =
        remember(existing) {
            val initial = existing?.computeNodes?.ifEmpty { listOfNotNull(existing.computeNode.takeIf { it.isNotBlank() }) }
            mutableStateListOf(*(initial ?: emptyList()).toTypedArray())
        }
    var autoAdd by remember(existing) { mutableStateOf(existing?.autoAddModels ?: false) }
    // A literal key is never shown; the field starts blank and blank keeps it (iOS form rule).
    val existingKey = existing?.apiKeyRef.orEmpty()
    // Servers ≥ v8.63.1 redact it (blank + api_key_ref_present); blank on save
    // omits the field and the server keeps the stored key.
    val existingRedactedKey = existingKey.isEmpty() && existing?.apiKeyRefPresent == true
    val existingLiteralKey = existingKey.isNotEmpty() && !com.dmzs.datawatchclient.transport.SecretMask.isReference(existingKey)
    var apiKeyRef by remember(existing) { mutableStateOf(if (existingLiteralKey) "" else existingKey) }
    var timeout by remember(existing) { mutableStateOf(existing?.timeoutSeconds?.takeIf { it > 0 }?.toString() ?: "") }
    var maxInflight by remember(existing) { mutableStateOf(existing?.maxInflight?.takeIf { it > 0 }?.toString() ?: "") }
    val tags = remember(existing) { mutableStateListOf(*(existing?.tags?.toTypedArray() ?: emptyArray())) }
    var binary by remember(existing) { mutableStateOf(existing?.binary ?: "") }
    var consoleCols by remember(existing) { mutableStateOf(existing?.consoleCols?.takeIf { it > 0 }?.toString() ?: "") }
    var consoleRows by remember(existing) { mutableStateOf(existing?.consoleRows?.takeIf { it > 0 }?.toString() ?: "") }
    var outputMode by remember(existing) { mutableStateOf(existing?.outputMode ?: "") }
    var inputMode by remember(existing) { mutableStateOf(existing?.inputMode ?: "") }
    var autoGitInit by remember(existing) { mutableStateOf(existing?.autoGitInit ?: false) }
    var autoGitCommit by remember(existing) { mutableStateOf(existing?.autoGitCommit ?: false) }
    var skipPermissions by remember(existing) { mutableStateOf(existing?.skipPermissions ?: false) }
    var channelEnabled by remember(existing) { mutableStateOf(existing?.channelEnabled ?: false) }
    var autoAcceptDisclaimer by remember(existing) { mutableStateOf(existing?.autoAcceptDisclaimer ?: false) }
    var permissionMode by remember(existing) { mutableStateOf(existing?.permissionMode ?: "") }
    var defaultEffort by remember(existing) { mutableStateOf(existing?.defaultEffort ?: "") }
    val fallbackChain =
        remember(existing) { mutableStateListOf(*(existing?.fallbackChain?.toTypedArray() ?: emptyArray())) }
    var testModel by remember(existing) { mutableStateOf("") }
    var testModelDropdown by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var testing by remember { mutableStateOf(false) }

    // Enabled models: existing.models, else the legacy single model (PWA existingModels).
    val modelPairs =
        remember(existing) {
            val initial =
                when {
                    existing == null -> emptyList()
                    existing.models.isNotEmpty() -> existing.models
                    existing.model.isNotBlank() ->
                        listOf(LlmModelPairDto(existing.computeNodes.firstOrNull() ?: existing.computeNode, existing.model))
                    else -> emptyList()
                }
            mutableStateListOf(*initial.toTypedArray())
        }

    val scope = rememberCoroutineScope()
    val saveFirstMsg = stringResource(R.string.llm_test_save_first)
    val testingMsg = stringResource(R.string.llm_test_running)
    val defaultLabel = stringResource(R.string.llm_option_default)
    val noneLabel = stringResource(R.string.llm_option_none)

    // opencode kinds source their model list from /api/opencode/models?node=<n>
    // (not /api/compute/nodes/$n/models), matching the session-wizard behaviour.
    val isOpenCode = kind.startsWith("opencode", ignoreCase = true)
    LaunchedEffect(kind) {
        val transport = resolveActiveTransport() ?: return@LaunchedEffect
        val nodesToLoad = if (isSaas) emptySet() else computeNodes.map { it.name }.toSet()
        val loaded = mutableMapOf<String, List<String>>()
        nodesToLoad.forEach { nodeName ->
            if (isOpenCode) {
                transport.fetchOpenCodeModels(node = nodeName).onSuccess { resp ->
                    loaded[nodeName] = resp.models.map { it.id }.filter { it.isNotBlank() }
                }
            } else {
                transport.getComputeNodeModels(nodeName, kind).onSuccess { loaded[nodeName] = it }
            }
        }
        nodeModels = loaded
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEdit) {
                    "✎ " + stringResource(R.string.llm_registry_edit) + ": " + existing!!.name
                } else {
                    "+ " + stringResource(R.string.llm_registry_add)
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.llm_field_name)) },
                    placeholder = { Text("llama3-70b") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isEdit,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.llm_registry_kind_label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { kindDropdown = true }) { Text(kind) }
                        DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                            LLM_KINDS.forEach { k ->
                                DropdownMenuItem(text = { Text(k) }, onClick = {
                                    kind = k
                                    kindDropdown = false
                                })
                            }
                        }
                    }
                }

                // ComputeNodes multi-select (local kinds only); order = failover order.
                if (!isSaas) {
                    Text(stringResource(R.string.llm_field_compute_nodes), style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        computeNodes.forEach { n ->
                            val idx = selectedNodes.indexOf(n.name)
                            androidx.compose.material3.FilterChip(
                                selected = idx >= 0,
                                onClick = { if (idx >= 0) selectedNodes.remove(n.name) else selectedNodes.add(n.name) },
                                label = {
                                    Text(
                                        (if (idx >= 0) "${idx + 1}. " else "") + "${n.name} (${n.kind.ifBlank { "?" }})",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.llm_field_compute_nodes_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Enabled Models table (node column hidden for SaaS kinds).
                HorizontalDivider()
                Text(stringResource(R.string.llm_field_enabled_models), style = MaterialTheme.typography.labelSmall)
                Text(
                    stringResource(R.string.llm_field_enabled_models_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!isSaas) {
                        Text(
                            stringResource(R.string.llm_models_node_col),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        stringResource(R.string.llm_models_model_col),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(32.dp))
                }
                modelPairs.forEachIndexed { idx, pair ->
                    var nodeDropdown by remember { mutableStateOf(false) }
                    var modelDropdown by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!isSaas) {
                            Box(modifier = Modifier.weight(1f)) {
                                TextButton(onClick = { nodeDropdown = true }) {
                                    Text(pair.computeNode.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                                }
                                DropdownMenu(expanded = nodeDropdown, onDismissRequest = { nodeDropdown = false }) {
                                    computeNodes.forEach { n ->
                                        DropdownMenuItem(text = { Text(n.name) }, onClick = {
                                            modelPairs[idx] = pair.copy(computeNode = n.name)
                                            nodeDropdown = false
                                        })
                                    }
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        val availableModels = if (isSaas) emptyList() else nodeModels[pair.computeNode].orEmpty()
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = pair.model,
                                onValueChange = { modelPairs[idx] = pair.copy(model = it) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. qwen3:8b", style = MaterialTheme.typography.bodySmall) },
                                trailingIcon =
                                    if (availableModels.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { modelDropdown = true }) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                            DropdownMenu(expanded = modelDropdown, onDismissRequest = { modelDropdown = false }) {
                                availableModels.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = {
                                        modelPairs[idx] = pair.copy(model = m)
                                        modelDropdown = false
                                    })
                                }
                            }
                        }
                        IconButton(onClick = { modelPairs.removeAt(idx) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                TextButton(onClick = { modelPairs.add(LlmModelPairDto("", "")) }) {
                    Text(stringResource(R.string.llm_models_add_row), style = MaterialTheme.typography.labelSmall)
                }
                if (!isSaas) {
                    LlmSwitchRow(stringResource(R.string.llm_field_auto_add_models), autoAdd) { autoAdd = it }
                }

                HorizontalDivider()
                OutlinedTextField(
                    value = apiKeyRef,
                    onValueChange = { apiKeyRef = it },
                    label = { Text(stringResource(R.string.llm_field_api_key_ref)) },
                    placeholder = {
                        Text(
                            if (existingLiteralKey || existingRedactedKey) com.dmzs.datawatchclient.transport.SecretMask.PLACEHOLDER else "\${secret:anthropic-key}",
                        )
                    },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { if (it.all { c -> c.isDigit() }) timeout = it },
                    label = { Text(stringResource(R.string.llm_field_timeout)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maxInflight,
                    onValueChange = { if (it.all { c -> c.isDigit() }) maxInflight = it },
                    label = { Text(stringResource(R.string.llm_field_max_inflight)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LlmChipInput(stringResource(R.string.llm_field_tags), tags, "fast, coding…")

                // PWA llmSessionSect (session-backend kinds).
                if (isSessionBackend) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = binary,
                        onValueChange = { binary = it },
                        label = { Text(stringResource(R.string.llm_field_binary)) },
                        placeholder = { Text("e.g. claude / aider / goose") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = consoleCols,
                            onValueChange = { if (it.all { c -> c.isDigit() }) consoleCols = it },
                            label = { Text(stringResource(R.string.llm_field_console_cols)) },
                            placeholder = { Text("120") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = consoleRows,
                            onValueChange = { if (it.all { c -> c.isDigit() }) consoleRows = it },
                            label = { Text(stringResource(R.string.llm_field_console_rows)) },
                            placeholder = { Text("40") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LlmChoiceRow(stringResource(R.string.llm_field_output_mode), outputMode, LLM_OUTPUT_MODES, defaultLabel) {
                        outputMode = it
                    }
                    LlmChoiceRow(stringResource(R.string.llm_field_input_mode), inputMode, LLM_INPUT_MODES, defaultLabel) {
                        inputMode = it
                    }
                    LlmSwitchRow(stringResource(R.string.llm_field_auto_git_init), autoGitInit) { autoGitInit = it }
                    LlmSwitchRow(stringResource(R.string.llm_field_auto_git_commit), autoGitCommit) { autoGitCommit = it }
                }

                // PWA llmClaudeSect (claude-code only).
                if (isClaudeCode) {
                    HorizontalDivider()
                    LlmSwitchRow(stringResource(R.string.llm_field_skip_permissions), skipPermissions) { skipPermissions = it }
                    LlmSwitchRow(stringResource(R.string.llm_field_channel_enabled), channelEnabled) { channelEnabled = it }
                    LlmSwitchRow(stringResource(R.string.llm_field_auto_accept), autoAcceptDisclaimer) { autoAcceptDisclaimer = it }
                    LlmChoiceRow(
                        stringResource(R.string.llm_field_permission_mode),
                        permissionMode,
                        LLM_PERMISSION_MODES,
                        noneLabel,
                    ) { permissionMode = it }
                    LlmChoiceRow(stringResource(R.string.llm_field_default_effort), defaultEffort, LLM_EFFORTS, defaultLabel) {
                        defaultEffort = it
                    }
                    LlmChipInput(stringResource(R.string.llm_field_fallback_chain), fallbackChain, "claude-personal, gemini-backup…")
                }

                // PWA test row: status + "Test model:" (first enabled | node / model) + Test.
                testStatus?.let { (msg, tone) ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (tone) {
                                1 -> Color(0xFF10B981)
                                2 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.llm_test_model_label), style = MaterialTheme.typography.bodySmall)
                    Box(modifier = Modifier.weight(1f)) {
                        val firstLabel = stringResource(R.string.llm_test_model_first)
                        TextButton(onClick = { testModelDropdown = true }) { Text(testModel.ifBlank { firstLabel }) }
                        DropdownMenu(expanded = testModelDropdown, onDismissRequest = { testModelDropdown = false }) {
                            DropdownMenuItem(text = { Text(firstLabel) }, onClick = {
                                testModel = ""
                                testModelDropdown = false
                            })
                            modelPairs.filter { it.model.isNotBlank() }.forEach { p ->
                                val label = if (p.computeNode.isNotBlank()) "${p.computeNode} / ${p.model}" else p.model
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    testModel = p.model
                                    testModelDropdown = false
                                })
                            }
                        }
                    }
                    TextButton(
                        enabled = !testing,
                        onClick = {
                            val editName = existing?.name
                            if (editName == null) {
                                testStatus = saveFirstMsg to 0
                                return@TextButton
                            }
                            testing = true
                            testStatus = testingMsg to 0
                            scope.launch {
                                val transport = resolveActiveTransport()
                                val result =
                                    transport?.testLlmJson(editName, testModel.ifBlank { null })
                                        ?: Result.failure(IllegalStateException("No server"))
                                testing = false
                                testStatus =
                                    result.fold(
                                        onSuccess = { o ->
                                            val text =
                                                (o["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                                    ?: o.toString().take(160)
                                            "✓ " + text.take(160).ifEmpty { "OK" } to 1
                                        },
                                        onFailure = { e -> "✕ " + (e.message ?: "Test failed").take(240) to 2 },
                                    )
                            }
                        },
                    ) { Text(stringResource(R.string.llm_test_btn)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanModels =
                        modelPairs.filter { it.model.isNotBlank() }.map { p ->
                            LlmModelPairDto(if (isSaas) "" else p.computeNode, p.model.trim())
                        }
                    val dto =
                        LlmRegistryEntryDto(
                            name = if (isEdit) existing!!.name else name.trim(),
                            kind = kind,
                            computeNodes = if (isSaas) emptyList() else selectedNodes.toList(),
                            model = cleanModels.firstOrNull()?.model.orEmpty(),
                            models = cleanModels,
                            enabled = existing?.enabled ?: true,
                            pretestEnabled = existing?.pretestEnabled ?: false,
                            autoAddModels = !isSaas && autoAdd,
                            apiKeyRef = apiKeyRef.trim().ifBlank { existingKey.takeIf { existingLiteralKey } },
                            timeoutSeconds = timeout.trim().toIntOrNull(),
                            maxInflight = maxInflight.trim().toIntOrNull(),
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
                    onSave(dto)
                },
                // PWA: only the name is required (add mode).
                enabled = isEdit || name.isNotBlank(),
            ) { Text(stringResource(if (isEdit) R.string.action_save else R.string.llm_add_btn)) }
        },
        dismissButton = {
            Row {
                // PWA buildLLM panel: "</> YAML" escape hatch (left of Cancel).
                TextButton(onClick = onOpenYaml) { Text(stringResource(R.string.llm_yaml_btn)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
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
            val transport =
                resolveActiveTransport() ?: run {
                    sessionsLoading = false
                    return@LaunchedEffect
                }
            transport.getLlmSessions(llm.name, sessionsPage, sessionsSize).fold(
                onSuccess = {
                    sessions = it.sessions
                    sessionsTotal = it.total
                },
                onFailure = {},
            )
            sessionsLoading = false
        }
    }

    val displayPairs =
        llm.models.ifEmpty {
            if (llm.computeNode.isNotBlank() || llm.model.isNotBlank()) {
                listOf(LlmModelPairDto(llm.computeNode, llm.model))
            } else {
                emptyList()
            }
        }
    val totalPages = if (sessionsSize > 0) (sessionsTotal + sessionsSize - 1) / sessionsSize else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(llm.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = {
                        selectedTab = 0
                    }, text = { Text(stringResource(R.string.llm_models_tab)) })
                    Tab(selected = selectedTab == 1, onClick = {
                        selectedTab = 1
                    }, text = { Text(stringResource(R.string.llm_in_use_tab)) })
                }
                when (selectedTab) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.llm_models_tab),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                            )
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
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        modelRefreshBanner?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (displayPairs.isEmpty()) {
                            Text(
                                stringResource(R.string.llm_models_none),
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    stringResource(R.string.llm_models_node_col),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.llm_models_model_col),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(displayPairs) { pair ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(
                                            pair.computeNode,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            pair.model,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    1 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$sessionsTotal ${stringResource(R.string.llm_in_use_tab)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Box {
                                TextButton(onClick = { sessionsSizeDropdown = true }) { Text("$sessionsSize / page") }
                                DropdownMenu(
                                    expanded = sessionsSizeDropdown,
                                    onDismissRequest = { sessionsSizeDropdown = false },
                                ) {
                                    listOf(5, 10, 50).forEach { sz ->
                                        DropdownMenuItem(text = { Text("$sz") }, onClick = {
                                            sessionsSize = sz
                                            sessionsPage = 1
                                            sessionsSizeDropdown = false
                                        })
                                    }
                                }
                            }
                        }
                        if (sessionsLoading) {
                            com.dmzs.datawatchclient.ui.common.PwaLoadingText()
                        } else if (sessions.isEmpty()) {
                            Text(
                                stringResource(R.string.llm_in_use_none),
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    stringResource(R.string.llm_in_use_task_col),
                                    modifier = Modifier.weight(2f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "State",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(sessions) { session ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(
                                            session.task,
                                            modifier = Modifier.weight(2f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            session.state,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                            // Pagination controls
                            if (totalPages > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = { if (sessionsPage > 1) sessionsPage-- },
                                        enabled = sessionsPage > 1,
                                    ) { Text("<") }
                                    Text("$sessionsPage / $totalPages", style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = {
                                        if (sessionsPage < totalPages) sessionsPage++
                                    }, enabled = sessionsPage < totalPages) { Text(">") }
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
