package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
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

    var task by remember { mutableStateOf("") }
    var sessionName by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var workingDir by remember { mutableStateOf("") }
    var filePickerOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }
    var resumeId by remember { mutableStateOf<String?>(null) }
    var autoGitInit by remember { mutableStateOf(false) }
    var autoGitCommit by remember { mutableStateOf(true) }

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

    // Available LLM backends on the selected server (from /api/backends).
    // Refreshed whenever the user picks a different server. The picker
    // also remembers the server's currently-active backend so we can
    // skip the setActiveBackend call when the user keeps the default.
    var backends by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeBackend by remember { mutableStateOf<String?>(null) }
    var pickedBackend by remember { mutableStateOf<String?>(null) }
    var backendsBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(selectedProfileId) {
        backends = emptyList()
        activeBackend = null
        pickedBackend = null
        backendsBlocked = false
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        transport.listBackends().fold(
            onSuccess = { v ->
                activeBackend = v.active
                pickedBackend = v.active
                // Filter to backends actually enabled on the selected
                // server. `/api/backends` returns every adapter the
                // daemon was built with, which made the New Session
                // picker list ghost backends (e.g. openai shown even
                // when no api_key is configured). Cross-reference the
                // config's per-backend enabled flag. A missing flag
                // means "user never configured" → hide.
                val enabledSet =
                    transport.fetchConfig().getOrNull()?.let { cfg ->
                        val root = kotlinx.serialization.json.JsonObject(cfg.raw.toMap())
                        v.llm.filter { name -> backendEnabled(root, name) }
                            .toSet()
                    } ?: v.llm.toSet()
                // Keep server-reported active even if the enabled flag
                // wasn't set — otherwise the picker might drop the
                // default and leave nothing selected.
                backends =
                    (enabledSet + listOfNotNull(v.active))
                        .distinct()
                        .filter { it.isNotBlank() }
            },
            onFailure = {
                // Server doesn't expose /api/backends — picker is hidden.
                backendsBlocked = true
            },
        )
    }

    // Model variants for ollama / openwebui backends. Only populated when
    // the picked backend is one of those two; other backends don't
    // enumerate a model list on the parent today.
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickedModel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedProfileId, pickedBackend) {
        models = emptyList()
        pickedModel = null
        val backend = pickedBackend?.lowercase() ?: return@LaunchedEffect
        if (backend != "ollama" && backend != "openwebui") return@LaunchedEffect
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).listModels(backend).onSuccess { list ->
            models = list
            pickedModel = list.firstOrNull()
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
    LaunchedEffect(selectedProfileId) {
        claudeModels = emptyList()
        claudeEfforts = emptyList()
        claudePermissionModes = emptyList()
        claudeOptionsAvailable = false
        pickedPermissionMode = ""
        pickedClaudeModel = ""
        pickedClaudeEffort = ""
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        transport.listClaudePermissionModes().onSuccess { modes ->
            claudePermissionModes = modes
            claudeOptionsAvailable = true
        }
        transport.listClaudeModels().onSuccess { claudeModels = it }
        transport.listClaudeEfforts().onSuccess { claudeEfforts = it }
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.new_session_task_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                SavedCommandLibraryDropdown(
                    onPick = { task = it },
                )
            }
            OutlinedTextField(
                value = task,
                onValueChange = { task = it },
                placeholder = { Text(stringResource(R.string.new_session_task_hint)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                maxLines = 8,
            )

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
                // Compute Node sub-picker — only when an LLM is selected and it has nodes
                if (pickedLlm != null) {
                    val allNodes = buildList {
                        add(pickedLlm!!.computeNode)
                        addAll(pickedLlm!!.computeNodes.filter { it != pickedLlm!!.computeNode })
                    }.distinct()
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
                }
            }

            // Backend picker — populated from /api/backends. Only renders
            // when the server actually exposes the endpoint AND the user
            // hasn't selected a v7 LLM (which supersedes the legacy path).
            if (!backendsBlocked && backends.isNotEmpty() && pickedLlm == null) {
                Text(
                    stringResource(R.string.session_llm_legacy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
                Text(
                    stringResource(R.string.new_session_backend_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                BackendPickerDropdown(
                    backends = backends,
                    selected = pickedBackend,
                    active = activeBackend,
                    onSelect = { pickedBackend = it },
                )
            } else if (!backendsBlocked && backends.isNotEmpty() && pickedLlm != null) {
                // v7 selected — show legacy section as grayed-out notice only
                Text(
                    stringResource(R.string.session_llm_legacy_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value =
                            pickedClusterProfile.ifEmpty {
                                "— Local service instance (daemon-side) —"
                            },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(
                                onClick = { clusterMenuOpen = !clusterMenuOpen },
                            ) { Text("▾") }
                        },
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

            // Model picker — only visible for ollama / openwebui. The
            // parent's /api/sessions/start doesn't accept a `model` field
            // (PWA sends `backend` + `profile` only); model selection is
            // server-side backend config. Shown here as informational —
            // the user can see what's installed before kicking off. A
            // future patch could PUT /api/profiles to change the server's
            // configured model for the chosen backend before /start.
            if (models.isNotEmpty()) {
                Text(
                    "Model (server-configured)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                ModelPickerDropdown(
                    models = models,
                    selected = pickedModel,
                    onSelect = { pickedModel = it },
                )
                Text(
                    "Models installed on the selected backend. Changing this " +
                        "on mobile requires a backend config update (v0.14).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Advanced claude-code options (v5.27.5+) — only when backend
            // is claude-code AND the server exposes /api/llm/claude/*.
            val isClaudeCode = pickedBackend?.lowercase()?.contains("claude") == true ||
                (pickedBackend == null && activeBackend?.lowercase()?.contains("claude") == true)
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

            val errorEmptyTask = stringResource(R.string.new_session_error_empty_task)
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
                            if (task.isBlank()) {
                                banner = errorEmptyTask
                                return@Button
                            }
                            submitting = true
                            banner = null
                            scope.launch {
                                val transport = ServiceLocator.transportFor(profile)
                                // If user picked a non-active backend, switch
                                // it server-wide first. Parent has no per-
                                // session backend param on /api/sessions/start,
                                // so this is the closest mobile can get today.
                                val backendToUse = pickedBackend
                                if (backendToUse != null && backendToUse != activeBackend) {
                                    transport.setActiveBackend(backendToUse).onFailure { err ->
                                        banner =
                                            "Couldn't switch backend to $backendToUse — " +
                                            "${err.message ?: err::class.simpleName}. " +
                                            "Starting with server's current backend."
                                    }
                                }
                                // v0.39.1 (#20) — branch the spawn
                                // path. Project profile selected →
                                // POST /api/agents with the F10 body.
                                // No profile (project-directory mode)
                                // → keep the historic POST
                                // /api/sessions/start with workingDir
                                // + backend.
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
                                            // v7 LLM path takes precedence over legacy backend
                                            backend = if (pickedLlm == null) pickedBackend else null,
                                            resumeId = resumeId,
                                            autoGitInit = autoGitInit,
                                            autoGitCommit = autoGitCommit,
                                            permissionMode = pickedPermissionMode.ifBlank { null },
                                            model = pickedClaudeModel.ifBlank { null },
                                            claudeEffort = pickedClaudeEffort.ifBlank { null },
                                            llm = pickedLlm?.name,
                                            computeNode = pickedComputeNode,
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
                        enabled = !submitting && task.isNotBlank() && selectedProfileId != null,
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
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                )
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
private fun ModelPickerDropdown(
    models: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected ?: models.first(),
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
            models.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
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
private fun BackendPickerDropdown(
    backends: List<String>,
    selected: String?,
    active: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = selected ?: active ?: backends.first()
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
            backends.forEach { name ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(name, modifier = Modifier.weight(1f))
                            if (name == active) {
                                Text(
                                    "active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
private fun backendEnabled(
    root: kotlinx.serialization.json.JsonObject,
    name: String,
): Boolean {
    // The server stores each backend's config under a top-level
    // section (ollama.*, openwebui.*, session.* for claude-code,
    // shell_backend.* for shell, opencode_acp.*, etc.). Use the
    // canonical enabled-path resolver so we stay in lockstep with
    // the LLM card's toggle writes.
    val key = com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas.enabledKey(name)
    // Try the flat dot-path form first (matches saved-as-patch shape).
    root[key]?.let { v ->
        return (v as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toBooleanStrictOrNull() == true
    }
    // Fall back to nested { section: { enabled: bool } } which is
    // how /api/config returns the server's in-memory tree.
    val dotIdx = key.indexOf('.')
    if (dotIdx <= 0) return false
    val sec = key.substring(0, dotIdx)
    val leaf = key.substring(dotIdx + 1)
    val section = root[sec] as? kotlinx.serialization.json.JsonObject ?: return false
    val v = section[leaf] as? kotlinx.serialization.json.JsonPrimitive ?: return false
    return v.content.toBooleanStrictOrNull() == true
}
