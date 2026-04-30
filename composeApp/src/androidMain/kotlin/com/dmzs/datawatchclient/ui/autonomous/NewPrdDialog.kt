package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
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

    var projectProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var clusterProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var backendOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var permissionModeOptions by remember { mutableStateOf<List<String>>(emptyList()) }

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
        title = { Text("New PRD") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                // ── Profile dropdown ──────────────────────────────────────
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = if (profile == "__dir__") "— project directory —" else profile,
                        onValueChange = {},
                        label = { Text("Profile") },
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
                        label = { Text("Decomposition profile") },
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
                        label = { Text("Project directory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    // ── Backend dropdown (enabled only, shell excluded) ────
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = backend.ifEmpty { "(inherit)" },
                            onValueChange = {},
                            label = { Text("Backend") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { backendMenuOpen = !backendMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = backendMenuOpen, onDismissRequest = { backendMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("(inherit)") },
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
                            value = effort.ifEmpty { "(inherit)" },
                            onValueChange = {},
                            label = { Text("Effort") },
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
                                        text = { Text(if (e.isEmpty()) "(inherit)" else e) },
                                        onClick = { effort = e; effortMenuOpen = false },
                                    )
                                }
                        }
                    }

                    // ── Model dropdown: only for ollama / openwebui ───────
                    if (modelsForBackend.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = model.ifEmpty { "(backend default)" },
                                onValueChange = {},
                                label = { Text("Model") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { modelMenuOpen = !modelMenuOpen }) { Text("▾") }
                                },
                            )
                            DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("(backend default)") },
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
                                value = permissionMode.ifEmpty { "(inherit)" },
                                onValueChange = {},
                                label = { Text("Permission mode") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { permissionModeMenuOpen = !permissionModeMenuOpen }) { Text("▾") }
                                },
                            )
                            DropdownMenu(expanded = permissionModeMenuOpen, onDismissRequest = { permissionModeMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("(inherit)") },
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
                            label = { Text("Cluster") },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                            )
                        } else {
                            NewPrdRequestDto(
                                name = name.trim(),
                                title = title.trim().ifBlank { null },
                                projectProfile = profile,
                                clusterProfile = cluster.ifBlank { null },
                                decompositionProfile = decomp.ifBlank { null },
                                permissionMode = permissionMode.ifBlank { null },
                            )
                        }
                    if (req.name.isNotBlank()) onCreate(req)
                },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
