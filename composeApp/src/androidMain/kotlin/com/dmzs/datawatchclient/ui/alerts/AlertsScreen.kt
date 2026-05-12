package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
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
 *
 * Sprint 22 (alpha.30 #115): redesigned top bar with chip filters,
 * sort toggle, search bar, and dismiss-all action.
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

    Scaffold(
        topBar = {
            AlertsTopBar(
                state = state,
                onSetChip = vm::setChipFilter,
                onToggleSort = {
                    vm.setSortMode(
                        if (state.sortMode == AlertsViewModel.SortMode.BySession) {
                            AlertsViewModel.SortMode.Chronological
                        } else {
                            AlertsViewModel.SortMode.BySession
                        },
                    )
                },
                onDismissAll = vm::dismissAll,
                onSearchChange = vm::setSearch,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        TextButton(onClick = vm::dismissBanner) { Text(stringResource(R.string.action_dismiss)) }
                    }
                }
            }

            if (state.sortMode == AlertsViewModel.SortMode.Chronological) {
                // Flat chronological view — no group headers, newest-first.
                if (state.flatChronoAlerts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.alerts_empty_active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.flatChronoAlerts, key = { it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                showQuickReply = false,
                                onQuickReply = { alert.sessionId?.let { onOpenSession(it) } },
                                onSchedule = { /* no session in flat view */ },
                                onOpenSession = { alert.sessionId?.let { onOpenSession(it) } },
                                onMarkRead = { vm.markAlertRead(alert.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                // 3-tab layout: Active / Historical / System (Sprint 27 alpha.33).
                TabRow(
                    selectedTabIndex = when (state.selectedTab) {
                        AlertsViewModel.Tab.Active -> 0
                        AlertsViewModel.Tab.Historical -> 1
                        AlertsViewModel.Tab.System -> 2
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = state.selectedTab == AlertsViewModel.Tab.Active,
                        onClick = { vm.selectTab(AlertsViewModel.Tab.Active) },
                        text = {
                            Text(
                                "${stringResource(R.string.alerts_active_tab_label)} (${state.active.sumOf { it.alerts.size }})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                    Tab(
                        selected = state.selectedTab == AlertsViewModel.Tab.Historical,
                        onClick = { vm.selectTab(AlertsViewModel.Tab.Historical) },
                        text = {
                            Text(
                                "${stringResource(R.string.alerts_historical_tab_label)} (${state.historical.sumOf { it.alerts.size }})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                    Tab(
                        selected = state.selectedTab == AlertsViewModel.Tab.System,
                        onClick = { vm.selectTab(AlertsViewModel.Tab.System) },
                        text = {
                            Text(
                                "${stringResource(R.string.alerts_system_tab_label)} (${state.system.sumOf { it.alerts.size }})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }

                val groups = state.visibleGroups
                if (groups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text =
                                when (state.selectedTab) {
                                    AlertsViewModel.Tab.Active -> stringResource(R.string.alerts_empty_active)
                                    AlertsViewModel.Tab.Historical -> stringResource(R.string.alerts_empty_inactive)
                                    AlertsViewModel.Tab.System -> stringResource(R.string.alerts_empty_inactive)
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
                                    // Active groups default-expand; others default-collapse.
                                    (
                                        state.selectedTab == AlertsViewModel.Tab.Active &&
                                            group.sessionId !in state.expandedSessionIds
                                    )
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
 * Custom top bar for the Alerts screen (Sprint 22 alpha.30 redesign).
 *
 * Row 1: "Alerts" title + sort toggle + dismiss-all button
 * Row 2: Horizontal chip filter row (All / Prompts / Errors / Warn / Info)
 * Row 3: Search text field (always visible, matches PWA)
 */
@Composable
private fun AlertsTopBar(
    state: AlertsViewModel.UiState,
    onSetChip: (AlertsViewModel.ChipFilter) -> Unit,
    onToggleSort: () -> Unit,
    onDismissAll: () -> Unit,
    onSearchChange: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: title + sort toggle + dismiss-all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.alerts_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                // Mute button (noop for now — future: toggle dock mute)
                IconButton(onClick = { /* noop: mute dock */ }) {
                    Text("🔕", style = TextStyle(fontSize = 20.sp))
                }
                // Sort toggle: BySession ↔ Chronological
                IconButton(
                    onClick = onToggleSort,
                ) {
                    Icon(
                        Icons.Filled.SortByAlpha,
                        contentDescription = stringResource(R.string.alert_sort_tip),
                        tint = if (state.sortMode == AlertsViewModel.SortMode.Chronological) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                // Dismiss-all button
                IconButton(onClick = onDismissAll) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.alert_dismiss_all_tip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Row 2: chip filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AlertsViewModel.ChipFilter.entries.forEach { chip ->
                    val label = when (chip) {
                        AlertsViewModel.ChipFilter.All -> stringResource(R.string.alert_chip_all)
                        AlertsViewModel.ChipFilter.Prompt -> stringResource(R.string.alert_chip_prompt)
                        AlertsViewModel.ChipFilter.Error -> stringResource(R.string.alert_chip_error)
                        AlertsViewModel.ChipFilter.Warn -> stringResource(R.string.alert_chip_warn)
                        AlertsViewModel.ChipFilter.Info -> stringResource(R.string.alert_chip_info)
                    }
                    FilterChip(
                        selected = state.chipFilter == chip,
                        onClick = { onSetChip(chip) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Row 3: search bar (always visible)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(stringResource(R.string.alert_search_ph), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
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
 *
 * Sprint 22: background tint based on alert type (prompt = amber,
 * error = red, others = surface).
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
    val isPromptType = alert.type.contains("input", ignoreCase = true)
    val levelColor = when {
        isPromptType -> Color(0xFFF59E0B)
        else -> severityColor(alert.severity)
    }
    val bgColor = when {
        isPromptType -> Color(0xFFF59E0B).copy(alpha = 0.08f)
        alert.severity == AlertSeverity.Error -> Color(0xFFEF4444).copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
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
                    .background(bgColor)
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
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp,
                            ),
                    ) {
                        Text(stringResource(R.string.alerts_action_reply), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                OutlinedButton(
                    onClick = onSchedule,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                ) {
                    Text(stringResource(R.string.alerts_action_schedule), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onOpenSession,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                ) {
                    Text(stringResource(R.string.action_open), style = MaterialTheme.typography.labelSmall)
                }
                if (!alert.read) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onMarkRead,
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
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
