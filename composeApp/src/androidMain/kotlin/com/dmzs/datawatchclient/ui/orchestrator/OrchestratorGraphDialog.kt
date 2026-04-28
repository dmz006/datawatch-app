package com.dmzs.datawatchclient.ui.orchestrator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.ObserverSummaryDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorNodeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orchestrator PRD-DAG graph dialog.
 *
 * v0.39.0 (issue #7) — renders nodes as a list (no force-directed
 * layout — overkill for a phone screen) with each row carrying its
 * **observer_summary** badge: CPU %, RSS MB, envelope count, and
 * last-push age. Nodes without observer_summary just hide the
 * badge so older daemons + non-Shape-A nodes render gracefully.
 *
 * Edges render under each node as `→ targetId (kind)` lines so the
 * DAG topology is still legible without drawing arrows.
 */
@Composable
public fun OrchestratorGraphDialog(
    graphId: String,
    onDismiss: () -> Unit,
    vm: OrchestratorGraphViewModel = viewModel(),
) {
    LaunchedEffect(graphId) { vm.refresh(graphId) }
    val state by vm.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.graph?.name?.takeIf { it.isNotBlank() } ?: "Graph $graphId") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                state.banner?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                state.graph?.nodes?.forEach { node ->
                    GraphNodeRow(
                        node = node,
                        outgoing = state.graph?.edges?.filter { it.from == node.id }.orEmpty(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun GraphNodeRow(
    node: OrchestratorNodeDto,
    outgoing: List<com.dmzs.datawatchclient.transport.dto.OrchestratorEdgeDto>,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(nodeStatusColor(node.status), CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                node.name?.takeIf { it.isNotBlank() } ?: node.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                node.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            node.observerSummary?.let { ObserverBadge(it) }
        }
        outgoing.forEach { edge ->
            Text(
                "→ ${edge.to}${edge.kind?.let { " ($it)" }.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun ObserverBadge(s: ObserverSummaryDto) {
    val parts = buildList {
        s.cpuPct?.let { add("%.0f%%".format(it)) }
        s.rssMb?.let { add("${it} MB") }
        s.envelopeCount?.let { add("${it}p") }
    }
    if (parts.isEmpty()) return
    Box(
        modifier =
            Modifier
                .background(
                    Color(0xFF7C3AED).copy(alpha = 0.18f),
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7C3AED),
        )
    }
}

private fun nodeStatusColor(status: String): Color =
    when (status.lowercase()) {
        "running" -> Color(0xFF22C55E)
        "complete", "approved" -> Color(0xFF3B82F6)
        "needs_review", "awaiting_approval" -> Color(0xFFF59E0B)
        "rejected", "cancelled" -> Color(0xFFEF4444)
        else -> Color(0xFF94A3B8)
    }

public class OrchestratorGraphViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val graph: OrchestratorGraphDto? = null,
        val banner: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh(id: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.orchestratorGraph(id).fold(
                onSuccess = { dto -> _state.value = UiState(graph = dto) },
                onFailure = { err ->
                    _state.value = UiState(
                        banner = "Load failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
    }
}
