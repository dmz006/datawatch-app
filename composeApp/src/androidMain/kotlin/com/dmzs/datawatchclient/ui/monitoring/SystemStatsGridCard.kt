@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.ObserverPeerDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.ui.common.LiveDot
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Observer — System Stats Grid (BL379 parity).
 * One card per system: local server (from /api/stats) + each observer peer
 * (/api/observer/peers/{name}/stats). Shows CPU/RAM/GPU progress bars
 * matching the PWA's perSystemGrid. Refreshes every 10 s.
 */
@Composable
public fun SystemStatsGridCard(vm: SystemStatsGridViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(10_000)
            vm.refresh()
        }
    }

    if (state.local == null && state.peers.isEmpty() && !state.loading) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PwaSectionTitle(
                    title = "System Resources",
                    modifier = Modifier.weight(1f),
                )
                LiveDot()
            }
            state.local?.let { LocalSystemCard(it) }
            state.peers.forEach { (peer, detail) -> PeerSystemCard(peer, detail) }
        }
    }
}

@Composable
private fun LocalSystemCard(stats: StatsDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
            Text(
                "local",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFF3B82F6).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text("local", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B82F6))
            }
        }

        val cpuLoad = stats.cpuLoad1 ?: 0.0
        val cpuCores = (stats.cpuCores ?: 1).coerceAtLeast(1)
        val cpuPct = (cpuLoad / cpuCores).coerceIn(0.0, 1.0).toFloat()
        val cpuLabel = "${String.format("%.2f", cpuLoad)}/$cpuCores cores"
        StatBar(label = "CPU", pct = cpuPct, valueLabel = cpuLabel, color = cpuBarColor(cpuPct.toDouble() * 100.0))

        val memUsed = stats.memUsed ?: 0L
        val memTotal = (stats.memTotal ?: 1L).coerceAtLeast(1L)
        val memPct = (memUsed.toFloat() / memTotal.toFloat()).coerceIn(0f, 1f)
        StatBar(
            label = "RAM",
            pct = memPct,
            valueLabel = "${fmtBytes(memUsed)} / ${fmtBytes(memTotal)}",
            color = if (memPct > 0.85f) Color(0xFFEF4444) else Color(0xFF3B82F6),
        )

        if (stats.gpuName != null) {
            val gpuUtil = (stats.gpuUtilPct ?: 0.0).toFloat() / 100f
            val gpuColor = if (gpuUtil > 0.8f) Color(0xFFEF4444) else Color(0xFF60A5FA)
            val gpuLabel = "${stats.gpuUtilPct?.toInt() ?: 0}% · ${stats.gpuTemp?.toInt() ?: 0}°C"
            StatBar(label = "GPU util", pct = gpuUtil, valueLabel = gpuLabel, color = gpuColor)
            val gpuUsedMb = stats.gpuMemUsedMb ?: 0L
            val gpuTotalMb = (stats.gpuMemTotalMb ?: 0L)
            if (gpuTotalMb > 0) {
                val vramPct = (gpuUsedMb.toFloat() / gpuTotalMb.toFloat()).coerceIn(0f, 1f)
                StatBar(
                    label = "GPU VRAM",
                    pct = vramPct,
                    valueLabel = "$gpuUsedMb / $gpuTotalMb MB",
                    color = Color(0xFF60A5FA),
                )
            }
        }
    }
}

