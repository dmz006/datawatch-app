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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.domain.ServerProfile
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
    val reachable by vm.reachable.collectAsState()
    val lastProbeEpochMs by vm.lastProbeEpochMs.collectAsState()
    val schedulesVm: com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    var scheduleFor by remember { mutableStateOf<Session?>(null) }
    // G16: per-session filter within the Active tab; reset on tab change.
    var sessionFilter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selectedTab) { sessionFilter = null }

    Scaffold(
        topBar = {
            AlertsTopBar(
                state = state,
                reachable = reachable,
                lastProbeEpochMs = lastProbeEpochMs,
                onRetry = vm::refresh,
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
                onSelectProfile = vm::selectProfile,
                onSelectAll = vm::selectAllServers,
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

                // G16: per-session sub-tabs when Active + 2+ groups.
                if (state.selectedTab == AlertsViewModel.Tab.Active && state.active.size >= 2) {
                    val activeGroups = state.active
                    val filterIdx = if (sessionFilter == null) 0 else
                        activeGroups.indexOfFirst { it.sessionId == sessionFilter }
                            .let { if (it < 0) 0 else it + 1 }
                    ScrollableTabRow(
                        selectedTabIndex = filterIdx,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                    ) {
                        Tab(
                            selected = sessionFilter == null,
                            onClick = { sessionFilter = null },
                            text = {
                                Text(
                                    stringResource(R.string.alerts_filter_all),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                        activeGroups.forEach { group ->
                            val shortLabel = group.session?.name
                                ?.takeIf { it.isNotBlank() }
                                ?.take(10)
                                ?: group.sessionId.substringAfterLast('-').take(8)
                            Tab(
                                selected = sessionFilter == group.sessionId,
                                onClick = { sessionFilter = group.sessionId },
                                text = {
                                    Text(
                                        shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }

                val groups = if (sessionFilter != null && state.selectedTab == AlertsViewModel.Tab.Active) {
                    state.active.filter { it.sessionId == sessionFilter }
                } else {
                    state.visibleGroups
                }
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
                                serverName = state.groupProfileNames[group.sessionId],
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
 * Uses TopAppBar for consistent chrome with Sessions/Autonomous, then
 * a Surface strip below for chip filters and search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsTopBar(
    state: AlertsViewModel.UiState,
    reachable: Boolean? = null,
    lastProbeEpochMs: Long? = null,
    onRetry: () -> Unit = {},
    onSetChip: (AlertsViewModel.ChipFilter) -> Unit,
    onToggleSort: () -> Unit,
    onDismissAll: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Column {
        TopAppBar(
            title = {
                Box {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = { pickerOpen = !pickerOpen })
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.allServersMode) stringResource(R.string.sessions_all_servers)
                            else (state.activeProfile?.displayName ?: stringResource(R.string.sessions_no_server)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.sessions_switch_server))
                    }
                    DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                        if (state.allProfiles.size > 1) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.sessions_all_servers), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        if (state.allServersMode) Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = { onSelectAll(); pickerOpen = false },
                            )
                            HorizontalDivider()
                        }
                        if (state.allProfiles.isEmpty()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.sessions_no_servers)) }, onClick = { pickerOpen = false }, enabled = false)
                        } else {
                            state.allProfiles.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AlertsStatusDot(enabled = p.enabled)
                                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                                Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                                                Text(p.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (p.id == state.activeProfile?.id) Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = { onSelectProfile(p.id); pickerOpen = false },
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                DocsLinkAction("https://docs.anthropic.com/en/docs/claude-code")
                // Sort toggle: BySession ↔ Chronological
                IconButton(onClick = onToggleSort) {
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
                if (!state.allServersMode && state.activeProfile != null) {
                    ReachabilityDot(
                        reachable = reachable,
                        lastProbeEpochMs = lastProbeEpochMs,
                        onRetry = onRetry,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Chip filter row
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
                // Search bar
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
    serverName: String? = null,
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpenSession),
                    )
                    if (group.state != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stateLabel(group.state),
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor,
                            maxLines = 1,
                        )
                    }
                    serverName?.let { name ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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


@Composable
private fun AlertsStatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
}
