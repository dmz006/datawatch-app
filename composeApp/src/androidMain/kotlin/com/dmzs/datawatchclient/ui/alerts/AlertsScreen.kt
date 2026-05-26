package com.dmzs.datawatchclient.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
 *  - Active(N) / Historical(N) / System(N) primary tabs (always visible)
 *  - Per-tab chip filters (All/Prompt/Error/Warn/Info) below the tabs
 *  - Per-session collapsible group headers with state pill + count
 *  - Per-alert cards with level-colored left border + timestamp +
 *    title + body
 *  - Quick-reply dropdown on the first (latest) alert of any
 *    `waiting_input` session (PWA app.js:5573-5580)
 *  - Swipe-left on a group header mutes the session
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

    Scaffold(
        topBar = {
            AlertsTopBar(
                state = state,
                reachable = reachable,
                lastProbeEpochMs = lastProbeEpochMs,
                onRetry = vm::refresh,
                onSelectProfile = vm::selectProfile,
                onSelectAll = vm::selectAllServers,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Error banner
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

            // PWA-style primary tab row — always visible regardless of sort mode.
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
                            "${stringResource(R.string.alerts_active_tab_label)} (${state.active.size})",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == AlertsViewModel.Tab.Historical,
                    onClick = { vm.selectTab(AlertsViewModel.Tab.Historical) },
                    text = {
                        Text(
                            "${stringResource(R.string.alerts_historical_tab_label)} (${state.historical.size})",
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

            // PWA-style filter bar: 🔔 count + controls + chips + search.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: 🔔 N alerts + sort + ✕ + 🔕 + ↻
                    val totalCount = state.chipCounts[AlertsViewModel.ChipFilter.All] ?: 0
                    val sortLabel = if (state.sortMode == AlertsViewModel.SortMode.BySession) {
                        "⏷ ${stringResource(R.string.alert_sort_session)}"
                    } else {
                        "🕒 ${stringResource(R.string.alert_sort_chrono)}"
                    }
                    val dwBorder = Color(0xFF2D3148)
                    val controlShape = RoundedCornerShape(6.dp)
                    @Composable
                    fun ControlBtn(label: String, onClick: () -> Unit) {
                        Box(
                            modifier = Modifier
                                .border(1.dp, dwBorder, controlShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant, controlShape)
                                .clickable(onClick = onClick)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "🔔 $totalCount ${if (totalCount == 1) "alert" else "alerts"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        ControlBtn(sortLabel) {
                            vm.setSortMode(
                                if (state.sortMode == AlertsViewModel.SortMode.BySession)
                                    AlertsViewModel.SortMode.Chronological
                                else AlertsViewModel.SortMode.BySession,
                            )
                        }
                        ControlBtn("✕", vm::dismissAll)
                        ControlBtn("🔕") { vm.dismissAll() }
                        ControlBtn("↻", vm::refresh)
                    }
                    HorizontalDivider(color = dwBorder.copy(alpha = 0.5f))
                    // Row 2: chips with emoji + ×N counts
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AlertsViewModel.ChipFilter.entries.forEach { chip ->
                            val count = state.chipCounts[chip] ?: 0
                            val chipLabel = when (chip) {
                                AlertsViewModel.ChipFilter.All -> "${stringResource(R.string.alert_chip_all)} ×$count"
                                AlertsViewModel.ChipFilter.Prompt -> "🟡 ${stringResource(R.string.alert_chip_prompt)} ×$count"
                                AlertsViewModel.ChipFilter.Error -> "🔴 ${stringResource(R.string.alert_chip_error)} ×$count"
                                AlertsViewModel.ChipFilter.Warn -> "🟠 ${stringResource(R.string.alert_chip_warn)} ×$count"
                                AlertsViewModel.ChipFilter.Info -> "⚪ ${stringResource(R.string.alert_chip_info)} ×$count"
                            }
                            val chipBorderColor = when (chip) {
                                AlertsViewModel.ChipFilter.Prompt, AlertsViewModel.ChipFilter.Warn -> Color(0xFFF59E0B)
                                AlertsViewModel.ChipFilter.Error -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val isSelected = state.chipFilter == chip
                            val chipBg = if (isSelected) chipBorderColor else MaterialTheme.colorScheme.surfaceVariant
                            val chipFg = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
                            Box(
                                modifier = Modifier
                                    .border(1.dp, chipBorderColor, RoundedCornerShape(10.dp))
                                    .background(chipBg, RoundedCornerShape(10.dp))
                                    .clickable { vm.setChipFilter(chip) }
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(chipLabel, fontSize = 12.sp, color = chipFg)
                            }
                        }
                    }
                    // Row 3: search
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = vm::setSearch,
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

            val groups = state.visibleGroups

            if (state.sortMode == AlertsViewModel.SortMode.Chronological) {
                // Flat chronological view scoped to the current tab's groups.
                val flatChrono = groups.flatMap { it.alerts }.sortedByDescending { it.createdAt }
                if (flatChrono.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.alerts_empty_active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        items(flatChrono, key = { it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                showQuickReply = false,
                                onQuickReply = { alert.sessionId?.let { onOpenSession(it) } },
                                onMarkRead = { vm.markAlertRead(alert.id) },
                            )
                        }
                    }
                }
            } else {
                // Grouped (BySession) view.
                if (groups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.refreshing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
                        } else {
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
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(groups, key = { it.sessionId }) { group ->
                            val expanded =
                                group.sessionId in state.expandedSessionIds ||
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
                                onMarkRead = vm::markAlertRead,
                            )
                        }
                    }
                }
            }
        }
    }

}