private fun sysGridDotColor(lastPushAt: String?): Color {
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

@Composable
private fun PeerSystemCard(peer: ObserverPeerDto, detail: ComputeNodeDetailDto?) {
    val dotColor = sysGridDotColor(peer.lastPushAt)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(
                peer.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            val shape = (peer.hostInfo?.shape ?: peer.shape).ifBlank { null }
            if (shape != null) {
                val shapeColor = when (shape) {
                    "agent" -> Color(0xFF8B5CF6)
                    "cluster" -> Color(0xFF10B981)
                    else -> Color(0xFF3B82F6)
                }
                Box(
                    modifier = Modifier
                        .background(shapeColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(shape, style = MaterialTheme.typography.labelSmall, color = shapeColor)
                }
            }
        }

        if (detail == null) {
            Text(
                "No stats available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
            return@Column
        }

        detail.cpu?.let { cpu ->
            val cpuPct = (cpu.pct / 100.0).coerceIn(0.0, 1.0).toFloat()
            val loadStr = if (cpu.load1 > 0 || cpu.load5 > 0)
                "${cpu.pct.toInt()}% · ${String.format("%.2f", cpu.load1)}/${String.format("%.2f", cpu.load5)}/${String.format("%.2f", cpu.load15)}"
            else
                "${cpu.pct.toInt()}%"
            StatBar(label = "CPU", pct = cpuPct, valueLabel = loadStr, color = cpuBarColor(cpu.pct))
        } ?: detail.cpuPct?.let { pct ->
            val cpuPct = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()
            StatBar(label = "CPU", pct = cpuPct, valueLabel = "${pct.toInt()}%", color = cpuBarColor(pct))
        }

        detail.mem?.let { mem ->
            val memPct = if (mem.totalBytes > 0)
                (mem.usedBytes.toFloat() / mem.totalBytes.toFloat()).coerceIn(0f, 1f)
            else
                mem.pct.toFloat() / 100f
            StatBar(
                label = "RAM",
                pct = memPct,
                valueLabel = "${fmtBytes(mem.usedBytes)} / ${fmtBytes(mem.totalBytes)}",
                color = if (memPct > 0.85f) Color(0xFFEF4444) else Color(0xFF3B82F6),
            )
        }

        detail.gpu.forEachIndexed { idx, gpu ->
            val label = if (detail.gpu.size > 1) "GPU ${idx + 1}" else "GPU"
            val utilPct = (gpu.utilPct / 100.0).coerceIn(0.0, 1.0).toFloat()
            val gpuColor = if (utilPct > 0.8f) Color(0xFFEF4444) else Color(0xFF60A5FA)
            StatBar(label = "$label util", pct = utilPct, valueLabel = "${gpu.utilPct.toInt()}%", color = gpuColor)
            if (gpu.tempC > 0) {
                val tempColor = when {
                    gpu.tempC >= 80 -> Color(0xFFEF4444)
                    gpu.tempC >= 60 -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }
                val tempPct = (gpu.tempC / 100.0).coerceIn(0.0, 1.0).toFloat()
                StatBar(label = "$label temp", pct = tempPct, valueLabel = "${gpu.tempC.toInt()}°C", color = tempColor)
            }
            if (gpu.memTotalBytes > 0) {
                val vramPct = (gpu.memUsedBytes.toFloat() / gpu.memTotalBytes.toFloat()).coerceIn(0f, 1f)
                StatBar(
                    label = "$label VRAM",
                    pct = vramPct,
                    valueLabel = "${fmtBytes(gpu.memUsedBytes)} / ${fmtBytes(gpu.memTotalBytes)}",
                    color = Color(0xFF60A5FA),
                )
            }
        }
    }
}

@Composable
private fun StatBar(label: String, pct: Float, valueLabel: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private fun cpuBarColor(pct: Double): Color = when {
    pct >= 80 -> Color(0xFFEF4444)
    pct >= 50 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
}

private fun fmtBytes(b: Long): String = when {
    b >= 1_000_000_000_000L -> "${String.format("%.1f", b / 1_000_000_000_000.0)} TB"
    b >= 1_000_000_000L -> "${String.format("%.1f", b / 1_000_000_000.0)} GB"
    b >= 1_000_000L -> "${String.format("%.1f", b / 1_000_000.0)} MB"
    b > 0L -> "$b B"
    else -> "—"
}

public class SystemStatsGridViewModel(
    private val resolver: com.dmzs.datawatchclient.ui.common.ProfileResolver =
        com.dmzs.datawatchclient.ui.common.ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val local: StatsDto? = null,
        val peers: List<Pair<ObserverPeerDto, ComputeNodeDetailDto?>> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            val local = transport.stats().getOrNull()
            val peers = transport.observerPeers().getOrNull()?.peers.orEmpty().map { peer ->
                val detail = runCatching { transport.getObserverPeerStats(peer.name).getOrNull() }.getOrNull()
                peer to detail
            }
            _state.value = UiState(loading = false, local = local, peers = peers)
        }
    }
}
