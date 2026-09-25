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
import androidx.compose.ui.text.font.FontFamily
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

private val EFFORT_OPTIONS = listOf("", "low", "medium", "high", "max", "quick", "normal", "thorough")

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
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onRequestRevision: (note: String) -> Unit,
    onEditPrd: (title: String?, spec: String?, permissionMode: String?) -> Unit,
    onDelete: () -> Unit,
    onEditStory: (storyId: String, newTitle: String?, newDescription: String?) -> Unit,
    onEditFiles: (storyId: String, files: List<String>) -> Unit,
    onCancelStory: ((storyId: String, reason: String?) -> Unit)? = null,
    onCancelTask: ((taskId: String, reason: String?) -> Unit)? = null,
    onRequeueTask: ((taskId: String) -> Unit)? = null,
    onEditTask: ((taskId: String, newSpec: String) -> Unit)? = null,
    automataTypes: List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto> = emptyList(),
    onSetType: ((String) -> Unit)? = null,
    onSetGuidedMode: ((Boolean) -> Unit)? = null,
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
) {
    BackHandler(enabled = true, onBack = onDismiss)

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
    var approveOpen by remember { mutableStateOf(false) }
    var approveNote by remember { mutableStateOf("") }
    // Memory strategy on delete (#175)
    var memoryStrategy by remember { mutableStateOf("keep") }
    var memoryArchiveRoles by remember { mutableStateOf("") }
    var memoryArchiveScope by remember { mutableStateOf("project-shared") }
    // Memory recall (#183)
    var recallQuery by remember { mutableStateOf("") }

    val showProgressTab = status == "running" || status == "decomposing"
    val tabs =
        buildList {
            add(stringResource(R.string.prd_tab_overview))
            add(stringResource(R.string.prd_tab_stories))
            add(stringResource(R.string.prd_tab_decisions))
            add(stringResource(R.string.prd_tab_graph))
            if (showProgressTab) add(stringResource(R.string.automata_sg_progress))
        }

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
                                Text(
                                    displaySpec,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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

                    // Lifecycle strip
                    LifecycleStrip(status)

                    // Inline compute stats card — visible for planning/decomposing/running (PWA parity)
                    if (status in setOf("planning", "decomposing", "running")) {
                        Spacer(Modifier.height(8.dp))
                        PrdActiveComputeCard(
                            status = status,
                            computeNodeDetail = prdComputeNodeDetail,
                            computeNodeRef = prdComputeNodeRef,
                        )
                    }

                    // Compact running-session card (session link) — shown when a task is active
                    val activeTask = prd.stories.flatMap { it.tasks }.firstOrNull { it.status == "in_progress" }
                    val activeSessionId = activeTask?.sessionId
                    if (activeSessionId != null) {
                        Spacer(Modifier.height(6.dp))
                        PrdRunningSessionCard(sessionId = activeSessionId, taskName = activeTask.task)
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
                                    onClick = {
                                        onDecompose()
                                        onDismiss()
                                    },
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
                    when (selectedTab) {
                        0 -> {
                            PrdTypeRow(prd, automataTypes, onSetType)
                            PrdGuidedModeRow(prd, onSetGuidedMode)
                            PrdSkillsRow(prd, onSetSkills)
                            prd.spec?.takeIf { it.isNotBlank() }?.let { spec ->
                                Text(
                                    spec,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        1 -> {
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
                                            projectDir = prd.projectDir,
                                            onOpenFile = onOpenFile,
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
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
                        3 -> {
                            // Graph tab — orchestrator DAG (#184)
                            when {
                                prdGraphLoading -> {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = androidx.compose.ui.Modifier
                                            .padding(24.dp)
                                            .align(androidx.compose.ui.Alignment.CenterHorizontally),
                                    )
                                    Text(
                                        stringResource(R.string.prd_tab_graph_loading),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        4 -> {
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
                    }
                }
            } // end tab content item
        } // end LazyColumn
    }

    // ── Sub-dialogs ────────────────────────────────────────────────────────

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
}

@Composable
private fun PrdStatusBadge(status: String) {
    val color = prdStatusColor(status)
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            status.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
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

    // Planning backend must be ollama or openwebui (headless /api/ask only)
    val planningBackends = backends

    // Model list for the currently selected execution backend (empty = show free-text)
    val execModels = when {
        backend.contains("ollama", ignoreCase = true) -> ollamaModels
        backend.contains("openwebui", ignoreCase = true) -> openWebUiModels
        else -> emptyList()
    }
    // Model list for the planning backend
    val planModels = when {
        decompositionProfile.contains("ollama", ignoreCase = true) -> ollamaModels
        decompositionProfile.contains("openwebui", ignoreCase = true) -> openWebUiModels
        else -> emptyList()
    }

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
                // Execution model — dropdown for ollama/openwebui, free-text otherwise (BL-AT-2)
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
                            execModels.forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { model = m; modelMenuOpen = false })
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
                    // Planning model — dropdown or free-text depending on planning backend (BL-AT-4)
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
                                planModels.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = { decompositionModel = m; planningModelMenuOpen = false })
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
    projectDir: String? = null,
    onOpenFile: ((path: String) -> Unit)? = null,
) {
    val activeStoryStatuses = remember {
        setOf("running", "in_progress", "active", "awaiting_approval", "verifying", "running_tests")
    }
    var expanded by remember { mutableStateOf(story.status.lowercase() in activeStoryStatuses) }
    var cancelStoryOpen by remember { mutableStateOf(false) }
    var cancelStoryReason by remember { mutableStateOf("") }

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

        // Expandable body: description + files + edit buttons
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
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
                        }
                    }
                }
                // Read-only extras: progress + aggregated files_touched + session link (PWA parity)
                if (!canEdit && story.tasks.isNotEmpty()) {
                    val total = story.tasks.size
                    val done = story.tasks.count { it.status in setOf("complete", "completed", "done") }
                    val activeCount = story.tasks.count { it.status in setOf("verifying", "running_tests") }
                    val pct = if (total > 0) done * 100 / total else 0
                    val progressColor = when {
                        pct == 100 -> Color(0xFF10B981)
                        activeCount > 0 -> Color(0xFF3B82F6)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "$done/$total · $pct%${if (activeCount > 0) " · ⟳ $activeCount active" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 2.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        color = progressColor,
                    )
                    // Aggregated files_touched from all tasks (deduped, capped at 12)
                    val allTouched = story.tasks.flatMap { it.filesTouched }.distinct().take(12)
                    if (allTouched.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "✅ Touched:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            allTouched.forEach { f -> FilePill(name = f, color = Color(0xFF10B981)) }
                        }
                    }
                    // Session link — first task with non-empty session_id
                    val firstSession = story.tasks.firstOrNull { !it.sessionId.isNullOrBlank() }?.sessionId
                    firstSession?.let { sid ->
                        Text(
                            "→ ${sid.take(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { SessionsNavChannel.jumpTo(sid) }.padding(top = 2.dp, bottom = 2.dp),
                        )
                    }
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
                            projectDir = projectDir,
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            }
        }
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
    projectDir: String? = null,
    onOpenFile: ((path: String) -> Unit)? = null,
) {
    val canRetry = (task.status == "failed" || task.status == "blocked") && prdStatus == "running"
    val canRequeue = task.status in setOf("complete", "cancelled")
    val canCancel = task.status !in setOf("complete", "cancelled", "failed")
    val canEdit = prdStatus in setOf("needs_review", "revisions_asked")

    val activeTaskStatuses2 = remember {
        setOf("running", "in_progress", "verifying", "running_tests", "blocked", "failed")
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
        "cancelled", "canceled" -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        "pending" -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val hasBody = task.spec.isNotBlank() || task.filesTouched.isNotEmpty() || task.files.isNotEmpty() ||
        !task.sessionId.isNullOrBlank() || !task.error.isNullOrBlank() ||
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
            (canCancel && onCancelTask != null) || (canEdit && onEditTask != null)
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
                if (canEdit && onEditTask != null) {
                    TextButton(onClick = { editTaskSpec = task.task; editTaskOpen = true }) {
                        Text(
                            stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.labelSmall,
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

    if (editTaskOpen && onEditTask != null) {
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
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Text(
                    when (status) {
                        "planning", "decomposing" -> "Decomposing PRD..."
                        "running" -> "Running..."
                        else -> status
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Spacer(Modifier.height(8.dp))
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
