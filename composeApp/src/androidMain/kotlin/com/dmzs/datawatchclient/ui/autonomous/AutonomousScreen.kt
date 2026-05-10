package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.theme.pwaCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AutonomousScreen(
    vm: AutonomousViewModel = viewModel(),
    tmplVm: TemplatesViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var newOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var includeTemplates by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var openPrdId by remember { mutableStateOf<String?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var tmplCreateOpen by remember { mutableStateOf(false) }
    var identityWizardOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh(); vm.loadAutomataTypes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.autonomous_title)) },
                actions = {
                    // Robot icon FIRST (left of search) — visible only on this screen
                    IconButton(onClick = { identityWizardOpen = true }) {
                        Icon(Icons.Filled.SmartToy, contentDescription = stringResource(R.string.identity_wizard_open))
                    }
                    if (currentTab == 0) {
                        IconButton(onClick = { filterOpen = !filterOpen }) {
                            Icon(
                                if (filterOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (filterOpen) stringResource(R.string.autonomous_filter_close) else stringResource(R.string.autonomous_filter_open),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (openPrdId == null) {
                FloatingActionButton(
                    onClick = { if (currentTab == 1) tmplCreateOpen = true else newOpen = true },
                    modifier = Modifier.offset(y = 36.dp).padding(end = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.autonomous_fab_new))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = currentTab) {
                Tab(selected = currentTab == 0, onClick = { currentTab = 0 }, text = { Text(stringResource(R.string.autonomous_tab_prds)) })
                Tab(selected = currentTab == 1, onClick = { currentTab = 1 }, text = { Text(stringResource(R.string.autonomous_tab_templates)) })
            }
            when (currentTab) {
                0 -> PrdsBody(state, filterOpen, includeTemplates, statusFilter, onOpenPrd = { openPrdId = it }, onStatusFilter = { statusFilter = it }, onIncludeTemplates = { includeTemplates = it })
                else -> TemplatesTab(vm = tmplVm, createOpen = tmplCreateOpen, onCreateDismiss = { tmplCreateOpen = false })
            }
        }
    }

    if (newOpen) {
        NewPrdDialog(onDismiss = { newOpen = false }, onCreate = { req -> vm.create(req); newOpen = false })
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
            )
        }
    }
}

@Composable
private fun PrdsBody(
    state: AutonomousViewModel.UiState,
    filterOpen: Boolean,
    includeTemplates: Boolean,
    statusFilter: String?,
    onOpenPrd: (String) -> Unit,
    onStatusFilter: (String?) -> Unit,
    onIncludeTemplates: (Boolean) -> Unit,
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
    val visible = state.prds.filter { prd ->
        (includeTemplates || !prd.isTemplate) && (statusFilter == null || prd.status.equals(statusFilter, ignoreCase = true))
    }
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
                items(visible, key = { it.id }) { prd -> PrdRow(prd, onClick = { onOpenPrd(prd.id) }) }
            }
        }
    }
}

@Composable
private fun PrdRow(
    prd: PrdDto,
    onClick: () -> Unit = {},
) {
    val statusColor = prdStatusColor(prd.status)
    val storyLabel = if (prd.stories.isNotEmpty()) stringResource(R.string.autonomous_story_count, prd.stories.size) else null
    val metaText = listOfNotNull(storyLabel, prd.backend?.takeIf { it.isNotBlank() }, prd.effort?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .drawBehind { drawRect(color = statusColor, topLeft = Offset.Zero, size = Size(4.dp.toPx(), size.height)) }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(prd.title?.takeIf { it.isNotBlank() } ?: prd.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
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
            prd.spec?.lines()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { firstLine ->
                Text(firstLine.take(80), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, modifier = Modifier.padding(top = 1.dp))
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