/**
 * Alerts top bar: server picker dropdown + sort toggle + dismiss-all.
 * Chip filters and search have moved below the primary TabRow in the
 * content area (PWA-style per-tab secondary filters).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsTopBar(
    state: AlertsViewModel.UiState,
    reachable: Boolean? = null,
    lastProbeEpochMs: Long? = null,
    onRetry: () -> Unit = {},
    onSelectProfile: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
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
            DocsLinkAction("datawatch-definitions.md#alerts")
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
    onMarkRead: (String) -> Unit,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val stateColor = stateAccentColor(group.state)
    val dwBorder = Color(0xFF2D3148)
    val cardShape = RoundedCornerShape(6.dp)
    // PWA session card: border:1px solid var(--border); border-radius:6px; margin-bottom:10px
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .border(1.dp, dwBorder, cardShape)
            .background(MaterialTheme.colorScheme.surface, cardShape)
            .pointerInput(group.sessionId) {
                if (group.sessionId == AlertsViewModel.AlertGroup.SYSTEM_BUCKET) return@pointerInput
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -swipeThresholdPx) onDismiss() },
                    onDragCancel = { dx = 0f },
                ) { _, delta -> dx += delta }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Session header: ▼/▶ | name | state | [auto] count · last HH:MM:SS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (expanded) "▼" else "▶",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenSession),
                )
                if (group.state != null) {
                    Text(
                        stateLabel(group.state),
                        fontSize = 11.sp,
                        color = stateColor,
                        maxLines = 1,
                    )
                }
                serverName?.let { name ->
                    Text(
                        name,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // right-aligned: count [· 🟡 N] · last HH:MM:SS (matches PWA margin-left:auto)
                Spacer(modifier = Modifier.weight(1f))
                val lastTs = group.alerts.firstOrNull()?.createdAt
                val promptCount = group.alerts.count { a ->
                    group.state == SessionState.Waiting ||
                        a.type.contains("input", ignoreCase = true) ||
                        a.type == "needs_input" || a.type == "input_needed" ||
                        Regex("\\b(needs input|prompt|waiting)\\b", RegexOption.IGNORE_CASE).containsMatchIn(a.title)
                }
                val countText = "${group.alerts.size} alert${if (group.alerts.size == 1) "" else "s"}"
                val promptHint = if (promptCount > 0) " · 🟡 $promptCount" else ""
                val lastText = if (lastTs != null) " · last ${formatAlertTime(lastTs)}" else ""
                Text(
                    "$countText$promptHint$lastText",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = if (lastTs != null) FontFamily.Monospace else FontFamily.Default,
                    maxLines = 1,
                )
            }

            if (expanded) {
                val canQuickReply =
                    group.session?.state == SessionState.Waiting &&
                        group.sessionId != AlertsViewModel.AlertGroup.SYSTEM_BUCKET
                // PWA uses padding:6px 10px on the content div, no dividers between alerts
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    group.alerts.forEachIndexed { idx, alert ->
                        AlertCard(
                            alert = alert,
                            showQuickReply = canQuickReply && idx == 0,
                            onQuickReply = { onOpenSession() },
                            onMarkRead = { onMarkRead(alert.id) },
                            sessionState = group.state,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-alert card. Matches PWA renderRow: left border + badge + time + title in one Row,
 * body below, quick-reply select for waiting sessions (first alert only).
 */
