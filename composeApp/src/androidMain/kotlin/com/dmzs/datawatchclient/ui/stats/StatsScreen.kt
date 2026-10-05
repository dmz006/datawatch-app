package com.dmzs.datawatchclient.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.transport.dto.WebSearchStatsDto
import com.dmzs.datawatchclient.transport.dto.WebSearchStatsV2Dto
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import com.dmzs.datawatchclient.ui.theme.pwaCard
import androidx.compose.foundation.border

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
    DisposableEffect(vm) {
        vm.setVisible(true)
        onDispose { vm.setVisible(false) }
    }
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
        DatawatchLoadingContent()
        return
    }

    // Server card needs live stats (user 2026-04-22 — "cpu and stats
    // are important and should be there and real time displayed"), so
    // it gets both info and stats. LLM-backend row dropped — a fleet
    // can run multiple backends and the Monitor tab isn't the right
    // surface for a single-backend readout.
    info?.let { ServerInfoCard(it, s) }
    s?.let {
        SessionStatisticsCard(it, state.maxSessions)
        SystemStatisticsCard(it)
        NetworkCard(it)
        DaemonCard(it)
        InfrastructureCard(it, info)
        // eBPF "Degraded" banner — surfaces when the daemon was built
        // with eBPF capture support but the kernel probes aren't
        // attached (needs `datawatch setup ebpf` or equivalent). PWA
        // renders the same banner prominently on the Monitor tab.
        EbpfDegradedBanner(it)
        if (it.rtkInstalled) RtkCard(it)
        MemoryStatsCard(it)
        // Ollama card only when the server reports an actually-online
        // Ollama endpoint. Previously rendered on `ollamaStats != null`
        // which showed an "offline" card on servers that merely list
        // Ollama as a backend without it running.
        it.ollamaStats?.takeIf { o -> o.available }?.let { o -> OllamaStatsCard(o) }
        if (it.envelopes.isNotEmpty()) EnvelopesCard(it.envelopes)
        if (it.backends.isNotEmpty()) BackendHealthCard(it.backends)
    }
    // BL391: prefer multi-provider stats (v8.39.0+), fall back to legacy single-provider card.
    val wsV2 = state.webSearchStatsV2
    val wsLegacy = state.webSearchStats
    when {
        wsV2 != null -> WebSearchCardV2(wsV2)
        wsLegacy?.enabled == true -> WebSearchCard(wsLegacy)
    }
}

// ---------- New v4.1.0 observer cards ----------

/**
 * eBPF "Degraded" banner — surfaces on the Monitor tab when the
 * daemon ships eBPF capture support but the kernel probes aren't
 * attached (common after a fresh install — needs `datawatch setup
 * ebpf`). Renders only when [ebpfEnabled] is explicitly true and
 * [ebpfActive] is false. Mirrors PWA app.js eBPF status strip.
 */
@Composable
private fun DegradedBanner(message: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EbpfDegradedBanner(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    val enabled = s.ebpfEnabled ?: return
    if (!enabled || s.ebpfActive) return
    val ebpfDefaultMsg = stringResource(R.string.stats_ebpf_degraded_default)
    val msg = s.ebpfMessage?.takeIf { it.isNotBlank() } ?: ebpfDefaultMsg
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.stats_ebpf_degraded_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NetworkCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    StatsCard(id = "network", title = if (s.ebpfActive) "Network (datawatch)" else "Network (system)") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow(stringResource(R.string.stats_row_download), formatBytes(s.netRxBytes))
            MonoRow(stringResource(R.string.stats_row_upload), formatBytes(s.netTxBytes))
        }
    }
}

