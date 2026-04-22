package com.dmzs.datawatchclient.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        NetworkCard(it)
        DaemonCard(it)
        InfrastructureCard(it, info)
        if (it.rtkInstalled) RtkCard(it)
        MemoryStatsCard(it)
        it.ollamaStats?.let { o -> OllamaStatsCard(o) }
        if (it.envelopes.isNotEmpty()) EnvelopesCard(it.envelopes)
        if (it.backends.isNotEmpty()) BackendHealthCard(it.backends)
    }
}

// ---------- New v4.1.0 observer cards ----------

@Composable
private fun NetworkCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    PwaCardContainer {
        PwaSectionTitle(if (s.ebpfActive) "Network (datawatch)" else "Network (system)")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow("↓ Download", formatBytes(s.netRxBytes))
            MonoRow("↑ Upload", formatBytes(s.netTxBytes))
        }
    }
}

@Composable
private fun DaemonCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    PwaCardContainer {
        PwaSectionTitle("Daemon")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (s.daemonRssBytes > 0) MonoRow("Memory", "${formatBytes(s.daemonRssBytes)} RSS")
            if (s.goroutines > 0) MonoRow("Goroutines", s.goroutines.toString())
            if (s.openFds > 0) MonoRow("File descriptors", s.openFds.toString())
            MonoRow("Uptime", formatUptime(s.uptimeSeconds))
        }
    }
}

@Composable
private fun InfrastructureCard(
    s: com.dmzs.datawatchclient.transport.dto.StatsDto,
    info: ServerInfo?,
) {
    val host = s.boundInterfaces.firstOrNull() ?: "0.0.0.0"
    val httpPort = s.webPort ?: info?.serverPort ?: 8080
    val hasTls = s.tlsEnabled && s.tlsPort > 0
    PwaCardContainer {
        PwaSectionTitle("Infrastructure")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow("HTTP", "http://$host:$httpPort${if (hasTls) " (→ HTTPS)" else ""}")
            if (hasTls) MonoRow("HTTPS", "https://$host:${s.tlsPort}")
            s.mcpSsePort?.let { MonoRow("MCP SSE", "${s.mcpSseHost ?: "0.0.0.0"}:$it") }
            val tmux = "${s.tmuxSessions} sessions" +
                (if (s.orphanedTmux.isNotEmpty()) " · ${s.orphanedTmux.size} orphan" else "")
            MonoRow("Tmux", tmux)
        }
    }
}

@Composable
private fun RtkCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    PwaCardContainer {
        PwaSectionTitle("RTK Token Savings")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow("Version", s.rtkVersion ?: "?")
            val hooksColor =
                if (s.rtkHooksActive) LocalDatawatchColors.current.success
                else LocalDatawatchColors.current.warning
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hooks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (s.rtkHooksActive) "active" else "inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = hooksColor,
                )
            }
            MonoRow("Tokens saved", s.rtkTotalSaved.toString())
            MonoRow(
                "Avg savings",
                if (s.rtkAvgSavingsPct != null) "%.1f%%".format(s.rtkAvgSavingsPct) else "—",
            )
            MonoRow("Commands", s.rtkTotalCommands.toString())
        }
    }
}

@Composable
private fun MemoryStatsCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    val dw = LocalDatawatchColors.current
    PwaCardContainer {
        PwaSectionTitle("Episodic Memory")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (s.memoryEnabled) "enabled" else "disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.memoryEnabled) dw.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.memoryEnabled) {
                s.memoryBackend?.let { MonoRow("Backend", it) }
                s.memoryEmbedder?.let { MonoRow("Embedder", it) }
                if (s.memoryEncrypted) {
                    MonoRow("Encryption", "encrypted (${s.memoryKeyFingerprint ?: "?"})")
                } else {
                    MonoRow("Encryption", "plaintext")
                }
                MonoRow("Total", s.memoryTotalCount.toString())
                MonoRow("Manual", s.memoryManualCount.toString())
                MonoRow("Sessions", s.memorySessionCount.toString())
                MonoRow("Learnings", s.memoryLearningCount.toString())
                MonoRow("DB Size", formatBytes(s.memoryDbSizeBytes))
            }
        }
    }
}

@Composable
private fun OllamaStatsCard(o: com.dmzs.datawatchclient.transport.dto.OllamaStatsDto) {
    val dw = LocalDatawatchColors.current
    val running = o.runningModels
    val totalVram = running.sumOf { it.sizeVram }
    PwaCardContainer {
        PwaSectionTitle("Ollama Server")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow("Host", o.host ?: "—")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (o.available) "online" else "offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (o.available) dw.success else MaterialTheme.colorScheme.error,
                )
            }
            MonoRow("Models", o.modelCount.toString())
            MonoRow("Disk Used", formatBytes(o.totalSizeBytes))
            MonoRow("Running", running.size.toString())
            MonoRow("VRAM Used", formatBytes(totalVram))
            running.forEach {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(formatBytes(it.sizeVram), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun EnvelopesCard(envs: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>) {
    PwaCardContainer {
        PwaSectionTitle("Process envelopes")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            envs.sortedByDescending { it.cpuPct }.forEach { env ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            env.label.ifBlank { env.id },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${env.kind} · ${env.pids.size} pid${if (env.pids.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.1f%% CPU".format(env.cpuPct),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            formatBytes(env.rssBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendHealthCard(backends: List<com.dmzs.datawatchclient.transport.dto.BackendStatusDto>) {
    val dw = LocalDatawatchColors.current
    PwaCardContainer {
        PwaSectionTitle("Backend health")
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            backends.forEach { b ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dotColor = if (b.reachable) dw.success else MaterialTheme.colorScheme.error
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(
                                    color = dotColor,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                    )
                    Text(
                        b.name,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (b.reachable) {
                        Text(
                            "${b.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val err = b.error
                        if (err != null) {
                            Text(
                                err.take(30),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonoRow(label: String, value: String) {
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
