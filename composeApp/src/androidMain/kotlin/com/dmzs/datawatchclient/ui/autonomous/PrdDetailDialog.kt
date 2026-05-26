package com.dmzs.datawatchclient.ui.autonomous

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
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

private val EFFORT_OPTIONS = listOf("", "low", "medium", "high", "max", "quick", "normal", "thorough")

/**
 * PRD detail — full-screen Scaffold with 3 tabs: Overview, Stories, Decisions.
 * Replaces the AlertDialog to give the content room to breathe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PrdDetailDialog(
    prd: PrdDto,
    backends: List<String> = emptyList(),
    permissionModes: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onDecompose: () -> Unit,
    onSetLlm: (backend: String, effort: String, model: String) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onRequestRevision: (note: String) -> Unit,
    onEditPrd: (title: String?, spec: String?, permissionMode: String?) -> Unit,
    onDelete: () -> Unit,
    onEditStory: (storyId: String, newTitle: String?, newDescription: String?) -> Unit,
    onEditFiles: (storyId: String, files: List<String>) -> Unit,
    automataTypes: List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto> = emptyList(),
    onSetType: ((String) -> Unit)? = null,
    onSetGuidedMode: ((Boolean) -> Unit)? = null,
    onSetSkills: ((List<String>) -> Unit)? = null,
    onCloneTemplate: (() -> Unit)? = null,
) {
    BackHandler(enabled = true, onBack = onDismiss)

    val status = prd.status
    val canReview = status == "needs_review" || status == "revisions_asked"
    val canEdit = status != "running"
    val isCancellable = status in setOf("running", "draft", "approved", "needs_review", "revisions_asked", "decomposing", "planning")

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

    val tabs = listOf(
        stringResource(R.string.prd_tab_overview),
        stringResource(R.string.prd_tab_stories),
        stringResource(R.string.prd_tab_decisions),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = { editPrdOpen = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                    IconButton(onClick = { deleteConfirmOpen = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── Header section — intrinsic height, stays fixed at top ─────
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

                // Spec snippet
                prd.spec?.takeIf { it.isNotBlank() }?.let { fullSpec ->
                    val snippet = if (fullSpec.length > 280) fullSpec.take(280) + "…" else fullSpec
                    val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2
                    Box(
                        modifier = Modifier
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
                        Text(
                            snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Lifecycle strip
                LifecycleStrip(status)

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
                                onClick = { onApprove(); onDismiss() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.18f),
                                    contentColor = Color(0xFF10B981),
                                ),
                            ) { Text(stringResource(R.string.action_approve)) }
                        }
                        if (status == "approved") {
                            FilledTonalButton(
                                onClick = { onRun(); onDismiss() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF3B82F6).copy(alpha = 0.18f),
                                    contentColor = Color(0xFF3B82F6),
                                ),
                            ) { Text(stringResource(R.string.prd_detail_run)) }
                        }
                        if (isCancellable) {
                            FilledTonalButton(
                                onClick = { onCancel(); onDismiss() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                        if (status == "draft" || status == "revisions_asked") {
                            FilledTonalButton(
                                onClick = { onDecompose(); onDismiss() },
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
                            Text(stringResource(R.string.action_reject), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { reviseOpen = true }) {
                            Text(stringResource(R.string.prd_detail_revise), color = Color(0xFFF59E0B), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (status != "running" && status != "completed") {
                        TextButton(onClick = { llmOpen = true }) {
                            Text(stringResource(R.string.prd_detail_llm), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(onClick = { graphOpen = true }) {
                        Text(stringResource(R.string.prd_detail_graph), style = MaterialTheme.typography.labelSmall)
                    }
                    if (onCloneTemplate != null && status in setOf("completed", "done", "approved", "cancelled", "failed")) {
                        TextButton(onClick = { onCloneTemplate(); onDismiss() }) {
                            Text(stringResource(R.string.prd_btn_clone_template), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Tab strip ──────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // ── Tab content — fills remaining space, scrolls independently ─
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
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
                                spec.take(240),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                            )
                        }
                    }
                    1 -> {
                        val conflicts = buildMap<String, List<String>> {
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
                                StoryRow(
                                    story = story,
                                    canEdit = canEdit,
                                    onEdit = { editingStory = story },
                                    onEditFiles = { editingFilesFor = story },
                                    conflicts = conflicts,
                                )
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
                                val label = buildString {
                                    decision.kind?.let { append("[$it] ") }
                                    append(decision.note ?: "")
                                    decision.actor?.let { append(" ($it)") }
                                }
                                Text("• $label", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Sub-dialogs ────────────────────────────────────────────────────────

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
            dismissButton = { TextButton(onClick = { rejectOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
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
            dismissButton = { TextButton(onClick = { reviseOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (llmOpen) {
        LlmOverrideDialog(
            currentBackend = prd.backend.orEmpty(),
            currentEffort = prd.effort.orEmpty(),
            currentModel = prd.model.orEmpty(),
            backends = backends,
            onDismiss = { llmOpen = false },
            onSave = { b, e, m -> onSetLlm(b, e, m); llmOpen = false },
        )
    }

    if (editPrdOpen) {
        EditPrdDialog(
            currentTitle = prd.title.orEmpty(),
            currentSpec = prd.spec.orEmpty(),
            currentPermissionMode = prd.permissionMode.orEmpty(),
            permissionModes = permissionModes,
            onDismiss = { editPrdOpen = false },
            onSave = { title, spec, pm -> onEditPrd(title, spec, pm); editPrdOpen = false },
        )
    }

    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            title = { Text(stringResource(R.string.prd_detail_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.prd_detail_delete_body, prd.title ?: prd.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); deleteConfirmOpen = false; onDismiss() },
                ) { Text(stringResource(R.string.action_delete), color = Color(0xFF7C2D12)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
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
            onSave = { files -> onEditFiles(story.id, files); editingFilesFor = null },
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
        modifier = Modifier
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
    backends: List<String>,
    onDismiss: () -> Unit,
    onSave: (backend: String, effort: String, model: String) -> Unit,
) {
    var backend by remember { mutableStateOf(currentBackend) }
    var effort by remember { mutableStateOf(currentEffort) }
    var model by remember { mutableStateOf(currentModel) }
    var backendMenuOpen by remember { mutableStateOf(false) }
    var effortMenuOpen by remember { mutableStateOf(false) }

    val inheritLabel = stringResource(R.string.new_prd_inherit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_detail_set_llm_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
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
                        DropdownMenuItem(text = { Text(inheritLabel) }, onClick = { backend = ""; backendMenuOpen = false })
                        backends.forEach { b ->
                            DropdownMenuItem(text = { Text(b) }, onClick = { backend = b; backendMenuOpen = false })
                        }
                    }
                }
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
                        EFFORT_OPTIONS.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(if (e.isEmpty()) inheritLabel else e) },
                                onClick = { effort = e; effortMenuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(backend, effort, model) }) { Text(stringResource(R.string.action_save)) } },
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
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.prd_detail_title_label)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spec, onValueChange = { spec = it },
                    label = { Text(stringResource(R.string.prd_detail_spec_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    maxLines = 8,
                )
                if (permissionModes.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = permissionMode.ifEmpty { inheritLabel },
                            onValueChange = {},
                            label = { Text(stringResource(R.string.new_prd_permission_mode_label)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { pmMenuOpen = !pmMenuOpen }) { Text("▾") }
                            },
                        )
                        DropdownMenu(expanded = pmMenuOpen, onDismissRequest = { pmMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(inheritLabel) },
                                onClick = { permissionMode = ""; pmMenuOpen = false },
                            )
                            permissionModes.forEach { pm ->
                                DropdownMenuItem(
                                    text = { Text(pm) },
                                    onClick = { permissionMode = pm; pmMenuOpen = false },
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
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Always-visible header row: title + chevron + status pill
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            StoryStatusPill(story.status)
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
                            FilePill(name = f, color = Color(0xFF3B82F6), conflict = others.isNotEmpty(), conflictNote = if (others.isNotEmpty()) "also in ${others.joinToString(", ")}" else null)
                        }
                        story.filesTouched.forEach { f -> FilePill(f, color = Color(0xFF10B981)) }
                    }
                    if (canEdit) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(" ${stringResource(R.string.action_edit)}", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = onEditFiles) {
                                Text(stringResource(R.string.prd_detail_edit_files), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryStatusPill(status: String) {
    val color = when (status.lowercase()) {
        "complete" -> Color(0xFF3B82F6)
        "in_progress" -> Color(0xFF10B981)
        "awaiting_approval" -> Color(0xFFF59E0B)
        "rejected" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }
    Box(modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun FilePill(name: String, color: Color, conflict: Boolean = false, conflictNote: String? = null) {
    val pillColor = if (conflict) Color(0xFFEF4444) else color
    Column {
        Box(modifier = Modifier.background(pillColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text(if (conflict) "⚠ $name" else "📝 $name", style = MaterialTheme.typography.labelSmall, color = pillColor, maxLines = 1)
        }
        conflictNote?.let { note -> Text(note, style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), maxLines = 1) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStoryDialog(story: PrdStoryDto, onDismiss: () -> Unit, onSave: (newTitle: String?, newDescription: String?) -> Unit) {
    var title by remember(story.id) { mutableStateOf(story.title) }
    var description by remember(story.id) { mutableStateOf(story.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prd_detail_edit_story_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.prd_detail_title_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.prd_detail_description_label)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 6)
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
        Text(stringResource(R.string.automata_detail_type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        prd.type?.takeIf { it.isNotBlank() }?.let { t ->
            Text(t, style = MaterialTheme.typography.labelSmall)
        }
        if (onSetType != null && types.isNotEmpty()) {
            TextButton(onClick = { menuOpen = true }) { Text("▾", style = MaterialTheme.typography.labelSmall) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                types.forEach { dt ->
                    DropdownMenuItem(text = { Text(dt.label) }, onClick = { onSetType(dt.id); menuOpen = false })
                }
            }
        }
    }
}

@Composable
private fun PrdGuidedModeRow(prd: PrdDto, onSetGuidedMode: ((Boolean) -> Unit)?) {
    if (!prd.guidedMode && onSetGuidedMode == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.automata_detail_guided_mode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onSetGuidedMode != null) {
            androidx.compose.material3.Switch(checked = prd.guidedMode, onCheckedChange = onSetGuidedMode)
        } else {
            Text(if (prd.guidedMode) "on" else "off", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrdSkillsRow(prd: PrdDto, onSetSkills: ((List<String>) -> Unit)?) {
    var editOpen by remember { mutableStateOf(false) }
    var skillsText by remember(prd.skills) { mutableStateOf(prd.skills.joinToString(", ")) }
    if (prd.skills.isEmpty() && onSetSkills == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.automata_detail_skills), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            prd.skills.forEach { skill ->
                Box(androidx.compose.ui.Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(skill, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        if (onSetSkills != null) {
            IconButton(onClick = { editOpen = true }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        }
    }
    if (editOpen && onSetSkills != null) {
        AlertDialog(
            onDismissRequest = { editOpen = false },
            title = { Text(stringResource(R.string.automata_detail_skills)) },
            text = {
                OutlinedTextField(value = skillsText, onValueChange = { skillsText = it }, label = { Text(stringResource(R.string.new_prd_skills_label)) }, singleLine = true, modifier = androidx.compose.ui.Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetSkills(skillsText.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    editOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFilesDialog(story: PrdStoryDto, onDismiss: () -> Unit, onSave: (files: List<String>) -> Unit) {
    var text by remember(story.id) { mutableStateOf(story.files.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Files for ${story.title.ifBlank { story.id }}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.prd_detail_files_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.prd_detail_files_label)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 8)
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
