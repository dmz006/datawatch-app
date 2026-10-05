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
import com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent
import com.dmzs.datawatchclient.ui.common.LiveDot
import com.dmzs.datawatchclient.ui.common.relativeTimeLabel
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState

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
    var crossHostOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    var snapshotTarget by remember { mutableStateOf<String?>(null) }
    if (crossHostOpen) CrossHostDialog(onDismiss = { crossHostOpen = false })
    snapshotTarget?.let { name -> PeerSnapshotDialog(name = name, onDismiss = { snapshotTarget = null }) }
    removeTarget?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.observer_remove_peer_title, name)) },
            text = { Text(stringResource(R.string.observer_remove_peer_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.removePeer(name)
                    removeTarget = null
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { removeTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    PwaCard(
        id = "observer_peers",
        title = "Federated peers",
        docsAnchor = "federated-peers",
        headerActions = { Box(Modifier.padding(end = 8.dp)) { LiveDot() } },
    ) {
        run {
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
            // PWA "↔ Cross-host view" — local + every peer with cross-peer caller attribution.
            androidx.compose.material3.TextButton(
                onClick = { crossHostOpen = true },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text("↔ " + stringResource(R.string.observer_cross_host_view), style = MaterialTheme.typography.labelSmall) }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (state.loading && state.peers.isEmpty()) {
                    DatawatchLoadingContent(verticalPadding = 16.dp)
                } else if (state.groupByNode) {
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
                            peers.forEach { peer -> PeerRow(peer, onRemove = { removeTarget = peer.name }, onSnapshot = { snapshotTarget = peer.name }) }
                        }
                        if (state.unbound.isNotEmpty()) {
                            Text(
                                stringResource(R.string.peer_group_unbound),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            state.unbound.forEach { peer -> PeerRow(peer, onRemove = { removeTarget = peer.name }, onSnapshot = { snapshotTarget = peer.name }) }
                        }
                    }
                } else {
                    // Flat view with filter pills (original)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // PWA pill order + counts: All / Agents / Standalone / Cluster.
                        listOf(
                            FederatedPeersViewModel.Filter.All to "All",
                            FederatedPeersViewModel.Filter.Agent to "Agents",
                            FederatedPeersViewModel.Filter.Standalone to "Standalone",
                            FederatedPeersViewModel.Filter.Cluster to "Cluster",
                        ).forEach { (f, label) ->
                            val count = state.peers.count { peerMatchesFilter(it, f) }
                            FilterChip(
                                selected = state.filter == f,
                                onClick = { vm.setFilter(f) },
                                label = { Text("$label ($count)", style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    val visible = state.peers.filter { peer -> peerMatchesFilter(peer, state.filter) }
                    if (visible.isEmpty()) {
                        Text(
                            "No peers in this group.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        visible.forEach { peer -> PeerRow(peer, onRemove = { removeTarget = peer.name }, onSnapshot = { snapshotTarget = peer.name }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: ObserverPeerDto,
    onRemove: () -> Unit = {},
    onSnapshot: () -> Unit = {},
) {
    val staleDotColor = staleDotColor(peer.lastPushAt)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(10.dp).background(
                    color = healthDotColor(peer.lastPushAt),
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.size(4.dp))
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = staleDotColor) }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
                    peer.lastPushAt?.takeIf { it.isNotBlank() }?.let { ts ->
                        val ageLabel = runCatching {
                            relativeTimeLabel(kotlinx.datetime.Instant.parse(ts).toEpochMilliseconds())
                        }.getOrDefault(ts)
                        append(" · pushed $ageLabel")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // alpha.24: use compute_node field directly — no second-fetch
        val nodeName = peer.computeNode
        if (nodeName != null) {
            SuggestionChip(
                onClick = {},
                label = { Text("⇄ $nodeName", style = MaterialTheme.typography.labelSmall) },
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF00897B).copy(alpha = 0.18f),
                        labelColor = Color(0xFF4DB6AC),
                    ),
            )
            Spacer(Modifier.size(4.dp))
        } else {
            SuggestionChip(
                onClick = {},
                label = { Text(stringResource(R.string.observer_free), style = MaterialTheme.typography.labelSmall) },
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
            Spacer(Modifier.size(4.dp))
        }
        ShapeBadge(peer.hostInfo?.shape ?: peer.shape)
        // Parity D55a — PWA 📊 opens the peer snapshot modal.
        androidx.compose.material3.IconButton(onClick = onSnapshot, modifier = Modifier.size(28.dp)) {
            Text("📊", style = MaterialTheme.typography.bodyMedium)
        }
        // PWA × — remove peer (rotates token; peer auto-re-registers).
        androidx.compose.material3.IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** PWA observer peer filter pills (`cs_peer_filter`). */
internal fun peerMatchesFilter(
    peer: ObserverPeerDto,
    filter: FederatedPeersViewModel.Filter,
): Boolean =
    when (filter) {
        FederatedPeersViewModel.Filter.All -> true
        FederatedPeersViewModel.Filter.Standalone -> peer.shape == "standalone"
        FederatedPeersViewModel.Filter.Cluster -> peer.shape == "cluster"
        FederatedPeersViewModel.Filter.Agent -> peer.shape == "agent" || peer.hostInfo?.shape == "agent"
    }

/** One envelope row in the peer snapshot (PWA `renderObserverSnapshot`). */
internal data class SnapshotEnvelope(
    val kind: String,
    val id: String,
    val cpuPct: Double,
    val rssMb: Long,
    val procs: Int,
    val fds: Int,
)

private fun kotlinx.serialization.json.JsonObject.num(key: String): Double =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0

private fun kotlinx.serialization.json.JsonObject.txt(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

/** Header line `name · host · os arch · uptime Ns` (PWA observerSnapPeerLine). */
internal fun snapshotHeaderLine(
    name: String,
    snap: kotlinx.serialization.json.JsonObject,
): String {
    val host = snap["host"] as? kotlinx.serialization.json.JsonObject
    val hostName = host?.txt("name") ?: "?"
    val os = host?.txt("os") ?: "?"
    val arch = host?.txt("arch").orEmpty()
    val uptime = host?.num("uptime_seconds")?.toLong() ?: 0L
    return "$name · $hostName · $os $arch · uptime ${uptime}s"
}

/** Envelopes sorted by CPU desc, as the PWA renders them. */
internal fun snapshotEnvelopes(snap: kotlinx.serialization.json.JsonObject): List<SnapshotEnvelope> =
    (snap["envelopes"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        .map { e ->
            SnapshotEnvelope(
                kind = e.txt("kind") ?: "?",
                id = e.txt("id") ?: "?",
                cpuPct = e.num("cpu_pct"),
                rssMb = Math.round(e.num("rss_bytes") / 1e6),
                procs = e.num("process_count").toInt(),
                fds = e.num("open_fds").toInt(),
            )
        }
        .sortedByDescending { it.cpuPct }

/**
 * Parity D55a — PWA `showObserverPeerSnapshot`: "Peer snapshot" modal fed by
 * GET /api/observer/peers/{name}/stats — header line, then envelopes sorted by
 * CPU with cpu / rss / procs / fds.
 */
@Composable
private fun PeerSnapshotDialog(
    name: String,
    onDismiss: () -> Unit,
) {
    var snap by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        val (_, transport) = com.dmzs.datawatchclient.ui.common.ProfileResolver.Default.resolve() ?: return@LaunchedEffect
        transport.fetchObserverPeerSnapshot(name).fold(
            onSuccess = { snap = it },
            onFailure = { error = it.message ?: it::class.simpleName },
        )
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.observer_peer_snapshot_title))
                Text(
                    snap?.let { snapshotHeaderLine(name, it) } ?: "$name — loading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                val s = snap
                when {
                    error != null ->
                        Text(
                            stringResource(R.string.observer_peer_snapshot_unavailable, error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    s == null ->
                        Text(
                            "Fetching /api/observer/peers/$name/stats …",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else -> {
                        val envs = snapshotEnvelopes(s)
                        if (envs.isEmpty()) {
                            Text(
                                stringResource(R.string.observer_peer_snapshot_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        envs.forEach { e ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("${e.kind} · ${e.id}", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(
                                    "cpu ${"%.1f".format(e.cpuPct)}% · rss ${e.rssMb} MB · ${e.procs} procs · ${e.fds} fds",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** PWA `showCrossHostView`: envelopes grouped by peer with 🔗 cross-host caller tags. */
@Composable
private fun CrossHostDialog(onDismiss: () -> Unit) {
    var data by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (_, transport) =
            com.dmzs.datawatchclient.ui.common.ProfileResolver.Default.resolve() ?: run {
                error = "no server"
                return@LaunchedEffect
            }
        transport.fetchCrossHostEnvelopesJson()
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "load failed" }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.observer_cross_host_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val err = error
                val obj = data
                when {
                    err != null -> Text("load failed: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    obj == null -> DatawatchLoadingContent(verticalPadding = 12.dp)
                    else -> {
                        val byPeer = obj["by_peer"] as? kotlinx.serialization.json.JsonObject
                        if (byPeer.isNullOrEmpty()) {
                            Text(
                                stringResource(R.string.observer_cross_host_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        byPeer?.forEach { (peer, envs) ->
                            val list = (envs as? kotlinx.serialization.json.JsonArray).orEmpty()
                            Text(
                                "$peer  (${list.size} envelopes)",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                            list.forEach { e -> CrossHostEnvelopeRow(e as? kotlinx.serialization.json.JsonObject ?: return@forEach) }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

@Composable
private fun CrossHostEnvelopeRow(e: kotlinx.serialization.json.JsonObject) {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            listOf(e.str("id").ifBlank { "?" }, e.str("kind"), e.str("label")).filter { it.isNotBlank() }.joinToString("  "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = mono,
        )
        fun addrs(key: String, ip: String, port: String) =
            (e[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                .joinToString(", ") { "${it.str(ip)}:${it.str(port)}" }
        addrs("listen_addrs", "ip", "port").takeIf { it.isNotBlank() }?.let {
            Text("listen: $it", style = MaterialTheme.typography.labelSmall, color = dim)
        }
        addrs("outbound_edges", "target_ip", "target_port").takeIf { it.isNotBlank() }?.let {
            Text("outbound: $it", style = MaterialTheme.typography.labelSmall, color = dim)
        }
        (e["callers"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .forEach { c ->
                val caller = c.str("caller").ifBlank { "?" }
                val cross = isCrossHostCaller(caller)
                Text(
                    (if (cross) "🔗 cross  " else "") + "$caller  ${c.str("caller_kind")} · ${c.str("conns").ifBlank { "0" }} conns",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cross) MaterialTheme.colorScheme.primary else dim,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
    }
}

/** PWA: a caller is cross-host when it has at least three `:`-separated parts (`peer:kind:id`). */
internal fun isCrossHostCaller(caller: String): Boolean = caller.contains(':') && caller.split(':').size >= 3

@Composable
private fun ShapeBadge(shape: String) {
    val s = shape.lowercase()
    val (label, color) =
        when (s) {
            "agent" -> "agent" to Color(0xFF8B5CF6)
            "cluster" -> "cluster" to Color(0xFF10B981)
            "standalone" -> "standalone" to Color(0xFF3B82F6)
            else -> (s.ifBlank { "—" }) to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun staleDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val ageHours =
        runCatching {
            val parsed = kotlinx.datetime.Instant.parse(lastPushAt)
            val ageMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()
            (ageMs / 3_600_000).toInt()
        }.getOrDefault(-1)
    return when {
        ageHours < 0 -> Color(0xFF94A3B8)
        ageHours < 1 -> Color(0xFF10B981)
        ageHours < 6 -> Color(0xFFFFB300)
        else -> Color(0xFFEF4444)
    }
}

private fun healthDotColor(lastPushAt: String?): Color {
    if (lastPushAt.isNullOrBlank()) return Color(0xFF94A3B8)
    val parsed =
        runCatching { kotlinx.datetime.Instant.parse(lastPushAt) }.getOrNull()
            ?: return Color(0xFF94A3B8)
    val ageSec = (kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()) / 1000
    return when {
        ageSec <= 15 -> Color(0xFF10B981)
        ageSec <= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
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
                    val stale =
                        dto.peers.any { peer ->
                            runCatching {
                                val parsed = kotlinx.datetime.Instant.parse(peer.lastPushAt ?: return@any false)
                                val ageMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - parsed.toEpochMilliseconds()
                                ageMs >= 6 * 3_600_000L
                            }.getOrDefault(false)
                        }
                    _state.value =
                        _state.value.copy(
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

    /** PWA `removeObserverPeer`: DELETE /api/observer/peers/{name}; result → alert dock (D41a). */
    public fun removePeer(name: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.removeObserverPeer(name).fold(
                onSuccess = {
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post("Removed peer $name")
                    refresh()
                },
                onFailure = { e ->
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        "Remove failed: ${e.message ?: e::class.simpleName}",
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                    )
                },
            )
        }
    }

    private suspend fun loadByNode(transport: com.dmzs.datawatchclient.transport.TransportClient) {
        transport.getObserverPeersByNode().onSuccess { dto ->
            _state.value = _state.value.copy(byNode = dto.byNode, unbound = dto.unbound)
        }
    }
}
