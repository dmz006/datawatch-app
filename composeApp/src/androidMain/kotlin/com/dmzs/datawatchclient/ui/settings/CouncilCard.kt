package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.CouncilLivePhase
import com.dmzs.datawatchclient.transport.CouncilLiveReducer
import com.dmzs.datawatchclient.transport.CouncilLiveState
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.CouncilConfigDto
import com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto
import com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto
import com.dmzs.datawatchclient.transport.dto.CouncilRunDto
import com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest
import com.dmzs.datawatchclient.ui.common.MicAttachableTextField
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CouncilCard() {
    var personas by remember { mutableStateOf<List<CouncilPersonaDto>>(emptyList()) }
    var runs by remember { mutableStateOf<List<CouncilRunDto>>(emptyList()) }
    var config by remember { mutableStateOf(CouncilConfigDto()) }
    var configLlmRef by remember(config) { mutableStateOf(config.llmRef ?: "") }
    var configMaxParallel by remember(config) { mutableStateOf(config.maxParallel?.toString() ?: "") }
    var configDraftRetention by remember(config) { mutableStateOf(config.draftRetentionDays?.toString() ?: "") }
    var proposal by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("quick") }
    var selectedPersonas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPersonasSheet by remember { mutableStateOf(false) }
    var showAddWizard by remember { mutableStateOf(false) }
    var editingPersona by remember { mutableStateOf<CouncilPersonaForEdit?>(null) }
    var personaToDelete by remember { mutableStateOf<CouncilPersonaDto?>(null) }
    var whisperConfigured by remember { mutableStateOf(false) }
    var activeTransport by remember { mutableStateOf<TransportClient?>(null) }
    var liveRun by remember { mutableStateOf<CouncilLiveState?>(null) }
    var liveRunIsLive by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadAll() {
        val activeId = ServiceLocator.activeServerStore.get()
        val sp =
            ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) {
                        list.filter { it.enabled }.firstOrNull()
                    } else {
                        list.firstOrNull { it.id == activeId && it.enabled }
                    }
                } ?: return
        val t = ServiceLocator.transportFor(sp)
        activeTransport = t
        t.councilListPersonas().onSuccess { list ->
            personas = list
            // PWA renders every persona checkbox pre-checked.
            if (selectedPersonas.isEmpty()) selectedPersonas = list.filter { it.enabled }.map { it.name }.toSet()
        }
        t.councilListRuns().onSuccess { runs = it }
        t.councilGetConfig().onSuccess { config = it }
        t.fetchInfo().onSuccess { info -> whisperConfigured = info.whisperConfigured }
    }

    fun createPersona(
        name: String,
        prompt: String,
        description: String,
        assistBackend: String?,
    ) {
        scope.launch {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp =
                    ServiceLocator.profileRepository.observeAll()
                        .first { list -> list.any { it.enabled } }
                        .let { list ->
                            if (activeId == null) {
                                list.filter { it.enabled }.firstOrNull()
                            } else {
                                list.firstOrNull { it.id == activeId && it.enabled }
                            }
                        } ?: return@runCatching
                val dto =
                    CouncilPersonaCreateDto(
                        name = name,
                        prompt = prompt,
                        description = description,
                        assistBackend = assistBackend,
                    )
                ServiceLocator.transportFor(sp).createCouncilPersona(dto)
                    .onSuccess { loadAll() }
            }
        }
    }

    fun updatePersona(
        name: String,
        prompt: String,
        description: String,
        assistBackend: String?,
    ) {
        scope.launch {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp =
                    ServiceLocator.profileRepository.observeAll()
                        .first { list -> list.any { it.enabled } }
                        .let { list ->
                            if (activeId == null) {
                                list.filter { it.enabled }.firstOrNull()
                            } else {
                                list.firstOrNull { it.id == activeId && it.enabled }
                            }
                        } ?: return@runCatching
                val dto =
                    CouncilPersonaCreateDto(
                        name = name,
                        prompt = prompt,
                        description = description,
                        assistBackend = assistBackend,
                    )
                ServiceLocator.transportFor(sp).updateCouncilPersona(name, dto)
                    .onSuccess { loadAll() }
            }
        }
    }

    fun deletePersona(name: String) {
        scope.launch {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp =
                    ServiceLocator.profileRepository.observeAll()
                        .first { list -> list.any { it.enabled } }
                        .let { list ->
                            if (activeId == null) {
                                list.filter { it.enabled }.firstOrNull()
                            } else {
                                list.firstOrNull { it.id == activeId && it.enabled }
                            }
                        } ?: return@runCatching
                ServiceLocator.transportFor(sp).deleteCouncilPersona(name)
                    .onSuccess { loadAll() }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { loadAll() } }

    PwaCard(
        id = "council",
        title = stringResource(R.string.council_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        innerPadding = PaddingValues(12.dp),
    ) {
        // ── PERSONAS section ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.council_personas_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (personas.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showPersonasSheet = true },
                        modifier = Modifier.padding(0.dp),
                    ) { Text("Manage", style = MaterialTheme.typography.labelSmall) }
                }
                Button(
                    onClick = { showAddWizard = true },
                    modifier = Modifier.padding(0.dp),
                ) { Text("Add", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (personas.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                personas.forEach { persona ->
                    FilterChip(
                        selected = persona.enabled && persona.name in selectedPersonas,
                        onClick = {
                            selectedPersonas =
                                if (persona.name in selectedPersonas) {
                                    selectedPersonas - persona.name
                                } else {
                                    selectedPersonas + persona.name
                                }
                        },
                        label = { Text(persona.name, style = MaterialTheme.typography.labelSmall) },
                        enabled = persona.enabled,
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── PERSONA MANAGEMENT SHEET ──────────────────────────────────────
        if (showPersonasSheet) {
            ModalBottomSheet(onDismissRequest = { showPersonasSheet = false }) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.council_personas_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn {
                        items(personas) { persona ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(persona.name, style = MaterialTheme.typography.bodyMedium)
                                        if (persona.isBuiltin) {
                                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                                Text(
                                                    stringResource(R.string.council_persona_builtin_badge),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                        }
                                    }
                                    if (persona.description.isNotBlank()) {
                                        Text(
                                            persona.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    editingPersona =
                                        CouncilPersonaForEdit(
                                            name = persona.name,
                                            prompt = persona.prompt,
                                            description = persona.description,
                                            isBuiltin = persona.isBuiltin,
                                        )
                                    showPersonasSheet = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.council_persona_edit),
                                    )
                                }
                                if (!persona.isBuiltin) {
                                    IconButton(onClick = {
                                        personaToDelete = persona
                                        showPersonasSheet = false
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.council_persona_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showAddWizard = true
                            showPersonasSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add New Persona") }
                }
            }
        }

        // ── PERSONA WIZARD SHEET ──────────────────────────────────────────
        if (showAddWizard || editingPersona != null) {
            CouncilPersonaWizardSheet(
                onDismiss = {
                    showAddWizard = false
                    editingPersona = null
                },
                onSave = { name, prompt, desc, backend ->
                    if (editingPersona != null) {
                        updatePersona(name, prompt, desc, backend)
                    } else {
                        createPersona(name, prompt, desc, backend)
                    }
                    showAddWizard = false
                    editingPersona = null
                },
                existingPersona = editingPersona,
                whisperConfigured = whisperConfigured,
            )
        }

        // ── PERSONA DELETE CONFIRM ────────────────────────────────────────
        personaToDelete?.let { persona ->
            AlertDialog(
                onDismissRequest = { personaToDelete = null },
                title = { Text(stringResource(R.string.council_persona_delete_confirm_title, persona.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        deletePersona(persona.name)
                        personaToDelete = null
                    }) { Text(stringResource(R.string.council_persona_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { personaToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        // ── FIREHOSE TOGGLE (#97) ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.council_firehose_label),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = config.commFirehose,
                onCheckedChange = { newVal ->
                    val updated = config.copy(commFirehose = newVal)
                    config = updated
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get()
                            val sp =
                                ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) {
                                            list.filter { it.enabled }.firstOrNull()
                                        } else {
                                            list.firstOrNull { it.id == activeId && it.enabled }
                                        }
                                    } ?: return@runCatching
                            ServiceLocator.transportFor(sp).councilUpdateConfig(updated)
                                .onSuccess { config = it }
                        }
                    }
                },
            )
        }

        // ── COUNCIL CONFIG (G14/G22) ──────────────────────────────────────
        OutlinedTextField(
            value = configLlmRef,
            onValueChange = { configLlmRef = it },
            label = { Text(stringResource(R.string.council_config_llm_ref)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = configMaxParallel,
                onValueChange = { if (it.all { c -> c.isDigit() }) configMaxParallel = it },
                label = { Text(stringResource(R.string.council_config_max_parallel)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = configDraftRetention,
                onValueChange = { if (it.all { c -> c.isDigit() }) configDraftRetention = it },
                label = { Text(stringResource(R.string.council_config_draft_retention)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = {
                val updated =
                    config.copy(
                        llmRef = configLlmRef.trim().ifBlank { null },
                        maxParallel = configMaxParallel.trim().toIntOrNull(),
                        draftRetentionDays = configDraftRetention.trim().toIntOrNull(),
                    )
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp =
                            ServiceLocator.profileRepository.observeAll()
                                .first { list -> list.any { it.enabled } }
                                .let { list ->
                                    if (activeId == null) {
                                        list.filter { it.enabled }.firstOrNull()
                                    } else {
                                        list.firstOrNull { it.id == activeId && it.enabled }
                                    }
                                } ?: return@runCatching
                        ServiceLocator.transportFor(sp).councilUpdateConfig(updated)
                            .onSuccess { config = it }
                    }
                }
            },
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.action_save)) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── START RUN form ────────────────────────────────────────────────
        MicAttachableTextField(
            value = proposal,
            onValueChange = { proposal = it },
            label = { Text(stringResource(R.string.council_run_proposal)) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            minLines = 2,
            whisperConfigured = whisperConfigured,
            onMicClick = null,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == "quick",
                onClick = { mode = "quick" },
                label = { Text(stringResource(R.string.council_mode_quick)) },
            )
            FilterChip(
                selected = mode == "debate",
                onClick = { mode = "debate" },
                label = { Text(stringResource(R.string.council_mode_debate)) },
            )
        }
        FilledTonalButton(
            onClick = {
                if (proposal.isNotBlank()) {
                    scope.launch {
                        runCatching {
                            val activeId = ServiceLocator.activeServerStore.get()
                            val sp =
                                ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) {
                                            list.filter { it.enabled }.firstOrNull()
                                        } else {
                                            list.firstOrNull { it.id == activeId && it.enabled }
                                        }
                                    } ?: return@runCatching
                            val chosen = personas.map { it.name }.filter { it in selectedPersonas }
                            val req =
                                StartCouncilRunRequest(
                                    proposal = proposal.trim(),
                                    mode = mode,
                                    personas = chosen,
                                )
                            startError = null
                            ServiceLocator.transportFor(sp).councilStartRun(req)
                                .onSuccess { run ->
                                    // Async-first server: ack is {id, status, events_path} —
                                    // open the live watch (PWA councilOpenLiveWatch).
                                    liveRun =
                                        CouncilLiveState(
                                            runId = run.id,
                                            proposal = req.proposal,
                                            mode = mode,
                                            personas = chosen,
                                            roundsTotal = CouncilLiveReducer.roundsForMode(mode),
                                        )
                                    liveRunIsLive = true
                                    proposal = ""
                                }
                                .onFailure { e -> startError = e.message ?: e::class.simpleName }
                        }
                    }
                }
            },
            enabled = proposal.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.council_run_btn)) }

        startError?.let { err ->
            Text(
                stringResource(R.string.council_start_failed) + ": " + err,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // ── RECENT RUNS (PWA council_recent, limit 5) ──────────────────────
        if (runs.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.council_results_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            runs.take(5).forEachIndexed { idx, run ->
                if (idx > 0) HorizontalDivider()
                CouncilRunRow(
                    run = run,
                    onOpen = {
                        liveRun = CouncilLiveReducer.fromRun(run)
                        liveRunIsLive = !run.isFinished
                    },
                )
            }
        }
    }

    val sheetRun = liveRun
    val sheetTransport = activeTransport
    if (sheetRun != null && sheetTransport != null) {
        CouncilLiveRunSheet(
            transport = sheetTransport,
            initial = sheetRun,
            live = liveRunIsLive,
            onDismiss = { liveRun = null },
            onFinished = { scope.launch { runCatching { loadAll() } } },
        )
    }
}

/** PWA recent-run line: mode chip · "N personas × M rounds" · short id · detail. */
@Composable
private fun CouncilRunRow(
    run: CouncilRunDto,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .background(Color(0xFF6366F1).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(run.mode, style = MaterialTheme.typography.labelSmall, color = Color(0xFF6366F1))
        }
        Text(
            stringResource(R.string.council_runs_summary, run.personas.size, run.rounds.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            run.id.take(8),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!run.isFinished) CouncilPhaseChip(CouncilLivePhase.RUNNING)
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.council_btn_detail), style = MaterialTheme.typography.labelSmall)
        }
    }
}
