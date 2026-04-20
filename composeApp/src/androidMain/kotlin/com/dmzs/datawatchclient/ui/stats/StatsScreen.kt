package com.dmzs.datawatchclient.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * Live stats dashboard — PWA Settings → Monitor parity. Polls
 * `/api/stats` every 5 s + refreshes `/api/info` on the same cadence
 * (cheap, changes rarely). Renders as a column of `pwaCard()` sections
 * with the PWA's dark palette + bar-colour threshold behaviour.
 */

/**
 * Embedded Monitor content — used from Settings/Monitor sub-tab. The old
 * bottom-nav Stats tab is gone per PWA parity; all this content renders
 * inside Settings now. No Scaffold / TopAppBar — caller provides its own
 * chrome.
 */
@Composable
public fun StatsScreenContent(vm: StatsViewModel = viewModel()) {
    val state by vm.state.collectAsState()

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
    val info = state.info

    if (s == null && info == null && state.banner == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    info?.let { ServerInfoCard(it) }
    s?.let {
        SessionCountsCard(it)
        ResourceCard(it)
        UptimeCard(it, info)
    }
}

@Composable
private fun ServerInfoCard(info: ServerInfo) {
    PwaCardContainer {
        PwaSectionTitle("Server")
        InfoRow("Hostname", info.hostname)
        InfoRow("Daemon", "v${info.version}")
        info.llmBackend?.let { InfoRow("LLM backend", it) }
        info.messagingBackend?.let { InfoRow("Messaging", it) }
        if (info.serverHost != null && info.serverPort != null) {
            InfoRow("Bound to", "${info.serverHost}:${info.serverPort}")
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SessionCountsCard(s: StatsDto) {
    val dw = LocalDatawatchColors.current
    PwaCardContainer {
        PwaSectionTitle("Sessions")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BigStat("Total", s.sessionsTotal.toString(), MaterialTheme.colorScheme.onSurface)
            BigStat("Running", s.sessionsRunning.toString(), dw.success)
            BigStat("Waiting", s.sessionsWaiting.toString(), dw.waiting)
        }
    }
}

@Composable
private fun BigStat(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
    }
}

@Composable
private fun ResourceCard(s: StatsDto) {
    PwaCardContainer {
        PwaSectionTitle("Host resources")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ResourceRow("CPU", s.cpuPct)
            ResourceRow("Memory", s.memPct)
            s.diskPct?.let { ResourceRow("Disk", it) }
            s.gpuPct?.let { ResourceRow("GPU", it) }
        }
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
            Text(
                "${"%.1f".format(pct)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = pctColor(pct),
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            color = pctColor(pct),
            trackColor = LocalDatawatchColors.current.bg3,
        )
    }
}

@Composable
private fun pctColor(pct: Double): Color {
    val dw = LocalDatawatchColors.current
    return when {
        pct >= 90 -> MaterialTheme.colorScheme.error
        pct >= 70 -> dw.warning
        else -> dw.success
    }
}

@Composable
private fun UptimeCard(
    s: StatsDto,
    info: ServerInfo?,
) {
    PwaCardContainer {
        PwaSectionTitle("Daemon")
        InfoRow("Uptime", formatUptime(s.uptimeSeconds))
        info?.sessionCount?.let {
            InfoRow("Sessions tracked", it.toString())
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
private fun PwaCardContainer(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
