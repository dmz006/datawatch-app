package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.common.VoiceRecordingDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Start-session form — v0.11 parity with the PWA's "New session" tab.
 * Re-uses `TransportClient.startSession`. LLM backend picker is read-only in
 * this phase (the active backend governs; per-session override lands in v0.11
 * phase 2.7's active-backend picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun NewSessionScreen(
    onStarted: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var task by remember { mutableStateOf("") }
    var taskExpanded by remember { mutableStateOf(false) }
    var sessionName by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var workingDir by remember { mutableStateOf("") }
    var filePickerOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var resumeId by remember { mutableStateOf<String?>(null) }
    var autoGitInit by remember { mutableStateOf(false) }
    var autoGitCommit by remember { mutableStateOf(true) }
    var whisperConfigured by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<com.dmzs.datawatchclient.voice.VoiceRecorder?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var transcribingVoice by remember { mutableStateOf(false) }
    val micPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                runCatching { r.start() }
                    .onSuccess { voiceRecorder = r; showVoiceDialog = true }
                    .onFailure { e ->
                        android.widget.Toast.makeText(
                            context,
                            "Recording failed: ${e.message ?: e::class.simpleName}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
            }
        }

    // Recent done sessions for the Resume dropdown — matches PWA's
    // populateResumeDropdown: 30 most-recent completed/failed/killed.
    var recentDone by remember {
        mutableStateOf<List<com.dmzs.datawatchclient.domain.Session>>(emptyList())
    }
    LaunchedEffect(selectedProfileId) {
        recentDone = emptyList()
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listSessions().onSuccess { list ->
            recentDone =
                list.filter {
                    it.state == com.dmzs.datawatchclient.domain.SessionState.Completed ||
                        it.state == com.dmzs.datawatchclient.domain.SessionState.Killed ||
                        it.state == com.dmzs.datawatchclient.domain.SessionState.Error
                }.sortedByDescending { it.lastActivityAt }.take(30)
        }
    }


    // Claude-code advanced options — permission mode + model + effort.
    // Fetched from /api/llm/claude/{models,efforts,permission_modes} (v5.27.5+).
    // 404 = older daemon → hide the block entirely.
    // v7 LLM registry picker — populated from /api/llms (filtered to enabled=true).
    // Selecting an LLM cascades to its compute_nodes list for the compute node picker.
    var llmEntries by remember {
        mutableStateOf<List<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>>(emptyList())
    }
    var pickedLlm by remember {
        mutableStateOf<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto?>(null)
    }
    var pickedComputeNode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedProfileId) {
        llmEntries = emptyList()
        pickedLlm = null
        pickedComputeNode = null
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listLlms().onSuccess { list ->
            llmEntries = list.filter { it.enabled }
        }
    }
    // When LLM selection changes, reset compute node picker to first option
    LaunchedEffect(pickedLlm) {
        pickedComputeNode = null
    }

    var claudeModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var claudeEfforts by remember { mutableStateOf<List<String>>(emptyList()) }
    var claudePermissionModes by remember { mutableStateOf<List<String>>(emptyList()) }
    var claudeOptionsAvailable by remember { mutableStateOf(false) }
    var pickedPermissionMode by remember { mutableStateOf("") }
    var pickedClaudeModel by remember { mutableStateOf("") }
    var pickedClaudeEffort by remember { mutableStateOf("") }
    var nonClaudeModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedNonClaudeModel by remember { mutableStateOf("") }
    var pickedNonClaudeEffort by remember { mutableStateOf("") }
    // datawatch v8.8.3 — opt-in Chrome DevTools Protocol integration.
    var chromeIntegration by remember { mutableStateOf(false) }
    // OpenCode grouped model list — Map of providerLabel → model ids.
    var openCodeModelGroups by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    // Load claude options and whisperConfigured whenever the server profile changes.
    LaunchedEffect(selectedProfileId) {
        claudeModels = emptyList()
        claudeEfforts = emptyList()
        claudePermissionModes = emptyList()
        claudeOptionsAvailable = false
        pickedPermissionMode = ""
        pickedClaudeModel = ""
        pickedClaudeEffort = ""
        whisperConfigured = false
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        transport.listClaudePermissionModes().onSuccess { modes ->
            claudePermissionModes = modes
            claudeOptionsAvailable = true
        }
        transport.listClaudeModels().onSuccess { claudeModels = it }
        transport.listClaudeEfforts().onSuccess { claudeEfforts = it }
        transport.fetchConfig().onSuccess { cfg ->
            whisperConfigured =
                (cfg.raw["whisper"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("enabled")
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" }
                    ?: false
        }
    }
    // Reset picks and load models when LLM selection or server changes.
    LaunchedEffect(pickedLlm, selectedProfileId) {
        pickedPermissionMode = ""
        pickedClaudeModel = ""
        pickedClaudeEffort = ""
        nonClaudeModels = emptyList()
        openCodeModelGroups = emptyMap()
        pickedNonClaudeModel = ""
        pickedNonClaudeEffort = ""
        val llm = pickedLlm ?: return@LaunchedEffect
        if (llm.kind.lowercase().contains("claude")) return@LaunchedEffect
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        if (llm.kind.startsWith("opencode", ignoreCase = true)) {
            transport.fetchOpenCodeModels().onSuccess { resp ->
                val groups = resp.models
                    .groupBy { it.providerLabel.ifBlank { it.provider } }
                    .mapValues { (_, list) -> list.map { it.id } }
                openCodeModelGroups = groups
                // Pre-select the server-declared default if nothing chosen yet.
                if (pickedNonClaudeModel.isBlank() && resp.defaultModel.isNotBlank()) {
                    pickedNonClaudeModel = resp.defaultModel
                }
                nonClaudeModels = resp.models.map { it.id }
            }
            return@LaunchedEffect
        }
        // Registry-defined models take priority (other non-opencode backends store them here)
        val registryModels = llm.models.map { it.model }.filter { it.isNotBlank() }
        if (registryModels.isNotEmpty()) {
            nonClaudeModels = registryModels
            return@LaunchedEffect
        }
        // Dynamic fetch for ollama/openwebui
        transport.listModels(llm.kind).onSuccess { list ->
            if (list.isNotEmpty()) nonClaudeModels = list
        }
    }

    // Server-defined F10 profiles (agent profiles). Keyed by name; each
    // value is the profile body — we only need the backend field for
    // display. Profile picker is optional ("Default (no profile)" on top).
    var serverProfiles by remember {
        mutableStateOf<List<Pair<String, String>>>(emptyList())
    }
    var pickedServerProfile by remember { mutableStateOf<String?>(null) }
    // v0.39.1 (#20) — cluster sub-dropdown opens when a project
    // profile is selected. Empty string = "Local service instance"
    // (daemon-side clone path), matching PWA v5.26.34.
    var clusterProfiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedClusterProfile by remember { mutableStateOf("") }
    LaunchedEffect(selectedProfileId) {
        serverProfiles = emptyList()
        clusterProfiles = emptyList()
        pickedServerProfile = null
        pickedClusterProfile = ""
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listProfiles().onSuccess { map ->
            serverProfiles =
                map.entries
                    .sortedBy { it.key }
                    .map { (name, body) ->
                        val backend =
                            (
                                body["backend"]
                                    as? kotlinx.serialization.json.JsonPrimitive
                            )?.content ?: "?"
                        name to backend
                    }
        }
        // Load cluster profiles too — only shown once the user picks a
        // project profile (PWA hides until then).
        ServiceLocator.transportFor(profile).listKindProfiles("cluster").onSuccess { list ->
            clusterProfiles =
                list.mapNotNull { obj ->
                    (obj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
        }
    }

    // Keep the Server dropdown synced with ActiveServerStore so the LLM
    // backend picker loads the *selected* server's enabled list, not a
    // stale one. The prior guard `if (selectedProfileId == null)` only
    // seeded on first composition, so flipping the active server
    // elsewhere left the picker pinned to the original server's
    // backends (user report 2026-04-23). Track whether the user has
    // *manually* chosen a server on this screen; when they have, we
    // stop auto-syncing so we don't overwrite their explicit choice.
    var userPickedServer by remember { mutableStateOf(false) }
    LaunchedEffect(profiles, activeId) {
        if (userPickedServer) return@LaunchedEffect
        val enabled = profiles.filter { it.enabled }
        val target =
            when {
                activeId != null && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS ->
                    enabled.firstOrNull { it.id == activeId }?.id
                else -> null
            } ?: enabled.firstOrNull()?.id
        if (target != null && target != selectedProfileId) {
            selectedProfileId = target
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_session_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    DocsLinkAction("howto/new-session.md")
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
        ) {
            banner?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                stringResource(R.string.new_session_name_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                placeholder = { Text(stringResource(R.string.new_session_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Task field — collapsed by default; tap envelope to expand.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { taskExpanded = !taskExpanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Mail,
                        contentDescription = stringResource(R.string.new_session_task_label),
                        tint = if (taskExpanded || task.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.new_session_task_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (taskExpanded || task.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (taskExpanded) {
                    SavedCommandLibraryDropdown(onPick = { task = it })
                }
            }
            if (taskExpanded) {
                if (showVoiceDialog) {
                    VoiceRecordingDialog(
                        onCancel = {
                            voiceRecorder?.cancel()
                            voiceRecorder = null
                            showVoiceDialog = false
                        },
                        onSend = {
                            val r = voiceRecorder ?: run { showVoiceDialog = false; return@VoiceRecordingDialog }
                            voiceRecorder = null
                            showVoiceDialog = false
                            val captured = r.stop() ?: return@VoiceRecordingDialog
                            transcribingVoice = true
                            scope.launch {
                                val profile = profiles.firstOrNull { it.id == selectedProfileId }
                                if (profile != null) {
                                    ServiceLocator.transportFor(profile)
                                        .transcribeAudio(
                                            audio = captured.first,
                                            audioMime = captured.second,
                                            sessionId = null,
                                            autoExec = false,
                                        ).onSuccess { result ->
                                            task = (task + " " + result.transcript.trim()).trim()
                                        }.onFailure { err ->
                                            android.widget.Toast.makeText(
                                                context,
                                                "Transcribe failed: ${err.message}",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                }
                                transcribingVoice = false
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = if (transcribingVoice) "Transcribing…" else task,
                        onValueChange = { if (!transcribingVoice) task = it },
                        placeholder = { Text(stringResource(R.string.new_session_task_hint)) },
                        enabled = !transcribingVoice,
                        modifier = Modifier.weight(1f).heightIn(min = 100.dp),
                        maxLines = 8,
                    )
                    if (whisperConfigured) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                                    runCatching { r.start() }
                                        .onSuccess { voiceRecorder = r; showVoiceDialog = true }
                                        .onFailure { e ->
                                            android.widget.Toast.makeText(
                                                context, "Recording failed: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !transcribingVoice,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.new_session_server_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            ServerPickerDropdown(
                profiles = profiles.filter { it.enabled },
                selectedId = selectedProfileId,
                onSelect = { id ->
                    selectedProfileId = id
                    // Any explicit pick from the dropdown disables the
                    // auto-sync with ActiveServerStore — otherwise if the
                    // user chooses server B while active is A, the effect
                    // would snap back to A on recomposition.
                    userPickedServer = true
                },
            )

            // v0.83.0 — v7 LLM picker. Only shown when the server exposes
            // at least one enabled LLM in /api/llms. When an LLM is
            // selected, the legacy backend picker is hidden (v7 path).
            if (llmEntries.isNotEmpty()) {
                Text(
                    stringResource(R.string.session_llm_picker_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                Text(
                    stringResource(R.string.session_llm_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LlmPickerDropdown(
                    llms = llmEntries,
                    selected = pickedLlm,
                    noneLabel = stringResource(R.string.session_llm_none_option),
                    onSelect = { picked -> pickedLlm = picked },
                )
                // Compute Node sub-picker — non-claude LLMs only, and only when nodes exist.
                val isClaudeLlm = pickedLlm?.kind?.lowercase()?.contains("claude") == true
                if (pickedLlm != null && !isClaudeLlm) {
                    val allNodes = buildList {
                        add(pickedLlm!!.computeNode)
                        addAll(pickedLlm!!.computeNodes.filter { it != pickedLlm!!.computeNode })
                    }.distinct().filter { it.isNotBlank() }
                    if (allNodes.isNotEmpty()) {
                        Text(
                            stringResource(R.string.session_compute_node_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        val anyNodeLabel = stringResource(R.string.session_llm_any_node)
                        val primaryLabel = stringResource(R.string.session_compute_node_primary)
                        val failoverFmt = stringResource(R.string.session_compute_node_failover)
                        ComputeNodePickerDropdown(
                            nodes = allNodes,
                            selected = pickedComputeNode,
                            anyNodeLabel = anyNodeLabel,
                            primaryLabel = primaryLabel,
                            failoverFmt = failoverFmt,
                            onSelect = { pickedComputeNode = it },
                        )
                    }
                    // Model picker — grouped for opencode, flat for others
                    if (openCodeModelGroups.isNotEmpty()) {
                        GroupedModelDropdown(
                            label = stringResource(R.string.new_session_model_label),
                            groups = openCodeModelGroups,
                            selected = pickedNonClaudeModel,
                            noneLabel = stringResource(R.string.new_session_config_default),
                            onSelect = { pickedNonClaudeModel = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    } else if (nonClaudeModels.isNotEmpty()) {
                        SimpleDropdown(
                            label = stringResource(R.string.new_session_model_label),
                            options = nonClaudeModels,
                            selected = pickedNonClaudeModel,
                            noneLabel = stringResource(R.string.new_session_config_default),
                            onSelect = { pickedNonClaudeModel = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                    // Effort picker — static list, always shown for non-claude LLMs
                    SimpleDropdown(
                        label = stringResource(R.string.new_session_effort_label),
                        options = listOf("low", "medium", "high", "max", "quick", "normal", "thorough"),
                        selected = pickedNonClaudeEffort,
                        noneLabel = stringResource(R.string.new_session_config_default),
                        onSelect = { pickedNonClaudeEffort = it },
                        modifier = Modifier.fillMaxWidth().padding(top = if (nonClaudeModels.isNotEmpty()) 8.dp else 12.dp),
                    )
                }
            }


            // Profile picker — maps to /api/profiles. Optional; users
            // can start "Default (no profile)" and the parent picks its
            // own backend/profile.
            //
            // v0.39.1 (#20 / PWA v5.26.63 routing): when set, the
            // session goes through `POST /api/agents` with the
            // project_profile + cluster_profile body — the F10
            // image_pair carries the worker LLM. When unset, the
            // session falls back to `POST /api/sessions/start` with
            // working dir + backend (the historic mobile path).
            if (serverProfiles.isNotEmpty()) {
                Text(
                    stringResource(R.string.new_session_profile_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                ProfilePickerDropdown(
                    profiles = serverProfiles,
                    selected = pickedServerProfile,
                    onSelect = { pickedServerProfile = it },
                )
            }

            // Cluster sub-dropdown — only when a project profile is
            // selected. First option is the "local service instance"
            // sentinel (empty string), remaining are configured cluster
            // profiles. Mirrors PWA v5.26.34.
            if (pickedServerProfile != null) {
                Text(
                    stringResource(R.string.new_session_cluster_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                var clusterMenuOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = clusterMenuOpen,
                    onExpandedChange = { clusterMenuOpen = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value =
                            pickedClusterProfile.ifEmpty {
                                "— Local service instance (daemon-side) —"
                            },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clusterMenuOpen) },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = clusterMenuOpen,
                        onDismissRequest = { clusterMenuOpen = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text("— Local service instance (daemon-side) —")
                            },
                            onClick = {
                                pickedClusterProfile = ""
                                clusterMenuOpen = false
                            },
                        )
                        clusterProfiles.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    pickedClusterProfile = c
                                    clusterMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            // Advanced claude-code options — only when a claude LLM is explicitly selected.
            val isClaudeCode = pickedLlm?.kind?.lowercase()?.contains("claude") == true
            if (claudeOptionsAvailable && isClaudeCode) {
                Text(
                    stringResource(R.string.new_session_advanced_claude),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                if (claudePermissionModes.isNotEmpty()) {
                    SimpleDropdown(
                        label = stringResource(R.string.new_session_permission_mode_label),
                        options = claudePermissionModes,
                        selected = pickedPermissionMode,
                        noneLabel = stringResource(R.string.new_session_config_default),
                        onSelect = { pickedPermissionMode = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (claudeModels.isNotEmpty()) {
                    SimpleDropdown(
                        label = stringResource(R.string.new_session_model_label),
                        options = claudeModels,
                        selected = pickedClaudeModel,
                        noneLabel = stringResource(R.string.new_session_config_default),
                        onSelect = { pickedClaudeModel = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (claudeEfforts.isNotEmpty()) {
                    SimpleDropdown(
                        label = stringResource(R.string.new_session_effort_label),
                        options = claudeEfforts,
                        selected = pickedClaudeEffort,
                        noneLabel = stringResource(R.string.new_session_config_default),
                        onSelect = { pickedClaudeEffort = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Switch(
                        checked = chromeIntegration,
                        onCheckedChange = { chromeIntegration = it },
                    )
                    Text(
                        stringResource(R.string.session_chrome),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Text(
                stringResource(R.string.new_session_working_dir_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    placeholder = { Text(stringResource(R.string.new_session_working_dir_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { filePickerOpen = true },
                    enabled = selectedProfileId != null,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Browse…") }
            }

            // Resume previous session dropdown — PWA matches this with
            // populateResumeDropdown (last 30 done sessions). Selecting
            // sets `resumeId`, which the server uses to warm-restart
            // rather than start fresh.
            if (recentDone.isNotEmpty()) {
                Text(
                    stringResource(R.string.new_session_resume_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                ResumePickerDropdown(
                    sessions = recentDone,
                    selectedId = resumeId,
                    onSelect = { resumeId = it },
                )
            }

            Text(
                stringResource(R.string.new_session_git_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Switch(
                    checked = autoGitInit,
                    onCheckedChange = { autoGitInit = it },
                )
                Text(
                    stringResource(R.string.new_session_auto_git_init),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                )
                androidx.compose.material3.Switch(
                    checked = autoGitCommit,
                    onCheckedChange = { autoGitCommit = it },
                )
                Text(
                    stringResource(R.string.new_session_auto_git_commit),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // PWA `renderSessionBacklog` — up to 20 most-recent done
            // sessions with a Restart button per row. Lets users warm-
            // resume without digging through the Sessions tab history
            // toggle.
            if (recentDone.isNotEmpty()) {
                Text(
                    stringResource(R.string.new_session_recent_sessions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
                recentDone.take(20).forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val display = s.name ?: s.taskSummary ?: s.id
                            Text(
                                if (display.length > 60) display.take(60) + "…" else display,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${s.state.name.lowercase()} · ${s.backend ?: "?"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val profile =
                                    profiles.firstOrNull { it.id == selectedProfileId }
                                        ?: return@OutlinedButton
                                scope.launch {
                                    ServiceLocator.transportFor(profile)
                                        .restartSession(s.id)
                                        .onSuccess { onStarted(s.id) }
                                }
                            },
                        ) { Text("Restart", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) }
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    Button(
                        onClick = {
                            val profile =
                                profiles.firstOrNull { it.id == selectedProfileId }
                                    ?: return@Button
                            submitting = true
                            banner = null
                            scope.launch {
                                val transport = ServiceLocator.transportFor(profile)
                                val pickedProfile = pickedServerProfile
                                val outcome =
                                    if (pickedProfile != null) {
                                        transport.startAgent(
                                            com.dmzs.datawatchclient.transport.dto.StartAgentRequestDto(
                                                task = task.trim(),
                                                projectProfile = pickedProfile,
                                                clusterProfile =
                                                    pickedClusterProfile.takeIf { it.isNotBlank() },
                                                name = sessionName.trim().ifBlank { null },
                                            ),
                                        )
                                    } else {
                                        transport.startSession(
                                            task = task.trim(),
                                            workingDir = workingDir.trim().ifBlank { null },
                                            profileName = null,
                                            name = sessionName.trim().ifBlank { null },
                                            backend = null,
                                            resumeId = resumeId,
                                            autoGitInit = autoGitInit,
                                            autoGitCommit = autoGitCommit,
                                            permissionMode = pickedPermissionMode.ifBlank { null },
                                            model = (if (isClaudeCode) pickedClaudeModel else pickedNonClaudeModel).ifBlank { null },
                                            claudeEffort = (if (isClaudeCode) pickedClaudeEffort else pickedNonClaudeEffort).ifBlank { null },
                                            llm = pickedLlm?.name,
                                            computeNode = pickedComputeNode,
                                            chrome = chromeIntegration.takeIf { it },
                                        )
                                    }
                                outcome.fold(
                                    onSuccess = { sessionId ->
                                        submitting = false
                                        onStarted(sessionId)
                                    },
                                    onFailure = { err ->
                                        submitting = false
                                        banner = "Start failed — " +
                                            (err.message ?: err::class.simpleName)
                                    },
                                )
                            }
                        },
                        enabled = !submitting && selectedProfileId != null,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        } else {
                            Text(stringResource(R.string.action_start))
                        }
                    }
                }
            }
        }
    }

    if (filePickerOpen) {
        com.dmzs.datawatchclient.ui.files.FilePickerDialog(
            pickerMode = com.dmzs.datawatchclient.ui.files.PickerMode.FolderOnly,
            onPicked = { picked ->
                filePickerOpen = false
                if (picked != null) workingDir = picked
            },
        )
    }
}

/**
 * "From library" dropdown that pulls saved commands from /api/commands
 * on the active server and inlines the picked command's body into the
 * caller's task text field. Silently hides itself when there are no
 * saved commands (first-time-use state) so it doesn't clutter the form.
 */
@Composable
private fun SavedCommandLibraryDropdown(onPick: (String) -> Unit) {
    val vm: com.dmzs.datawatchclient.ui.commands.SavedCommandsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val state by vm.state.collectAsState()
    if (state.commands.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.new_session_from_library), style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.commands.forEach { cmd ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column {
                            Text(cmd.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                cmd.command,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    },
                    onClick = {
                        onPick(cmd.command)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    options: List<String>,
    selected: String,
    noneLabel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.ifEmpty { noneLabel },
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = { onSelect(""); expanded = false },
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

/** Model picker that renders provider group labels as non-selectable headers. */
@Composable
private fun GroupedModelDropdown(
    label: String,
    groups: Map<String, List<String>>,
    selected: String,
    noneLabel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = selected.ifEmpty { noneLabel },
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                androidx.compose.material3.TextButton(
                    onClick = { expanded = !expanded },
                ) { Text("▾") }
            },
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = { onSelect(""); expanded = false },
            )
            groups.forEach { (groupLabel, models) ->
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
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "  $model",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = { onSelect(model); expanded = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumePickerDropdown(
    sessions: List<com.dmzs.datawatchclient.domain.Session>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val startFreshLabel = stringResource(R.string.new_session_start_fresh)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value =
                when {
                    selectedId == null -> startFreshLabel
                    selected == null -> selectedId
                    else ->
                        (selected.name ?: selected.taskSummary?.take(60) ?: selected.id)
                },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(startFreshLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            sessions.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                s.name ?: s.id,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                s.taskSummary?.take(60) ?: "(no task)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(s.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerDropdown(
    profiles: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val noProfileLabel = stringResource(R.string.new_session_no_profile)
    val display =
        selected ?: noProfileLabel
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noProfileLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            profiles.forEach { (name, backend) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name)
                            Text(
                                backend,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerPickerDropdown(
    profiles: List<ServerProfile>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedId }
    if (profiles.isEmpty()) {
        Text(
            stringResource(R.string.new_session_error_no_servers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: stringResource(R.string.new_session_pick_server),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            profiles.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.displayName)
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(p.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * v0.83.0 — v7 LLM registry picker. Lists enabled LLMs from /api/llms.
 * Label format: "<name> (<kind>)".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmPickerDropdown(
    llms: List<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>,
    selected: com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto?,
    noneLabel: String,
    onSelect: (com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selected?.let { "${it.name} (${it.kind})" } ?: noneLabel
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(noneLabel) },
                onClick = { onSelect(null); expanded = false },
            )
            llms.forEach { llm ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("${llm.name} (${llm.kind})", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                llm.computeNode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onSelect(llm); expanded = false },
                )
            }
        }
    }
}

/**
 * v0.83.0 — Compute Node picker. Cascades from the selected LLM's node list.
 * First node = primary, subsequent = failover N.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComputeNodePickerDropdown(
    nodes: List<String>,
    selected: String?,
    anyNodeLabel: String,
    primaryLabel: String,
    failoverFmt: String,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun nodeLabel(index: Int, name: String): String = when (index) {
        0 -> "$name $primaryLabel"
        else -> "$name ${String.format(failoverFmt, index)}"
    }
    val displayValue = selected?.let { sel ->
        val idx = nodes.indexOf(sel)
        if (idx >= 0) nodeLabel(idx, sel) else sel
    } ?: anyNodeLabel
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(anyNodeLabel) },
                onClick = { onSelect(null); expanded = false },
            )
            nodes.forEachIndexed { idx, name ->
                DropdownMenuItem(
                    text = { Text(nodeLabel(idx, name)) },
                    onClick = { onSelect(name); expanded = false },
                )
            }
        }
    }
}

/**
 * Inspect `/api/config` for `backends.<name>.enabled`. Tolerates
 * both the flat dot-path storage the parent daemon uses and the
 * legacy nested `{backends: {name: {enabled: true}}}` shape so
 * older servers still filter correctly.
 */
