package com.dmzs.datawatchclient.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.transport.dto.StatsDto

/**
 * Live stats dashboard — host CPU/mem/disk/GPU + session counts + uptime.
 * Polls every 5 s via [StatsViewModel]. Source of truth is /api/stats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats" + (state.serverName?.let { " — $it" } ?: "")) },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        if (state.refreshing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize(),
        ) {
            state.banner?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val s = state.stats
            if (s == null && state.banner == null) {
                Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (s != null) {
                ResourceCard("Host resources", s)
                SessionCountsCard(s)
                UptimeCard(s)
            }
        }
    }
}

@Composable
private fun ResourceCard(
    title: String,
    s: StatsDto,
) {
    SectionCard(title) {
        ResourceRow("CPU", s.cpuPct)
        ResourceRow("Memory", s.memPct)
        s.diskPct?.let { ResourceRow("Disk", it) }
        s.gpuPct?.let { ResourceRow("GPU", it) }
    }
}

@Composable
private fun ResourceRow(
    label: String,
    pct: Double?,
) {
    if (pct == null) return
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${"%.1f".format(pct)} %", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = pctColor(pct),
        )
    }
}

@Composable
private fun pctColor(pct: Double): Color =
    when {
        pct >= 90 -> MaterialTheme.colorScheme.error
        pct >= 70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun SessionCountsCard(s: StatsDto) {
    SectionCard("Sessions") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("Total", s.sessionsTotal.toString())
            Stat("Running", s.sessionsRunning.toString(), MaterialTheme.colorScheme.primary)
            Stat("Waiting", s.sessionsWaiting.toString(), MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UptimeCard(s: StatsDto) {
    SectionCard("Daemon") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Uptime", style = MaterialTheme.typography.bodyMedium)
            Text(formatUptime(s.uptimeSeconds), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3600
    val m = (seconds % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (h > 0 || d > 0) append("${h}h ")
        append("${m}m")
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}