@Composable
private fun DaemonCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    StatsCard(id = "daemon", title = stringResource(R.string.stats_section_daemon)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (s.daemonRssBytes > 0) {
                MonoRow(
                    stringResource(R.string.stats_row_memory),
                    "${formatBytes(s.daemonRssBytes)} RSS",
                )
            }
            if (s.goroutines > 0) MonoRow(stringResource(R.string.stats_row_goroutines), s.goroutines.toString())
            if (s.openFds > 0) MonoRow(stringResource(R.string.stats_row_open_fds), s.openFds.toString())
            MonoRow(stringResource(R.string.stats_row_uptime), formatUptime(s.uptimeSeconds))
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
    StatsCard(id = "infrastructure", title = stringResource(R.string.stats_section_infrastructure)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow(
                stringResource(R.string.stats_row_http),
                "http://$host:$httpPort${if (hasTls) " (→ HTTPS)" else ""}",
            )
            if (hasTls) MonoRow(stringResource(R.string.stats_row_https), "https://$host:${s.tlsPort}")
            s.mcpSsePort?.let {
                MonoRow(
                    stringResource(R.string.stats_row_mcp_sse),
                    "${s.mcpSseHost ?: "0.0.0.0"}:$it",
                )
            }
            val tmux =
                "${s.tmuxSessions} sessions" +
                    (if (s.orphanedTmux.isNotEmpty()) " · ${s.orphanedTmux.size} orphan" else "")
            MonoRow(stringResource(R.string.stats_row_tmux), tmux)
        }
    }
}

@Composable
private fun RtkCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    StatsCard(id = "rtk", title = stringResource(R.string.stats_section_rtk)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow(stringResource(R.string.stats_row_version), s.rtkVersion ?: "?")
            val hooksColor =
                if (s.rtkHooksActive) {
                    LocalDatawatchColors.current.success
                } else {
                    LocalDatawatchColors.current.warning
                }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_row_hooks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (s.rtkHooksActive) "active" else "inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = hooksColor,
                )
            }
            MonoRow(stringResource(R.string.stats_row_tokens_saved), s.rtkTotalSaved.toString())
            MonoRow(
                stringResource(R.string.stats_row_avg_savings),
                if (s.rtkAvgSavingsPct != null) "%.1f%%".format(s.rtkAvgSavingsPct) else "—",
            )
            MonoRow(stringResource(R.string.stats_row_commands), s.rtkTotalCommands.toString())
        }
    }
}

@Composable
private fun MemoryStatsCard(s: com.dmzs.datawatchclient.transport.dto.StatsDto) {
    val dw = LocalDatawatchColors.current
    StatsCard(id = "memory", title = stringResource(R.string.stats_section_memory)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_row_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (s.memoryEnabled) {
                        stringResource(
                            R.string.stats_label_enabled,
                        )
                    } else {
                        stringResource(R.string.stats_label_disabled)
                    },
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
                MonoRow(stringResource(R.string.stats_row_total), s.memoryTotalCount.toString())
                MonoRow(stringResource(R.string.stats_row_manual), s.memoryManualCount.toString())
                MonoRow(stringResource(R.string.stats_row_sessions), s.memorySessionCount.toString())
                MonoRow(stringResource(R.string.stats_row_learnings), s.memoryLearningCount.toString())
                MonoRow(stringResource(R.string.stats_row_db_size), formatBytes(s.memoryDbSizeBytes))
            }
        }
    }
}

