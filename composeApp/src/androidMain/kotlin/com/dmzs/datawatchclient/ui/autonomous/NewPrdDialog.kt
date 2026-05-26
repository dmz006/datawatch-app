package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto
import com.dmzs.datawatchclient.ui.files.FilePickerDialog
import com.dmzs.datawatchclient.ui.files.PickerMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * New PRD / Automaton wizard — mirrors PWA openLaunchAutomatonWizard.
 *
 * Wizard order:
 *   1. Template strip (Browse)
 *   2. Intent/spec — large text, auto-detects type
 *   3. Title (optional)
 *   4. Workspace (profile) dropdown
 *   5. Directory picker (only in __dir__ mode)
 *   6. Backend, Model, Effort dropdowns (only in __dir__ mode)
 *   7. Advanced section (collapsible): guided mode, security scan,
 *      rules check, per-story approval, settings link
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPrdDialog(
    onDismiss: () -> Unit,
    onCreate: (NewPrdRequestDto) -> Unit,
    onBrowseTemplates: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    // ── Wizard state ───────────────────────────────────────────────────────
    var spec by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    /** Auto-inferred PRD type from spec text. */
    var prdType by remember { mutableStateOf("software") }
    var prdTypeMenuOpen by remember { mutableStateOf(false) }

    /** "__dir__" sentinel = project-directory mode; else = profile name. */
    var profile by remember { mutableStateOf("__dir__") }
    var profileMenuOpen by remember { mutableStateOf(false) }

    var selectedDir by remember { mutableStateOf("") }
    var dirPickerOpen by remember { mutableStateOf(false) }

    var backend by remember { mutableStateOf("") }
    var backendMenuOpen by remember { mutableStateOf(false) }
    var effort by remember { mutableStateOf("") }
    var effortMenuOpen by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var modelMenuOpen by remember { mutableStateOf(false) }

    // Advanced section
    var advancedOpen by remember { mutableStateOf(false) }
    var guidedMode by remember { mutableStateOf(false) }
    var scanEnabled by remember { mutableStateOf(true) }
    var rulesEnabled by remember { mutableStateOf(true) }
    var storyApproval by remember { mutableStateOf(false) }

    // Remote data
    var projectProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var backendOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    // OpenCode grouped model list for the PRD model picker (providerLabel → model ids).
    var openCodeModelGroups by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var openCodeDefaultModel by remember { mutableStateOf("") }

    val inheritLabel = stringResource(R.string.new_prd_inherit)
    val backendDefaultLabel = stringResource(R.string.new_prd_backend_default)

    /** Model names for the currently-selected backend (empty = hide field). */
    val modelsForBackend: List<String> = when {
        backend.startsWith("opencode") -> availableModels["opencode"].orEmpty()
        else -> availableModels[backend].orEmpty()
    }

    val usingProfile = profile.isNotEmpty() && profile != "__dir__"

    // Auto-infer type from spec
    LaunchedEffect(spec) {
        val lower = spec.lowercase()
        prdType = when {
            lower.containsAny("code", "test", "refactor", "build", "fix", "implement") -> "software"
            lower.containsAny("research", "analyze", "study") -> "research"
            lower.containsAny("deploy", "restart", "migrate", "monitor") -> "operational"
            spec.isBlank() -> "software"
            else -> "personal"
        }
    }

    // Load remote data
    LaunchedEffect(Unit) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            val sp = ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.firstOrNull { it.enabled }
                    else list.firstOrNull { it.id == activeId && it.enabled }
                        ?: list.firstOrNull { it.enabled }
                } ?: return@runCatching
            val transport = ServiceLocator.transportFor(sp)

            coroutineScope {
                val backendsD = async { transport.listBackends() }
                val projectD = async { transport.listKindProfiles("project") }
                val ollamaD = async { transport.listOllamaModels() }
                val owuiD = async { transport.listOpenWebUiModels() }
                val llmsD = async { transport.listLlms() }

                backendsD.await().onSuccess { view -> backendOptions = view.llm }
                projectD.await().onSuccess { list ->
                    projectProfiles = list.mapNotNull { obj ->
                        (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }
                val models = mutableMapOf<String, List<String>>()
                ollamaD.await().onSuccess { if (it.isNotEmpty()) models["ollama"] = it }
                owuiD.await().onSuccess { if (it.isNotEmpty()) models["openwebui"] = it }
                // Fetch opencode models from the live API for grouped display + default pre-select.
                val openCodeResp = transport.fetchOpenCodeModels()
                openCodeResp.onSuccess { resp ->
                    val groups = resp.models
                        .groupBy { it.providerLabel.ifBlank { it.provider } }
                        .mapValues { (_, list) -> list.map { it.id } }
                    openCodeModelGroups = groups
                    openCodeDefaultModel = resp.defaultModel
                    val allIds = resp.models.map { it.id }.filter { it.isNotBlank() }
                    if (allIds.isNotEmpty()) models["opencode"] = allIds
                }.onFailure {
                    // Fall back to LLM-registry models if /api/opencode/models is unavailable.
                    llmsD.await().onSuccess { llmList ->
                        val opencodeMods = llmList
                            .filter { it.enabled && it.kind.startsWith("opencode") }
                            .flatMap { entry -> entry.models.map { p -> p.model } + listOf(entry.model) }
                            .filter { it.isNotBlank() }
                            .distinct()
                        if (opencodeMods.isNotEmpty()) models["opencode"] = opencodeMods
                    }
                }
                availableModels = models
            }
        }
    }

    // Clear model when backend changes to one with no known model list
    LaunchedEffect(backend) { if (modelsForBackend.isEmpty()) model = "" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Launch Automaton") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Template strip ───────────────────────────────────────────
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Start from template",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onBrowseTemplates(); onDismiss() }) {
                            Text("Browse")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Intent / Spec ────────────────────────────────────────────
                OutlinedTextField(
                    value = spec,
                    onValueChange = { spec = it },
                    label = { Text("What do you want to accomplish?") },
                    placeholder = { Text("Add a CACHE column to /api/stats… or describe your goal", style = MaterialTheme.typography.bodySmall) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Type detection row ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Detected: $prdType",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { prdTypeMenuOpen = !prdTypeMenuOpen }) {
                            Text("▾", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(expanded = prdTypeMenuOpen, onDismissRequest = { prdTypeMenuOpen = false }) {
                            listOf("software", "research", "operational", "personal").forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t) },
                                    onClick = { prdType = t; prdTypeMenuOpen = false },
                                )
                            }
                        }
                    }
                }

                // ── Title (optional) ─────────────────────────────────────────
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_prd_display_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                // ── Workspace (Profile) dropdown ─────────────────────────────
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

                if (!usingProfile) {
                    // ── Directory picker ─────────────────────────────────────
                    val dirDisplayText = selectedDir.ifBlank { "~/" }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clickable { dirPickerOpen = true },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                stringResource(R.string.new_prd_project_dir_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                dirDisplayText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // ── Backend dropdown ─────────────────────────────────────
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

                    // ── Effort dropdown ──────────────────────────────────────
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

                    // ── Model dropdown: only for backends with model lists ────
                    if (modelsForBackend.isNotEmpty()) {
                        val isOpenCode = backend.startsWith("opencode", ignoreCase = true)
                        val showGroups = isOpenCode && openCodeModelGroups.isNotEmpty()
                        // Pre-select default when the opencode backend is chosen and no model set.
                        LaunchedEffect(isOpenCode, openCodeDefaultModel) {
                            if (isOpenCode && model.isBlank() && openCodeDefaultModel.isNotBlank()) {
                                model = openCodeDefaultModel
                            }
                        }
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
                                if (showGroups) {
                                    openCodeModelGroups.forEach { (groupLabel, models) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    groupLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            },
                                            onClick = {},
                                            enabled = false,
                                        )
                                        models.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text("  $m") },
                                                onClick = { model = m; modelMenuOpen = false },
                                            )
                                        }
                                    }
                                } else {
                                    modelsForBackend.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = { model = m; modelMenuOpen = false },
                                        )
                                    }
                                }
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
                }

                // ── Advanced section ─────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advancedOpen = !advancedOpen }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Advanced ${if (advancedOpen) "▴" else "▾"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (advancedOpen) {
                    // Guided Mode
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.new_prd_guided_mode_label),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(checked = guidedMode, onCheckedChange = { guidedMode = it })
                    }
                    // Security Scan
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Security scan",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(checked = scanEnabled, onCheckedChange = { scanEnabled = it })
                    }
                    // Rules Check
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Rules check",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(checked = rulesEnabled, onCheckedChange = { rulesEnabled = it })
                    }
                    // Per-story approval
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Per-story approval",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Switch(checked = storyApproval, onCheckedChange = { storyApproval = it })
                    }
                    // Settings link
                    TextButton(
                        onClick = { onOpenSettings(); onDismiss() },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            "Configure skills in Settings →",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val req = if (!usingProfile) {
                        NewPrdRequestDto(
                            name = "",
                            title = title.trim().ifBlank { null },
                            spec = spec.trim().ifBlank { null },
                            projectDir = selectedDir.ifBlank { null },
                            backend = backend.ifBlank { null },
                            effort = effort.ifBlank { null },
                            model = model.ifBlank { null },
                            type = prdType.ifBlank { null },
                            guidedMode = if (guidedMode) true else null,
                        )
                    } else {
                        NewPrdRequestDto(
                            name = "",
                            title = title.trim().ifBlank { null },
                            spec = spec.trim().ifBlank { null },
                            projectProfile = profile,
                            clusterProfile = null,
                            type = prdType.ifBlank { null },
                            guidedMode = if (guidedMode) true else null,
                        )
                    }
                    onCreate(req)
                },
                enabled = spec.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    // ── Directory file picker ────────────────────────────────────────────
    if (dirPickerOpen) {
        FilePickerDialog(
            pickerMode = PickerMode.FolderOnly,
            onPicked = { path ->
                if (path != null) selectedDir = path
                dirPickerOpen = false
            },
        )
    }
}

private fun String.containsAny(vararg keywords: String): Boolean =
    keywords.any { this.contains(it, ignoreCase = true) }
