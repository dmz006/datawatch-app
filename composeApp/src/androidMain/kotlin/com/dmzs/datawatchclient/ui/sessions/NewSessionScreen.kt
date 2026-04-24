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
import androidx.compose.ui.unit.dp
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

    // Server-defined F10 profiles (agent profiles). Keyed by name; each
    // value is the profile body — we only need the backend field for
    // display. Profile picker is optional ("Default (no profile)" on top).
    var serverProfiles by remember {
        mutableStateOf<List<Pair<String, String>>>(emptyList())
    }
    var pickedServerProfile by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedProfileId) {
        serverProfiles = emptyList()
        pickedServerProfile = null
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
    }

    // Default-select active profile (or the first enabled one) on first composition.
    LaunchedEffect(profiles, activeId) {
        if (selectedProfileId == null) {
            val enabled = profiles.filter { it.enabled }
            selectedProfileId = when {
                activeId != null && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS ->
                    enabled.firstOrNull { it.id == activeId }?.id
                else -> null
            } ?: enabled.firstOrNull()?.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New session") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(16.dp),
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
                "Session name",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                placeholder = { Text("e.g. Auth refactor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Task",
                    style = MaterialTheme.typography.labelLarge,
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
                placeholder = { Text("e.g. refactor payments module to use new auth") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                maxLines = 8,
            )

            Text(
                "Server",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            ServerPickerDropdown(
                profiles = profiles.filter { it.enabled },
                selectedId = selectedProfileId,
                onSelect = { selectedProfileId = it },
            )

            // Backend picker — populated from /api/backends. Only renders
            // when the server actually exposes the endpoint (avoids a
            // confusing "no backends" state on older parents).
            if (!backendsBlocked && backends.isNotEmpty()) {
                Text(
                    "LLM backend",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                BackendPickerDropdown(
                    backends = backends,
                    selected = pickedBackend,
                    active = activeBackend,
                    onSelect = { pickedBackend = it },
                )
            }

            // Profile picker — maps to /api/profiles. Optional; users
            // can start "Default (no profile)" and the parent picks its
            // own backend/profile. When set, profile name is sent on
            // POST /api/sessions/start.
            if (serverProfiles.isNotEmpty()) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                ProfilePickerDropdown(
                    profiles = serverProfiles,
                    selected = pickedServerProfile,
                    onSelect = { pickedServerProfile = it },
                )
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
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
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

            Text(
                "Working directory (optional)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = workingDir,
                    onValueChange = { workingDir = it },
                    placeholder = { Text("Server path — e.g. /home/user/code") },
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
                    "Resume previous (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                ResumePickerDropdown(
                    sessions = recentDone,
                    selectedId = resumeId,
                    onSelect = { resumeId = it },
                )
            }

            Text(
                "Git",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
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
                    "Auto git init",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                )
                androidx.compose.material3.Switch(
                    checked = autoGitCommit,
                    onCheckedChange = { autoGitCommit = it },
                )
                Text(
                    "Auto git commit",
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
                    "Recent sessions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
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
                                val profile = profiles.firstOrNull { it.id == selectedProfileId }
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
                TextButton(onClick = onCancel, enabled = !submitting) { Text("Cancel") }
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    Button(
                        onClick = {
                            val profile =
                                profiles.firstOrNull { it.id == selectedProfileId }
                                    ?: return@Button
                            if (task.isBlank()) {
                                banner = "Task cannot be empty."
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
                                transport
                                    .startSession(
                                        task = task.trim(),
                                        workingDir = workingDir.trim().ifBlank { null },
                                        profileName = pickedServerProfile,
                                        name = sessionName.trim().ifBlank { null },
                                        backend = pickedBackend,
                                        resumeId = resumeId,
                                        autoGitInit = autoGitInit,
                                        autoGitCommit = autoGitCommit,
                                    )
                                    .fold(
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
                            Text("Start")
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
            Text("From library ▾", style = MaterialTheme.typography.labelMedium)
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
private fun ResumePickerDropdown(
    sessions: List<com.dmzs.datawatchclient.domain.Session>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value =
                when {
                    selectedId == null -> "Start fresh"
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
                text = { Text("Start fresh") },
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
    val display =
        selected ?: "Default (no profile)"
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
                text = { Text("Default (no profile)") },
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
            "No servers configured — add one in Settings first.",
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
            value = selected?.displayName ?: "Pick a server",
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
 * Inspect `/api/config` for `backends.<name>.enabled`. Tolerates
 * both the flat dot-path storage the parent daemon uses and the
 * legacy nested `{backends: {name: {enabled: true}}}` shape so
 * older servers still filter correctly.
 */
private fun backendEnabled(
    root: kotlinx.serialization.json.JsonObject,
    name: String,
): Boolean {
    root["backends.$name.enabled"]?.let { v ->
        return (v as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toBooleanStrictOrNull() == true
    }
    val nested =
        (root["backends"] as? kotlinx.serialization.json.JsonObject)?.get(name)
            as? kotlinx.serialization.json.JsonObject ?: return false
    val v = nested["enabled"] as? kotlinx.serialization.json.JsonPrimitive ?: return false
    return v.content.toBooleanStrictOrNull() == true
}
