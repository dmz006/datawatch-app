package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.ui.settings.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings → Monitor → Federated peers card. Mirrors PWA
 * `loadObserverPeers()` (datawatch v4.4.0+).
 *
 * Renders Shape B / C / Agent peers registered with the parent.
 * Each row carries a coloured health dot (green ≤15 s push age,
 * amber ≤60 s, red >60 s, grey if never), a shape badge, the
 * peer's last-push age, and the underlying hostname.
 *
 * v0.36.0 (issue #2 + #6): the card hides itself when the server
 * returns no peers (single-node setup). The Agents/Standalone/
 * Cluster filter pill row above the list closes #6 — taps narrow
 * the rendered list without re-fetching.
 */
@Composable
public fun FederatedPeersCard(vm: FederatedPeersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    if (state.peers.isEmpty() && !state.loading) return // single-node — hide entirely

    Section(title = "Federated peers") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // Filter pills (issue #6 — S13 agents filter).
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
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
            val visible =
                state.peers.filter { peer ->
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
                visible.forEach { peer -> PeerRow(peer = peer) }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: ObserverPeerDto) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Health dot — green/amber/red driven by last-push age,
        // grey when never pushed. Mirrors the PWA dot semantics.
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .background(
                        color = healthDotColor(peer.lastPushAt),
                        shape = CircleShape,
                    ),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                peer.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    val effectiveShape =
                        peer.hostInfo?.shape?.takeIf { it.isNotBlank() } ?: peer.shape
                    append(effectiveShape.ifBlank { "—" })
                    peer.version?.takeIf { it.isNotBlank() }?.let { append(" · v$it") }
                    peer.lastPushAt?.takeIf { it.isNotBlank() }?.let {
                        append(" · pushed $it")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        ShapeBadge(peer.hostInfo?.shape ?: peer.shape)
    }
}

@Composable
private fun ShapeBadge(shape: String) {
    val s = shape.lowercase()
    val (label, color) =
        when (s) {
            "agent" -> "agent" to Color(0xFF7C3AED)
            "cluster" -> "cluster" to Color(0xFF22C55E)
            "standalone" -> "standalone" to Color(0xFF3B82F6)
            else -> (s.ifBlank { "—" }) to MaterialTheme.colorScheme.onSurfaceVariant
        }
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .background(
                    color = color.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun healthDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val parsed =
        runCatching { kotlinx.datetime.Instant.parse(lastPushAt) }.getOrNull()
            ?: return Color(0xFF94A3B8)
    val ageSec =
        (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()) /
            1000
    return when {
        ageSec <= 15 -> Color(0xFF22C55E)
        ageSec <= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
}

public class FederatedPeersViewModel : ViewModel() {
    public enum class Filter { All, Standalone, Cluster, Agent }

    public data class UiState(
        val loading: Boolean = true,
        val peers: List<ObserverPeerDto> = emptyList(),
        val filter: Filter = Filter.All,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val activeId = ServiceLocator.activeServerStore.get() ?: return@launch
            val profile =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled } ?: return@launch
            ServiceLocator.transportFor(profile).observerPeers().fold(
                onSuccess = { dto ->
                    _state.value = _state.value.copy(
                        loading = false,
                        peers = dto.peers,
                        error = null,
                    )
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        loading = false,
                        peers = emptyList(),
                        error = err.message,
                    )
                },
            )
        }
    }

    public fun setFilter(filter: Filter) {
        _state.value = _state.value.copy(filter = filter)
    }
}