@Composable
private fun AlertCard(
    alert: Alert,
    showQuickReply: Boolean,
    onQuickReply: () -> Unit,
    onMarkRead: () -> Unit,
    sessionState: SessionState? = null,
) {
    // Prompt: waiting_input session OR type contains "input" OR title matches PWA regex.
    val isPromptType = sessionState == SessionState.Waiting ||
        alert.type.contains("input", ignoreCase = true) ||
        alert.type == "needs_input" || alert.type == "input_needed" ||
        Regex("\\b(needs input|prompt|waiting)\\b", RegexOption.IGNORE_CASE).containsMatchIn(alert.title)
    val isError = alert.severity == AlertSeverity.Error

    // Badge text, bg, text color — matches PWA kindBadge logic
    val badgeText: String
    val badgeBg: Color
    val badgeFg: Color
    val borderColor: Color
    val bgColor: Color
    when {
        isPromptType -> {
            badgeText = "🟡 PROMPT"
            badgeBg = Color(0xFFF59E0B)
            badgeFg = Color(0xFF0F1117) // var(--bg) = dark background
            borderColor = Color(0xFFF59E0B)
            bgColor = Color(0xFFF59E0B).copy(alpha = 0.08f)
        }
        isError -> {
            badgeText = "🔴 ERROR"
            badgeBg = Color(0xFFEF4444)
            badgeFg = Color.White
            borderColor = Color(0xFFEF4444)
            bgColor = Color(0xFFEF4444).copy(alpha = 0.06f)
        }
        else -> {
            badgeText = "⚪ ${alert.severity.name.lowercase()}"
            badgeBg = MaterialTheme.colorScheme.surfaceVariant
            badgeFg = MaterialTheme.colorScheme.onSurfaceVariant
            borderColor = Color(0xFF2D3148) // var(--border)
            bgColor = Color.Transparent
        }
    }

    // PWA: margin:4px 0; border-radius:0 4px 4px 0 (left flat, right rounded)
    val alertCardShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bgColor, alertCardShape),
    ) {
        // Left border — 3dp colored (no radius on left side)
        Box(modifier = Modifier.width(3.dp).background(borderColor)) {
            Spacer(modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // Badge + time + title in one Row (matches PWA flex row)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                    )
                }
                Text(
                    formatAlertTime(alert.createdAt),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                if (alert.title.isNotBlank()) {
                    Text(
                        alert.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (alert.message.isNotBlank()) {
                Text(
                    alert.message.take(500),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Quick reply — for waiting sessions, first alert only
            if (showQuickReply) {
                OutlinedButton(
                    onClick = onQuickReply,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp, vertical = 2.dp,
                    ),
                ) {
                    Text(stringResource(R.string.alerts_quick_reply_ph), fontSize = 11.sp)
                }
            }
            // ✓ mark-read — subtle; not in PWA but needed for Android UX
            if (!alert.read) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onMarkRead,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 6.dp, vertical = 2.dp,
                        ),
                    ) {
                        Text("✓", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        SessionState.Running -> Color(0xFF10B981) // var(--success)
        SessionState.Waiting -> Color(0xFFF59E0B) // var(--warning) — matches PWA waiting_input
        else -> MaterialTheme.colorScheme.onSurfaceVariant // var(--text2)
    }

private fun stateLabel(s: SessionState?): String =
    when (s) {
        null -> ""
        SessionState.Waiting -> "🟠 waiting input"
        SessionState.Running -> "🟢 running"
        else -> "✅ ${s.name.lowercase()}"
    }

/** Formats alert timestamp as HH:MM:SS — matches PWA toLocaleTimeString('en-GB', {hour12:false}). */
private fun formatAlertTime(ts: kotlinx.datetime.Instant): String {
    val inst = java.time.Instant.ofEpochMilli(ts.toEpochMilliseconds())
    val ldt = java.time.LocalDateTime.ofInstant(inst, java.time.ZoneId.systemDefault())
    return "%02d:%02d:%02d".format(ldt.hour, ldt.minute, ldt.second)
}

@Composable
private fun AlertsStatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
}
