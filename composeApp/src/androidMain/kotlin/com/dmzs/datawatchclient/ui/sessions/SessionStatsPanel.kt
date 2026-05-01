package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import com.dmzs.datawatchclient.ui.stats.StatsViewModel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * B11 — per-session process stats panel. Reads the active StatsDto's
 * `envelopes` list, finds the entry whose id matches [sessionId] (prefix-
 * or exact-match), and renders CPU %, RSS, and net Rx/Tx throughput.
 *
 * Self-hides the data rows when no matching envelope is found (eBPF may
 * not be active or the daemon predates observer envelopes).
 */
@Composable
public fun SessionStatsPanel(
    sessionId: String,
    modifier: Modifier = Modifier,
    statsVm: StatsViewModel = viewModel(),
) {
    val statsState by statsVm.state.collectAsState()
    val envelope: StatEnvelopeDto? = statsState.stats?.envelopes?.firstOrNull { env ->
        env.kind == "session" && (
            env.id == sessionId ||
                sessionId.startsWith(env.id) ||
                env.id.startsWith(sessionId)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        if (envelope != null) {
            EnvelopeStatsCard(envelope)
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.session_detail_stats_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EnvelopeStatsCard(env: StatEnvelopeDto) {
    val dw = LocalDatawatchColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle("Process Stats")
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // CPU ring + summary row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cpuFraction = (env.cpuPct / 100.0).toFloat().coerceIn(0f, 1f)
                    val cpuColor = when {
                        env.cpuPct >= 90 -> MaterialTheme.colorScheme.error
                        env.cpuPct >= 70 -> dw.warning
                        else -> dw.success
                    }
                    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(72.dp),
                            color = dw.bg3,
                            strokeWidth = 6.dp,
                            trackColor = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        CircularProgressIndicator(
                            progress = { cpuFraction },
                            modifier = Modifier.size(72.dp),
                            color = cpuColor,
                            strokeWidth = 6.dp,
                            trackColor = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        Text(
                            "%.0f%%".format(env.cpuPct),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cpuColor,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StatRow("CPU", "%.1f%%".format(env.cpuPct))
                        StatRow("RSS", formatBytes(env.rssBytes))
                        if (env.threads > 0) StatRow("Threads", env.threads.toString())
                        if (env.fds > 0) StatRow("FDs", env.fds.toString())
                    }
                }
                // Network throughput
                if (env.netRxBps > 0 || env.netTxBps > 0) {
                    StatRow("Net ↓", "${formatBytes(env.netRxBps)}/s")
                    StatRow("Net ↑", "${formatBytes(env.netTxBps)}/s")
                }
                // GPU (when present)
                if (env.gpuPct > 0.0 || env.gpuMemBytes > 0) {
                    if (env.gpuPct > 0.0) StatRow("GPU", "%.1f%%".format(env.gpuPct))
                    if (env.gpuMemBytes > 0) StatRow("GPU Mem", formatBytes(env.gpuMemBytes))
                }
                // PID list (collapsed to root + count)
                if (env.rootPid > 0) {
                    val pidLabel = if (env.pids.size > 1) "${env.rootPid} (+${env.pids.size - 1})" else env.rootPid.toString()
                    StatRow("PID", pidLabel)
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
