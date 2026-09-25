package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.ui.common.LiveDot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Observatory — Peer Resources block (v8.25.3 parity, #168).
 * Shows CPU%, mem, GPU stats per registered observer peer that has a bound compute node,
 * plus any auto-created local compute nodes (e.g. the server's own "datawatch-stats" node).
 * Refreshes every 8 s while visible.
 */
@Composable
public fun PeerResourcesCard(vm: PeerResourcesViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(8_000)
            vm.refresh()
        }
    }

    if (state.peers.isEmpty() && state.localNodes.isEmpty() && !state.loading) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.obs_peer_resources),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                LiveDot()
            }
            // Local server compute nodes (auto-created by the daemon, e.g. "datawatch-stats")
            state.localNodes.forEach { (node, detail) ->
                LocalNodeRow(node = node, detail = detail)
            }
            // Observer-peer bound compute nodes
            if (state.peers.isEmpty() && state.localNodes.isEmpty()) {
                Text(
                    stringResource(R.string.obs_peer_no_peers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                state.peers.forEach { (peer, detail) ->
                    PeerResourceRow(peer = peer, detail = detail)
                }
            }
        }
    }
}

@Composable
private fun LocalNodeRow(node: ComputeNodeDto, detail: ComputeNodeDetailDto?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(10.dp).background(
                    color = Color(0xFF10B981),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
            )
            Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(Color(0xFF3B82F6).copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("local", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6))
            }
        }
        if (detail == null) {
            Text(
                stringResource(R.string.obs_cn_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        } else {
            // CPU + Memory row
            val hasCpu = detail.cpu != null || detail.cpuPct != null
            val hasMem = detail.mem != null
            if (hasCpu || hasMem) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.cpu?.let { cpu ->
                        StatChip(label = stringResource(R.string.obs_cn_node_cpu), value = "${cpu.pct.toInt()}%", color = cpuColor(cpu.pct))
                    } ?: detail.cpuPct?.let { pct ->
                        StatChip(label = stringResource(R.string.obs_cn_node_cpu), value = "${pct.toInt()}%", color = cpuColor(pct))
                    }
                    detail.mem?.let { mem ->
                        val usedGb = mem.usedBytes / 1_073_741_824.0
                        val totalGb = mem.totalBytes / 1_073_741_824.0
                        StatChip(label = stringResource(R.string.obs_cn_node_mem), value = "${usedGb.toInt()}/${totalGb.toInt()} GB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // One row per GPU
            detail.gpu.forEachIndexed { gpuIdx, gpu ->
                val gpuLabel = if (detail.gpu.size > 1) "GPU ${gpuIdx + 1}" else "GPU"
                val vramUsedGb = gpu.memUsedBytes / 1_073_741_824.0
                val vramTotalGb = gpu.memTotalBytes / 1_073_741_824.0
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp)) {
                    Text(
                        gpuLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip(label = stringResource(R.string.obs_cn_gpu_util), value = "${gpu.utilPct.toInt()}%", color = Color(0xFF3B82F6))
                        if (gpu.powerW > 0) {
                            StatChip(label = stringResource(R.string.obs_cn_gpu_power), value = "${gpu.powerW.toInt()} W", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (vramTotalGb > 0) {
                            StatChip(label = stringResource(R.string.obs_cn_gpu_vram), value = "${vramUsedGb.toInt()}/${vramTotalGb.toInt()} GB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerResourceRow(peer: ObserverPeerDto, detail: ComputeNodeDetailDto?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Health dot
            Box(
                modifier = Modifier.size(10.dp).background(
                    color = peerHealthDotColor(peer.lastPushAt),
                    shape = CircleShape,
                ),
            )
            Text(peer.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            // Shape badge
            val shape = (peer.hostInfo?.shape ?: peer.shape).ifBlank { "—" }
            val shapeColor = when (shape) {
                "agent" -> Color(0xFF8B5CF6)
                "cluster" -> Color(0xFF10B981)
                "standalone" -> Color(0xFF3B82F6)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .background(shapeColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(shape, style = MaterialTheme.typography.labelSmall, color = shapeColor)
            }
        }
        if (detail == null) {
            Text(
                stringResource(R.string.obs_cn_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        } else {
            // CPU + Memory row
            val hasCpu = detail.cpu != null || detail.cpuPct != null
            val hasMem = detail.mem != null
            if (hasCpu || hasMem) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.cpu?.let { cpu ->
                        StatChip(
                            label = stringResource(R.string.obs_cn_node_cpu),
                            value = "${cpu.pct.toInt()}%",
                            color = cpuColor(cpu.pct),
                        )
                    } ?: detail.cpuPct?.let { pct ->
                        StatChip(
                            label = stringResource(R.string.obs_cn_node_cpu),
                            value = "${pct.toInt()}%",
                            color = cpuColor(pct),
                        )
                    }
                    detail.mem?.let { mem ->
                        val usedGb = mem.usedBytes / 1_073_741_824.0
                        val totalGb = mem.totalBytes / 1_073_741_824.0
                        StatChip(
                            label = stringResource(R.string.obs_cn_node_mem),
                            value = "${usedGb.toInt()}/${totalGb.toInt()} GB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // One row per GPU with all GPU stats
            detail.gpu.forEachIndexed { gpuIdx, gpu ->
                val gpuLabel = if (detail.gpu.size > 1) "GPU ${gpuIdx + 1}" else "GPU"
                val tempColor = when {
                    gpu.tempC >= 80 -> Color(0xFFEF4444)
                    gpu.tempC >= 60 -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }
                val vramUsedGb = gpu.memUsedBytes / 1_073_741_824.0
                val vramTotalGb = gpu.memTotalBytes / 1_073_741_824.0
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp)) {
                    Text(
                        gpuLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip(
                            label = stringResource(R.string.obs_cn_gpu_util),
                            value = "${gpu.utilPct.toInt()}%",
                            color = Color(0xFF3B82F6),
                        )
                        if (gpu.tempC > 0) {
                            StatChip(
                                label = stringResource(R.string.obs_cn_gpu_temp),
                                value = "${gpu.tempC.toInt()}°C",
                                color = tempColor,
                            )
                        }
                        if (gpu.powerW > 0) {
                            StatChip(
                                label = stringResource(R.string.obs_cn_gpu_power),
                                value = "${gpu.powerW.toInt()} W",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (vramTotalGb > 0) {
                            StatChip(
                                label = stringResource(R.string.obs_cn_gpu_vram),
                                value = "${vramUsedGb.toInt()}/${vramTotalGb.toInt()} GB",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                "$label: $value",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
    )
}

private fun cpuColor(pct: Double) = when {
    pct >= 90 -> Color(0xFFEF4444)
    pct >= 70 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
}

private fun peerHealthDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val parsed = runCatching { kotlinx.datetime.Instant.parse(lastPushAt) }.getOrNull()
        ?: return Color(0xFF94A3B8)
    val ageSec = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()) / 1000
    return when {
        ageSec <= 15 -> Color(0xFF10B981)
        ageSec <= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}

public class PeerResourcesViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val peers: List<Pair<ObserverPeerDto, ComputeNodeDetailDto?>> = emptyList(),
        /** Auto-created local compute nodes (e.g. the server's own "datawatch-stats" node). */
        val localNodes: List<Pair<ComputeNodeDto, ComputeNodeDetailDto?>> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            // Peer compute nodes
            val peersWithDetails = transport.observerPeers().getOrNull()?.peers.orEmpty().map { peer ->
                val detail = peer.computeNode?.let { transport.getComputeNodeDetail(it).getOrNull() }
                peer to detail
            }
            // Auto-created local nodes (e.g. the server's own "datawatch-stats") not already
            // shown via a peer row. Must use autoCreated, not observerPeer != null: the local
            // server node has observerPeer == null (it IS the server, not a peer of itself).
            // Dedup against BOTH the peer's bound computeNode name AND the peer's own name:
            // a local node named "johnnyjohnny" must be excluded if there is an observer peer
            // also named "johnnyjohnny", even when that peer's computeNode field differs.
            val peerExcludes = peersWithDetails
                .flatMap { (peer, _) -> listOfNotNull(peer.computeNode, peer.name) }
                .toSet()
            val localNodesWithDetails = transport.listComputeNodes().getOrNull().orEmpty()
                .filter { node -> node.autoCreated && node.name !in peerExcludes }
                .map { node -> node to transport.getComputeNodeDetail(node.name).getOrNull() }
            _state.value = UiState(loading = false, peers = peersWithDetails, localNodes = localNodesWithDetails)
        }
    }
}
