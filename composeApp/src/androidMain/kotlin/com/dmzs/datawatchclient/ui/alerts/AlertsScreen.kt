package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState

/**
 * Alerts tab — matches PWA `renderAlertsView` (app.js:5516) structure:
 *  - Active(N) / Inactive(N) sub-tabs sourced from `/api/alerts`
 *  - Per-session collapsible group headers with state pill + count
 *  - Per-alert cards with level-colored left border + timestamp +
 *    title + body
 *  - Quick-reply dropdown on the first (latest) alert of any
 *    `waiting_input` session (PWA app.js:5573-5580)
 *  - Swipe-left on a group header mutes the session (legacy mobile
 *    convention, retained since it's a useful gesture the PWA lacks)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AlertsScreen(
    onOpenSession: (String) -> Unit,
    vm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val schedulesVm: com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    var scheduleFor by remember { mutableStateOf<Session?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Alerts") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Tab row — PWA puts Active first and defaults to it when
            // there's any active group, else Inactive (app.js:5627).
            TabRow(
                selectedTabIndex =
                    if (state.selectedTab == AlertsViewModel.Tab.Active) 0 else 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = state.selectedTab == AlertsViewModel.Tab.Active,
                    onClick = { vm.selectTab(AlertsViewModel.Tab.Active) },
                    text = {
                        Text(
                            "Active (${state.active.sumOf { it.alerts.size }})",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == AlertsViewModel.Tab.Inactive,
                    onClick = { vm.selectTab(AlertsViewModel.Tab.Inactive) },
                    text = {
                        Text(
                            "Inactive (${state.inactive.sumOf { it.alerts.size }})",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }

            state.banner?.let { banner ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            banner,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = vm::dismissBanner) { Text("Dismiss") }
                    }
                }
            }

            val groups = state.visibleGroups
            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            when (state.selectedTab) {
                                AlertsViewModel.Tab.Active -> "No sessions need input. You're caught up."
                                AlertsViewModel.Tab.Inactive -> "No historical alerts."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(groups, key = { it.sessionId }) { group ->
                        val expanded =
                            group.sessionId in state.expandedSessionIds ||
                                // Active groups default-expand; inactive default-collapse.
                                (state.selectedTab == AlertsViewModel.Tab.Active &&
                                    group.sessionId !in state.expandedSessionIds)
                        AlertGroupCard(
                            group = group,
                            expanded = expanded,
                            onToggleExpand = { vm.toggleExpanded(group.sessionId) },
                            onOpenSession = {
                                group.session?.let { onOpenSession(it.id) }
                            },
                            onDismiss = { vm.dismissSession(group.sessionId) },
                            onSchedule = { scheduleFor = group.session },
                            onMarkRead = vm::markAlertRead,
                        )
                    }
                }
            }
        }
    }

    scheduleFor?.let { s ->
        val seed = s.lastPrompt?.take(200) ?: s.taskSummary ?: s.id
        com.dmzs.datawatchclient.ui.schedules.ScheduleDialog(
            initialTask = seed,
            title = "Schedule reply to ${s.name ?: s.id}",
            onConfirm = { task, cron, enabled ->
                schedulesVm.create(task, cron, enabled, sessionId = s.id)
                scheduleFor = null
            },
            onDismiss = { scheduleFor = null },
        )
    }
}

/**
 * Per-session group — header row with state pill + alert count + chevron.
 * Expanded body lists the per-alert cards. Swipe-left on the header
 * mutes the session (no-op when the group is the SYSTEM_BUCKET, which
 * has no underlying session).
 */
@Composable
private fun AlertGroupCard(
    group: AlertsViewModel.AlertGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenSession: () -> Unit,
    onDismiss: () -> Unit,
    onSchedule: () -> Unit,
    onMarkRead: (String) -> Unit,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val stateColor = stateAccentColor(group.state)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(group.sessionId) {
                    if (group.sessionId == AlertsViewModel.AlertGroup.SYSTEM_BUCKET) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (dx < -swipeThresholdPx) onDismiss()
                        },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpand)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (expanded) "▼" else "▶",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onOpenSession),
                )
                if (group.state != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stateLabel(group.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${group.alerts.size} alert${if (group.alerts.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                // Quick-reply dropdown on the latest alert for an
                // actively-waiting session — mirrors PWA app.js:5573-5580.
                val canQuickReply =
                    group.session?.state == SessionState.Waiting &&
                        group.sessionId != AlertsViewModel.AlertGroup.SYSTEM_BUCKET
                group.alerts.forEachIndexed { idx, alert ->
                    AlertCard(
                        alert = alert,
                        showQuickReply = canQuickReply && idx == 0,
                        onQuickReply = {
                            // Open session-detail; the composer auto-focuses
                            // when state==Waiting and the reply text auto-
                            // seeds from the latest prompt upstream.
                            onOpenSession()
                        },
                        onSchedule = onSchedule,
                        onOpenSession = onOpenSession,
                        onMarkRead = { onMarkRead(alert.id) },
                    )
                    HorizontalDivider()
                }
            } else {
                HorizontalDivider()
            }
        }
    }
}

/**
 * Per-alert card. Left border is level-colored; title sits bold above
 * the body. PWA mirror: app.js:5583-5591.
 */
@Composable
private fun AlertCard(
    alert: Alert,
    showQuickReply: Boolean,
    onQuickReply: () -> Unit,
    onSchedule: () -> Unit,
    onOpenSession: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val levelColor = severityColor(alert.severity)
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Level-colored left border (3dp wide).
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .background(levelColor),
        ) {
            // Height is expanded by the sibling Column; Box itself has no intrinsic height.
            Spacer(modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    alert.severity.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    timeAgo(alert.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (alert.title.isNotBlank()) {
                Text(
                    alert.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (alert.message.isNotBlank()) {
                Text(
                    alert.message.take(500), // match PWA `truncated` cutoff
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Action row — quick reply (if applicable) + Open + Schedule.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (showQuickReply) {
                    OutlinedButton(
                        onClick = onQuickReply,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text("Reply…", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                OutlinedButton(
                    onClick = onSchedule,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text("Schedule…", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onOpenSession,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
                if (!alert.read) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onMarkRead,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text("✓", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun severityColor(s: AlertSeverity): Color =
    when (s) {
        AlertSeverity.Error -> Color(0xFFEF4444)
        AlertSeverity.Warning -> Color(0xFFF59E0B)
        AlertSeverity.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun stateAccentColor(s: SessionState?): Color =
    when (s) {
        SessionState.Running -> Color(0xFF22C55E)
        SessionState.Waiting -> Color(0xFFF59E0B)
        SessionState.RateLimited -> Color(0xFFEAB308)
        SessionState.Error, SessionState.Killed, SessionState.Completed ->
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun stateLabel(s: SessionState?): String =
    when (s) {
        null -> ""
        SessionState.Waiting -> "waiting input"
        else -> s.name.lowercase()
    }

/** Minimal "x m ago" / "x h ago" formatter matching PWA timeAgo. */
private fun timeAgo(ts: kotlinx.datetime.Instant): String {
    val now = kotlinx.datetime.Clock.System.now()
    val deltaSec = (now - ts).inWholeSeconds
    return when {
        deltaSec < 60 -> "just now"
        deltaSec < 3_600 -> "${deltaSec / 60}m ago"
        deltaSec < 86_400 -> "${deltaSec / 3_600}h ago"
        else -> "${deltaSec / 86_400}d ago"
    }
}
