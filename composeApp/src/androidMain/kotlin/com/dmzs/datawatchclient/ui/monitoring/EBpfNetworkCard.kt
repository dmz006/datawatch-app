package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import com.dmzs.datawatchclient.ui.settings.Section
import com.dmzs.datawatchclient.ui.stats.StatsViewModel

/**
 * B63 — per-process eBPF network viewer. Reads envelopes from the active
 * StatsDto and renders a per-process bandwidth table sorted by total
 * throughput (rx + tx). Only renders when [StatsDto.ebpfActive] is true
 * and at least one envelope carries non-zero network counters.
 *
 * Lives under Settings → Monitor, after EBpfStatusCard.
 */
@Composable
public fun EBpfNetworkCard(vm: StatsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    val stats = state.stats ?: return

    // When eBPF is configured but probes aren't active (server-side issue),
    // show a placeholder rather than vanishing — so the user knows why data is missing.
    if (!stats.ebpfActive) {
        if (stats.ebpfEnabled == true) {
            Section(title = stringResource(R.string.stats_section_process_network)) {
                Text(
                    text = stringResource(R.string.stats_ebpf_configured_not_active),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        return
    }

    val rows = stats.envelopes
        .filter { it.netRxBps > 0 || it.netTxBps > 0 }
        .sortedByDescending { it.netRxBps + it.netTxBps }

    if (rows.isEmpty()) return

    Section(title = stringResource(R.string.stats_section_process_network)) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            // Header row
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    "Process",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "↓ RX",
                    modifier = Modifier.padding(end = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "↑ TX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            rows.forEach { env ->
                ProcessNetworkRow(env)
            }
        }
    }
}

@Composable
private fun ProcessNetworkRow(env: StatEnvelopeDto) {
    val label = env.label.ifBlank { env.id.take(16) }
    val pidStr = if (env.rootPid > 0) " (${env.rootPid})" else ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (pidStr.isNotEmpty()) {
                Text(
                    "pid$pidStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            formatBps(env.netRxBps),
            modifier = Modifier.padding(end = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            formatBps(env.netTxBps),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBps(bps: Long): String =
    when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
        bps >= 1_000 -> "%.1f KB/s".format(bps / 1_000.0)
        bps > 0 -> "$bps B/s"
        else -> "—"
    }
