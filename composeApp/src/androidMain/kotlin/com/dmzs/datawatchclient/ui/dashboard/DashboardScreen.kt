package com.dmzs.datawatchclient.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.common.SingleServerPickerTitle
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

private val DEFAULT_CARDS = listOf("tree", "ekg", "smoke")
private const val MB = 1_000_000L

/**
 * Dashboard Mission Control tab — parity with PWA alpha.71's ⊞ tab.
 * Renders the user's configured dashboard card layout from /api/dashboard/cards.
 * Falls back to [DEFAULT_CARDS] when no layout is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DashboardScreen(
    onOpenSession: (String) -> Unit = {},
    vm: DashboardViewModel = viewModel(),
    alertsVm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val reachable by vm.reachable.collectAsState()
    val lastProbeEpochMs by vm.lastProbeEpochMs.collectAsState()
    val alertsState by alertsVm.state.collectAsState()
    val dw = LocalDatawatchColors.current
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SingleServerPickerTitle(
                        active = state.activeProfile,
                        open = pickerOpen,
                        onToggle = { pickerOpen = !pickerOpen },
                        onDismiss = { pickerOpen = false },
                        profiles = state.allProfiles,
                        onSelect = { vm.selectProfile(it); pickerOpen = false },
                    )
                },
                actions = {
                    AlertsBellAction(alertsBadge = alertsState.watchedAlertCount)
                    if (state.activeProfile != null) {
                        ReachabilityDot(
                            reachable = reachable,
                            lastProbeEpochMs = lastProbeEpochMs,
                            onRetry = {},
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.cardsLoaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        state.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(innerPadding),
            ) {
                Text(
                    err,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val cardIds = state.cards.map { it.id }.ifEmpty { DEFAULT_CARDS }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Deduplicate: tree and orbital both map to constellation view;
            // ekg and sparklines both map to pulse view.
            val rendered = mutableSetOf<String>()
            cardIds.forEach { id ->
                val group = when (id) {
                    "tree", "orbital" -> "constellation"
                    "ekg", "sparklines" -> "pulse"
                    else -> id
                }
                if (rendered.add(group)) {
                    when (group) {
                        "constellation" -> ConstellationCard(state.sessions, onOpenSession)
                        "pulse" -> PulseCard(state.sessions, state.stats)
                        "events" -> RecentEventsCard(state.sessions, onOpenSession)
                        "gantt" -> PipelineCard(state.sessions)
                        "heatmap" -> HeatmapCard(state.sessions)
                        "guardrails" -> GuardrailsOverviewCard(state.sessions, onOpenSession)
                        "smoke" -> SystemHealthCard(state.stats, state.sessions)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Constellation card (tree / orbital) ----------------------------------------

@Composable
private fun ConstellationCard(sessions: List<Session>, onOpenSession: (String) -> Unit) {
    val dw = LocalDatawatchColors.current
    val running = sessions.filter { it.state == SessionState.Running }
    val waiting = sessions.filter { it.state == SessionState.Waiting }
    val error = sessions.filter { it.state == SessionState.Error }

    CardWrapper(title = stringResource(R.string.dash_card_constellation)) {
        // Count strip
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatChip(running.size.toString(), stringResource(R.string.dash_stat_running), dw.success)
            StatChip(waiting.size.toString(), stringResource(R.string.dash_stat_waiting), dw.warning)
            StatChip(error.size.toString(), stringResource(R.string.dash_stat_error), MaterialTheme.colorScheme.error)
            val done = sessions.count { it.state == SessionState.Completed || it.state == SessionState.Killed }
            StatChip(done.toString(), stringResource(R.string.dash_stat_done), MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Active session rows (running first, then waiting)
        val active = running + waiting
        if (active.isEmpty()) {
            Text(
                stringResource(R.string.dash_no_active_sessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            active.take(8).forEachIndexed { idx, session ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                SessionNodeRow(session, onOpenSession)
            }
            if (active.size > 8) {
                Text(
                    stringResource(R.string.dash_more_sessions, active.size - 8),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionNodeRow(session: Session, onOpenSession: (String) -> Unit) {
    val dw = LocalDatawatchColors.current
    val stateColor = when (session.state) {
        SessionState.Running -> dw.success
        SessionState.Waiting -> dw.warning
        SessionState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSession(session.id) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor),
        )
        val label = session.name?.takeIf { it.isNotBlank() }
            ?: session.taskSummary?.take(60)
            ?: session.id.take(8)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            session.state.name.lowercase().replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.labelSmall,
            color = stateColor,
        )
    }
}

// ---- Pulse card (ekg / sparklines) -----------------------------------------------

@Composable
private fun PulseCard(sessions: List<Session>, stats: StatsDto?) {
    val dw = LocalDatawatchColors.current
    val running = sessions.count { it.state == SessionState.Running }
    val waiting = sessions.count { it.state == SessionState.Waiting }
    val error = sessions.count { it.state == SessionState.Error }

    CardWrapper(title = stringResource(R.string.dash_card_pulse)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BigStat(running.toString(), stringResource(R.string.dash_stat_running), dw.success)
            BigStat(waiting.toString(), stringResource(R.string.dash_stat_waiting), dw.warning)
            BigStat(error.toString(), stringResource(R.string.dash_stat_error), MaterialTheme.colorScheme.error)
            BigStat(sessions.size.toString(), stringResource(R.string.dash_stat_total), MaterialTheme.colorScheme.onSurface)
        }

        if (stats != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            ResourceBar(
                label = stringResource(R.string.dash_resource_cpu),
                pct = stats.cpuLoad1?.let { load ->
                    stats.cpuCores?.let { c -> if (c > 0) (load / c).toFloat().coerceIn(0f, 1f) else null }
                } ?: stats.cpuPct?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                dw = dw,
            )
            ResourceBar(
                label = stringResource(R.string.dash_resource_mem),
                pct = stats.memUsed?.let { used ->
                    stats.memTotal?.let { total -> if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else null }
                } ?: stats.memPct?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
                dw = dw,
            )
        }
    }
}

@Composable
private fun ResourceBar(label: String, pct: Float?, dw: com.dmzs.datawatchclient.ui.theme.DatawatchColors) {
    if (pct == null) return
    val color = when {
        pct > 0.90f -> MaterialTheme.colorScheme.error
        pct > 0.70f -> dw.warning
        else -> dw.success
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            "${(pct * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp),
        )
    }
}

// ---- Recent Events card ----------------------------------------------------------

@Composable
private fun RecentEventsCard(sessions: List<Session>, onOpenSession: (String) -> Unit) {
    val recent = sessions.sortedByDescending { it.lastActivityAt }.take(6)
    CardWrapper(title = stringResource(R.string.dash_card_events)) {
        if (recent.isEmpty()) {
            Text(
                stringResource(R.string.dash_no_events),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recent.forEachIndexed { idx, session ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                RecentEventRow(session, onOpenSession)
            }
        }
    }
}

@Composable
private fun RecentEventRow(session: Session, onOpenSession: (String) -> Unit) {
    val dw = LocalDatawatchColors.current
    val stateColor = when (session.state) {
        SessionState.Running -> dw.success
        SessionState.Waiting -> dw.warning
        SessionState.Error -> MaterialTheme.colorScheme.error
        SessionState.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSession(session.id) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = session.name?.takeIf { it.isNotBlank() }
            ?: session.taskSummary?.take(50)
            ?: session.id.take(8)
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            relativeTime(session.lastActivityAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Pipeline / Gantt card -------------------------------------------------------

@Composable
private fun PipelineCard(sessions: List<Session>) {
    val dw = LocalDatawatchColors.current
    val active = sessions
        .filter { it.state == SessionState.Running || it.state == SessionState.Waiting }
        .sortedBy { it.createdAt }
        .take(6)

    CardWrapper(title = stringResource(R.string.dash_card_pipeline)) {
        if (active.isEmpty()) {
            Text(
                stringResource(R.string.dash_no_pipeline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val now = Clock.System.now()
            val oldest = active.minOf { it.createdAt }
            val rangeMs = (now - oldest).inWholeMilliseconds.coerceAtLeast(1L)
            active.forEachIndexed { idx, session ->
                if (idx > 0) Spacer(Modifier.height(4.dp))
                PipelineRow(session, oldest, rangeMs, dw)
            }
        }
    }
}

@Composable
private fun PipelineRow(
    session: Session,
    oldest: Instant,
    rangeMs: Long,
    dw: com.dmzs.datawatchclient.ui.theme.DatawatchColors,
) {
    val label = session.name?.takeIf { it.isNotBlank() }
        ?: session.taskSummary?.take(30)
        ?: session.id.take(8)
    val barColor = if (session.state == SessionState.Waiting) dw.warning else dw.success
    val startFraction = ((session.createdAt - oldest).inWholeMilliseconds.toFloat() / rangeMs).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - startFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
                    .align(Alignment.CenterEnd),
            )
        }
    }
}

// ---- Heatmap card ----------------------------------------------------------------

@Composable
private fun HeatmapCard(sessions: List<Session>) {
    val dw = LocalDatawatchColors.current
    val byState = SessionState.entries
        .associateWith { state -> sessions.count { it.state == state } }
        .filter { it.value > 0 }

    CardWrapper(title = stringResource(R.string.dash_card_heatmap)) {
        if (byState.isEmpty()) {
            Text(
                stringResource(R.string.dash_no_sessions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val maxCount = byState.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            byState.forEach { (state, count) ->
                val stateColor = when (state) {
                    SessionState.Running -> dw.success
                    SessionState.Waiting -> dw.warning
                    SessionState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        state.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(72.dp),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(count.toFloat() / maxCount)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(stateColor),
                        )
                    }
                    Text(count.toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                }
            }
        }
    }
}

// ---- Guardrails overview card ----------------------------------------------------

@Composable
private fun GuardrailsOverviewCard(sessions: List<Session>, onOpenSession: (String) -> Unit) {
    val dw = LocalDatawatchColors.current
    val errorSessions = sessions.filter { it.state == SessionState.Error }
    val runningSessions = sessions.filter { it.state == SessionState.Running }

    CardWrapper(title = stringResource(R.string.dash_card_guardrails)) {
        if (errorSessions.isEmpty() && runningSessions.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dw.success))
                Text(stringResource(R.string.dash_guardrails_clear), style = MaterialTheme.typography.bodySmall, color = dw.success)
            }
        } else {
            if (errorSessions.isNotEmpty()) {
                Text(
                    stringResource(R.string.dash_guardrails_blocked, errorSessions.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                errorSessions.take(4).forEach { session ->
                    val label = session.name?.takeIf { it.isNotBlank() }
                        ?: session.taskSummary?.take(50)
                        ?: session.id.take(8)
                    Text(
                        "• $label",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) }
                            .padding(vertical = 1.dp),
                        maxLines = 1,
                    )
                }
            }
            if (runningSessions.isNotEmpty()) {
                Text(
                    stringResource(R.string.dash_guardrails_running, runningSessions.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = dw.success,
                    modifier = Modifier.padding(top = if (errorSessions.isNotEmpty()) 6.dp else 0.dp),
                )
            }
        }
    }
}

// ---- System Health / Smoke card --------------------------------------------------

@Composable
private fun SystemHealthCard(stats: StatsDto?, sessions: List<Session>) {
    val dw = LocalDatawatchColors.current
    CardWrapper(title = stringResource(R.string.dash_card_smoke)) {
        if (stats == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.dash_loading), style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val cpuPct = stats.cpuLoad1?.let { load ->
                stats.cpuCores?.let { c -> if (c > 0) (load / c * 100).toInt() else null }
            } ?: stats.cpuPct?.toInt()
            val memText = stats.memUsed?.let { used ->
                stats.memTotal?.let { total ->
                    if (total > 0) "${used / MB}/${total / MB} MB" else null
                }
            } ?: stats.memPct?.let { "%.0f%%".format(it) }

            val healthy = (cpuPct ?: 0) < 90
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (healthy) dw.success else dw.warning))
                Text(
                    if (healthy) stringResource(R.string.dash_health_ok) else stringResource(R.string.dash_health_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (healthy) dw.success else dw.warning,
                )
            }
            Spacer(Modifier.height(6.dp))
            listOfNotNull(
                cpuPct?.let { "CPU: $it%" },
                memText?.let { "Mem: $it" },
                "Sessions: ${sessions.size}",
            ).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Shared primitives -----------------------------------------------------------

@Composable
private fun CardWrapper(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(title)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun relativeTime(instant: Instant): String {
    val diff = Clock.System.now() - instant
    return when {
        diff < 1.minutes -> "now"
        diff < 60.minutes -> "${diff.inWholeMinutes}m ago"
        diff.inWholeHours < 24 -> "${diff.inWholeHours}h ago"
        else -> "${diff.inWholeDays}d ago"
    }
}
