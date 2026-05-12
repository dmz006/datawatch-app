package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.dto.ContainerInfoDto
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

@Composable
public fun SessionStatsPanel(
    sessionId: String,
    session: Session? = null,
    modifier: Modifier = Modifier,
    onNavigateToComputeTab: (() -> Unit)? = null,
    onNavigateToLlmTab: (() -> Unit)? = null,
    sessionStatsVm: SessionStatsViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionStatsViewModel(sessionId) } },
        key = "session-stats-$sessionId",
    ),
) {
    val sparkState by sessionStatsVm.state.collectAsState()

    DisposableEffect(sessionId) {
        sessionStatsVm.startPolling()
        onDispose { sessionStatsVm.stopPolling() }
    }

    val envelope: StatEnvelopeDto? = sparkState.envelope

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Host card — always shown
        HostCard(envelope, sparkState.cpuSamples, sparkState.rssSamples)

        // Container card — conditional on envelope.container != null
        val containerInfo = envelope?.container
            ?: if (envelope?.containerId?.isNotBlank() == true) {
                ContainerInfoDto(
                    containerId = envelope.containerId ?: "",
                    image = envelope.image ?: "",
                )
            } else null
        if (containerInfo != null) {
            ContainerCard(containerInfo)
        }

        // ComputeNode card — conditional on session.computeNodeRef != null
        if (session?.computeNodeRef?.isNotBlank() == true) {
            ComputeNodeCard(
                computeNodeRef = session.computeNodeRef!!,
                gpuPct = envelope?.gpuPct ?: 0.0,
                gpuMemBytes = envelope?.gpuMemBytes ?: 0L,
                onNavigate = onNavigateToComputeTab,
            )
        }

        // LLM card — conditional on session.llmRef != null
        if (session?.llmRef?.isNotBlank() == true) {
            LlmCard(
                llmRef = session.llmRef!!,
                backendFamily = session.backend,
                onNavigate = onNavigateToLlmTab,
            )
        }

        if (envelope == null && containerInfo == null &&
            session?.computeNodeRef.isNullOrBlank() && session?.llmRef.isNullOrBlank()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
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
private fun HostCard(
    env: StatEnvelopeDto?,
    cpuSamples: List<Float>,
    rssSamples: List<Float>,
) {
    val dw = LocalDatawatchColors.current
    SectionCard {
        PwaSectionTitle(stringResource(R.string.stats_card_host))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val cpuPct = env?.cpuPct ?: 0.0
                val cpuFraction = (cpuPct / 100.0).toFloat().coerceIn(0f, 1f)
                val cpuColor = when {
                    cpuPct >= 90 -> MaterialTheme.colorScheme.error
                    cpuPct >= 70 -> dw.warning
                    else -> dw.success
                }
                Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(72.dp),
                        color = dw.bg3,
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent,
                    )
                    CircularProgressIndicator(
                        progress = { cpuFraction },
                        modifier = Modifier.size(72.dp),
                        color = cpuColor,
                        strokeWidth = 6.dp,
                        trackColor = Color.Transparent,
                    )
                    Text(
                        "%.0f%%".format(cpuPct),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cpuColor,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatRow(stringResource(R.string.stats_field_cpu), "%.1f%%".format(cpuPct))
                    val rssBytes = env?.rssBytes ?: 0L
                    StatRow(stringResource(R.string.stats_field_rss), formatBytes(rssBytes))
                    if ((env?.threads ?: 0) > 0) StatRow(stringResource(R.string.stats_field_threads), env!!.threads.toString())
                    if ((env?.fds ?: 0) > 0) StatRow(stringResource(R.string.stats_field_fds), env!!.fds.toString())
                    val pid = env?.rootPid ?: 0
                    if (pid > 0) {
                        val childCount = (env?.pids?.size ?: 0)
                        val pidLabel = if (childCount > 0) "PID $pid (+$childCount)" else "PID $pid"
                        StatRow(stringResource(R.string.stats_field_pid), pidLabel)
                    }
                }
            }

            if (cpuSamples.size >= 2) {
                Text(
                    stringResource(R.string.stats_field_cpu),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Sparkline(samples = cpuSamples, color = dw.success, modifier = Modifier.fillMaxWidth().height(36.dp))
                Spacer(Modifier.height(8.dp))
            }
            if (rssSamples.size >= 2) {
                Text(
                    stringResource(R.string.stats_field_rss),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Sparkline(samples = rssSamples, color = dw.accent2, modifier = Modifier.fillMaxWidth().height(36.dp))
                Spacer(Modifier.height(8.dp))
            }

            val env2 = env ?: return@Column
            if (env2.netRxBps > 0 || env2.netTxBps > 0) {
                Spacer(Modifier.height(4.dp))
                val netLabel = stringResource(R.string.stats_field_net)
                if (env2.netRxBps > 0) StatRow("$netLabel ↓", "${formatBytes(env2.netRxBps)}/s")
                if (env2.netTxBps > 0) StatRow("$netLabel ↑", "${formatBytes(env2.netTxBps)}/s")
            }
        }
    }
}

@Composable
private fun ContainerCard(info: ContainerInfoDto) {
    SectionCard {
        PwaSectionTitle(stringResource(R.string.stats_card_container))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (info.containerId.isNotBlank()) {
                StatRow(
                    stringResource(R.string.stats_container_id),
                    info.containerId.take(12),
                )
            }
            if (info.image.isNotBlank()) StatRow(stringResource(R.string.stats_container_image), info.image)
            if (info.runtime.isNotBlank()) StatRow(stringResource(R.string.stats_container_runtime), info.runtime)
        }
    }
}

@Composable
private fun ComputeNodeCard(
    computeNodeRef: String,
    gpuPct: Double,
    gpuMemBytes: Long,
    onNavigate: (() -> Unit)?,
) {
    val dw = LocalDatawatchColors.current
    SectionCard {
        PwaSectionTitle(stringResource(R.string.stats_card_compute_node))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatRow(stringResource(R.string.stats_card_compute_node), computeNodeRef)
            if (gpuPct > 0.0) StatRow(stringResource(R.string.stats_field_gpu), "%.1f%%".format(gpuPct))
            if (gpuMemBytes > 0) StatRow("GPU Mem", formatBytes(gpuMemBytes))
            if (onNavigate != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stats_open_compute),
                    style = MaterialTheme.typography.labelMedium,
                    color = dw.success,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate() }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LlmCard(
    llmRef: String,
    backendFamily: String?,
    onNavigate: (() -> Unit)?,
) {
    val dw = LocalDatawatchColors.current
    SectionCard {
        PwaSectionTitle(stringResource(R.string.stats_card_llm))
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StatRow(stringResource(R.string.stats_card_llm), llmRef)
            if (!backendFamily.isNullOrBlank()) {
                StatRow("Backend", backendFamily)
            }
            if (onNavigate != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.stats_open_llm),
                    style = MaterialTheme.typography.labelMedium,
                    color = dw.success,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate() }
                        .padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.stats_llm_more_soon),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun Sparkline(
    samples: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (samples.size < 2) return
    val maxVal = samples.max().takeIf { it > 0f } ?: 1f
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = w / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / maxVal) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        samples.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / maxVal) * h
            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(x, y))
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
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
