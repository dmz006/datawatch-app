package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * New PRD modal — full parity with PWA v5.26.60-62 openPRDCreateModal.
 *
 * Profile modes:
 *   __dir__ → project directory + backend/effort/model
 *   <profile name> → cluster dropdown; backend/effort/model hidden
 *
 * Model dropdown: only shown for `ollama` and `openwebui` backends,
 * populated from /api/ollama/models and /api/openwebui/models respectively.
 * Hidden (and value cleared) for all other backends — mirrors PWA
 * refreshLLMModelField which only fetches model lists for those two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPrdDialog(
    onDismiss: () -> Unit,
    onCreate: (NewPrdRequestDto) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    /** "__dir__" sentinel = project-directory mode; else = profile name. */
    var profile by remember { mutableStateOf("__dir__") }
    var profileMenuOpen by remember { mutableStateOf(false) }

    var projectDir by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf("") }
    var backendMenuOpen by remember { mutableStateOf(false) }
    var effort by remember { mutableStateOf("") }
    var effortMenuOpen by remember { mutableStateOf(false) }
    /** Model value; only used when backend has a known model list. */
    var model by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }

    var cluster by remember { mutableStateOf("") }
    var clusterMenuOpen by remember { mutableStateOf(false) }

    var decomp by remember { mutableStateOf("") }
    var decompMenuOpen by remember { mutableStateOf(false) }

    var permissionMode by remember { mutableStateOf("") }
    var permissionModeMenuOpen by remember { mutableStateOf(false) }

    var prdType by remember { mutableStateOf("") }
    var prdTypeMenuOpen by remember { mutableStateOf(false) }
    var guidedMode by remember { mutableStateOf(false) }
    var skills by remember { mutableStateOf("") }

    var projectProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var clusterProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var backendOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var permissionModeOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    val inheritLabel = stringResource(R.string.new_prd_inherit)
    val backendDefaultLabel = stringResource(R.string.new_prd_backend_default)

    /** Models keyed by backend name; only ollama and openwebui are fetched. */
    var availableModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    /** Model names for the currently-selected backend (empty = hide field). */
    val modelsForBackend: List<String> = availableModels[backend].orEmpty()

    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get() ?: return@runCatching
            val sp =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled } ?: return@runCatching
            val transport = ServiceLocator.transportFor(sp)

            coroutineScope {
                val backendsD = async { transport.listBackends() }
                val projectD = async { transport.listKindProfiles("project") }
                val clusterD = async { transport.listKindProfiles("cluster") }
                val ollamaD = async { transport.listOllamaModels() }
                val owuiD = async { transport.listOpenWebUiModels() }
                val pmD = async { transport.listClaudePermissionModes() }

                backendsD.await().onSuccess { view -> backendOptions = view.llm }
                projectD.await().onSuccess { list ->
                    projectProfiles = list.mapNotNull { obj ->
                        (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }
                clusterD.await().onSuccess { list ->
                    clusterProfiles = list.mapNotNull { obj ->
                        (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }
                val models = mutableMapOf<String, List<String>>()
                ollamaD.await().onSuccess { if (it.isNotEmpty()) models["ollama"] = it }
                owuiD.await().onSuccess { if (it.isNotEmpty()) models["openwebui"] = it }
                availableModels = models
                pmD.await().onSuccess { permissionModeOptions = it }
            }
        }
    }

    // Clear model when backend changes to one with no known model list
    LaunchedEffect(backend) { if (modelsForBackend.isEmpty()) model = "" }

    val usingProfile = profile.isNotEmpty() && profile != "__dir__"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_prd_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_prd_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_prd_display_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                // ── Profile dropdown ──────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = if (profile == "__dir__") "— project directory —" else profile,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.new_prd_profile_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { profileMenuOpen = !profileMenuOpen }) { Text("▾") }
                        },
                    )
                    DropdownMenu(expanded = profileMenuOpen, onDismissRequest = { profileMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("— project directory (local checkout) —") },
                            onClick = { profile = "__dir__"; profileMenuOpen = false },
                        )
                        projectProfiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = { profile = p; profileMenuOpen = false },
                            )
                        }
                    }
                }

                // ── Decomposition profile (always shown) ──────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = decomp.ifEmpty { "— inherit —" },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.new_prd_decomp_profile_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { decompMenuOpen = !decompMenuOpen }) { Text("▾") }
                        },
                    )
                    DropdownMenu(expanded = decompMenuOpen, onDismissRequest = { decompMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("— inherit —") },
                            onClick = { decomp = ""; decompMenuOpen = false },
                        )
                        projectProfiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = { decomp = p; decompMenuOpen = false },
                            )
                        }
                    }
                }

                if (!usingProfile) {
                    // ── Dir mode: project directory ───────────────────────
                    OutlinedTextField(
                        value = projectDir,
                        onValueChange = { projectDir = it },
                        label = { Text(stringResource(R.string.new_prd_project_dir_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    // ── Backend dropdown (enabled only, shell excluded) ────
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = backend.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_backend_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { backendMenuOpen = !backendMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = backendMenuOpen, onDismissRequest = { backendMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(inheritLabel) },
                                onClick = { backend = ""; backendMenuOpen = false },
                            )
                            backendOptions.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b) },
                                    onClick = { backend = b; backendMenuOpen = false },
                                )
                            }
                        }
                    }

                    // ── Effort dropdown ───────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = effort.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_effort_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { effortMenuOpen = !effortMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = effortMenuOpen, onDismissRequest = { effortMenuOpen = false }) {
                            listOf("", "low", "medium", "high", "max", "quick", "normal", "thorough")
                                .forEach { e ->
                                    DropdownMenuItem(
                                        text = { Text(if (e.isEmpty()) inheritLabel else e) },
                                        onClick = { effort = e; effortMenuOpen = false },
                                    )
                                }
                        }
                    }

                    // ── Model dropdown: only for ollama / openwebui ───────
                    if (modelsForBackend.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = model.ifEmpty { backendDefaultLabel },
                                onValueChange = {},
                                label = { Text(stringResource(R.string.new_prd_model_label)) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { modelMenuOpen = !modelMenuOpen }) { Text("▾") }
                                },
                            )
                            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(backendDefaultLabel) },
                                    onClick = { model = ""; modelMenuOpen = false },
                                )
                                modelsForBackend.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = { model = m; modelMenuOpen = false },
                                    )
                                }
                                // Always show current value even if it's no longer in the list
                                if (model.isNotEmpty() && model !in modelsForBackend) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$model (not in list)",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = { modelMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    // ── Permission mode (v5.27.5+) — only when server provides the list
                    if (permissionModeOptions.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = permissionMode.ifEmpty { inheritLabel },
                                onValueChange = {},
                                label = { Text(stringResource(R.string.new_prd_permission_mode_label)) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { permissionModeMenuOpen = !permissionModeMenuOpen }) { Text("▾") }
                                },
                            )
                            DropdownMenu(expanded = permissionModeMenuOpen, onDismissRequest = { permissionModeMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(inheritLabel) },
                                    onClick = { permissionMode = ""; permissionModeMenuOpen = false },
                                )
                                permissionModeOptions.forEach { pm ->
                                    DropdownMenuItem(
                                        text = { Text(pm) },
                                        onClick = { permissionMode = pm; permissionModeMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Profile mode: cluster dropdown ────────────────────
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = cluster.ifEmpty { "— Local service instance —" },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_cluster_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { clusterMenuOpen = !clusterMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = clusterMenuOpen, onDismissRequest = { clusterMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("— Local service instance (daemon-side) —") },
                                onClick = { cluster = ""; clusterMenuOpen = false },
                            )
                            clusterProfiles.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = { cluster = c; clusterMenuOpen = false },
                                )
                            }
                        }
                    }
                }
                // ── Type picker ────────────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = prdType.ifEmpty { "— none —" }, onValueChange = {},
                        label = { Text(stringResource(R.string.new_prd_type_label)) },
                        readOnly = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { prdTypeMenuOpen = !prdTypeMenuOpen }) { Text("▾") } },
                    )
                    DropdownMenu(expanded = prdTypeMenuOpen, onDismissRequest = { prdTypeMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("— none —") }, onClick = { prdType = ""; prdTypeMenuOpen = false })
                        listOf("software", "research", "operational", "personal").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { prdType = t; prdTypeMenuOpen = false })
                        }
                    }
                }

                // ── Guided Mode toggle ─────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.new_prd_guided_mode_label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.Switch(checked = guidedMode, onCheckedChange = { guidedMode = it })
                }

                // ── Skills ─────────────────────────────────────────────────
                OutlinedTextField(
                    value = skills, onValueChange = { skills = it },
                    label = { Text(stringResource(R.string.new_prd_skills_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedSkills = skills.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val req =
                        if (!usingProfile) {
                            NewPrdRequestDto(
                                name = name.trim(),
                                title = title.trim().ifBlank { null },
                                projectDir = projectDir.trim().ifBlank { null },
                                backend = backend.ifBlank { null },
                                effort = effort.ifBlank { null },
                                model = model.ifBlank { null },
                                decompositionProfile = decomp.ifBlank { null },
                                permissionMode = permissionMode.ifBlank { null },
                                type = prdType.ifBlank { null },
                                guidedMode = if (guidedMode) true else null,
                                skills = parsedSkills.ifEmpty { null },
                            )
                        } else {
                            NewPrdRequestDto(
                                name = name.trim(),
                                title = title.trim().ifBlank { null },
                                projectProfile = profile,
                                clusterProfile = cluster.ifBlank { null },
                                decompositionProfile = decomp.ifBlank { null },
                                permissionMode = permissionMode.ifBlank { null },
                                type = prdType.ifBlank { null },
                                guidedMode = if (guidedMode) true else null,
                                skills = parsedSkills.ifEmpty { null },
                            )
                        }
                    if (req.name.isNotBlank()) onCreate(req)
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
