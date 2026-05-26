package com.dmzs.datawatchclient.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.AnalyticsDto
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.SmokeProgressDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.common.SingleServerPickerTitle
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val DEFAULT_CARDS = listOf("tree", "ekg", "smoke")

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
    var editSheetOpen by remember { mutableStateOf(false) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    if (editSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                editSheetOpen = false
                vm.refreshCards()
            },
            sheetState = editSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                com.dmzs.datawatchclient.ui.settings.DashboardCardsCard()
            }
        }
    }

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
                    DocsLinkAction("datawatch-definitions.md#dashboard")
                    IconButton(onClick = { editSheetOpen = true }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit dashboard layout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            // tree and orbital are synonyms for the same constellation view.
            val rendered = mutableSetOf<String>()
            cardIds.forEach { id ->
                val group = when (id) {
                    "tree", "orbital" -> "constellation"
                    else -> id
                }
                if (rendered.add(group)) {
                    when (group) {
                        "constellation" -> ConstellationCard(state.sessions, state.prds, onOpenSession)
                        "ekg" -> PulseCard(state.sessions, state.stats)
                        "sparklines" -> SparklineCard(state.analytics)
                        "events" -> RecentEventsCard(state.sessions, onOpenSession)
                        "gantt" -> PipelineCard(state.sessions)
                        "heatmap" -> HeatmapCard(state.analytics)
                        "guardrails" -> GuardrailsOverviewCard(state.sessions, onOpenSession)
                        "smoke" -> SmokeProgressCard(state.smokeProgress)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Constellation card (tree / orbital) ----------------------------------------

@Composable
private fun ConstellationCard(sessions: List<Session>, prds: List<PrdDto>, onOpenSession: (String) -> Unit) {
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

        // Active PRDs
        if (prds.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Text(
                "PRDs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            prds.take(4).forEach { prd ->
                val prdColor = when (prd.status) {
                    "running" -> dw.success
                    "decomposing", "planning" -> dw.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(prdColor))
                    Text(
                        prd.title ?: prd.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        prd.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = prdColor,
                    )
                }
            }
            if (prds.size > 4) {
                Text(
                    "+${prds.size - 4} more PRDs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
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

// ---- Sparklines card (distinct from ekg) ----------------------------------------

@Composable
private fun SparklineCard(analytics: AnalyticsDto?) {
    val dw = LocalDatawatchColors.current
    CardWrapper(title = "Sparklines") {
        if (analytics == null || analytics.buckets.isEmpty()) {
            Text(
                "No historical data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CardWrapper
        }
        val recent = analytics.buckets.takeLast(7)
        val maxSessions = recent.maxOfOrNull { it.sessionCount }?.coerceAtLeast(1) ?: 1
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            recent.forEach { bucket ->
                val heightFraction = bucket.sessionCount.toFloat() / maxSessions
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((40 * heightFraction.coerceAtLeast(0.04f)).dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (bucket.failed > 0) dw.warning else dw.success),
                    )
                    Text(
                        bucket.date.takeLast(5).replace("-", "/"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                    )
                }
            }
        }
        analytics.successRate?.let { rate ->
            Text(
                "7-day success: ${"%.0f".format(rate * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---- Heatmap card (30-day calendar) ----------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapCard(analytics: AnalyticsDto?) {
    val dw = LocalDatawatchColors.current
    CardWrapper(title = stringResource(R.string.dash_card_heatmap)) {
        if (analytics == null) {
            Text(
                stringResource(R.string.dash_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CardWrapper
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val bucketMap = analytics.buckets.associateBy { it.date }
        val maxCount = analytics.buckets.maxOfOrNull { it.sessionCount }?.coerceAtLeast(1) ?: 1

        val cellSize = 14.dp
        val cellGap = 2.dp

        // 30 days grid — 5 weeks × 7 days but we only fill the last 30 days
        val days30 = (29 downTo 0).map { offset ->
            val date = (Clock.System.now() - offset.days).toLocalDateTime(TimeZone.currentSystemDefault()).date
            val key = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
            Pair(date, bucketMap[key]?.sessionCount ?: 0)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            maxItemsInEachRow = 7,
            horizontalArrangement = Arrangement.spacedBy(cellGap),
            verticalArrangement = Arrangement.spacedBy(cellGap),
        ) {
            days30.forEach { (date, count) ->
                val intensity = count.toFloat() / maxCount
                val cellColor = when {
                    count == 0 -> MaterialTheme.colorScheme.surfaceVariant
                    intensity < 0.25f -> dw.success.copy(alpha = 0.3f)
                    intensity < 0.5f -> dw.success.copy(alpha = 0.55f)
                    intensity < 0.75f -> dw.success.copy(alpha = 0.78f)
                    else -> dw.success
                }
                val isToday = date == today
                Box(
                    modifier = Modifier
                        .size(cellSize)
                        .clip(RoundedCornerShape(2.dp))
                        .background(cellColor)
                        .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else Modifier),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("30 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${analytics.buckets.sumOf { it.sessionCount }} sessions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

// ---- Smoke progress card ---------------------------------------------------------

@Composable
private fun SmokeProgressCard(smoke: SmokeProgressDto?) {
    val dw = LocalDatawatchColors.current
    val scope = rememberCoroutineScope()

    CardWrapper(title = stringResource(R.string.dash_card_smoke)) {
        if (smoke == null) {
            Text(
                "No smoke run active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CardWrapper
        }

        val statusColor = when (smoke.status) {
            "complete", "passed" -> dw.success
            "failed" -> MaterialTheme.colorScheme.error
            "running" -> dw.warning
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Text(
                smoke.status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.weight(1f),
            )
            if (smoke.completedAt == null) {
                Text(
                    "${(smoke.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LinearProgressIndicator(
            progress = { smoke.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).padding(bottom = 6.dp),
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${smoke.passed} passed",
                style = MaterialTheme.typography.labelSmall,
                color = dw.success,
            )
            Text(
                "${smoke.failed} failed",
                style = MaterialTheme.typography.labelSmall,
                color = if (smoke.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${smoke.total} total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (smoke.completedAt != null) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val id = ServiceLocator.activeServerStore.get()
                            val profiles = ServiceLocator.profileRepository.observeAll().first()
                            val p = profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
                            p?.let { ServiceLocator.transportFor(it).clearSmokeProgress() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Clear run", style = MaterialTheme.typography.labelSmall)
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