@Composable
private fun OllamaStatsCard(o: com.dmzs.datawatchclient.transport.dto.OllamaStatsDto) {
    val dw = LocalDatawatchColors.current
    val running = o.runningModels
    val totalVram = running.sumOf { it.sizeVram }
    StatsCard(id = "ollama", title = stringResource(R.string.stats_section_ollama)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MonoRow(stringResource(R.string.stats_row_host), o.host ?: "—")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.stats_row_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (o.available) {
                        stringResource(
                            R.string.stats_label_online,
                        )
                    } else {
                        stringResource(R.string.stats_label_offline)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (o.available) dw.success else MaterialTheme.colorScheme.error,
                )
            }
            MonoRow(stringResource(R.string.stats_row_models), o.modelCount.toString())
            MonoRow(stringResource(R.string.stats_row_disk_used), formatBytes(o.totalSizeBytes))
            MonoRow(stringResource(R.string.stats_row_running), running.size.toString())
            MonoRow(stringResource(R.string.stats_row_vram_used), formatBytes(totalVram))
            running.forEach {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        formatBytes(it.sizeVram),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvelopesCard(envs: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>) {
    StatsCard(id = "envelopes", title = stringResource(R.string.stats_section_envelopes)) {
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
    StatsCard(id = "backends", title = stringResource(R.string.stats_section_backends)) {
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
private fun MonoRow(
    label: String,
    value: String,
) {
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
private fun ServerInfoCard(
    info: ServerInfo,
    stats: StatsDto?,
) {
    // Server card shows identity + real-time vitals per user 2026-04-22.
    // LLM-backend row was removed — a fleet can run several backends and
    // the per-session badge in sessions list is the right surface for
    // that. Backends card below shows reachability.
    StatsCard(id = "server", title = stringResource(R.string.stats_section_server)) {
        InfoRow(stringResource(R.string.stats_row_hostname), info.hostname)
        InfoRow(stringResource(R.string.stats_row_daemon), "v${info.version}")
        info.messagingBackend?.let { InfoRow(stringResource(R.string.stats_row_messaging), it) }
        if (info.serverHost != null && info.serverPort != null) {
            InfoRow(stringResource(R.string.stats_row_bound_to), "${info.serverHost}:${info.serverPort}")
        }
        stats?.let { s ->
            // Live CPU load — prefer the richer `cpu_load_avg_1 / cores`
            // pair the PWA emits, fall back to the v1 `cpu_pct` scalar.
            val load1 = s.cpuLoad1
            val cores = s.cpuCores
            val cpuPctFlat = s.cpuPct
            val cpuText =
                when {
                    load1 != null && cores != null && cores > 0 ->
                        "%.2f load (%d cores)".format(load1, cores)
                    cpuPctFlat != null -> "%.1f%%".format(cpuPctFlat)
                    else -> null
                }
            cpuText?.let { InfoRow(stringResource(R.string.stats_row_cpu), it) }
            val memUsed = s.memUsed
            val memTotal = s.memTotal
            val memPctFlat = s.memPct
            val memText =
                if (memUsed != null && memTotal != null && memTotal > 0) {
                    val pct = (memUsed.toDouble() / memTotal.toDouble()) * 100.0
                    "${formatBytes(memUsed)} / ${formatBytes(memTotal)} (${"%.0f".format(pct)}%)"
                } else if (memPctFlat != null) {
                    "%.1f%%".format(memPctFlat)
                } else {
                    null
                }
            memText?.let { InfoRow(stringResource(R.string.stats_row_memory), it) }
            if (s.uptimeSeconds > 0) InfoRow(stringResource(R.string.stats_row_uptime), formatUptime(s.uptimeSeconds))
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
private fun SessionStatisticsCard(
    s: StatsDto,
    maxSessions: Int?,
) {
    val dw = LocalDatawatchColors.current
    // PWA shows a ring "X / max" with running/waiting breakdown below.
    // max comes from config session.max_sessions; when unknown, the
    // denominator falls back to max(total, 1) so the ring reads 100% full.
    val total = s.sessionsTotal
    val max = (maxSessions ?: total).coerceAtLeast(total).coerceAtLeast(1)
    val fraction = (total.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    // PWA donut is always the success colour (no threshold tint).
    val ringColor = dw.success
    StatsCard(id = "sessions", title = stringResource(R.string.stats_section_sessions)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(96.dp),
                    color = dw.bg3,
                    strokeWidth = 8.dp,
                    trackColor = Color.Transparent,
                )
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(96.dp),
                    color = ringColor,
                    strokeWidth = 8.dp,
                    trackColor = Color.Transparent,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        total.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (maxSessions != null) "of $maxSessions" else "sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPill(stringResource(R.string.stats_label_running), s.sessionsRunning, dw.success)
                StatPill(stringResource(R.string.stats_label_waiting), s.sessionsWaiting, dw.waiting)
                val idle = (s.sessionsTotal - s.sessionsRunning - s.sessionsWaiting).coerceAtLeast(0)
                StatPill(stringResource(R.string.stats_label_idle), idle, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            color = color,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
            )
            Text(
                label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SystemStatisticsCard(s: StatsDto) {
    // PWA renderStatsData (app.js:5769-5920) reads cpu_load_avg_1 / cpu_cores
    // for CPU load, mem_used / mem_total for memory, disk_used / disk_total
    // for disk, swap_used / swap_total when present, and the gpu_* block
    // when the host has a GPU. Mirror the same fields here.
    StatsCard(id = "system", title = stringResource(R.string.stats_section_system)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // CPU Load — fraction against core count, cap bar at 100%.
            val load1 = s.cpuLoad1
            val cores = s.cpuCores
            val cpuPctFlat = s.cpuPct
            val cpuPct: Double? =
                when {
                    load1 != null && cores != null && cores > 0 ->
                        (load1 / cores) * 100.0
                    cpuPctFlat != null -> cpuPctFlat
                    else -> null
                }
            val cpuSub =
                if (load1 != null && cores != null) {
                    "%.2f load · %d cores".format(load1, cores)
                } else {
                    null
                }
            UsageBar("CPU Load", cpuPct, cpuSub, StatsMetric.Cpu)
            PerCoreCpuStrip(s.cpuCoresDetail)

            // Memory — show used/total under the bar.
            val memUsed = s.memUsed
            val memTotal = s.memTotal
            val memPct: Double? =
                if (memUsed != null && memTotal != null && memTotal > 0) {
                    (memUsed.toDouble() / memTotal.toDouble()) * 100.0
                } else {
                    s.memPct
                }
            val memSub =
                if (memUsed != null && memTotal != null) {
                    "${formatBytes(memUsed)} / ${formatBytes(memTotal)}"
                } else {
                    null
                }
            UsageBar("Memory", memPct, memSub, StatsMetric.Memory)

            // Disk — same pattern, also tolerate v1 flat scalar.
            val diskUsed = s.diskUsed
            val diskTotal = s.diskTotal
            val diskPct: Double? =
                if (diskUsed != null && diskTotal != null && diskTotal > 0) {
                    (diskUsed.toDouble() / diskTotal.toDouble()) * 100.0
                } else {
                    s.diskPct
                }
            val diskSub =
                if (diskUsed != null && diskTotal != null) {
                    "${formatBytes(diskUsed)} / ${formatBytes(diskTotal)}"
                } else {
                    null
                }
            UsageBar("Disk", diskPct, diskSub, StatsMetric.Disk)

            // Swap — only render when the host actually has swap configured.
            if (s.swapTotal > 0) {
                val swapPct = (s.swapUsed.toDouble() / s.swapTotal.toDouble()) * 100.0
                UsageBar(
                    "Swap",
                    swapPct,
                    "${formatBytes(s.swapUsed)} / ${formatBytes(s.swapTotal)}",
                )
            }

            // GPU util — render when util% or GPU name is reported.
            // When util% is absent but VRAM data exists, skip the util bar here and
            // let the VRAM block below act as the primary GPU bar.
            val gpuUtilPct = s.gpuUtilPct ?: s.gpuPct
            val vramTotal = s.gpuMemTotalMb
            if (s.gpuName != null || gpuUtilPct != null) {
                val tempSuffix = s.gpuTemp?.let { " · ${"%.0f".format(it)}°C" } ?: ""
                val gpuSub = s.gpuName?.plus(tempSuffix) ?: tempSuffix.ifBlank { null }
                UsageBar("GPU", gpuUtilPct, gpuSub, StatsMetric.Gpu)
            }

            // GPU VRAM — shown as a bar.
            // When util% was absent (no GPU bar above), use label "GPU VRAM" so
            // the user always sees a GPU bar when the server reports memory data.
            if (vramTotal != null && vramTotal > 0) {
                val usedMb = s.gpuMemUsedMb ?: 0L
                val pct = (usedMb.toDouble() / vramTotal.toDouble()) * 100.0
                UsageBar(
                    "GPU VRAM",
                    pct,
                    "${formatBytes(usedMb * 1_000_000L)} / ${formatBytes(vramTotal * 1_000_000L)}",
                )
            }

            // GPU probe error — shown when probe failed but no GPU data was returned.
            val gpuErr = s.gpuError?.takeIf { it.isNotBlank() }
            if (gpuErr != null && s.gpuName == null && gpuUtilPct == null && (vramTotal == null || vramTotal == 0L)) {
                GpuProbeFailedCard(gpuErr)
            }
        }
    }
}

/** B5 — per-core CPU strip, rendered when server emits `cpu_cores_detail`. */
@Composable
private fun PerCoreCpuStrip(cores: List<Double>) {
    if (cores.isEmpty()) return
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            stringResource(R.string.stats_label_per_core),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        cores.chunked(8).forEachIndexed { chunkIdx, chunk ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                chunk.forEachIndexed { colIdx, pct ->
                    CoreMiniBar(
                        label = "C${chunkIdx * 8 + colIdx}",
                        pct = pct,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreMiniBar(
    label: String,
    pct: Double,
    modifier: Modifier = Modifier,
) {
    val clamped = pct.coerceIn(0.0, 100.0)
    val barColor =
        when {
            clamped >= 80 -> Color(0xFFEF4444)
            clamped >= 60 -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth((clamped / 100.0).toFloat())
                        .height(4.dp)
                        .background(barColor),
            )
        }
        Text(
            "${"%.0f".format(clamped)}%",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun UsageBar(
    label: String,
    pct: Double?,
    subtitle: String?,
    metric: StatsMetric = StatsMetric.Other,
) {
    if (pct == null) return
    val clamped = pct.coerceIn(0.0, 100.0)
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${"%.1f".format(clamped)}%",
                style = MaterialTheme.typography.bodyMedium,
                color = pctColor(metric, clamped),
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { (clamped / 100.0).toFloat() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            color = pctColor(metric, clamped),
            trackColor = LocalDatawatchColors.current.bg3,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Parity D29a — the metrics the PWA stats panel colours with its own thresholds. */
internal enum class StatsMetric { Cpu, Memory, Disk, Gpu, Other }

/** Colour role a [StatsMetric] bar takes at a given percentage. */
internal enum class StatsTone { Error, Warning, Success, Accent, Accent2 }

/**
 * Parity D29a — PWA `renderStatsData` thresholds verbatim (strict `>`):
 * CPU >80 error / >50 warning / success; Memory >85 error / accent;
 * Disk >90 error / accent2; GPU >80 error / success. Bars the PWA doesn't
 * draw (swap, VRAM) use accent.
 */
internal fun statsMetricTone(
    metric: StatsMetric,
    pct: Double,
): StatsTone =
    when (metric) {
        StatsMetric.Cpu -> if (pct > 80) StatsTone.Error else if (pct > 50) StatsTone.Warning else StatsTone.Success
        StatsMetric.Memory -> if (pct > 85) StatsTone.Error else StatsTone.Accent
        StatsMetric.Disk -> if (pct > 90) StatsTone.Error else StatsTone.Accent2
        StatsMetric.Gpu -> if (pct > 80) StatsTone.Error else StatsTone.Success
        StatsMetric.Other -> StatsTone.Accent
    }

@Composable
private fun pctColor(
    metric: StatsMetric,
    pct: Double,
): Color {
    val dw = LocalDatawatchColors.current
    return when (statsMetricTone(metric, pct)) {
        StatsTone.Error -> MaterialTheme.colorScheme.error
        StatsTone.Warning -> dw.warning
        StatsTone.Success -> dw.success
        StatsTone.Accent -> MaterialTheme.colorScheme.primary
        StatsTone.Accent2 -> dw.accent2
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
private fun WebSearchCard(ws: WebSearchStatsDto) {
    StatsCard(id = "web_search", title = stringResource(R.string.stats_section_web_search)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ws.provider?.let { MonoRow(stringResource(R.string.stats_row_ws_provider), it) }
            ws.url?.let { MonoRow(stringResource(R.string.stats_row_ws_url), it) }
            ws.engine?.let { MonoRow(stringResource(R.string.stats_row_ws_engine), it) }
            MonoRow(stringResource(R.string.stats_row_ws_results), ws.numResults.toString())
            MonoRow(stringResource(R.string.stats_row_ws_queries), ws.queriesTotal.toString())
            if (ws.errorsTotal > 0) {
                MonoRow(stringResource(R.string.stats_row_ws_errors), ws.errorsTotal.toString())
            }
            ws.lastQueryAt?.let { MonoRow(stringResource(R.string.stats_row_ws_last_query), it) }
        }
    }
}

// BL391 — multi-provider stats card (v8.39.0+)
@Composable
private fun WebSearchCardV2(ws: WebSearchStatsV2Dto) {
    StatsCard(id = "web_search", title = stringResource(R.string.stats_section_web_search)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Overall totals row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WebSearchStat(stringResource(R.string.ws_stats_total), ws.summary.total.toString(), modifier = Modifier.weight(1f))
                WebSearchStat(stringResource(R.string.ws_stats_today), ws.summary.today.toString(), modifier = Modifier.weight(1f))
                WebSearchStat(stringResource(R.string.ws_stats_week), ws.summary.thisWeek.toString(), modifier = Modifier.weight(1f))
                WebSearchStat(stringResource(R.string.ws_stats_month), ws.summary.thisMonth.toString(), modifier = Modifier.weight(1f))
            }
            if (ws.summary.cacheHits > 0) {
                MonoRow(stringResource(R.string.ws_stats_cache_hits), ws.summary.cacheHits.toString())
            }
            // Per-provider rows
            if (ws.summary.providers.size > 1) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ws.summary.providers.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${p.today}d · ${p.thisWeek}w · ${p.total}t",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (p.errors > 0) {
                            Text(
                                "${p.errors}err",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            // Daily sparkline (hand-rolled bar chart)
            if (ws.dailySeries.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                WebSearchSparkline(ws.dailySeries)
            }
        }
    }
}

@Composable
private fun WebSearchStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WebSearchSparkline(series: List<com.dmzs.datawatchclient.transport.dto.WebSearchDayCountDto>) {
    val max = series.maxOfOrNull { it.count } ?: 0
    if (max == 0) return
    val barColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        series.forEach { day ->
            val frac = if (max > 0) day.count.toFloat() / max else 0f
            val height = (frac * 32).dp.coerceAtLeast(2.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(barColor),
            )
        }
    }
}

/**
 * Stats sub-card on the shared collapsible [PwaCard] (D26a/D27a). The PWA nests
 * these inside its single "System Statistics" section, so every sub-card links
 * that section's docs slug; ids are namespaced `stats_<card>`.
 */
@Composable
private fun StatsCard(
    id: String,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    PwaCard(id = "stats_$id", title = title, docsAnchor = "system-statistics", content = content)
}

/**
 * PWA GPU-probe-failed stat card (full grid width, red border): a GPU probe
 * exists but its last poll failed (driver/library mismatch after an update),
 * which must not look like "no GPU present".
 */
@Composable
private fun GpuProbeFailedCard(error: String) {
    val red = MaterialTheme.colorScheme.error
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, red, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(10.dp),
    ) {
        Text(
            stringResource(R.string.stats_gpu_error_title),
            style = MaterialTheme.typography.labelSmall,
            color = red,
        )
        Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
