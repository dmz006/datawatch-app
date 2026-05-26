package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.IdentityDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.settings.IdentityWizardSheet
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    var selectMode by remember { mutableStateOf(false) }
    var includeTemplates by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    // PWA _automataState.historyOn: false = active PRDs only; true = include terminal statuses
    var historyOn by remember { mutableStateOf(false) }
    var openPrdId by remember { mutableStateOf<String?>(null) }
    // Retained so the slide-out animation shows the PRD instead of a blank
    var detailPrd by remember { mutableStateOf<PrdDto?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var tmplCreateOpen by remember { mutableStateOf(false) }
    var identityWizardOpen by remember { mutableStateOf(false) }
    var identity by remember { mutableStateOf(IdentityDto()) }
    var pickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Keep detailPrd alive through exit animation so slide-out doesn't flash blank
    LaunchedEffect(openPrdId, state.prds) {
        if (openPrdId != null) detailPrd = state.prds.firstOrNull { it.id == openPrdId }
    }

    LaunchedEffect(Unit) { vm.refresh(); vm.loadAutomataTypes() }
    LaunchedEffect(identityWizardOpen) {
        if (identityWizardOpen) {
            runCatching {
                val activeId = ServiceLocator.activeServerStore.get()
                val sp = ServiceLocator.profileRepository.observeAll()
                    .first { list -> list.any { it.enabled } }
                    .let { list ->
                        if (activeId == null) list.filter { it.enabled }.firstOrNull()
                        else list.firstOrNull { it.id == activeId && it.enabled }
                    } ?: return@runCatching
                ServiceLocator.transportFor(sp).getIdentity().onSuccess { identity = it }
            }
        }
    }

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
                    if (currentTab == 0) {
                        Text("⚡", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.autonomous_fab_new))
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Custom tab row — matches SessionDetailScreen style with icons on right
                val tabBorderColor = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.border
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawLine(
                                color = tabBorderColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AutonomousTab(
                        label = stringResource(R.string.autonomous_tab_prds),
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                    )
                    AutonomousTab(
                        label = stringResource(R.string.autonomous_tab_templates),
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                    )
                    Spacer(Modifier.weight(1f))
                    // Action buttons matching PWA .automata-action-btn order: ☑ select, ⊞ filter, ⏱ history
                    if (currentTab == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AutomataActionBtn("☑", active = selectMode) {
                                selectMode = !selectMode
                                if (!selectMode) vm.clearSelection()
                            }
                            AutomataActionBtn("⊞", active = filterOpen) { filterOpen = !filterOpen }
                            AutomataActionBtn("⏱", active = historyOn) { historyOn = !historyOn }
                        }
                    }
                }
                when (currentTab) {
                    0 -> PrdsBody(state, pinnedIds, filterOpen, includeTemplates, statusFilter, typeFilter, selectMode = selectMode, historyOn = historyOn, onOpenPrd = { if (!selectMode) openPrdId = it }, onStatusFilter = { statusFilter = it }, onIncludeTemplates = { includeTemplates = it }, onTypeFilter = { typeFilter = it }, onToggleSelect = { vm.toggleSelection(it) }, onTogglePin = { vm.togglePin(it) }, onRequestCancel = { vm.requestCancel(it) }, onApprove = { vm.approve(it) }, onPlan = { vm.decompose(it) }, onRun = { vm.runPrd(it) }, onReject = { id, reason -> vm.reject(id, reason) }, onRevise = { id, note -> vm.requestRevision(id, note) })
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
        NewPrdDialog(
            onDismiss = { newOpen = false },
            onCreate = { req -> vm.create(req); newOpen = false },
            onBrowseTemplates = { currentTab = 1; newOpen = false },
        )
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

    // Full-screen detail — slides in from right when a PRD is opened
    // detailPrd is kept non-null through the exit animation so the slide-out isn't blank
    AnimatedVisibility(
        visible = openPrdId != null,
        modifier = Modifier.fillMaxSize(),
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        detailPrd?.let { prd ->
            val id = prd.id
            PrdDetailDialog(
                prd = prd,
                backends = state.backends,
                permissionModes = state.permissionModes,
                onDismiss = { openPrdId = null },
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
                automataTypes = state.automataTypes,
                onSetType = { type -> vm.setPrdType(id, type) },
                onSetGuidedMode = { gm -> vm.setPrdGuidedMode(id, gm) },
                onSetSkills = { skills -> vm.setPrdSkills(id, skills) },
                onCloneTemplate = { vm.clonePrdToTemplate(id) },
            )
        }
    }

    if (identityWizardOpen) {
        IdentityWizardSheet(
            initial = identity,
            onDismiss = { identityWizardOpen = false },
            onFinish = { updated ->
                identity = updated
                identityWizardOpen = false
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.filter { it.enabled }.firstOrNull()
                                else list.firstOrNull { it.id == activeId && it.enabled }
                            } ?: return@launch
                        ServiceLocator.transportFor(sp).setIdentity(updated)
                    }
                }
            },
        )
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
    typeFilter: String? = null,
    selectMode: Boolean = false,
    historyOn: Boolean = false,
    onOpenPrd: (String) -> Unit,
    onStatusFilter: (String?) -> Unit,
    onIncludeTemplates: (Boolean) -> Unit,
    onTypeFilter: (String?) -> Unit = {},
    onToggleSelect: (String) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onRequestCancel: (String) -> Unit = {},
    onApprove: (String) -> Unit = {},
    onPlan: (String) -> Unit = {},
    onRun: (String) -> Unit = {},
    onReject: (String, String) -> Unit = { _, _ -> },
    onRevise: (String, String) -> Unit = { _, _ -> },
) {
    if (filterOpen) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                FilterChip(selected = statusFilter == null, onClick = { onStatusFilter(null) }, label = { Text(stringResource(R.string.autonomous_filter_all), style = MaterialTheme.typography.labelSmall) })
            }
            items(listOf("needs_review", "revisions_asked", "approved", "decomposing", "running", "complete", "rejected", "cancelled")) { s ->
                val statusColor = prdStatusColor(s)
                FilterChip(
                    selected = statusFilter == s,
                    onClick = { onStatusFilter(if (statusFilter == s) null else s) },
                    label = { Text(s.replace('_', ' '), style = MaterialTheme.typography.labelSmall) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = statusColor.copy(alpha = 0.18f),
                        selectedLabelColor = statusColor,
                        selectedLeadingIconColor = statusColor,
                    ),
                    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                        borderColor = if (statusFilter == s) statusColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        selectedBorderColor = statusColor,
                        enabled = true,
                        selected = statusFilter == s,
                    ),
                )
            }
            // PWA type filters: software / research / operational / personal
            items(listOf("software", "research", "operational", "personal")) { t ->
                FilterChip(
                    selected = typeFilter == t,
                    onClick = { onTypeFilter(if (typeFilter == t) null else t) },
                    label = { Text(t, style = MaterialTheme.typography.labelSmall) },
                )
            }
            item {
                FilterChip(selected = includeTemplates, onClick = { onIncludeTemplates(!includeTemplates) }, label = { Text(stringResource(R.string.autonomous_filter_templates), style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
    state.banner?.let { banner ->
        Text(banner, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    // PWA _AUTOMATA_ACTIVE_STATUSES — terminal statuses hidden when historyOn=false
    val terminalStatuses = setOf("completed", "complete", "cancelled", "rejected", "archived")
    val visible = state.prds
        .filter { prd ->
            (includeTemplates || !prd.isTemplate) &&
                (statusFilter == null || prd.status.equals(statusFilter, ignoreCase = true)) &&
                (typeFilter == null || prd.type.equals(typeFilter, ignoreCase = true)) &&
                // History filter: override when a status filter is explicitly set
                (historyOn || statusFilter != null || prd.status.lowercase() !in terminalStatuses)
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
        if (visible.isEmpty() && state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.autonomous_empty_state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { "${state.prdProfileNames[it.id].orEmpty()}|${it.id}" }) { prd ->
                    PrdRow(
                        prd = prd,
                        selected = prd.id in state.selectedIds,
                        pinned = prd.id in pinnedIds,
                        selectMode = selectMode,
                        serverName = state.prdProfileNames[prd.id],
                        onClick = { onOpenPrd(prd.id) },
                        onLongClick = { onToggleSelect(prd.id) },
                        onTogglePin = { onTogglePin(prd.id) },
                        onCancel = { onRequestCancel(prd.id) },
                        onApprove = { onApprove(prd.id) },
                        onPlan = { onPlan(prd.id) },
                        onRun = { onRun(prd.id) },
                        onReject = { reason -> onReject(prd.id, reason) },
                        onRevise = { note -> onRevise(prd.id, note) },
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
    selectMode: Boolean = false,
    serverName: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onCancel: () -> Unit = {},
    onApprove: () -> Unit = {},
    onPlan: () -> Unit = {},
    onRun: () -> Unit = {},
    onReject: (String) -> Unit = {},
    onRevise: (String) -> Unit = {},
) {
    val statusColor = prdStatusColor(prd.status)
    val statusLower = prd.status.lowercase()
    val isTerminal = statusLower in setOf("completed", "complete", "cancelled", "rejected", "archived")
    val showCancel = !isTerminal
    val showApprove = isApprovalState(statusLower)
    val showPlan = statusLower in setOf("draft", "revisions_asked")
    val showRun = statusLower == "approved"
    val showRejectRevise = showApprove

    // Local dialog state for Reject / Revise input
    var rejectDialogOpen by remember { mutableStateOf(false) }
    var reviseDialogOpen by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .let { mod ->
                if (selected) mod.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)) else mod
            }
            .drawBehind { drawRect(color = statusColor, topLeft = Offset.Zero, size = Size(4.dp.toPx(), size.height)) }
            .combinedClickable(
                onClick = if (selectMode) onLongClick else onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection indicator dot (only visible in selectMode)
            if (selectMode) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape,
                        )
                        .border(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            CircleShape,
                        ),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: type badge(s) + title (bold, flex) + status pill (right-justified) — mirrors PWA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    prd.type?.takeIf { it.isNotBlank() }?.let { TypeBadge(it) }
                    if (prd.isTemplate) Text(stringResource(R.string.autonomous_template_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        prd.title?.takeIf { it.isNotBlank() } ?: prd.name.takeIf { it.isNotBlank() } ?: "(no title)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(prd.status)
                }
                // Row 2: ID + server/date (right-justified, monospace) — mirrors PWA
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prd.depth > 0) Text(stringResource(R.string.autonomous_depth, prd.depth), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
                    prd.parentPrdId?.takeIf { it.isNotBlank() }?.let { pid ->
                        val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2
                        Box(Modifier.background(accent2.copy(alpha = 0.16f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp).padding(end = 6.dp)) {
                            Text("↗ ${pid.take(8)}", style = MaterialTheme.typography.labelSmall, color = accent2)
                        }
                    }
                    Text(
                        prd.id.take(8),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                    serverName?.let { name ->
                        Text(" · $name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    }
                    // PWA uses updated_at formatted as "DD/MM/YYYY, HH:MM:SS" (en-GB locale)
                    (prd.updatedAt ?: prd.createdAt)?.takeIf { it.isNotBlank() }?.let { ts ->
                        val display = runCatching {
                            val inst = java.time.Instant.parse(ts)
                            val ldt = java.time.LocalDateTime.ofInstant(inst, java.time.ZoneId.systemDefault())
                            "%02d/%02d/%04d, %02d:%02d:%02d".format(ldt.dayOfMonth, ldt.monthValue, ldt.year, ldt.hour, ldt.minute, ldt.second)
                        }.getOrDefault(ts.take(10))
                        Text("  $display", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    }
                }
                // Lifecycle strip (5 steps matching PWA: Plan → Review → Approve → Run → Done)
                LifecycleStrip(
                    status = prd.status,
                    onPlan = if (showPlan) onPlan else null,
                    onApprove = if (showApprove) onApprove else null,
                    onReject = if (showRejectRevise) { { rejectDialogOpen = true } } else null,
                    onRevise = if (showRejectRevise) { { reviseDialogOpen = true } } else null,
                    onRun = if (showRun) onRun else null,
                    onCancel = if (showCancel) onCancel else null,
                )
                // Action row: cancel left | approve+pin right — border-top separator mirrors PWA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .drawBehind {
                            drawLine(
                                color = androidx.compose.ui.graphics.Color(0xFF333333),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 0.5.dp.toPx(),
                            )
                        }
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showCancel) {
                        // PWA uses btn-secondary: bg3 background, border, normal text — not red
                        val dw2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .border(1.dp, dw2.border, RoundedCornerShape(6.dp))
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text("✕ ${stringResource(R.string.action_cancel)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (showApprove) {
                        TextButton(
                            onClick = onApprove,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        ) { Text("✓ ${stringResource(R.string.action_approve)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) }
                    }
                    // Pin button — 📌 when pinned (yellow), 📍 when not (dim)
                    TextButton(
                        onClick = onTogglePin,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (pinned) "📌" else "📍",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pinned) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
                // Stories envelope — always visible, matches PWA <details> (shows "no stories yet" when empty)
                var storiesExpanded by remember(prd.id) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { storiesExpanded = !storiesExpanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${if (storiesExpanded) "▾" else "▸"} Stories & tasks (${prd.stories.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (storiesExpanded) {
                    if (prd.stories.isEmpty()) {
                        Text("no stories yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                    } else {
                        Column(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
                            prd.stories.forEach { story ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        story.title.ifBlank { story.id.take(8) },
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    val sColor = when (story.status.lowercase()) {
                                        "complete", "completed" -> Color(0xFF10B981)
                                        "in_progress", "running" -> Color(0xFF3B82F6)
                                        "awaiting_approval", "needs_review" -> Color(0xFFF59E0B)
                                        "rejected", "cancelled" -> Color(0xFFEF4444)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(sColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(story.status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = sColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Reject dialog
    if (rejectDialogOpen) {
        AlertDialog(
            onDismissRequest = { rejectDialogOpen = false },
            title = { Text(stringResource(R.string.action_reject)) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Reason (optional)") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onReject(inputText.trim()); rejectDialogOpen = false }) {
                    Text(stringResource(R.string.action_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Revise dialog
    if (reviseDialogOpen) {
        AlertDialog(
            onDismissRequest = { reviseDialogOpen = false },
            title = { Text("Request Revision") },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Note (optional)") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRevise(inputText.trim()); reviseDialogOpen = false }) {
                    Text("↩ Revise")
                }
            },
            dismissButton = {
                TextButton(onClick = { reviseDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = prdStatusColor(status)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .border(1.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(status.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@Composable
internal fun TypeBadge(type: String) {
    val color = when (type.lowercase()) {
        "software" -> Color(0xFF6366F1)
        "research" -> Color(0xFFF59E0B)
        "operational" -> Color(0xFF10B981)
        "personal" -> Color(0xFFEC4899)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(type.lowercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/** Horizontal lifecycle strip — mirrors PWA renderLifecycleStrip exactly. */
@Composable
internal fun LifecycleStrip(
    status: String,
    onPlan: (() -> Unit)? = null,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
    onRevise: (() -> Unit)? = null,
    onRun: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val dw = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current
    val accent = MaterialTheme.colorScheme.primary   // #7C3AED = var(--accent)
    val success = dw.success                          // #10B981 = var(--success)
    val statusLower = status.lowercase()
    val isDanger = statusLower in setOf("rejected", "cancelled", "blocked", "archived")
    val isRunning = statusLower == "running"

    // 5-step lifecycle matching PWA: Plan(0) → Review(1) → Approve(2) → Run(3) → Done(4)
    val currentIndex = when {
        isDanger -> 4
        statusLower in setOf("draft", "planning", "revisions_asked", "decomposing") -> 0
        statusLower in setOf("needs_review", "awaiting_approval") -> 1
        statusLower == "approved" -> 2
        statusLower == "running" -> 3
        statusLower in setOf("complete", "completed") -> 4
        else -> -1
    }
    if (currentIndex == -1) return

    val hintText = when {
        statusLower in setOf("draft", "revisions_asked") -> "Next: Plan — break your spec into stories + tasks"
        statusLower == "decomposing" -> "Planning… review will become available shortly"
        statusLower in setOf("needs_review", "awaiting_approval") -> "Next: Review the plan and Approve / Reject / Revise"
        statusLower == "approved" -> "Next: Run the approved automaton"
        statusLower == "running" -> "Running — Cancel if needed"
        statusLower in setOf("complete", "completed") -> "✓ Completed"
        statusLower == "rejected" -> "✗ Rejected"
        statusLower == "cancelled" -> "Cancelled"
        statusLower == "archived" -> "Archived"
        else -> null
    }

    Column(modifier = Modifier.padding(top = 4.dp)) {
        // Hint text: uppercase + accent color, matching PWA .lifecycle-strip-current
        hintText?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
                color = accent,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        data class Step(val baseLabel: String, val activeLabel: String, val onClick: (() -> Unit)?)
        val planLabel = if (statusLower == "revisions_asked") "Re-plan" else "Plan"
        val steps = listOf(
            Step("Plan", "▶ $planLabel", onPlan),
            Step("Review", "Review", null),
            Step("Approve", "Approve", if (!isRunning) onApprove else null),
            Step("Run", if (isRunning) "■ Cancel" else "▶ Run", if (isRunning) onCancel else onRun),
            Step("Done", "Done", null),
        )
        val shape = RoundedCornerShape(6.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { idx, step ->
                val isActive = !isDanger && idx == currentIndex
                val isPast = !isDanger && idx < currentIndex
                // For danger (cancelled/rejected/archived): all steps before "done" show as green done
                val isDangerPast = isDanger && idx < steps.size - 1

                val label: String
                val bgColor: Color
                val textColor: Color
                val borderColor: Color?

                when {
                    isDangerPast -> {
                        // PWA: .lifecycle-step-btn.done — solid green, white text, ✓ prefix
                        label = "✓ ${step.baseLabel}"
                        bgColor = success
                        textColor = Color.White
                        borderColor = null
                    }
                    isDanger && idx == steps.size - 1 -> {
                        // Done step for danger status — matches PWA per-status styling
                        when (statusLower) {
                            "rejected" -> {
                                label = "✗ Rejected"
                                bgColor = Color.Transparent
                                textColor = Color(0xFFEF4444)
                                borderColor = Color(0xFFEF4444).copy(alpha = 0.4f)
                            }
                            else -> {
                                // cancelled / archived / blocked — plain dim step matching PWA opacity:0.6
                                label = statusLower.replaceFirstChar { it.uppercase() }
                                bgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                borderColor = dw.border
                            }
                        }
                    }
                    isPast -> {
                        // PWA: .lifecycle-step-btn.done — solid green, white text, ✓ prefix
                        label = "✓ ${step.baseLabel}"
                        bgColor = success
                        textColor = Color.White
                        borderColor = null
                    }
                    isActive -> {
                        // PWA: .lifecycle-step-btn.current — solid accent fill, white text
                        label = step.activeLabel
                        bgColor = accent
                        textColor = Color.White
                        borderColor = null
                    }
                    else -> {
                        // PWA: pending step — dim, outlined border (border: 1px solid var(--border))
                        label = step.baseLabel
                        bgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        borderColor = dw.border
                    }
                }

                val clickHandler = if (isActive) step.onClick else null

                Box(
                    modifier = Modifier
                        .background(bgColor, shape)
                        .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
                        .then(if (clickHandler != null) Modifier.clickable(onClick = clickHandler) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = textColor,
                        fontWeight = if (isActive || isPast || isDangerPast) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }

                if (idx < steps.size - 1) {
                    Text("›", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }

            // Approve step: Reject(✗) + Revise(↩) sub-buttons — matches PWA inline sub-actions
            if (isApprovalState(statusLower) && (onReject != null || onRevise != null)) {
                onReject?.let { handler ->
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .clickable(onClick = handler)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("✗", fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
                onRevise?.let { handler ->
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .clickable(onClick = handler)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("↩", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun isApprovalState(statusLower: String) =
    statusLower in setOf("needs_review", "awaiting_approval", "revisions_asked")

/** Sort rank: action-needed statuses first, then active, then terminal. */
internal fun prdStateRank(status: String): Int =
    when (status.lowercase()) {
        "needs_review", "revisions_asked", "awaiting_approval" -> 0
        "running" -> 1
        "decomposing", "planning" -> 2
        "approved" -> 3
        "draft" -> 4
        "cancelled", "rejected", "completed", "complete", "archived" -> 10
        else -> 5
    }

internal fun prdStatusColor(status: String): Color =
    when (status.lowercase()) {
        "running" -> Color(0xFF10B981)
        "approved" -> Color(0xFF7C3AED)
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

/** Action button matching PWA .automata-action-btn — border pill, accent when active. */
@Composable
private fun AutomataActionBtn(label: String, active: Boolean, onClick: () -> Unit) {
    val dw = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current
    val accent = MaterialTheme.colorScheme.primary
    val tintBg = Color(0xFF60A5FA).copy(alpha = 0.1f)  // .automata-action-btn.active bg
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 32.dp, height = 28.dp)
            .background(if (active) tintBg else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (active) accent else dw.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(label, fontSize = 13.sp, color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Custom tab button — matches PWA .automata-tab / .automata-tab.active pill style. */
@Composable
private fun AutonomousTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary               // #7C3AED = var(--accent)
    val activeBg = Color(0xFF60A5FA).copy(alpha = 0.12f)        // rgba(96,165,250,0.12) = active tint
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) activeBg else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
