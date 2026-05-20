package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
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
import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeersByNodeDto
import com.dmzs.datawatchclient.ui.common.LiveDot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings → Monitor → Federated peers card. Mirrors PWA
 * `loadObserverPeers()` / `loadObserverPeersByNode()` (datawatch v4.4.0+ / alpha.24).
 *
 * Renders Shape B / C / Agent peers registered with the parent.
 * Each row carries a coloured health dot (green ≤15 s push age,
 * amber ≤60 s, red >60 s, grey if never), a shape badge, the
 * peer's last-push age, and the underlying hostname.
 *
 * v0.36.0 (#2 + #6): card hides on zero peers (single-node setup).
 * v0.88.0 Sprint 19 (#111): "Group by ComputeNode" toggle (alpha.24 #231).
 *   - Uses `compute_node` field on peer responses — no second-fetch needed.
 *   - Toggle ON fetches `/api/observer/peers/by-node` for bucketed view.
 */
@Composable
public fun FederatedPeersCard(vm: FederatedPeersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(8_000)
            vm.refresh()
        }
    }

    if (state.peers.isEmpty() && !state.loading) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Federated peers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                LiveDot()
            }
            // Group-by-node toggle row (alpha.24 #231)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(R.string.peer_group_by_node),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.peer_group_by_node_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.groupByNode,
                    onCheckedChange = { vm.setGroupByNode(it) },
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (state.groupByNode) {
                    // Bucketed view: one section per ComputeNode + unbound
                    if (state.byNode.isEmpty() && state.unbound.isEmpty()) {
                        Text(
                            "No peers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.byNode.forEach { (nodeName, peers) ->
                            Text(
                                nodeName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            peers.forEach { peer -> PeerRow(peer) }
                        }
                        if (state.unbound.isNotEmpty()) {
                            Text(
                                stringResource(R.string.peer_group_unbound),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            state.unbound.forEach { peer -> PeerRow(peer) }
                        }
                    }
                } else {
                    // Flat view with filter pills (original)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            FederatedPeersViewModel.Filter.All to "All",
                            FederatedPeersViewModel.Filter.Standalone to "Standalone",
                            FederatedPeersViewModel.Filter.Cluster to "Cluster",
                            FederatedPeersViewModel.Filter.Agent to "Agents",
                        ).forEach { (f, label) ->
                            FilterChip(
                                selected = state.filter == f,
                                onClick = { vm.setFilter(f) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    val visible = state.peers.filter { peer ->
                        when (state.filter) {
                            FederatedPeersViewModel.Filter.All -> true
                            FederatedPeersViewModel.Filter.Standalone -> peer.shape == "standalone"
                            FederatedPeersViewModel.Filter.Cluster -> peer.shape == "cluster"
                            FederatedPeersViewModel.Filter.Agent ->
                                peer.shape == "agent" || peer.hostInfo?.shape == "agent"
                        }
                    }
                    if (visible.isEmpty()) {
                        Text(
                            "No peers in this group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visible.forEach { peer -> PeerRow(peer) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: ObserverPeerDto) {
    val staleDotColor = staleDotColor(peer.lastPushAt)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(10.dp).background(
                color = healthDotColor(peer.lastPushAt),
                shape = CircleShape,
            ),
        )
        Spacer(Modifier.size(4.dp))
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = staleDotColor) }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                peer.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    val effectiveShape = peer.hostInfo?.shape?.takeIf { it.isNotBlank() } ?: peer.shape
                    append(effectiveShape.ifBlank { "—" })
                    peer.version?.takeIf { it.isNotBlank() }?.let { append(" · v$it") }
                    peer.lastPushAt?.takeIf { it.isNotBlank() }?.let { append(" · pushed $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        // alpha.24: use compute_node field directly — no second-fetch
        val nodeName = peer.computeNode
        if (nodeName != null) {
            SuggestionChip(
                onClick = {},
                label = { Text("⇄ $nodeName", style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF00897B).copy(alpha = 0.18f),
                    labelColor = Color(0xFF4DB6AC),
                ),
            )
            Spacer(Modifier.size(4.dp))
        } else {
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(R.string.observer_free), style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.size(4.dp))
        }
        ShapeBadge(peer.hostInfo?.shape ?: peer.shape)
    }
}

@Composable
private fun ShapeBadge(shape: String) {
    val s = shape.lowercase()
    val (label, color) = when (s) {
        "agent" -> "agent" to Color(0xFF7C3AED)
        "cluster" -> "cluster" to Color(0xFF10B981)
        "standalone" -> "standalone" to Color(0xFF3B82F6)
        else -> (s.ifBlank { "—" }) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun staleDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val ageHours = runCatching {
        val parsed = kotlinx.datetime.Instant.parse(lastPushAt)
        val ageMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()
        (ageMs / 3_600_000).toInt()
    }.getOrDefault(-1)
    return when {
        ageHours < 0  -> Color(0xFF94A3B8)
        ageHours < 1  -> Color(0xFF00E676)
        ageHours < 6  -> Color(0xFFFFB300)
        else          -> Color(0xFFEF4444)
    }
}

private fun healthDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val parsed = runCatching { kotlinx.datetime.Instant.parse(lastPushAt) }.getOrNull()
        ?: return Color(0xFF94A3B8)
    val ageSec = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()) / 1000
    return when {
        ageSec <= 15 -> Color(0xFF10B981)
        ageSec <= 60 -> Color(0xFFF59E0B)
        else         -> Color(0xFFEF4444)
    }
}

public class FederatedPeersViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public enum class Filter { All, Standalone, Cluster, Agent }

    public data class UiState(
        val loading: Boolean = true,
        val peers: List<ObserverPeerDto> = emptyList(),
        val filter: Filter = Filter.All,
        val error: String? = null,
        val anyPeerStale: Boolean = false,
        /** alpha.24 #231: group-by-node toggle + bucketed data */
        val groupByNode: Boolean = false,
        val byNode: Map<String, List<ObserverPeerDto>> = emptyMap(),
        val unbound: List<ObserverPeerDto> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.observerPeers().fold(
                onSuccess = { dto ->
                    val stale = dto.peers.any { peer ->
                        runCatching {
                            val parsed = kotlinx.datetime.Instant.parse(peer.lastPushAt ?: return@any false)
                            val ageMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()
                            ageMs >= 6 * 3_600_000L
                        }.getOrDefault(false)
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        peers = dto.peers,
                        error = null,
                        anyPeerStale = stale,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(loading = false, peers = emptyList(), error = err.message)
                },
            )
            if (_state.value.groupByNode) loadByNode(transport)
        }
    }

    public fun setFilter(filter: Filter) {
        _state.value = _state.value.copy(filter = filter)
    }

    public fun setGroupByNode(on: Boolean) {
        _state.value = _state.value.copy(groupByNode = on)
        if (on) {
            viewModelScope.launch {
                val (_, transport) = resolver.resolve() ?: return@launch
                loadByNode(transport)
            }
        }
    }

    private suspend fun loadByNode(transport: com.dmzs.datawatchclient.transport.TransportClient) {
        transport.getObserverPeersByNode().onSuccess { dto ->
            _state.value = _state.value.copy(byNode = dto.byNode, unbound = dto.unbound)
        }
    }
}
