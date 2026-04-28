package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.transport.dto.PrdDto

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
public fun AutonomousScreen(
    vm: AutonomousViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var newOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PRDs") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New PRD")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.banner?.let { banner ->
                Text(
                    banner,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.prds.isEmpty() && !state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No PRDs yet — tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(state.prds, key = { it.id }) { prd -> PrdRow(prd) }
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
}

@Composable
private fun PrdRow(prd: PrdDto) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
