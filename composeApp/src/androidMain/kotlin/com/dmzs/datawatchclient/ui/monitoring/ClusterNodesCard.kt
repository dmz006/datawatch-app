package com.dmzs.datawatchclient.ui.monitoring

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.dmzs.datawatchclient.transport.dto.ObserverClusterNodeDto
import com.dmzs.datawatchclient.ui.settings.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings → Monitor → Cluster nodes card. Mirrors PWA
 * `loadObserverClusterNodes()` (datawatch v4.5.0). Renders only
 * when `/api/observer/stats` returns a non-empty `cluster.nodes`
 * array; single-node setups see no card.
 *
 * v0.36.0 — issue #3.
 */
@Composable
public fun ClusterNodesCard(vm: ClusterNodesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    if (state.nodes.isEmpty()) return

    Section(title = "Cluster nodes") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            state.nodes.forEach { node -> ClusterNodeRow(node) }
        }
    }
}

@Composable
private fun ClusterNodeRow(node: ObserverClusterNodeDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        if (node.ready) Color(0xFF22C55E) else Color(0xFFEF4444),
                        CircleShape,
                    ),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${node.podCount} pods",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.pressures.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    node.pressures.forEach { p ->
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        Color(0xFFF59E0B).copy(alpha = 0.18f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                p,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF59E0B),
                            )
                        }
                    }
                }
            }
            UsageBar("CPU", node.cpuPct)
            UsageBar("Mem", node.memPct)
        }
    }
}

@Composable
private fun UsageBar(label: String, pct: Double) {
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 32.dp, height = 14.dp),
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).size(width = 0.dp, height = 6.dp),
            color =
                when {
                    pct >= 80 -> Color(0xFFEF4444)
                    pct >= 60 -> Color(0xFFF59E0B)
                    else -> Color(0xFF22C55E)
                },
        )
    }
}

public class ClusterNodesViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val nodes: List<ObserverClusterNodeDto> = emptyList(),
        val error: String? = null,
    )
    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.observerStats().fold(
                onSuccess = { dto ->
                    _state.value = _state.value.copy(
                        nodes = dto.cluster?.nodes.orEmpty(),
                        error = null,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        nodes = emptyList(),
                        error = err.message,
                    )
                },
            )
        }
    }
}
