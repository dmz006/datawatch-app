package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * Autonomous tab — PRD lifecycle list.
 *
 * v0.38.0 (issue #11) — initial scaffolding. List + FAB-driven New
 * PRD modal with the unified Profile dropdown that PWA v5.26.30
 * collapsed (single Profile selector replaces project_dir +
 * project_profile + cluster_profile fields).
 *
 * Story-level review (#12), per-story approval (#18), file
 * association (#19), filter toggle (#13) ship in v0.38.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AutonomousScreen(vm: AutonomousViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var newOpen by remember { mutableStateOf(false) }
    // v0.38.1 (#13) — filter row hidden behind a magnifier toggle in
    // the TopAppBar; templates checkbox + status filter inline. Closed
    // by default, matching PWA v5.26.36-46.
    var filterOpen by remember { mutableStateOf(false) }
    var includeTemplates by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var openPrdId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autonomous") },
                actions = {
                    IconButton(onClick = { filterOpen = !filterOpen }) {
                        Icon(
                            if (filterOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription =
                                if (filterOpen) "Close filter" else "Filter",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // PRDs FAB: hidden when a detail panel is open so the
            // affordance only appears on the list view (PWA v5.26.36).
            if (openPrdId == null) {
                FloatingActionButton(onClick = { newOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New autonomous plan")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filterOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All") },
                    )
                    listOf(
                        "needs_review",
                        "revisions_asked",
                        "approved",
                        "decomposing",
                        "running",
                        "complete",
                        "rejected",
                        "cancelled",
                    ).forEach { s ->
                        FilterChip(
                            selected = statusFilter == s,
                            onClick = { statusFilter = if (statusFilter == s) null else s },
                            label = { Text(s.replace('_', ' ')) },
                        )
                    }
                    FilterChip(
                        selected = includeTemplates,
                        onClick = { includeTemplates = !includeTemplates },
                        label = { Text("Templates") },
                    )
                }
            }
            state.banner?.let { banner ->
                Text(
                    banner,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val visible =
                state.prds.filter { prd ->
                    (includeTemplates || !prd.isTemplate) &&
                        (statusFilter == null || prd.status.equals(statusFilter, ignoreCase = true))
                }
            if (visible.isEmpty() && !state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No plans match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .align(Alignment.Center)
                                .alpha(0.10f),
                    )
                    LazyColumn {
                        items(visible, key = { it.id }) { prd ->
                            PrdRow(prd, onClick = { openPrdId = prd.id })
                        }
                    }
                }
            }
        }
    }

    if (newOpen) {
        NewPrdDialog(
            onDismiss = { newOpen = false },
            onCreate = { req ->
                vm.create(req)
                newOpen = false
            },
        )
    }
    openPrdId?.let { id ->
        val prd = state.prds.firstOrNull { it.id == id }
        if (prd != null) {
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
                onEditStory = { storyId, newTitle, newDescription ->
                    vm.editStory(id, storyId, newTitle, newDescription)
                },
                onEditFiles = { storyId, files ->
                    vm.editFiles(id, storyId, files)
                },
            )
        }
    }
}

@Composable
private fun PrdRow(
    prd: PrdDto,
    onClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .pwaCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(prdStatusColor(prd.status), CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            Text(
                prd.title?.takeIf { it.isNotBlank() } ?: prd.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(prd.status)
                if (prd.depth > 0) {
                    Text(
                        "depth ${prd.depth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (prd.isTemplate) {
                    Text(
                        "template",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                prd.projectProfile?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (prd.stories.isNotEmpty()) {
                Text(
                    "${prd.stories.size} stories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = prdStatusColor(status)
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            status.lowercase().replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

internal fun prdStatusColor(status: String): Color =
    when (status.lowercase()) {
        "running" -> Color(0xFF22C55E)
        "complete", "approved" -> Color(0xFF3B82F6)
        "needs_review", "awaiting_approval" -> Color(0xFFF59E0B)
        "revisions_asked" -> Color(0xFFA855F7)
        "rejected", "cancelled" -> Color(0xFFEF4444)
        "decomposing" -> Color(0xFF94A3B8)
        else -> Color(0xFF94A3B8)
    }
