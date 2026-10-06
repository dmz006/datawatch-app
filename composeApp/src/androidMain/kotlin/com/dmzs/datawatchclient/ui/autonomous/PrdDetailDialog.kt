package com.dmzs.datawatchclient.ui.autonomous

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.ui.sessions.SessionStatsViewModel
import com.dmzs.datawatchclient.ui.shell.SessionsNavChannel
import androidx.compose.material3.OutlinedButton

internal val EFFORT_OPTIONS = listOf("", "low", "medium", "high", "max", "quick", "normal", "thorough")

/**
 * PRD detail — full-screen Scaffold with 3 tabs: Overview, Stories, Decisions.
 * Replaces the AlertDialog to give the content room to breathe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun PrdDetailDialog(
    prd: PrdDto,
    backends: List<String> = emptyList(),
    permissionModes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onApprove: (note: String?) -> Unit,
    onReject: (String) -> Unit,
    onDecompose: () -> Unit,
    onSetLlm: (backend: String, effort: String, model: String, decompositionProfile: String, decompositionModel: String) -> Unit,
    onResetTask: ((prdId: String, taskId: String) -> Unit)? = null,
    onResetToDraft: (() -> Unit)? = null,
    /** Model list from /api/ollama/models — empty = Ollama not configured. */
    ollamaModels: List<String> = emptyList(),
    /** Model list from /api/openwebui/models — empty = OpenWebUI not configured. */
    openWebUiModels: List<String> = emptyList(),
    /** Flat model ID list from /api/opencode/models — empty when unavailable. */
    openCodeModels: List<String> = emptyList(),
    /** Grouped opencode models: providerLabel → model IDs. */
    openCodeModelGroups: Map<String, List<String>> = emptyMap(),
    /** Models for non-ollama/openwebui/opencode backends (e.g. quad): kind → model IDs. */
    extraBackendModels: Map<String, List<String>> = emptyMap(),
    onRun: () -> Unit,
    onCancel: () -> Unit,
    /** Parity D52b — PWA detail Pause (running) / Resume (paused). */
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onRequestRevision: (note: String) -> Unit,
    onEditPrd: (title: String?, spec: String?, permissionMode: String?) -> Unit,
    onDelete: () -> Unit,
    onEditStory: (storyId: String, newTitle: String?, newDescription: String?) -> Unit,
    onEditFiles: (storyId: String, files: List<String>) -> Unit,
    onCancelStory: ((storyId: String, reason: String?) -> Unit)? = null,
    onCancelTask: ((taskId: String, reason: String?) -> Unit)? = null,
    onRequeueTask: ((taskId: String) -> Unit)? = null,
    onEditTask: ((taskId: String, newSpec: String) -> Unit)? = null,
    onAddStory: ((title: String, description: String) -> Unit)? = null,
    onRemoveStory: ((storyId: String) -> Unit)? = null,
    onAddTask: ((storyId: String, title: String, spec: String) -> Unit)? = null,
    onRemoveTask: ((storyId: String, taskId: String) -> Unit)? = null,
    onApproveStory: ((storyId: String) -> Unit)? = null,
    onRejectStory: ((storyId: String, reason: String) -> Unit)? = null,
    automataTypes: List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto> = emptyList(),
    onSetType: ((String) -> Unit)? = null,
    onSetGuidedMode: ((Boolean) -> Unit)? = null,
    onSetContinueOnStoryFailure: ((Boolean?) -> Unit)? = null,
    onSetSkills: ((List<String>) -> Unit)? = null,
    onCloneTemplate: (() -> Unit)? = null,
    onOpenFile: ((path: String) -> Unit)? = null,
    prdGraph: com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto? = null,
    prdGraphLoading: Boolean = false,
    /** BL386 — null = not yet loaded; empty = none available. */
    memoryReport: String? = null,
    memoryReportLoading: Boolean = false,
    onFetchMemoryReport: (() -> Unit)? = null,
    /** BL385/#183 — scoped recall results for this PRD. */
    memoryRecallResults: List<com.dmzs.datawatchclient.transport.dto.ScopedMemoryEntryDto> = emptyList(),
    memoryRecallLoading: Boolean = false,
    onRecallMemory: ((query: String) -> Unit)? = null,
    /** BL386 — delete with memory strategy (keep/purge/archive). */
    onDeleteWithMemory: ((strategy: String, roleFilter: List<String>, archiveToScope: String?) -> Unit)? = null,
    /** Observer process envelopes — all, keyed by session_id, refreshed every 5s while running. */
    prdEnvelopes: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto> = emptyList(),
    /** Live compute node stats (GPU/CPU/Mem) for the PRD's active backend. */
    prdComputeNodeDetail: com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto? = null,
    /** Name of the compute node currently providing stats. */
    prdComputeNodeRef: String? = null,
    /** Active sessions for this PRD with status boards. PWA prdActiveSessionCard parity. */
    prdActiveSessions: List<com.dmzs.datawatchclient.ui.autonomous.AutonomousViewModel.PrdActiveSessionInfo> = emptyList(),
    /** Capacity admission pools + wait queue. PWA prdCapacityCard parity. */
    prdCapacity: com.dmzs.datawatchclient.transport.dto.CapacityResponseDto? = null,
    /** #192 — set Automaton admission priority (higher = runs first). */
    onSetPriority: ((Int) -> Unit)? = null,
    /** BL370 — set per-Automaton max_concurrent_tasks (0 = global default). */
    onSetConcurrency: ((Int) -> Unit)? = null,
    /** #191 — set allowed read/write directory scope. */
    onSetDirs: ((readDirs: List<String>, writeDirs: List<String>) -> Unit)? = null,
    /** #202 — re-resolve stuck depends_on refs (v8.36.6). */
    onRepairDependsOn: (() -> Unit)? = null,
    /** PWA overflow "Archive" — completed / rejected / cancelled automata. */
    onArchive: (() -> Unit)? = null,
    // Parity D24a — Scan / Rules tabs.
    scanResult: com.dmzs.datawatchclient.transport.dto.ScanResultDto? = null,
    scanLoading: Boolean = false,
    onLoadScan: (() -> Unit)? = null,
    onTriggerScan: (() -> Unit)? = null,
    onCreateFixPrd: (() -> Unit)? = null,
    onProposeRules: (() -> Unit)? = null,
    proposedRules: com.dmzs.datawatchclient.transport.dto.RuleProposalDto? = null,
    onDismissProposedRules: (() -> Unit)? = null,
    rulesResult: String? = null,
    rulesLoading: Boolean = false,
    onRunRules: (() -> Unit)? = null,
    /** PWA story ⚙ — project profile names for the execution-profile override. */
    projectProfiles: List<String> = emptyList(),
    /** PWA story 🤖 — POST set_story_llm (storyId, backend, effort, model). */
    onSetStoryLlm: ((storyId: String, backend: String, effort: String, model: String) -> Unit)? = null,
    /** PWA story ⚙ — POST set_story_profile (storyId, profile; empty = inherit). */
    onSetStoryProfile: ((storyId: String, profile: String) -> Unit)? = null,
    /** PWA task ✎ "Edit spec + LLM" — edit_task and/or set_task_llm. */
    onEditTaskSpecLlm: ((taskId: String, newSpec: String?, llm: Triple<String, String, String>?) -> Unit)? = null,
    /** Live planning stream state for this automaton (PWA _startDecomposeStream). */
    decomposeLive: com.dmzs.datawatchclient.transport.sse.DecomposeLiveState? = null,
    /** PWA lifecycle strip "Instantiate" for template automata. */
    onInstantiateTemplate: ((vars: Map<String, String>) -> Unit)? = null,
) {
    BackHandler(enabled = true, onBack = onDismiss)
    val modelsFor: (String) -> List<String> = { b ->
        when {
            b.contains("ollama", ignoreCase = true) -> ollamaModels
            b.contains("openwebui", ignoreCase = true) -> openWebUiModels
            b.startsWith("opencode", ignoreCase = true) -> openCodeModels
            else -> extraBackendModels[b].orEmpty()
        }
    }
    var instantiateOpen by remember { mutableStateOf(false) }

    val status = prd.status
    val canReview = status == "needs_review" || status == "revisions_asked"
    val canEdit = status != "running"
    val isCancellable = status !in setOf("cancelled", "completed", "done", "rejected", "failed", "archived")
    val canResetToDraft = status !in setOf("running", "planning", "archived")

    var selectedTab by remember { mutableStateOf(0) }
    var rejectOpen by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var reviseOpen by remember { mutableStateOf(false) }
    var reviseNote by remember { mutableStateOf("") }
    var llmOpen by remember { mutableStateOf(false) }
    var editPrdOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var editingStory: PrdStoryDto? by remember { mutableStateOf(null) }
    var editingFilesFor: PrdStoryDto? by remember { mutableStateOf(null) }
    var graphOpen by remember { mutableStateOf(false) }
    var addStoryOpen by remember { mutableStateOf(false) }
    var addStoryTitle by remember { mutableStateOf("") }
    var addStoryDescription by remember { mutableStateOf("") }
    var approveOpen by remember { mutableStateOf(false) }
    var approveNote by remember { mutableStateOf("") }
    // Memory strategy on delete (#175)
    var memoryStrategy by remember { mutableStateOf("keep") }
    var memoryArchiveRoles by remember { mutableStateOf("") }
    var memoryArchiveScope by remember { mutableStateOf("project-shared") }
    // Memory recall (#183)
    var recallQuery by remember { mutableStateOf("") }

    val showProgressTab = status == "running" || status == "decomposing"
    // Parity D24a — PWA tab set: Overview · Stories · Decisions · Scan · Rules.
    // Graph and Progress are cards on Overview.
    val tabs =
        listOf(
            stringResource(R.string.prd_tab_overview),
            stringResource(R.string.prd_tab_stories),
            stringResource(R.string.prd_tab_decisions),
            stringResource(R.string.prd_tab_scan),
            stringResource(R.string.prd_tab_rules),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        prd.title?.takeIf { it.isNotBlank() } ?: prd.name.takeIf { it.isNotBlank() } ?: "(no title)",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = { editPrdOpen = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                    IconButton(onClick = { deleteConfirmOpen = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            item {
                // ── Header section — scrolls with page ──────────────────────
                Column(modifier = Modifier.padding(12.dp)) {
                    // Row 1: type badge + template badge + spacer + status pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        prd.type?.takeIf { it.isNotBlank() }?.let { TypeBadge(it) }
                        if (prd.isTemplate) {
                            Text(
                                stringResource(R.string.autonomous_template_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        PrdStatusBadge(status)
                    }

                    // Row 2: id code + created date
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            prd.id,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                        prd.createdAt?.takeIf { it.isNotBlank() }?.let { ts ->
                            Text(
                                ts.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                    }

                    // Spec snippet with expand/collapse
                    prd.spec?.takeIf { it.isNotBlank() }?.let { fullSpec ->
                        var specExpanded by remember { mutableStateOf(false) }
                        val isLong = fullSpec.length > 280
                        val displaySpec = if (specExpanded || !isLong) fullSpec else fullSpec.take(280)
                        val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .drawBehind {
                                        drawRect(
                                            color = accent2,
                                            topLeft = Offset.Zero,
                                            size = Size(3.dp.toPx(), size.height),
                                        )
                                    }
                                    .padding(start = 8.dp),
                        ) {
                            Column {
                                if (specExpanded) {
                                    MarkdownView(
                                        text = fullSpec,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Text(
                                        displaySpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isLong) {
                                    Text(
                                        if (specExpanded) stringResource(R.string.automata_spec_hide)
                                        else stringResource(R.string.automata_spec_show_full),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .clickable { specExpanded = !specExpanded },
                                    )
                                }
                            }
                        }
                    }

                    // Lifecycle strip (template automata: single Instantiate step, PWA renderLifecycleStrip)
                    LifecycleStrip(
                        status,
                        isTemplate = prd.isTemplate,
                        onInstantiate = if (onInstantiateTemplate != null) ({ instantiateOpen = true }) else null,
                    )
                    // Live planning stream (PWA #decompose-progress box under the toolbar)
                    if (decomposeLive != null) DecomposeLiveCard(decomposeLive)

                    // Active session card — PWA prdActiveSessionCard parity (planning/decomposing/running/blocked)
                    if (status in setOf("planning", "decomposing", "running", "blocked")) {
                        Spacer(Modifier.height(8.dp))
                        PrdActiveSessionsCard(
                            prdStatus = status,
                            activeSessions = prdActiveSessions,
                            computeNodeDetail = prdComputeNodeDetail,
                            computeNodeRef = prdComputeNodeRef,
                            liveStreamShown = decomposeLive != null,
                        )
                    }

                    // Capacity card — PWA prdCapacityCard parity
                    val showCapacity = prdCapacity != null &&
                        (prdCapacity.pools.any { it.limit > 0 } || prdCapacity.waiting.isNotEmpty())
                    if (showCapacity && prdCapacity != null) {
                        Spacer(Modifier.height(8.dp))
                        PrdCapacityCard(capacity = prdCapacity)
                    }

                    // Terminal-state hint
                    if (status in listOf("done", "aborted", "failed", "archived")) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.prd_terminal_state_hint),
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    // PWA lifecycle overflow: Archive for terminal (non-archived) automata.
                    if (onArchive != null && status in setOf("completed", "rejected", "cancelled")) {
                        OutlinedButton(
                            onClick = onArchive,
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("📦 " + stringResource(R.string.prd_action_archive)) }
                    }

                    // Primary action buttons
                    val hasPrimaryAction = canReview || status == "approved" || isCancellable
                    if (hasPrimaryAction) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canReview) {
                                FilledTonalButton(
                                    onClick = {
                                        approveNote = ""
                                        approveOpen = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF10B981).copy(alpha = 0.18f),
                                            contentColor = Color(0xFF10B981),
                                        ),
                                ) { Text(stringResource(R.string.action_approve)) }
                            }
                            if (status == "approved") {
                                FilledTonalButton(
                                    onClick = {
                                        onRun()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFF3B82F6).copy(alpha = 0.18f),
                                            contentColor = Color(0xFF3B82F6),
                                        ),
                                ) { Text(stringResource(R.string.prd_detail_run)) }
                            }
                            if (status == "running" && onPause != null) {
                                FilledTonalButton(
                                    onClick = onPause,
                                    modifier = Modifier.weight(1f),
                                ) { Text("⏸ " + stringResource(R.string.automata_action_pause)) }
                            }
                            if (status == "paused" && onResume != null) {
                                FilledTonalButton(
                                    onClick = onResume,
                                    modifier = Modifier.weight(1f),
                                ) { Text("▶ " + stringResource(R.string.automata_action_resume)) }
                            }
                            if (isCancellable) {
                                FilledTonalButton(
                                    onClick = {
                                        onCancel()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.5f,
                                                ),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                ) { Text(stringResource(R.string.action_cancel)) }
                            }
                            if (status == "draft" || status == "revisions_asked") {
                                FilledTonalButton(
                                    // Stay on the detail so the live planning stream shows (PWA).
                                    onClick = { onDecompose() },
                                    modifier = Modifier.weight(1f),
                                ) { Text(stringResource(R.string.prd_detail_decompose)) }
                            }
                        }
                    }

                    // Secondary actions
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        if (canReview) {
                            TextButton(onClick = { rejectOpen = true }) {
                                Text(
                                    stringResource(R.string.action_reject),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = { reviseOpen = true }) {
                                Text(
                                    stringResource(R.string.prd_detail_revise),
                                    color = Color(0xFFF59E0B),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (status != "running" && status != "completed") {
                            TextButton(onClick = { llmOpen = true }) {
                                Text(
                                    stringResource(R.string.prd_detail_llm),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        TextButton(onClick = { graphOpen = true }) {
                            Text(stringResource(R.string.prd_detail_graph), style = MaterialTheme.typography.labelSmall)
                        }
                        if (onCloneTemplate != null && status in setOf("completed", "done", "approved", "cancelled", "failed")) {
                            TextButton(onClick = {
                                onCloneTemplate()
                                onDismiss()
                            }) {
                                Text(
                                    stringResource(R.string.prd_btn_clone_template),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (onResetToDraft != null && canResetToDraft) {
                            TextButton(onClick = {
                                onResetToDraft()
                                onDismiss()
                            }) {
                                Text(
                                    stringResource(R.string.prd_btn_reset_to_draft),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (onRepairDependsOn != null) {
                            TextButton(onClick = {
                                onRepairDependsOn()
                                onDismiss()
                            }) {
                                Text(
                                    stringResource(R.string.prd_btn_repair_depends_on),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
            } // end header item

            // ── Tab strip — sticks to top as header scrolls away ──────────
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, label ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            // ── Tab content ────────────────────────────────────────────────
            item {
                Column(
                    modifier =
                        Modifier
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Parity D24a — PWA five tabs; Graph + Progress render as cards on Overview.
                        if (selectedTab == 0) {
                            // #191 scope_warnings banner
                            if (prd.scopeWarnings.isNotEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            stringResource(R.string.prd_scope_warnings_title),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            stringResource(R.string.prd_scope_warnings_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        prd.scopeWarnings.forEach { w ->
                                            Text(
                                                "• $w",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                }
                            }
                            PrdTypeRow(prd, automataTypes, onSetType)
                            PrdGuidedModeRow(prd, onSetGuidedMode)
                            PrdContinueOnStoryFailureRow(prd, onSetContinueOnStoryFailure)
                            PrdSkillsRow(prd, onSetSkills)
                            PrdConcurrencyRow(prd, onSetConcurrency)
                            PrdPriorityRow(prd, onSetPriority)
                            PrdScopeDirsRow(prd, onSetDirs)
                            PrdDepthCreatedMeta(prd)
                            prd.spec?.takeIf { it.isNotBlank() }?.let { spec ->
                                MarkdownView(
                                    text = spec,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // ── BL387 Memory stats tile ──────────────────────
                            val hasMemStats = (prd.prdSharedCount ?: 0) > 0 ||
                                (prd.storySharedCount ?: 0) > 0 ||
                                (prd.sessionLocalCount ?: 0) > 0
                            if (hasMemStats) {
                                PrdMemoryStatsTile(prd)
                            }

                            // ── BL386 Memory report section ──────────────────
                            val isTerminalForReport = status in setOf("completed", "complete", "done", "archived")
                            if (isTerminalForReport && onFetchMemoryReport != null) {
                                PrdMemoryReportSection(
                                    report = memoryReport,
                                    loading = memoryReportLoading,
                                    onFetch = onFetchMemoryReport,
                                )
                            }

                            // ── BL385/#183 Memory recall search card ─────────
                            if (onRecallMemory != null) {
                                PrdMemoryRecallCard(
                                    query = recallQuery,
                                    onQueryChange = { recallQuery = it },
                                    onSearch = { onRecallMemory(recallQuery) },
                                    results = memoryRecallResults,
                                    loading = memoryRecallLoading,
                                )
                            }

                            TextButton(
                                onClick = {
                                    SessionsNavChannel.jumpTo(prd.name)
                                    onDismiss()
                                },
                            ) {
                                Text(stringResource(R.string.prd_view_sessions))
                            }
                        }
                        if (selectedTab == 1) {
                            val conflicts =
                                buildMap<String, List<String>> {
                                    val byPath = mutableMapOf<String, MutableList<String>>()
                                    prd.stories
                                        .filter {
                                            it.status.lowercase() != "complete" &&
                                                it.status.lowercase() != "rejected"
                                        }
                                        .forEach { story ->
                                            story.files.forEach { f ->
                                                byPath.getOrPut(f) { mutableListOf() }.add(story.id)
                                            }
                                        }
                                    byPath.filter { it.value.size > 1 }.forEach { (path, ids) ->
                                        put(path, ids)
                                    }
                                }
                            if (prd.stories.isEmpty()) {
                                Text(
                                    stringResource(R.string.prd_detail_no_stories),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.prd_detail_stories_header, prd.stories.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                prd.stories.forEach { story ->
                                    key(story.id) {
                                        StoryRow(
                                            story = story,
                                            canEdit = canEdit,
                                            onEdit = { editingStory = story },
                                            onEditFiles = { editingFilesFor = story },
                                            conflicts = conflicts,
                                            prdId = prd.id,
                                            prdStatus = status,
                                            onResetTask = onResetTask,
                                            onCancelStory = onCancelStory?.let { cb -> { r -> cb(story.id, r) } },
                                            onCancelTask = onCancelTask,
                                            onRequeueTask = onRequeueTask,
                                            onEditTask = onEditTask,
                                            onRemoveStory = onRemoveStory?.let { cb -> { cb(story.id) } },
                                            onAddTask = onAddTask?.let { cb -> { title, spec -> cb(story.id, title, spec) } },
                                            onRemoveTask = onRemoveTask?.let { cb -> { taskId -> cb(story.id, taskId) } },
                                            onApproveStory = onApproveStory?.let { cb -> { cb(story.id) } },
                                            onRejectStory = onRejectStory?.let { cb -> { reason -> cb(story.id, reason) } },
                                            projectDir = prd.projectDir,
                                            onOpenFile = onOpenFile,
                                            backends = backends,
                                            modelsFor = modelsFor,
                                            projectProfiles = projectProfiles,
                                            onSetStoryLlm = onSetStoryLlm?.let { cb -> { b, e, m -> cb(story.id, b, e, m) } },
                                            onSetStoryProfile = onSetStoryProfile?.let { cb -> { p -> cb(story.id, p) } },
                                            onEditTaskSpecLlm = onEditTaskSpecLlm,
                                        )
                                    }
                                }
                                if (canEdit && onAddStory != null) {
                                    TextButton(
                                        onClick = { addStoryTitle = ""; addStoryDescription = ""; addStoryOpen = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("+ Add story", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        if (selectedTab == 2) {
                            val decisions = prd.decisions
                            if (decisions.isNullOrEmpty()) {
                                Text(
                                    stringResource(R.string.prd_tab_decisions_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                decisions.forEach { decision ->
                                    val label =
                                        buildString {
                                            decision.kind?.let { append("[$it] ") }
                                            append(decision.note ?: "")
                                            decision.actor?.let { append(" ($it)") }
                                        }
                                    Text(
                                        "• $label",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (selectedTab == 0 && (prdGraphLoading || (prdGraph != null && prdGraph.nodes.isNotEmpty()))) {
                            PrdOverviewCardTitle(stringResource(R.string.prd_tab_graph))
                            // Graph tab — orchestrator DAG (#184)
                            when {
                                prdGraphLoading -> {
                                    com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent(
                                        label = stringResource(R.string.prd_tab_graph_loading),
                                        eyeSize = 32.dp,
                                        verticalPadding = 16.dp,
                                    )
                                }
                                prdGraph == null || prdGraph.nodes.isEmpty() -> {
                                    Text(
                                        stringResource(R.string.prd_tab_graph_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                else -> {
                                    PrdDagCanvas(
                                        graph = prdGraph,
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .height(400.dp),
                                    )
                                }
                            }
                        }
                        if (selectedTab == 0 && showProgressTab) {
                            PrdOverviewCardTitle(stringResource(R.string.automata_sg_progress))
                            // Progress tab — per-story task completion bars with CPU/RSS annotation
                            // and compute node resource section (mirrors PWA _renderStatusGraphs).
                            val totalStories = prd.stories.size
                            val totalTasks = prd.stories.sumOf { it.tasks.size }
                            val decomposed = totalStories > 0
                            // Build session → envelope lookup for per-story CPU/RSS.
                            val envelopeBySession = prdEnvelopes.filter { it.sessionId != null }
                                .associateBy { it.sessionId!! }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp),
                            ) {
                                Text(
                                    if (decomposed) "✓ ${stringResource(R.string.automata_sg_decomposed)}" else "… ${stringResource(R.string.automata_sg_decomposed)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (decomposed) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "$totalStories ${stringResource(R.string.automata_sg_stories)}  ·  $totalTasks ${stringResource(R.string.automata_sg_tasks)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (prd.stories.isEmpty()) {
                                Text(
                                    stringResource(R.string.automata_sg_no_stories),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val terminalStates = setOf("completed", "done", "failed", "cancelled", "skipped")
                                val activeStates = setOf("verifying", "running_tests", "in_progress", "running")
                                prd.stories.forEach { story ->
                                    val storyTotal = story.tasks.size
                                    val storyDone = story.tasks.count { it.status in terminalStates }
                                    val fraction = if (storyTotal > 0) storyDone.toFloat() / storyTotal else 0f
                                    val hasActiveTasks = story.tasks.any { it.status in activeStates }
                                    val effectiveStatus = if (hasActiveTasks) "in_progress" else story.status
                                    // Per-story CPU/RSS from observer envelopes.
                                    val storySessionIds = story.tasks.mapNotNull { it.sessionId }.toSet()
                                    val storyEnvs = storySessionIds.mapNotNull { envelopeBySession[it] }
                                    val avgCpu = if (storyEnvs.isNotEmpty())
                                        storyEnvs.sumOf { it.cpuPct } / storyEnvs.size else -1.0
                                    val totalRssMb = storyEnvs.sumOf { it.rssBytes } / 1_048_576.0
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val statusDot = when {
                                                storyDone == storyTotal && storyTotal > 0 -> "✓"
                                                effectiveStatus == "in_progress" -> "▶"
                                                else -> "·"
                                            }
                                            val dotColor = when {
                                                storyDone == storyTotal && storyTotal > 0 -> Color(0xFF10B981)
                                                effectiveStatus == "in_progress" -> Color(0xFF3B82F6)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            Text(
                                                statusDot,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = dotColor,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                            Text(
                                                story.title.ifBlank { story.id },
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "$storyDone/$storyTotal",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${(fraction * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.defaultMinSize(minWidth = 34.dp),
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            color = when {
                                                storyDone == storyTotal && storyTotal > 0 -> Color(0xFF10B981)
                                                effectiveStatus == "in_progress" -> Color(0xFF3B82F6)
                                                story.status == "failed" -> Color(0xFFEF4444)
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                        )
                                        // Per-story CPU/RSS annotation from observer envelopes.
                                        if (avgCpu >= 0 || totalRssMb > 0) {
                                            val parts = buildList {
                                                if (avgCpu >= 0) add("CPU ${avgCpu.toInt()}%")
                                                if (totalRssMb > 0) add("${totalRssMb.toInt()} MB")
                                            }
                                            Text(
                                                parts.joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(top = 1.dp),
                                            )
                                        }
                                    }
                                }
                            }

                        }
                    if (selectedTab == 3) {
                        // Parity D24a — PWA Scan tab: help, ▶ Run Scan, verdict + findings.
                        Text(
                            stringResource(R.string.prd_scan_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.runtime.LaunchedEffect(prd.id) { onLoadScan?.invoke() }
                        ScanResultCard(
                            scanResult = scanResult,
                            scanLoading = scanLoading,
                            onTriggerScan = onTriggerScan,
                            onCreateFixPrd = onCreateFixPrd,
                            onProposeRules = onProposeRules,
                            proposedRules = proposedRules,
                            onDismissProposedRules = onDismissProposedRules,
                        )
                    }
                    if (selectedTab == 4) {
                        // Parity D24a — PWA Rules tab: AGENT.md / project-rules check.
                        PrdRulesTab(rulesResult = rulesResult, rulesLoading = rulesLoading, onRunRules = onRunRules)
                    }
                }
            } // end tab content item
        } // end LazyColumn
    }

    // ── Sub-dialogs ────────────────────────────────────────────────────────

    if (instantiateOpen && onInstantiateTemplate != null) {
        PrdInstantiateTemplateDialog(
            onDismiss = { instantiateOpen = false },
            onSubmit = { vars -> onInstantiateTemplate(vars) },
        )
    }

    if (approveOpen) {
        AlertDialog(
            onDismissRequest = { approveOpen = false },
            title = { Text(stringResource(R.string.action_approve)) },
            text = {
                OutlinedTextField(
                    value = approveNote,
                    onValueChange = { approveNote = it },
                    label = { Text(stringResource(R.string.prd_detail_approve_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onApprove(approveNote.trim().takeIf { it.isNotBlank() })
                        approveOpen = false
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_approve), color = Color(0xFF10B981)) }
            },
            dismissButton = {
                TextButton(onClick = { approveOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (rejectOpen) {
        AlertDialog(
            onDismissRequest = { rejectOpen = false },
            title = { Text(stringResource(R.string.prd_detail_reject_title)) },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text(stringResource(R.string.prd_detail_reject_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            onReject(rejectReason.trim())
                            rejectOpen = false
                            onDismiss()
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                ) { Text(stringResource(R.string.action_reject), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { rejectOpen = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (reviseOpen) {
        AlertDialog(
            onDismissRequest = { reviseOpen = false },
            title = { Text(stringResource(R.string.prd_detail_revise_title)) },
            text = {
                OutlinedTextField(
                    value = reviseNote,
                    onValueChange = { reviseNote = it },
                    label = { Text(stringResource(R.string.prd_detail_revise_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (reviseNote.isNotBlank()) {
                            onRequestRevision(reviseNote.trim())
                            reviseOpen = false
                            onDismiss()
                        }
                    },
                    enabled = reviseNote.isNotBlank(),
                ) { Text(stringResource(R.string.action_send)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { reviseOpen = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (llmOpen) {
        LlmOverrideDialog(
            currentBackend = prd.backend.orEmpty(),
            currentEffort = prd.effort.orEmpty(),
            currentModel = prd.model.orEmpty(),
            currentDecompositionProfile = prd.decompositionProfile.orEmpty(),
            currentDecompositionModel = prd.decompositionModel.orEmpty(),
            backends = backends,
            ollamaModels = ollamaModels,
            openWebUiModels = openWebUiModels,
            openCodeModels = openCodeModels,
            openCodeModelGroups = openCodeModelGroups,
            extraBackendModels = extraBackendModels,
            onDismiss = { llmOpen = false },
            onSave = { b, e, m, dp, dm ->
                onSetLlm(b, e, m, dp, dm)
                llmOpen = false
            },
        )
    }

    if (editPrdOpen) {
        EditPrdDialog(
            currentTitle = prd.title.orEmpty(),
            currentSpec = prd.spec.orEmpty(),
            currentPermissionMode = prd.permissionMode.orEmpty(),
            permissionModes = permissionModes,
            onDismiss = { editPrdOpen = false },
            onSave = { title, spec, pm ->
                onEditPrd(title, spec, pm)
                editPrdOpen = false
            },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text(stringResource(R.string.prd_detail_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.prd_detail_delete_body, prd.title ?: prd.name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (onDeleteWithMemory != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.prd_delete_memory_strategy),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        listOf("keep", "purge", "archive").forEach { strategy ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { memoryStrategy = strategy },
                            ) {
                                RadioButton(selected = memoryStrategy == strategy, onClick = { memoryStrategy = strategy })
                                Text(
                                    when (strategy) {
                                        "keep" -> stringResource(R.string.prd_delete_memory_keep)
                                        "purge" -> stringResource(R.string.prd_delete_memory_purge)
                                        else -> stringResource(R.string.prd_delete_memory_archive)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (memoryStrategy == "archive") {
                            OutlinedTextField(
                                value = memoryArchiveRoles,
                                onValueChange = { memoryArchiveRoles = it },
                                label = { Text(stringResource(R.string.prd_delete_memory_archive_roles)) },
                                placeholder = { Text(stringResource(R.string.prd_delete_memory_archive_roles_hint)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                maxLines = 2,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (onDeleteWithMemory != null && memoryStrategy != "keep") {
                            val roles = memoryArchiveRoles.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            onDeleteWithMemory(memoryStrategy, roles, if (memoryStrategy == "archive") memoryArchiveScope else null)
                        } else {
                            onDelete()
                        }
                        deleteConfirmOpen = false
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_delete), color = Color(0xFF7C2D12)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    editingStory?.let { story ->
        EditStoryDialog(
            story = story,
            onDismiss = { editingStory = null },
            onSave = { newTitle, newDescription ->
                onEditStory(story.id, newTitle, newDescription)
                editingStory = null
            },
        )
    }

    editingFilesFor?.let { story ->
        EditFilesDialog(
            story = story,
            onDismiss = { editingFilesFor = null },
            onSave = { files ->
                onEditFiles(story.id, files)
                editingFilesFor = null
            },
        )
    }

    if (graphOpen) {
        com.dmzs.datawatchclient.ui.orchestrator.OrchestratorGraphDialog(
            graphId = prd.id,
            onDismiss = { graphOpen = false },
        )
    }

    if (addStoryOpen && onAddStory != null) {
        AlertDialog(
            onDismissRequest = { addStoryOpen = false },
            title = { Text("Add story") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addStoryTitle,
                        onValueChange = { addStoryTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = addStoryDescription,
                        onValueChange = { addStoryDescription = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddStory(addStoryTitle.trim(), addStoryDescription.trim())
                        addStoryOpen = false
                    },
                    enabled = addStoryTitle.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { addStoryOpen = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
}

@Composable
private fun PrdStatusBadge(status: String) {
    // Parity D23a — same PWA state-badge tokens + pulse as the list pill.
    PrdStatusPill(status)
}

// ── LLM override sub-dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmOverrideDialog(
    currentBackend: String,
    currentEffort: String,
    currentModel: String,
    currentDecompositionProfile: String = "",
    currentDecompositionModel: String = "",
    backends: List<String>,
    ollamaModels: List<String> = emptyList(),
    openWebUiModels: List<String> = emptyList(),
    openCodeModels: List<String> = emptyList(),
    openCodeModelGroups: Map<String, List<String>> = emptyMap(),
    extraBackendModels: Map<String, List<String>> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (backend: String, effort: String, model: String, decompositionProfile: String, decompositionModel: String) -> Unit,
) {
    var backend by remember { mutableStateOf(currentBackend) }
    var effort by remember { mutableStateOf(currentEffort) }
    var model by remember { mutableStateOf(currentModel) }
    var decompositionProfile by remember { mutableStateOf(currentDecompositionProfile) }
    var decompositionModel by remember { mutableStateOf(currentDecompositionModel) }
    var backendMenuOpen by remember { mutableStateOf(false) }
    var effortMenuOpen by remember { mutableStateOf(false) }
    var planningMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var planningModelMenuOpen by remember { mutableStateOf(false) }

    val planningBackends = backends

    // Model list for the currently selected execution backend
    val execModels = when {
        backend.contains("ollama", ignoreCase = true) -> ollamaModels
        backend.contains("openwebui", ignoreCase = true) -> openWebUiModels
        backend.startsWith("opencode", ignoreCase = true) -> openCodeModels
        else -> extraBackendModels[backend].orEmpty()
    }
    val execIsOpenCode = backend.startsWith("opencode", ignoreCase = true) && openCodeModelGroups.isNotEmpty()

    // Model list for the planning backend
    val planModels = when {
        decompositionProfile.contains("ollama", ignoreCase = true) -> ollamaModels
        decompositionProfile.contains("openwebui", ignoreCase = true) -> openWebUiModels
        decompositionProfile.startsWith("opencode", ignoreCase = true) -> openCodeModels
        else -> extraBackendModels[decompositionProfile].orEmpty()
    }
    val planIsOpenCode = decompositionProfile.startsWith("opencode", ignoreCase = true) && openCodeModelGroups.isNotEmpty()

    val inheritLabel = stringResource(R.string.new_prd_inherit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_detail_set_llm_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = backendMenuOpen,
                    onExpandedChange = { backendMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = backend.ifEmpty { inheritLabel },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.new_prd_backend_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = backendMenuOpen) },
                    )
                    DropdownMenu(expanded = backendMenuOpen, onDismissRequest = { backendMenuOpen = false }) {
                        DropdownMenuItem(text = { Text(inheritLabel) }, onClick = {
                            backend = ""
                            backendMenuOpen = false
                        })
                        backends.forEach { b ->
                            DropdownMenuItem(text = { Text(b) }, onClick = {
                                backend = b
                                backendMenuOpen = false
                            })
                        }
                    }
                }
                // Execution model — dropdown for ollama/openwebui/opencode, free-text otherwise
                if (execModels.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelMenuOpen,
                        onExpandedChange = { modelMenuOpen = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = model.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_model_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
                        )
                        DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(inheritLabel) }, onClick = { model = ""; modelMenuOpen = false })
                            if (execIsOpenCode) {
                                openCodeModelGroups.forEach { (groupLabel, groupModels) ->
                                    DropdownMenuItem(
                                        text = { Text(groupLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                        onClick = {},
                                        enabled = false,
                                    )
                                    groupModels.forEach { m ->
                                        DropdownMenuItem(text = { Text("  $m") }, onClick = { model = m; modelMenuOpen = false })
                                    }
                                }
                            } else {
                                execModels.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelMenuOpen = false })
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.new_prd_model_label)) },
                        placeholder = { Text(stringResource(R.string.new_prd_backend_default)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                // Planning backend — ollama/openwebui only (v8.20.0)
                if (planningBackends.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = planningMenuOpen,
                        onExpandedChange = { planningMenuOpen = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = decompositionProfile.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.prd_detail_planning_backend)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planningMenuOpen) },
                        )
                        DropdownMenu(expanded = planningMenuOpen, onDismissRequest = { planningMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(inheritLabel) }, onClick = {
                                decompositionProfile = ""
                                planningMenuOpen = false
                            })
                            planningBackends.forEach { b ->
                                DropdownMenuItem(text = { Text(b) }, onClick = {
                                    decompositionProfile = b
                                    planningMenuOpen = false
                                })
                            }
                        }
                    }
                    // Planning model — dropdown or free-text depending on planning backend
                    if (planModels.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = planningModelMenuOpen,
                            onExpandedChange = { planningModelMenuOpen = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = decompositionModel.ifEmpty { inheritLabel },
                                onValueChange = {},
                                label = { Text(stringResource(R.string.prd_detail_planning_model)) },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = planningModelMenuOpen) },
                            )
                            DropdownMenu(expanded = planningModelMenuOpen, onDismissRequest = { planningModelMenuOpen = false }) {
                                DropdownMenuItem(text = { Text(inheritLabel) }, onClick = { decompositionModel = ""; planningModelMenuOpen = false })
                                if (planIsOpenCode) {
                                    openCodeModelGroups.forEach { (groupLabel, groupModels) ->
                                        DropdownMenuItem(
                                            text = { Text(groupLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) },
                                            onClick = {},
                                            enabled = false,
                                        )
                                        groupModels.forEach { m ->
                                            DropdownMenuItem(text = { Text("  $m") }, onClick = { decompositionModel = m; planningModelMenuOpen = false })
                                        }
                                    }
                                } else {
                                    planModels.forEach { m ->
                                        DropdownMenuItem(text = { Text(m) }, onClick = { decompositionModel = m; planningModelMenuOpen = false })
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = decompositionModel,
                            onValueChange = { decompositionModel = it },
                            label = { Text(stringResource(R.string.prd_detail_planning_model)) },
                            placeholder = { Text(stringResource(R.string.new_prd_backend_default)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = effortMenuOpen,
                    onExpandedChange = { effortMenuOpen = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = effort.ifEmpty { inheritLabel },
                        onValueChange = {},
                        label = { Text(stringResource(R.string.new_prd_effort_label)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = effortMenuOpen) },
                    )
                    DropdownMenu(expanded = effortMenuOpen, onDismissRequest = { effortMenuOpen = false }) {
                        EFFORT_OPTIONS.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(if (e.isEmpty()) inheritLabel else e) },
                                onClick = {
                                    effort = e
                                    effortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(backend, effort, model, decompositionProfile, decompositionModel) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ── Edit PRD title/spec sub-dialog ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPrdDialog(
    currentTitle: String,
    currentSpec: String = "",
    currentPermissionMode: String = "",
    permissionModes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (title: String?, spec: String?, permissionMode: String?) -> Unit,
) {
    var title by remember { mutableStateOf(currentTitle) }
    var spec by remember { mutableStateOf(currentSpec) }
    var permissionMode by remember { mutableStateOf(currentPermissionMode) }
    var pmMenuOpen by remember { mutableStateOf(false) }

    val inheritLabel = stringResource(R.string.new_prd_inherit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_detail_edit_prd_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.prd_detail_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec,
                    onValueChange = { spec = it },
                    label = { Text(stringResource(R.string.prd_detail_spec_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    maxLines = 8,
                )
                if (permissionModes.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = pmMenuOpen,
                        onExpandedChange = { pmMenuOpen = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = permissionMode.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_permission_mode_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pmMenuOpen) },
                        )
                        DropdownMenu(expanded = pmMenuOpen, onDismissRequest = { pmMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(inheritLabel) },
                                onClick = {
                                    permissionMode = ""
                                    pmMenuOpen = false
                                },
                            )
                            permissionModes.forEach { pm ->
                                DropdownMenuItem(
                                    text = { Text(pm) },
                                    onClick = {
                                        permissionMode = pm
                                        pmMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newTitle = title.takeIf { it != currentTitle && it.isNotBlank() }
                val newSpec = spec.takeIf { it.isNotBlank() }
                val newPm = permissionMode.takeIf { it != currentPermissionMode }
                onSave(newTitle, newSpec, newPm)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ── Story row + sub-dialogs ───────────────────────────────────────────────

/**
 * PWA `_renderStoryReadOnlyExtras`: "Progress: d/t tasks · p%[ · ⟳ n active]" + bar,
 * "✅ Touched:" aggregate (deduped, capped at 12, "+N more") and "→ Session <id>"
 * (first task with a session id) which opens the worker session detail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryReadOnlyExtras(story: PrdStoryDto) {
    val total = story.tasks.size
    val done = story.tasks.count { it.status in setOf("complete", "completed") }
    val activeCount = story.tasks.count { it.status in setOf("verifying", "running_tests") }
    val pct = if (total > 0) kotlin.math.round(100.0 * done / total).toInt() else 0
    val accent = MaterialTheme.colorScheme.primary
    val progressText = stringResource(R.string.prd_story_progress, done, total, pct)
    val activeText = stringResource(R.string.prd_story_active, activeCount)
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(progressText)
            if (activeCount > 0) {
                append(" · ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.SemiBold))
                append(activeText)
                pop()
            }
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
    )
    androidx.compose.material3.LinearProgressIndicator(
        progress = { pct / 100f },
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        color = if (pct == 100) Color(0xFF10B981) else accent,
    )
    val touched = story.tasks.flatMap { it.filesTouched }.distinct()
    val capped = touched.take(12)
    if (capped.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.prd_story_touched),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            capped.forEach { f -> FilePill(name = f, color = Color(0xFF10B981)) }
            if (touched.size > capped.size) {
                Text(
                    stringResource(R.string.prd_story_touched_more, touched.size - capped.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val sid = story.tasks.firstOrNull { !it.sessionId.isNullOrBlank() }?.sessionId
    if (sid != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clickable { com.dmzs.datawatchclient.ui.DeepLinks.pendingSessionTarget.tryEmit(sid) }
                    .padding(top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                stringResource(R.string.prd_story_session_link) + " ",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Text(sid, style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryRow(
    story: PrdStoryDto,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onEditFiles: () -> Unit,
    conflicts: Map<String, List<String>> = emptyMap(),
    prdId: String = "",
    prdStatus: String = "",
    onResetTask: ((prdId: String, taskId: String) -> Unit)? = null,
    onCancelStory: ((reason: String?) -> Unit)? = null,
    onCancelTask: ((taskId: String, reason: String?) -> Unit)? = null,
    onRequeueTask: ((taskId: String) -> Unit)? = null,
    onEditTask: ((taskId: String, newSpec: String) -> Unit)? = null,
    onRemoveStory: (() -> Unit)? = null,
    onAddTask: ((title: String, spec: String) -> Unit)? = null,
    onRemoveTask: ((taskId: String) -> Unit)? = null,
    onApproveStory: (() -> Unit)? = null,
    onRejectStory: ((reason: String) -> Unit)? = null,
    projectDir: String? = null,
    onOpenFile: ((path: String) -> Unit)? = null,
    backends: List<String> = emptyList(),
    modelsFor: (String) -> List<String> = { emptyList() },
    projectProfiles: List<String> = emptyList(),
    onSetStoryLlm: ((backend: String, effort: String, model: String) -> Unit)? = null,
    onSetStoryProfile: ((profile: String) -> Unit)? = null,
    onEditTaskSpecLlm: ((taskId: String, newSpec: String?, llm: Triple<String, String, String>?) -> Unit)? = null,
) {
    // PWA renderStory `editable` gate (needs_review / revisions_asked / cancelled).
    val pwaEditable = com.dmzs.datawatchclient.transport.dto.PrdLlmBadge.isEditable(prdStatus)
    var storyLlmOpen by remember { mutableStateOf(false) }
    var storyProfileOpen by remember { mutableStateOf(false) }
    val activeStoryStatuses = remember {
        setOf("running", "in_progress", "active", "awaiting_approval", "verifying", "running_tests")
    }
    var expanded by remember { mutableStateOf(story.status.lowercase() in activeStoryStatuses) }
    var cancelStoryOpen by remember { mutableStateOf(false) }
    var cancelStoryReason by remember { mutableStateOf("") }
    var rejectStoryOpen by remember { mutableStateOf(false) }
    var rejectStoryReason by remember { mutableStateOf("") }
    var addTaskOpen by remember { mutableStateOf(false) }
    var addTaskTitle by remember { mutableStateOf("") }
    var addTaskSpec by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Always-visible header row: title + chevron + status pill
        // Effective status: if story claims "completed" but tasks are still active, show actual task state.
        val activeTaskStatuses = setOf("running", "in_progress", "verifying", "running_tests")
        val hasActiveTasks = story.tasks.any { it.status in activeTaskStatuses }
        val effectiveStatus = if (story.status.lowercase() in setOf("completed", "complete") && hasActiveTasks) {
            story.tasks.firstOrNull { it.status in activeTaskStatuses }?.status ?: story.status
        } else {
            story.status
        }
        // clickable on header Row only — prevents file-pill/task-row taps from collapsing the story.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
            Text(
                story.title.ifBlank { story.id },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            StoryStatusPill(effectiveStatus)
        }
        // PWA story header pills: prof (read-only only) + LLM override.
        val profPill = com.dmzs.datawatchclient.transport.dto.PrdLlmBadge.storyProfileLabel(story, pwaEditable)
        val llmPill = com.dmzs.datawatchclient.transport.dto.PrdLlmBadge.storyLabel(story)
        if (profPill != null || llmPill != null) {
            FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                profPill?.let { PrdMiniPill(it) }
                llmPill?.let { PrdMiniPill(it) }
            }
        }

        // Guided-mode approve/reject: visible only when story is awaiting_approval and PRD is running.
        val canApproveReject = story.status.lowercase() == "awaiting_approval" &&
            prdStatus.lowercase() in setOf("approved", "active", "running")
        if (canApproveReject && (onApproveStory != null || onRejectStory != null)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onApproveStory != null) {
                    Button(
                        onClick = { onApproveStory() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(stringResource(R.string.action_approve), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                if (onRejectStory != null) {
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { rejectStoryReason = ""; rejectStoryOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(stringResource(R.string.action_reject), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Expandable body: description + files + edit buttons
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                // PWA 5(a): verdicts on a dedicated row, first in the story body.
                StoryVerdictsRow(story.verdicts)
                story.description?.takeIf { it.isNotBlank() }?.let { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (story.files.isNotEmpty() || story.filesTouched.isNotEmpty() || canEdit) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        story.files.forEach { f ->
                            val others = conflicts[f]?.filter { it != story.id }.orEmpty()
                            val resolvedPath = if (f.startsWith("/")) f else "${projectDir?.trimEnd('/').orEmpty()}/$f"
                            FilePill(
                                name = f,
                                color = Color(0xFF3B82F6),
                                conflict = others.isNotEmpty(),
                                conflictNote =
                                    if (others.isNotEmpty()) {
                                        "also in ${others.joinToString(
                                            ", ",
                                        )}"
                                    } else {
                                        null
                                    },
                                onClick = if (onOpenFile != null && isViewable(f)) {
                                    { onOpenFile(resolvedPath) }
                                } else null,
                            )
                        }
                        story.filesTouched.forEach { f ->
                            val resolvedPath = if (f.startsWith("/")) f else "${projectDir?.trimEnd('/').orEmpty()}/$f"
                            FilePill(
                                name = f,
                                color = Color(0xFF10B981),
                                onClick = if (onOpenFile != null && isViewable(f)) {
                                    { onOpenFile(resolvedPath) }
                                } else null,
                            )
                        }
                    }
                    if (canEdit) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(
                                    " ${stringResource(R.string.action_edit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            TextButton(onClick = onEditFiles) {
                                Text(
                                    stringResource(R.string.prd_detail_edit_files),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            // PWA ⚙ execution-profile + 🤖 LLM override (editable states only).
                            if (pwaEditable && onSetStoryProfile != null) {
                                TextButton(onClick = { storyProfileOpen = true }) {
                                    Text("⚙", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (pwaEditable && onSetStoryLlm != null) {
                                TextButton(onClick = { storyLlmOpen = true }) {
                                    Text("🤖", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val storyIsActive = story.status !in setOf("complete", "cancelled", "rejected")
                            if (onCancelStory != null && storyIsActive) {
                                TextButton(onClick = { cancelStoryReason = ""; cancelStoryOpen = true }) {
                                    Text(
                                        stringResource(R.string.action_cancel),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (onRemoveStory != null) {
                                TextButton(onClick = { onRemoveStory() }) {
                                    Text("🗑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                // PWA `_renderStoryReadOnlyExtras`: read-only cards (!editable) with tasks get
                // the progress row + bar, the aggregated files_touched row and the worker
                // session link (iOS PrdStoryReadOnlyExtras parity).
                if (!pwaEditable && story.tasks.isNotEmpty()) {
                    StoryReadOnlyExtras(story)
                }
                // Task list — v8.23.0 parity
                if (story.tasks.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    story.tasks.forEach { task ->
                        TaskRow(
                            task = task,
                            prdId = prdId,
                            prdStatus = prdStatus,
                            onResetTask = onResetTask,
                            onCancelTask = onCancelTask?.let { cb -> { r -> cb(task.id, r) } },
                            onRequeueTask = onRequeueTask?.let { cb -> { cb(task.id) } },
                            onEditTask = onEditTask?.let { cb -> { spec -> cb(task.id, spec) } },
                            onRemoveTask = onRemoveTask?.let { cb -> { cb(task.id) } },
                            projectDir = projectDir,
                            onOpenFile = onOpenFile,
                            backends = backends,
                            modelsFor = modelsFor,
                            onEditTaskSpecLlm = onEditTaskSpecLlm?.let { cb -> { spec, llm -> cb(task.id, spec, llm) } },
                        )
                    }
                }
                if (canEdit && onAddTask != null) {
                    TextButton(
                        onClick = { addTaskTitle = ""; addTaskSpec = ""; addTaskOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("+ Add task", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (storyLlmOpen && onSetStoryLlm != null) {
        PrdItemLlmDialog(
            title = stringResource(R.string.prd_set_story_llm_title),
            hint = stringResource(R.string.prd_story_llm_hint),
            spec = null,
            currentBackend = story.backend.orEmpty(),
            currentEffort = story.effort.orEmpty(),
            currentModel = story.model.orEmpty(),
            backends = backends,
            modelsFor = modelsFor,
            onDismiss = { storyLlmOpen = false },
            onSave = { _, llm -> llm?.let { (b, e, m) -> onSetStoryLlm(b, e, m) } },
        )
    }
    if (storyProfileOpen && onSetStoryProfile != null) {
        PrdStoryProfileDialog(
            storyId = story.id,
            current = story.executionProfile.orEmpty(),
            profiles = projectProfiles,
            onDismiss = { storyProfileOpen = false },
            onSave = { onSetStoryProfile(it) },
        )
    }
    if (cancelStoryOpen && onCancelStory != null) {
        AlertDialog(
            onDismissRequest = { cancelStoryOpen = false },
            title = { Text(stringResource(R.string.prd_detail_cancel_story_title)) },
            text = {
                OutlinedTextField(
                    value = cancelStoryReason,
                    onValueChange = { cancelStoryReason = it },
                    label = { Text(stringResource(R.string.prd_detail_cancel_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelStory(cancelStoryReason.trim().takeIf { it.isNotBlank() })
                        cancelStoryOpen = false
                    },
                ) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { cancelStoryOpen = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
    if (rejectStoryOpen && onRejectStory != null) {
        AlertDialog(
            onDismissRequest = { rejectStoryOpen = false },
            title = { Text(stringResource(R.string.prd_detail_reject_title)) },
            text = {
                OutlinedTextField(
                    value = rejectStoryReason,
                    onValueChange = { rejectStoryReason = it },
                    label = { Text(stringResource(R.string.prd_detail_reject_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectStoryReason.isNotBlank()) {
                            onRejectStory(rejectStoryReason.trim())
                            rejectStoryOpen = false
                        }
                    },
                ) { Text(stringResource(R.string.action_reject), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { rejectStoryOpen = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
    if (addTaskOpen && onAddTask != null) {
        AlertDialog(
            onDismissRequest = { addTaskOpen = false },
            title = { Text("Add task") },
            text = {
                Column {
                    OutlinedTextField(
                        value = addTaskTitle,
                        onValueChange = { addTaskTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = addTaskSpec,
                        onValueChange = { addTaskSpec = it },
                        label = { Text("Spec (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddTask(addTaskTitle.trim(), addTaskSpec.trim())
                        addTaskOpen = false
                    },
                    enabled = addTaskTitle.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { addTaskOpen = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TaskRow(
    task: PrdTaskDto,
    prdId: String,
    prdStatus: String,
    onResetTask: ((prdId: String, taskId: String) -> Unit)?,
    onCancelTask: ((reason: String?) -> Unit)? = null,
    onRequeueTask: (() -> Unit)? = null,
    onEditTask: ((newSpec: String) -> Unit)? = null,
    onRemoveTask: (() -> Unit)? = null,
    projectDir: String? = null,
    onOpenFile: ((path: String) -> Unit)? = null,
    backends: List<String> = emptyList(),
    modelsFor: (String) -> List<String> = { emptyList() },
    onEditTaskSpecLlm: ((newSpec: String?, llm: Triple<String, String, String>?) -> Unit)? = null,
) {
    // Server now accepts reset_task for PRDBlocked (same as PRDFailed) — widen gate to match.
    val canRetry = (task.status == "failed" || task.status == "blocked") && prdStatus in setOf("running", "blocked", "cancelled")
    val canRequeue = task.status in setOf("complete", "cancelled")
    val canCancel = task.status !in setOf("complete", "cancelled", "failed")
    val canEdit = prdStatus in setOf("needs_review", "revisions_asked", "cancelled")

    val activeTaskStatuses2 = remember {
        setOf("running", "in_progress", "verifying", "running_tests", "blocked", "failed", "waiting_capacity")
    }
    var expanded by remember { mutableStateOf(task.status.lowercase() in activeTaskStatuses2) }
    var cancelTaskOpen by remember { mutableStateOf(false) }
    var cancelTaskReason by remember { mutableStateOf("") }
    var editTaskOpen by remember { mutableStateOf(false) }
    var editTaskSpec by remember { mutableStateOf(task.spec.ifBlank { task.task }) }

    // Status glyph — matches PWA status mapping
    val (statusGlyph, statusColor) = when (task.status) {
        "running", "in_progress" -> "▶" to Color(0xFF3B82F6)
        "verifying" -> "⟳" to Color(0xFF8B5CF6)
        "running_tests" -> "🧪" to Color(0xFF06B6D4)
        "complete", "completed" -> "✓" to Color(0xFF10B981)
        "failed" -> "✗" to Color(0xFFEF4444)
        "blocked" -> "⛔" to Color(0xFFF59E0B)
        "waiting_capacity" -> "⏳" to Color(0xFFF59E0B)
        "cancelled", "canceled" -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        "pending" -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val hasBody = task.spec.isNotBlank() || task.filesTouched.isNotEmpty() || task.files.isNotEmpty() ||
        !task.sessionId.isNullOrBlank() || !task.error.isNullOrBlank() || !task.waitReason.isNullOrBlank() ||
        task.verification != null || canRetry || canRequeue || canCancel || canEdit

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                if (task.status == "failed") Color(0xFF7C2D12).copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Header: chevron + status glyph + task ID (code) + title
        // clickable is on the header Row only (not the whole column) — prevents file-pill
        // taps from propagating to the toggle and also avoids gesture conflicts during scroll.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (hasBody) Modifier.clickable { expanded = !expanded } else Modifier,
        ) {
            if (hasBody) {
                Text(
                    if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            if (statusGlyph.isNotEmpty()) {
                Text(
                    statusGlyph,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Text(
                task.id.take(8),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                task.task.ifBlank { task.spec.take(80) },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                task.status.replace('_', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        // PWA inline badges (visible collapsed or expanded): LLM override, ↳ spawn, → child.
        val taskLlm = com.dmzs.datawatchclient.transport.dto.PrdLlmBadge.taskLabel(task)
        val childId = task.childPrdId?.takeIf { it.isNotBlank() }
        if (taskLlm != null || task.spawnPrd || childId != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                taskLlm?.let { PrdMiniPill(it) }
                if (task.spawnPrd) {
                    PrdMiniPill(stringResource(R.string.prd_task_spawn_badge), color = Color(0xFF8B5CF6))
                }
                childId?.let { cid ->
                    Text(
                        stringResource(R.string.prd_task_child_link, cid.take(8)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded) { Column(modifier = Modifier.padding(top = 4.dp)) {
        // Full spec text
        if (task.spec.isNotBlank()) {
            Text(
                task.spec,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        // Planned files (task.files)
        if (task.files.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Files:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                task.files.forEach { f -> FilePill(name = f, color = Color(0xFF3B82F6)) }
            }
        }
        // files_touched chips (B102 parity — uncommitted + non-git files written by task session)
        if (task.filesTouched.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                task.filesTouched.forEach { f ->
                    val resolvedPath = if (f.startsWith("/")) f else "${projectDir?.trimEnd('/').orEmpty()}/$f"
                    FilePill(
                        name = f,
                        color = Color(0xFF10B981),
                        onClick = if (onOpenFile != null && isViewable(f)) {
                            { onOpenFile(resolvedPath) }
                        } else null,
                    )
                }
            }
        }
        // Session chip
        task.sessionId?.takeIf { it.isNotBlank() }?.let { sid ->
            Text(
                "→ ${sid.take(8)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable {
                    SessionsNavChannel.jumpTo(sid)
                }.padding(top = 2.dp),
            )
        }
        // Error panel
        task.error?.takeIf { it.isNotBlank() }?.let { err ->
            Surface(
                color = Color(0xFF7C2D12).copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        task.waitReason?.takeIf { task.status == "waiting_capacity" && it.isNotBlank() }?.let { reason ->
            Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    "⏳ $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
        // Verification summary + issues
        task.verification?.let { v ->
            val ok = v.severity?.lowercase() !in listOf("error", "high", "critical")
            val verifyColor = if (ok) Color(0xFF10B981) else Color(0xFFF59E0B)
            val icon = if (ok) "✓" else "✗"
            v.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    "$icon $summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = verifyColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            v.issues.forEach { issue ->
                Text(
                    "• $issue",
                    style = MaterialTheme.typography.labelSmall,
                    color = verifyColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        // Action buttons row
        val hasActions = (canRetry && onResetTask != null) || (canRequeue && onRequeueTask != null) ||
            (canCancel && onCancelTask != null) || (canEdit && (onEditTask != null || onEditTaskSpecLlm != null)) ||
            (canEdit && onRemoveTask != null)
        if (hasActions) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (canRetry && onResetTask != null) {
                    TextButton(onClick = { onResetTask(prdId, task.id) }) {
                        Text("↺ Retry", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
                    }
                }
                if (canRequeue && onRequeueTask != null) {
                    TextButton(onClick = { onRequeueTask() }) {
                        Text("↺ Re-run", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6))
                    }
                }
                if (canEdit && (onEditTask != null || onEditTaskSpecLlm != null)) {
                    TextButton(onClick = { editTaskSpec = task.spec.ifBlank { task.task }; editTaskOpen = true }) {
                        Text(
                            stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (canEdit && onRemoveTask != null) {
                    TextButton(onClick = { onRemoveTask() }) {
                        Text(
                            "🗑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (canCancel && onCancelTask != null) {
                    TextButton(onClick = { cancelTaskReason = ""; cancelTaskOpen = true }) {
                        Text(
                            stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        } } // end AnimatedVisibility + inner Column
    }

    if (cancelTaskOpen && onCancelTask != null) {
        AlertDialog(
            onDismissRequest = { cancelTaskOpen = false },
            title = { Text(stringResource(R.string.prd_detail_cancel_task_title)) },
            text = {
                OutlinedTextField(
                    value = cancelTaskReason,
                    onValueChange = { cancelTaskReason = it },
                    label = { Text(stringResource(R.string.prd_detail_cancel_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelTask(cancelTaskReason.trim().takeIf { it.isNotBlank() })
                        cancelTaskOpen = false
                    },
                ) { Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { cancelTaskOpen = false }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }

    if (editTaskOpen && onEditTaskSpecLlm != null) {
        // PWA openPRDEditTaskModal: spec + per-task LLM override in one dialog.
        PrdItemLlmDialog(
            title = stringResource(R.string.prd_edit_task_title_prefix) + " " + task.id.take(8),
            hint = stringResource(R.string.prd_task_llm_hint),
            spec = task.spec.ifBlank { task.task },
            currentBackend = task.backend.orEmpty(),
            currentEffort = task.effort.orEmpty(),
            currentModel = task.model.orEmpty(),
            backends = backends,
            modelsFor = modelsFor,
            onDismiss = { editTaskOpen = false },
            onSave = { spec, llm -> onEditTaskSpecLlm(spec, llm) },
        )
    } else if (editTaskOpen && onEditTask != null) {
        AlertDialog(
            onDismissRequest = { editTaskOpen = false },
            title = { Text(stringResource(R.string.prd_detail_edit_task_title)) },
            text = {
                OutlinedTextField(
                    value = editTaskSpec,
                    onValueChange = { editTaskSpec = it },
                    label = { Text(stringResource(R.string.prd_detail_task_spec_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editTaskSpec.isNotBlank()) {
                            onEditTask(editTaskSpec.trim())
                            editTaskOpen = false
                        }
                    },
                    enabled = editTaskSpec.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editTaskOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StoryStatusPill(status: String) {
    val color =
        when (status.lowercase()) {
            "complete" -> Color(0xFF3B82F6)
            "in_progress" -> Color(0xFF10B981)
            "awaiting_approval" -> Color(0xFFF59E0B)
            "rejected" -> Color(0xFFEF4444)
            else -> Color(0xFF94A3B8)
        }
    Box(
        modifier =
            Modifier.background(
                color.copy(alpha = 0.18f),
                RoundedCornerShape(8.dp),
            ).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FilePill(
    name: String,
    color: Color,
    conflict: Boolean = false,
    conflictNote: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val pillColor = if (conflict) Color(0xFFEF4444) else color
    Column {
        Box(
            modifier = Modifier
                .background(pillColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(
                if (conflict) "⚠ $name" else "📝 $name",
                style = MaterialTheme.typography.labelSmall,
                color = if (onClick != null) pillColor else pillColor,
                maxLines = 1,
            )
        }
        conflictNote?.let { note ->
            Text(note, style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStoryDialog(
    story: PrdStoryDto,
    onDismiss: () -> Unit,
    onSave: (newTitle: String?, newDescription: String?) -> Unit,
) {
    var title by remember(story.id) { mutableStateOf(story.title) }
    var description by remember(story.id) { mutableStateOf(story.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_detail_edit_story_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = title, onValueChange = {
                    title = it
                }, label = {
                    Text(stringResource(R.string.prd_detail_title_label))
                }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = {
                    description = it
                }, label = {
                    Text(stringResource(R.string.prd_detail_description_label))
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 6)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(title.takeIf { it != story.title }, description.takeIf { it != story.description.orEmpty() })
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PrdTypeRow(
    prd: PrdDto,
    types: List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto>,
    onSetType: ((String) -> Unit)?,
) {
    if (prd.type == null && types.isEmpty()) return
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.automata_detail_type),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        prd.type?.takeIf { it.isNotBlank() }?.let { t ->
            Text(t, style = MaterialTheme.typography.labelSmall)
        }
        if (onSetType != null && types.isNotEmpty()) {
            TextButton(onClick = { menuOpen = true }) { Text("▾", style = MaterialTheme.typography.labelSmall) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                types.forEach { dt ->
                    DropdownMenuItem(text = { Text(dt.label) }, onClick = {
                        onSetType(dt.id)
                        menuOpen = false
                    })
                }
            }
        }
    }
}

@Composable
private fun PrdGuidedModeRow(
    prd: PrdDto,
    onSetGuidedMode: ((Boolean) -> Unit)?,
) {
    if (!prd.guidedMode && onSetGuidedMode == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.automata_detail_guided_mode),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onSetGuidedMode != null) {
            androidx.compose.material3.Switch(checked = prd.guidedMode, onCheckedChange = onSetGuidedMode)
        } else {
            Text(if (prd.guidedMode) "on" else "off", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PrdContinueOnStoryFailureRow(
    prd: PrdDto,
    onSet: ((Boolean?) -> Unit)?,
) {
    val current = prd.continueOnStoryFailure
    if (current == null && onSet == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Continue on story failure",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onSet != null) {
            // Three-state: null (inherit global) shown as unchecked; true = on; false = off.
            // Tapping toggles true → false → null → true.
            val checked = current == true
            val label = when (current) {
                true -> "on"
                false -> "off"
                null -> "default"
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = { newVal ->
                    onSet(
                        when (current) {
                            null -> true
                            true -> false
                            false -> null
                        },
                    )
                },
            )
        } else {
            Text(
                when (current) { true -> "on"; false -> "off"; null -> "default" },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrdSkillsRow(
    prd: PrdDto,
    onSetSkills: ((List<String>) -> Unit)?,
) {
    var editOpen by remember { mutableStateOf(false) }
    var skillsText by remember(prd.skills) { mutableStateOf(prd.skills.joinToString(", ")) }
    if (prd.skills.isEmpty() && onSetSkills == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.automata_detail_skills),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            prd.skills.forEach { skill ->
                Box(
                    androidx.compose.ui.Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(4.dp),
                    ).padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        skill,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (onSetSkills != null) {
            IconButton(onClick = {
                editOpen = true
            }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        }
    }
    if (editOpen && onSetSkills != null) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.automata_detail_skills)) },
            text = {
                OutlinedTextField(value = skillsText, onValueChange = {
                    skillsText = it
                }, label = {
                    Text(stringResource(R.string.new_prd_skills_label))
                }, singleLine = true, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetSkills(skillsText.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    editOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { editOpen = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * PWA prdSettings "Max concurrent tasks (0 = global default)" (BL370) — numeric
 * 0–32, POST set_concurrency. Hidden when unset and not editable.
 */
@Composable
private fun PrdConcurrencyRow(prd: PrdDto, onSetConcurrency: ((Int) -> Unit)?) {
    if (prd.maxConcurrentTasks <= 0 && onSetConcurrency == null) return
    var editOpen by remember { mutableStateOf(false) }
    var text by remember(prd.maxConcurrentTasks) { mutableStateOf(prd.maxConcurrentTasks.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.prd_settings_concurrency_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            prd.maxConcurrentTasks.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onSetConcurrency != null) {
            IconButton(onClick = { editOpen = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        }
    }
    if (editOpen && onSetConcurrency != null) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.prd_settings_concurrency_label)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.prd_settings_concurrency_hint)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // PWA: parseInt(value || '0') || 0; the input is min 0 / max 32.
                    val n = (text.toIntOrNull() ?: 0).coerceIn(0, 32)
                    if (n != prd.maxConcurrentTasks) onSetConcurrency(n)
                    editOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PrdPriorityRow(prd: PrdDto, onSetPriority: ((Int) -> Unit)?) {
    if (prd.priority == 3 && onSetPriority == null) return
    var editOpen by remember { mutableStateOf(false) }
    var priorityText by remember(prd.priority) { mutableStateOf(prd.priority.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.prd_settings_priority_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            prd.priority.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onSetPriority != null) {
            IconButton(onClick = { editOpen = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        }
    }
    if (editOpen && onSetPriority != null) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.prd_settings_priority_label)) },
            text = {
                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.prd_settings_priority_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    priorityText.toIntOrNull()?.let { onSetPriority(it) }
                    editOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PrdScopeDirsRow(prd: PrdDto, onSetDirs: ((List<String>, List<String>) -> Unit)?) {
    val hasAny = prd.readDirs.isNotEmpty() || prd.writeDirs.isNotEmpty()
    if (!hasAny && onSetDirs == null) return
    var editOpen by remember { mutableStateOf(false) }
    var writeDirsText by remember(prd.writeDirs) { mutableStateOf(prd.writeDirs.joinToString("\n")) }
    var readDirsText by remember(prd.readDirs) { mutableStateOf(prd.readDirs.joinToString("\n")) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.automata_detail_writable_dirs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onSetDirs != null) {
                IconButton(onClick = { editOpen = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                }
            }
        }
        if (prd.writeDirs.isNotEmpty()) {
            prd.writeDirs.forEach { dir ->
                Text(dir, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (prd.readDirs.isNotEmpty()) {
            Text(
                stringResource(R.string.automata_detail_readonly_dirs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            prd.readDirs.forEach { dir ->
                Text(dir, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (editOpen && onSetDirs != null) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.prd_settings_write_dirs_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.prd_settings_dirs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = writeDirsText, onValueChange = { writeDirsText = it },
                        label = { Text(stringResource(R.string.prd_settings_write_dirs_label)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    )
                    OutlinedTextField(
                        value = readDirsText, onValueChange = { readDirsText = it },
                        label = { Text(stringResource(R.string.prd_settings_read_dirs_label)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val writeDirs = writeDirsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val readDirs = readDirsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    onSetDirs(readDirs, writeDirs)
                    editOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFilesDialog(
    story: PrdStoryDto,
    onDismiss: () -> Unit,
    onSave: (files: List<String>) -> Unit,
) {
    var text by remember(story.id) { mutableStateOf(story.files.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Files for ${story.title.ifBlank { story.id }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.prd_detail_files_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = text, onValueChange = {
                    text = it
                }, label = {
                    Text(stringResource(R.string.prd_detail_files_label))
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 8)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(50))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Compact inline card showing live CPU/RAM/GPU for the in-progress session — matches PWA PRD detail header. */
@Composable
private fun PrdRunningSessionCard(sessionId: String, taskName: String) {
    val vm: SessionStatsViewModel =
        viewModel(
            factory = viewModelFactory { initializer { SessionStatsViewModel(sessionId) } },
            key = "prd-compact-$sessionId",
        )
    val state by vm.state.collectAsState()
    androidx.compose.runtime.DisposableEffect(sessionId) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    val envelope = state.envelope
    val detail = state.computeNodeDetail

    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Badge + session short-id
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3B82F6).copy(alpha = 0.18f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("running", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6))
                }
                Text(
                    sessionId.take(8),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Task name
            if (taskName.isNotBlank()) {
                Text(
                    taskName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // CPU row
            val cpuPct = envelope?.cpuPct ?: 0.0
            if (envelope != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CPU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.1f%%".format(cpuPct), style = MaterialTheme.typography.labelSmall)
                }
            }
            // RAM bar — system RAM from compute node
            detail?.mem?.let { mem ->
                if (mem.totalBytes > 0) {
                    val usedGb = mem.usedBytes / 1_073_741_824.0
                    val totalGb = mem.totalBytes / 1_073_741_824.0
                    val fraction = (mem.usedBytes.toFloat() / mem.totalBytes).coerceIn(0f, 1f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f GB / %.1f GB".format(usedGb, totalGb), style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF8B5CF6),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            // GPU bar — util + temp + power from remote compute node
            detail?.gpu?.firstOrNull()?.let { gpu ->
                val gpuFraction = (gpu.utilPct / 100.0).toFloat().coerceIn(0f, 1f)
                val gpuColor = when {
                    gpu.utilPct >= 90 -> Color(0xFFEF4444)
                    gpu.utilPct >= 70 -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }
                val gpuLabel = buildString {
                    append("%.0f%%".format(gpu.utilPct))
                    if (gpu.tempC > 0) append(" ${gpu.tempC.toInt()}°C")
                    if (gpu.powerW > 0) append(" ${gpu.powerW.toInt()}W")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GPU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(gpuLabel, style = MaterialTheme.typography.labelSmall, color = gpuColor)
                }
                LinearProgressIndicator(
                    progress = { gpuFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = gpuColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ── Active session card — PWA _loadPRDActiveSessionCard parity ───────────────

@Composable
private fun PrdActiveSessionsCard(
    prdStatus: String,
    activeSessions: List<com.dmzs.datawatchclient.ui.autonomous.AutonomousViewModel.PrdActiveSessionInfo>,
    computeNodeDetail: com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto?,
    computeNodeRef: String?,
    /** The live planning stream card already shows "Decomposing Automaton…". */
    liveStreamShown: Boolean = false,
) {
    val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2

    @Composable
    fun SessionRow(info: com.dmzs.datawatchclient.ui.autonomous.AutonomousViewModel.PrdActiveSessionInfo) {
        val sess = info.session
        val board = info.board
        val stateStr = sess.state.name.lowercase()
        val stateColor = when (stateStr) {
            "running" -> Color(0xFF3B82F6)
            "waiting", "waiting_input" -> Color(0xFFF59E0B)
            "completed", "complete" -> Color(0xFF10B981)
            "failed", "error", "killed" -> Color(0xFFEF4444)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val hookColor = when (board?.hookHealth) {
            "alive" -> Color(0xFF22C55E)
            "stale" -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(color = accent2, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height))
                }
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                )
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .background(stateColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(0.5.dp, stateColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(stateStr, style = MaterialTheme.typography.labelSmall, color = stateColor)
                }
                Text(
                    sess.fullId.takeLast(8),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sess.name?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                // Hook health dot
                Text("●", style = MaterialTheme.typography.labelSmall, color = hookColor)
                // Test stats
                board?.tests?.let { t ->
                    if (t.passing > 0 || t.failing > 0) {
                        Text(
                            "${t.passing}✓/${t.failing}✗",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (t.failing > 0) Color(0xFFEF4444) else Color(0xFF22C55E),
                        )
                    }
                }
            }
            // Story/task context
            val ctxParts = listOfNotNull(info.storyTitle, info.taskTitle)
            if (ctxParts.isNotEmpty()) {
                Text(
                    ctxParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            // Last event / focus
            board?.lastEvent?.let { ev ->
                val focus = buildString {
                    ev.event?.let { append(it) }
                    ev.tool?.let { append(" · $it") }
                }
                if (focus.isNotBlank()) {
                    Text(
                        focus,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }

    when {
        activeSessions.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeSessions.forEach { info -> SessionRow(info) }
                // Resource bars (from compute node) below the session rows
                if (computeNodeDetail != null) {
                    PrdActiveComputeCard(
                        status = prdStatus,
                        computeNodeDetail = computeNodeDetail,
                        computeNodeRef = computeNodeRef,
                        showStatusLine = false,
                    )
                }
            }
        }
        prdStatus in setOf("planning", "decomposing") -> {
            // One status line only: the live stream card owns it when shown;
            // the compute card then renders just the node + resource bars.
            if (!liveStreamShown || computeNodeDetail != null) {
                PrdActiveComputeCard(
                    status = prdStatus,
                    computeNodeDetail = computeNodeDetail,
                    computeNodeRef = computeNodeRef,
                    showStatusLine = !liveStreamShown,
                )
            }
        }
        prdStatus == "running" -> {
            // Stuck: running but no active session found
            Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "⚠ No active session record",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B),
                    )
                    Text(
                        "Status looks active but no spawned session was found. The LLM call may have failed silently.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> {
            PrdActiveComputeCard(status = prdStatus, computeNodeDetail = computeNodeDetail, computeNodeRef = computeNodeRef)
        }
    }
}

// ── Capacity card — PWA prdCapacityCard parity ────────────────────────────────

@Composable
private fun PrdCapacityCard(capacity: com.dmzs.datawatchclient.transport.dto.CapacityResponseDto) {
    val poolsWithLimit = capacity.pools.filter { it.limit > 0 }
    val poolsUnlimited = capacity.pools.filter { it.limit <= 0 && (it.held + it.external) > 0 }
    val idleNodes = capacity.pools.filter { it.name != "host" && it.limit <= 0 && (it.held + it.external) == 0 }
    if (poolsWithLimit.isEmpty() && poolsUnlimited.isEmpty() && idleNodes.isEmpty() && capacity.waiting.isEmpty()) return

    fun poolLabel(name: String): String = when {
        name == "host" -> "Host sessions (all tasks + interactive)"
        name.startsWith("node:") -> "Compute node: ${name.removePrefix("node:")}"
        name.startsWith("llm:") -> "LLM: ${name.removePrefix("llm:")}"
        else -> name
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Capacity",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            poolsWithLimit.forEach { pool ->
                val used = pool.held + pool.external
                val fraction = (used.toFloat() / pool.limit).coerceIn(0f, 1f)
                val pct = (fraction * 100).toInt()
                val barColor = when {
                    pct >= 100 -> Color(0xFFEF4444)
                    pct >= 75 -> Color(0xFFF59E0B)
                    else -> Color(0xFF22C55E)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(poolLabel(pool.name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$used / ${pool.limit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            poolsUnlimited.forEach { pool ->
                val used = pool.held + pool.external
                Text(
                    "${poolLabel(pool.name)}: $used · no limit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            idleNodes.forEach { pool ->
                Text(
                    "${poolLabel(pool.name)} · no cap configured",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (capacity.waiting.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    "Waiting (${capacity.waiting.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                capacity.waiting.forEach { w ->
                    Text(
                        "⏳ ${w.holder.take(8)} · ${w.reason.orEmpty().take(60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── BL387 Memory stats tile ──────────────────────────────────────────────────

@Composable
private fun PrdMemoryStatsTile(prd: PrdDto) {
    val counts = listOfNotNull(
        prd.prdSharedCount?.takeIf { it > 0 }?.let { stringResource(R.string.memory_scope_prd_shared) to it },
        prd.storySharedCount?.takeIf { it > 0 }?.let { stringResource(R.string.memory_scope_story_shared) to it },
        prd.sessionLocalCount?.takeIf { it > 0 }?.let { stringResource(R.string.memory_scope_session_local) to it },
    )
    if (counts.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.prd_memory_stats),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                counts.forEach { (label, count) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── BL386 Memory report section ──────────────────────────────────────────────

@Composable
private fun PrdMemoryReportSection(
    report: String?,
    loading: Boolean,
    onFetch: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                if (report != null) expanded = !expanded else onFetch()
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.prd_memory_report),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (expanded) stringResource(R.string.prd_memory_report_hide)
                    else stringResource(R.string.prd_memory_report_show),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded && report != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    report,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── BL385/#183 Memory recall search card ─────────────────────────────────────

@Composable
private fun PrdMemoryRecallCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<com.dmzs.datawatchclient.transport.dto.ScopedMemoryEntryDto>,
    loading: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.prd_memory_recall_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                label = { Text(stringResource(R.string.prd_memory_recall)) },
            )
            TextButton(onClick = onSearch, enabled = query.isNotBlank() && !loading) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.prd_memory_recall_search))
            }
        }
        if (!loading && query.isNotBlank() && results.isEmpty()) {
            Text(
                stringResource(R.string.prd_memory_recall_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.forEach { entry ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.scope?.let { scope ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    scope.replace("-", "‑"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        entry.role?.let { role ->
                            Text(
                                role,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        entry.score?.let { score ->
                            Text(
                                "%.2f".format(score),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Compute resource helpers ────────────────────────────────────────────────

private fun fmtBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "${bytes / 1_048_576} MB"
    bytes >= 1_024L -> "${bytes / 1_024} KB"
    else -> "$bytes B"
}

@Composable
private fun ComputeChip(label: String, color: Color, accent: Boolean = false) {
    val borderColor = if (accent) Color(0xFF3B82F6).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ── Inline PRD active compute card (PWA _loadPRDActiveSessionCard parity) ───

@Composable
private fun PrdActiveComputeCard(
    status: String,
    computeNodeDetail: com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto?,
    computeNodeRef: String?,
    showStatusLine: Boolean = true,
) {
    val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = accent2,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
            )
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showStatusLine) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Text(
                        when (status) {
                            "planning", "decomposing" -> stringResource(R.string.decompose_in_progress)
                            "running" -> "Running…"
                            else -> status
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (computeNodeRef != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        computeNodeRef,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            if (computeNodeDetail != null) {
                if (showStatusLine || computeNodeRef != null) Spacer(Modifier.height(8.dp))
                val cpuPct = (computeNodeDetail.cpu?.pct ?: computeNodeDetail.cpuPct ?: 0.0).toFloat()
                if (cpuPct > 0f) {
                    PrdResourceBar(
                        label = "CPU",
                        valuePct = cpuPct,
                        valueLabel = "${cpuPct.toInt()}%",
                        color = when {
                            cpuPct >= 90f -> Color(0xFFEF4444)
                            cpuPct >= 70f -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        },
                    )
                }
                val memUsed = computeNodeDetail.mem?.usedBytes ?: 0L
                val memTotal = computeNodeDetail.mem?.totalBytes ?: 0L
                if (memTotal > 0L) {
                    val memPct = (memUsed * 100L / memTotal).toFloat()
                    PrdResourceBar(
                        label = "RAM",
                        valuePct = memPct,
                        valueLabel = "${fmtBytes(memUsed)} / ${fmtBytes(memTotal)}",
                        color = if (memPct >= 85f) Color(0xFFEF4444) else Color(0xFF60A5FA),
                    )
                }
                computeNodeDetail.gpu.forEachIndexed { gi, g ->
                    val gpuLabel = if (computeNodeDetail.gpu.size > 1) "GPU $gi" else (g.name.takeIf { it.isNotBlank() } ?: "GPU")
                    if (g.utilPct > 0) {
                        val utilPct = g.utilPct.toFloat()
                        val extraLabel = buildString {
                            append("${utilPct.toInt()}%")
                            if (g.tempC > 0) append("  ${g.tempC.toInt()}°C")
                            if (g.powerW > 0) append("  ${g.powerW.toInt()}W")
                        }
                        PrdResourceBar(
                            label = "$gpuLabel util",
                            valuePct = utilPct,
                            valueLabel = extraLabel,
                            color = if (utilPct >= 80f) Color(0xFFEF4444) else Color(0xFF60A5FA),
                        )
                    }
                    if (g.memTotalBytes > 0L) {
                        val vramPct = (g.memUsedBytes * 100L / g.memTotalBytes).toFloat()
                        PrdResourceBar(
                            label = "$gpuLabel VRAM",
                            valuePct = vramPct,
                            valueLabel = "${fmtBytes(g.memUsedBytes)} / ${fmtBytes(g.memTotalBytes)}",
                            color = if (vramPct >= 85f) Color(0xFFEF4444) else Color(0xFF60A5FA),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrdResourceBar(
    label: String,
    valuePct: Float,
    valueLabel: String,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { (valuePct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
    }
}

/** Section title for the Graph / Progress cards on the Overview tab (D24a). */
@Composable
private fun PrdOverviewCardTitle(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/**
 * Parity D24a — PWA `_renderDetailRulesTab`: help text, ▶ Run Rules Check
 * (POST /api/autonomous/prds/{id}/scan/rules) and the last result.
 */
@Composable
private fun PrdRulesTab(
    rulesResult: String?,
    rulesLoading: Boolean,
    onRunRules: (() -> Unit)?,
) {
    Text(
        stringResource(R.string.prd_rules_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (onRunRules != null) {
        androidx.compose.material3.OutlinedButton(onClick = onRunRules, enabled = !rulesLoading) {
            Text("▶ " + stringResource(R.string.rules_run), style = MaterialTheme.typography.labelSmall)
        }
    }
    when {
        rulesLoading -> Text("…", style = MaterialTheme.typography.bodySmall)
        rulesResult.isNullOrBlank() ->
            Text(
                stringResource(R.string.rules_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        else ->
            Surface(
                color = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.bg2,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    rulesResult,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(8.dp),
                )
            }
    }
}
