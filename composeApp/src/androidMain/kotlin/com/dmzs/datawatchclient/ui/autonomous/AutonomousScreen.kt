package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.theme.pwaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AutonomousScreen(
    vm: AutonomousViewModel = viewModel(),
    tmplVm: TemplatesViewModel = viewModel(),
    alertsVm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val pinnedIds by vm.pinnedAutomataIds.collectAsState()
    val reachable by vm.reachable.collectAsState()
    val lastProbeEpochMs by vm.lastProbeEpochMs.collectAsState()
    val alertsState by alertsVm.state.collectAsState()
    var newOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var includeTemplates by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var openPrdId by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var tmplCreateOpen by remember { mutableStateOf(false) }
    var identityWizardOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh(); vm.loadAutomataTypes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AutonomousServerPickerTitle(
                        active = state.activeProfile,
                        allMode = state.allServersMode,
                        open = pickerOpen,
                        onToggle = { pickerOpen = !pickerOpen },
                        onDismiss = { pickerOpen = false },
                        profiles = state.allProfiles,
                        onSelectAll = { vm.selectAllServers(); pickerOpen = false },
                        onSelect = { vm.selectProfile(it); pickerOpen = false },
                    )
                },
                actions = {
                    // Robot icon FIRST (left of all) — screen-specific identity shortcut
                    IconButton(onClick = { identityWizardOpen = true }) {
                        Text("🤖", style = MaterialTheme.typography.titleMedium)
                    }
                    DocsLinkAction("datawatch-definitions.md#automata")
                    if (currentTab == 0) {
                        IconButton(onClick = { filterOpen = !filterOpen }) {
                            Icon(
                                if (filterOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (filterOpen) stringResource(R.string.autonomous_filter_close) else stringResource(R.string.autonomous_filter_open),
                            )
                        }
                    }
                    AlertsBellAction(alertsBadge = alertsState.watchedAlertCount)
                    if (!state.allServersMode && state.activeProfile != null) {
                        ReachabilityDot(
                            reachable = reachable,
                            lastProbeEpochMs = lastProbeEpochMs,
                            onRetry = vm::refresh,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = openPrdId == null && state.selectedIds.isEmpty()) {
                FloatingActionButton(
                    onClick = { if (currentTab == 1) tmplCreateOpen = true else newOpen = true },
                    modifier = Modifier.offset(y = 36.dp).padding(end = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.autonomous_fab_new))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = currentTab) {
                    Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text(stringResource(R.string.autonomous_tab_prds)) })
                    Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text(stringResource(R.string.autonomous_tab_templates)) })
                }
                when (currentTab) {
                    0 -> PrdsBody(state, pinnedIds, filterOpen, includeTemplates, statusFilter, onOpenPrd = { openPrdId = it }, onStatusFilter = { statusFilter = it }, onIncludeTemplates = { includeTemplates = it }, onToggleSelect = { vm.toggleSelection(it) }, onTogglePin = { vm.togglePin(it) }, onRequestCancel = { vm.requestCancel(it) }, onApprove = { vm.approve(it) })
                    else -> TemplatesTab(vm = tmplVm, createOpen = tmplCreateOpen, onCreateDismiss = { tmplCreateOpen = false })
                }
            }
            // Multi-select bar (v0.76.0)
            AnimatedVisibility(
                visible = state.selectedIds.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            ) {
                Surface(tonalElevation = 8.dp, shape = RoundedCornerShape(28.dp)) {
                    LazyRow(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            AssistChip(onClick = {
                                state.selectedIds.forEach { vm.runPrd(it) }
                                vm.clearSelection()
                            }, label = { Text("Run") })
                        }
                        item {
                            AssistChip(onClick = {
                                state.selectedIds.forEach { vm.approve(it) }
                                vm.clearSelection()
                            }, label = { Text("Approve") })
                        }
                        item {
                            AssistChip(onClick = { vm.requestBatchCancelConfirm() }, label = { Text("Cancel") })
                        }
                        item {
                            AssistChip(onClick = {
                                state.selectedIds.forEach { vm.setPrdType(it, "archived") }
                                vm.clearSelection()
                            }, label = { Text("Archive") })
                        }
                        item {
                            AssistChip(onClick = { vm.requestBatchDeleteConfirm() }, label = { Text("Delete") })
                        }
                        item {
                            AssistChip(onClick = { vm.clearSelection() }, label = { Text("✕ Clear") })
                        }
                    }
                }
            }
        }
    }

    if (newOpen) {
        NewPrdDialog(onDismiss = { newOpen = false }, onCreate = { req -> vm.create(req); newOpen = false })
    }

    // Confirm-cancel dialog (Sprint 24 BL293)
    state.confirmCancelId?.let { cancelId ->
        val prd = state.prds.firstOrNull { it.id == cancelId }
        AlertDialog(
            onDismissRequest = { vm.dismissCancelConfirm() },
            title = { Text(stringResource(R.string.automata_confirm_cancel_title)) },
            text = { Text(stringResource(R.string.automata_confirm_cancel_body, prd?.title?.takeIf { it.isNotBlank() } ?: prd?.name ?: cancelId)) },
            confirmButton = {
                TextButton(onClick = { vm.cancelPrd(cancelId) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissCancelConfirm() }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
    // Sprint 30 — batch cancel confirm dialog
    if (state.showBatchCancelConfirm) {
        AlertDialog(
            onDismissRequest = { vm.dismissBatchConfirm() },
            title = { Text(stringResource(R.string.automata_confirm_cancel_title)) },
            text = { Text(stringResource(R.string.automata_confirm_batch_cancel, state.selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedIds.forEach { vm.cancelPrd(it) }
                    vm.clearSelection()
                    vm.dismissBatchConfirm()
                }) { Text(stringResource(R.string.action_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissBatchConfirm() }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    // Sprint 30 — batch hard-delete confirm dialog
    if (state.showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { vm.dismissBatchConfirm() },
            title = { Text(stringResource(R.string.automata_confirm_batch_delete_title)) },
            text = { Text(stringResource(R.string.automata_confirm_batch_delete, state.selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedIds.forEach { vm.hardDeletePrd(it) }
                    vm.clearSelection()
                    vm.dismissBatchConfirm()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissBatchConfirm() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    openPrdId?.let { id ->
        LaunchedEffect(id) { vm.loadScanResult(id) }
        val prd = state.prds.firstOrNull { it.id == id }
        if (prd != null) {
            PrdDetailDialog(
                prd = prd,
                backends = state.backends,
                permissionModes = state.permissionModes,
                onDismiss = { openPrdId = null; vm.clearScan() },
                onApprove = { vm.approve(id); openPrdId = null },
                onReject = { reason -> vm.reject(id, reason); openPrdId = null },
                onDecompose = { vm.decompose(id) },
                onSetLlm = { backend, effort, model -> vm.setLlm(id, backend, effort, model) },
                onRun = { vm.runPrd(id) },
                onCancel = { vm.cancelPrd(id) },
                onRequestRevision = { note -> vm.requestRevision(id, note) },
                onEditPrd = { title, spec, pm -> vm.editPrd(id, title, spec, pm) },
                onDelete = { vm.hardDeletePrd(id) },
                onEditStory = { storyId, newTitle, newDescription -> vm.editStory(id, storyId, newTitle, newDescription) },
                onEditFiles = { storyId, files -> vm.editFiles(id, storyId, files) },
                scanResult = state.scanResult,
                scanLoading = state.scanLoading,
                onTriggerScan = { vm.triggerScan(id) },
                onCreateFixPrd = { vm.createFixPrd(id) { newId -> openPrdId = newId } },
                onProposeRules = { vm.proposeRules(id) },
                proposedRules = state.proposedRules,
                onDismissProposedRules = { vm.clearProposedRules() },
                automataTypes = state.automataTypes,
                onSetType = { type -> vm.setPrdType(id, type) },
                onSetGuidedMode = { gm -> vm.setPrdGuidedMode(id, gm) },
                onSetSkills = { skills -> vm.setPrdSkills(id, skills) },
                onCloneTemplate = { vm.clonePrdToTemplate(id) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PrdsBody(
    state: AutonomousViewModel.UiState,
    pinnedIds: Set<String>,
    filterOpen: Boolean,
    includeTemplates: Boolean,
    statusFilter: String?,
    onOpenPrd: (String) -> Unit,
    onStatusFilter: (String?) -> Unit,
    onIncludeTemplates: (Boolean) -> Unit,
    onToggleSelect: (String) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onRequestCancel: (String) -> Unit = {},
    onApprove: (String) -> Unit = {},
) {
    if (filterOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(selected = statusFilter == null, onClick = { onStatusFilter(null) }, label = { Text(stringResource(R.string.autonomous_filter_all)) })
            listOf("needs_review", "revisions_asked", "approved", "decomposing", "running", "complete", "rejected", "cancelled").forEach { s ->
                FilterChip(selected = statusFilter == s, onClick = { onStatusFilter(if (statusFilter == s) null else s) }, label = { Text(s.replace('_', ' ')) })
            }
            FilterChip(selected = includeTemplates, onClick = { onIncludeTemplates(!includeTemplates) }, label = { Text(stringResource(R.string.autonomous_filter_templates)) })
        }
    }
    state.banner?.let { banner ->
        Text(banner, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    val visible = state.prds
        .filter { prd ->
            (includeTemplates || !prd.isTemplate) && (statusFilter == null || prd.status.equals(statusFilter, ignoreCase = true))
        }
        .sortedWith(
            compareBy(
                { if (it.id in pinnedIds) 0 else 1 },
                { prdStateRank(it.status) },
                { -(it.createdAt?.hashCode() ?: 0) },
            ),
        )
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).align(Alignment.Center).alpha(0.10f),
        )
        if (visible.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.autonomous_empty_state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { prd ->
                    PrdRow(
                        prd = prd,
                        selected = prd.id in state.selectedIds,
                        pinned = prd.id in pinnedIds,
                        serverName = state.prdProfileNames[prd.id],
                        onClick = { onOpenPrd(prd.id) },
                        onLongClick = { onToggleSelect(prd.id) },
                        onTogglePin = { onTogglePin(prd.id) },
                        onCancel = { onRequestCancel(prd.id) },
                        onApprove = { onApprove(prd.id) },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PrdRow(
    prd: PrdDto,
    selected: Boolean = false,
    pinned: Boolean = false,
    serverName: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onCancel: () -> Unit = {},
    onApprove: () -> Unit = {},
) {
    val statusColor = prdStatusColor(prd.status)
    val storyLabel = if (prd.stories.isNotEmpty()) stringResource(R.string.automata_stories_tasks, prd.stories.size) else null
    val metaText = listOfNotNull(storyLabel, prd.backend?.takeIf { it.isNotBlank() }, prd.effort?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
    val showCancel = prd.status.lowercase() in setOf("running", "decomposing", "approved")
    val showApprove = prd.status.lowercase() in setOf("needs_review", "revisions_asked", "awaiting_approval")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .let { mod ->
                if (selected) mod.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)) else mod
            }
            .drawBehind { drawRect(color = statusColor, topLeft = Offset.Zero, size = Size(4.dp.toPx(), size.height)) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prd.title?.takeIf { it.isNotBlank() } ?: prd.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    serverName?.let { name ->
                        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                Row(modifier = Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(prd.status)
                    prd.type?.takeIf { it.isNotBlank() }?.let { TypeBadge(it) }
                    if (prd.isTemplate) Text(stringResource(R.string.autonomous_template_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    if (prd.depth > 0) Text(stringResource(R.string.autonomous_depth, prd.depth), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    prd.projectProfile?.takeIf { it.isNotBlank() }?.let { profile ->
                        Text(profile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                metaText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)) }
                prd.createdAt?.takeIf { it.isNotBlank() }?.let { ts ->
                    Text(stringResource(R.string.automata_last_activity, ts.take(10)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.padding(top = 1.dp))
                }
            }
            // Pin toggle
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = if (pinned) stringResource(R.string.automata_unpin) else stringResource(R.string.automata_pin),
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp),
                )
            }
            // Selection checkbox
            Icon(
                imageVector = if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
        // Inline action buttons (BL293 button matrix)
        if (showCancel || showApprove) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showApprove) {
                    TextButton(
                        onClick = { onApprove(); onClick() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.action_approve), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (showCancel) {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = onClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(stringResource(R.string.action_open), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = prdStatusColor(status)
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun TypeBadge(type: String) {
    val color = when (type.lowercase()) {
        "software" -> Color(0xFF3B82F6)
        "research" -> Color(0xFFA855F7)
        "operational" -> Color(0xFFF97316)
        "personal" -> Color(0xFF14B8A6)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(type, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Sort rank: action-needed statuses first, then active, then terminal. */
internal fun prdStateRank(status: String): Int =
    when (status.lowercase()) {
        "needs_review", "revisions_asked", "awaiting_approval" -> 0
        "running" -> 1
        "decomposing" -> 2
        "approved" -> 3
        else -> 10
    }

internal fun prdStatusColor(status: String): Color =
    when (status.lowercase()) {
        "running" -> Color(0xFF22C55E)
        "approved" -> Color(0xFF14B8A6)
        "needs_review", "revisions_asked", "awaiting_approval" -> Color(0xFFF59E0B)
        "blocked", "rejected" -> Color(0xFFEF4444)
        "decomposing" -> Color(0xFFA855F7)
        "draft", "complete", "completed", "cancelled" -> Color(0xFF94A3B8)
        else -> Color(0xFF94A3B8)
    }

@Composable
private fun AutonomousServerPickerTitle(
    active: ServerProfile?,
    allMode: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    profiles: List<ServerProfile>,
    onSelectAll: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Box {
        Row(
            modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (allMode) stringResource(R.string.sessions_all_servers) else (active?.displayName ?: stringResource(R.string.sessions_no_server)))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.sessions_switch_server), modifier = Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            if (profiles.size > 1) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.sessions_all_servers), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (allMode) Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = onSelectAll,
                )
                HorizontalDivider()
            }
            if (profiles.isEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sessions_no_servers)) }, onClick = onDismiss, enabled = false)
            } else {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AutonomousStatusDot(enabled = p.enabled)
                                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(p.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (p.id == active?.id) Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = { onSelect(p.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutonomousStatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
}
