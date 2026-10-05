package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.CurrentStatusDto
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.shell.SessionsNavChannel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaStatePill
import com.dmzs.datawatchclient.ui.theme.pwaCard
import com.dmzs.datawatchclient.ui.theme.pwaStateEdge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Fullscreen

/**
 * Approximate session-row height in dp — used by the long-press drag
 * gesture to translate vertical drag distance into "N rows moved"
 * before calling `moveSessionByOffset`. Actual row heights vary
 * slightly with task-context lines (waiting_input rows are taller),
 * but the rounding self-corrects within ~1 row and users seldom drag
 * > 5 rows in one gesture.
 */
private const val ROW_HEIGHT_GUESS_DP: Int = 72

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsScreen(
    onOpenSession: (String) -> Unit = {},
    onEditServer: (String) -> Unit = {},
    onAddServer: () -> Unit = {},
    onNewSession: () -> Unit = {},
    /** BL303 — row maximize opens the session in Dashboard expand (status) mode. */
    onExpandSession: (String) -> Unit = {},
    vm: SessionsViewModel = viewModel(),
    alertsVm: AlertsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val watchedIds by vm.watchedIds.collectAsState()
    val alertsState by alertsVm.state.collectAsState()
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Parity D15a — PWA select mode: entered with the ☑ toolbar button (only
    // while History is on), checkboxes on inactive cards, fixed bottom bar.
    var selectionMode by remember { mutableStateOf(false) }
    LaunchedEffect(state.stateChip, state.backendFilter, state.filterText, state.showHistory) {
        selectedIds = emptySet()
    }
    LaunchedEffect(state.showHistory) {
        if (!state.showHistory) selectionMode = false
    }
    var bulkDeleteConfirmOpen by remember { mutableStateOf(false) }
    // Search / filter / sort toolbar is collapsed by default — user
    // 2026-04-24 (dmz006/datawatch#23). The top-app-bar search icon
    // toggles this. Stays implicitly "expanded" when filter text or
    // history are active so typed queries / visible state aren't hidden.
    var toolbarExpanded by remember { mutableStateOf(false) }
    val pendingFilter by SessionsNavChannel.pendingFilter.collectAsState()
    LaunchedEffect(pendingFilter) {
        val f = pendingFilter ?: return@LaunchedEffect
        vm.setFilterText(f)
        SessionsNavChannel.consume()
        toolbarExpanded = true
    }

    // BL-T14-1: Refresh on every ON_RESUME so sessions are current immediately
    // after screen unlock, app foreground, or returning from another screen.
    // The ViewModel's 5-second poll handles steady-state; this removes the
    // first-poll lag that made sessions feel stale on resume.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            run {
                TopAppBar(
                    title = {
                        // Parity D2a: brand title + server chip; switching
                        // moved to the PWA picker bar under the header.
                        SessionsHeaderTitle(
                            active = state.activeProfile,
                            allMode = state.allServersMode,
                        )
                    },
                    actions = {
                        // Inline refresh-in-progress spinner (Sessions
                        // tab auto-polls every 5 s — explicit refresh
                        // button was dropped in v0.14.2).
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(8.dp).size(18.dp),
                            )
                        }
                        // G3 — context help link (Claude Code hooks docs).
                        DocsLinkAction("datawatch-definitions.md#sessions-list")
                        // User direction 2026-04-24 + dmz006/datawatch#23
                        // — search icon lives on the top app bar, left
                        // of the reachability dot. Tapping toggles the
                        // filter toolbar underneath.
                        IconButton(onClick = { toolbarExpanded = !toolbarExpanded }) {
                            Icon(
                                if (toolbarExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription =
                                    if (toolbarExpanded) {
                                        stringResource(
                                            R.string.sessions_filter_collapse,
                                        )
                                    } else {
                                        stringResource(R.string.sessions_filter_expand)
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AlertsBellAction(alertsBadge = alertsState.watchedAlertCount)
                        // Reachability dot on the right (PWA places
                        // its connection indicator in the same spot).
                        // Single-server mode only; all-servers mode
                        // tracks many profiles so we hide the dot
                        // (ADR-0013).
                        if (!state.allServersMode && state.activeProfile != null) {
                            ReachabilityDot(
                                reachable = state.activeReachable,
                                lastProbeEpochMs = state.lastProbeEpochMs,
                                onRetry = vm::refresh,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                val doneStates = setOf(SessionState.Completed, SessionState.Killed, SessionState.Error)
                val doneIds =
                    state.visibleSessions.filter { it.state in doneStates }.map { it.id }.toSet()
                SessionsSelectBar(
                    selectedCount = selectedIds.size,
                    selectableCount = doneIds.size,
                    allSelected = doneIds.isNotEmpty() && selectedIds.containsAll(doneIds),
                    canDelete = state.deleteSupported,
                    onToggleAll = {
                        selectedIds = if (selectedIds.containsAll(doneIds)) emptySet() else doneIds
                    },
                    onDelete = { bulkDeleteConfirmOpen = true },
                    onCancel = {
                        selectedIds = emptySet()
                        selectionMode = false
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && state.activeProfile != null) {
                // User 2026-04-24 (round 2): "the + on sessions list
                // needs to be lower" — first pass (bottom = 24.dp)
                // didn't pull enough. Using a negative-y offset instead
                // of padding so the FAB drops past the Scaffold's
                // floating-action inset reserve; a 6.8" screen has
                // ~28 dp of gesture inset below the button and the
                // FAB is visibly below the bottom-nav bar's vertical
                // centre now.
                FloatingActionButton(
                    onClick = onNewSession,
                    modifier =
                        Modifier
                            .offset(y = 36.dp)
                            .padding(end = 4.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sessions_fab_new))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            com.dmzs.datawatchclient.ui.common.ServerPickerBar(
                profiles = state.allProfiles,
                activeId = state.activeProfile?.id,
                allMode = state.allServersMode,
                onSelect = vm::selectProfile,
                showAll = true,
                onSelectAll = vm::selectAllServers,
            )
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
            SessionsToolbar(
                filterText = state.filterText,
                onFilterTextChange = vm::setFilterText,
                backendCounts = state.backendCounts,
                activeBackendFilter = state.backendFilter,
                onToggleBackend = vm::toggleBackendFilter,
                showHistory = state.showHistory,
                historyCount = state.historyCount,
                onToggleShowHistory = vm::toggleShowHistory,
                expanded = toolbarExpanded,
                onCollapse = { toolbarExpanded = false },
                stateChip = state.stateChip,
                stateCounts = state.stateCounts,
                visibleStateChips = state.visibleStateChips,
                onStateChipChange = vm::setStateChip,
                selectMode = selectionMode,
                onToggleSelectMode = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds = emptySet()
                },
                treeView = state.treeView,
                onToggleTreeView = vm::toggleTreeView,
                pendingSchedules = state.pendingSchedules,
                onCancelSchedule = vm::cancelSchedule,
            )

            val visible = state.visibleSessions
            if (visible.isEmpty()) {
                if (state.refreshing) {
                    SessionSkeletonList()
                } else {
                    EmptyState(
                        showHint = state.activeProfile != null,
                        noServer = state.allProfiles.none { it.enabled },
                        onAddServer = onAddServer,
                    )
                }
            } else {
                // v0.33.15 (B9): datawatch eye watermark behind the
                // sessions list. PWA centers the brand icon at ~85%
                // page width as a faint background. Uses the shared
                // launcher-foreground vector which already draws the
                // eye + matrix + arcs; clipped to the list bounds
                // and painted at 10% alpha so it doesn't compete with
                // the row content.
                // Pull-to-refresh (mobile convention; iOS has `.refreshable`).
                val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                if (pullState.isRefreshing) {
                    LaunchedEffect(Unit) {
                        vm.refresh()
                        kotlinx.coroutines.delay(400)
                        while (vm.state.value.refreshing) kotlinx.coroutines.delay(100)
                        pullState.endRefresh()
                    }
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection),
                ) {
                    androidx.compose.foundation.Image(
                        painter =
                            androidx.compose.ui.res.painterResource(
                                id = com.dmzs.datawatchclient.R.drawable.ic_launcher_foreground,
                            ),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .align(androidx.compose.ui.Alignment.Center)
                                .alpha(0.10f),
                    )
                    LazyColumn {
                        // Key = profile:id to avoid LazyColumn duplicate-key crashes
                        // when the same session id appears under both a server's
                        // primary list and another server's federation fan-out.
                        // BL348 — tree view nests children under their parent (18 dp per level).
                        val rows =
                            if (state.treeView) {
                                state.treeRows
                            } else {
                                visible.map { SessionsViewModel.UiState.TreeRow(it, 0, false) }
                            }
                        items(rows, key = { "${it.session.serverProfileId}:${it.session.id}" }) { row ->
                            val session = row.session
                            val borderColor = LocalDatawatchColors.current.border
                            // Per-row drag state. The user long-presses the row
                            // to start dragging; while dragging, the row floats
                            // via `translationY` and other rows stay put. On
                            // release, `moveSessionByOffset` applies the delta
                            // in one shot, matching PWA `sessionDrop()` (app.js
                            // :1414-1415). Row height is approximated at 72 dp
                            // — exact height varies with task-context lines,
                            // but rounding errors self-correct within ~1 row
                            // and users seldom drag > 5 rows in one gesture.
                            val density = LocalDensity.current
                            val rowHeightPx =
                                with(density) { ROW_HEIGHT_GUESS_DP.dp.toPx() }
                            var dragOffsetY by remember(session.id) {
                                mutableStateOf(0f)
                            }
                            var isDragging by remember(session.id) {
                                mutableStateOf(false)
                            }
                            Column(
                                modifier =
                                    if (row.depth > 0) {
                                        Modifier
                                            .padding(start = (row.depth * 18).dp)
                                            .drawBehind {
                                                drawRect(
                                                    color = borderColor,
                                                    topLeft = androidx.compose.ui.geometry.Offset(6.dp.toPx(), 0f),
                                                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                                                )
                                            }
                                            .padding(start = 6.dp)
                                    } else {
                                        Modifier
                                    },
                            ) {
                            if (row.orphaned) {
                                Text(
                                    "⚠ " + stringResource(R.string.session_tree_orphaned),
                                    fontSize = 10.sp,
                                    color = LocalDatawatchColors.current.warning,
                                    modifier = Modifier.padding(start = 14.dp, top = 4.dp),
                                )
                            }
                            SessionRow(
                                session = session,
                                onExpand = { onExpandSession(session.id) },
                                backend = session.backend ?: state.backendByProfileId[session.serverProfileId],
                                reorderMode = state.reorderMode,
                                showHostname = state.allServersMode,
                                onMoveUp = { vm.moveUp(session.id) },
                                onMoveDown = { vm.moveDown(session.id) },
                                onQuickReply = { text -> vm.quickReply(session.id, text) },
                                fetchSavedCommands = { vm.fetchSavedCommands(session.id) },
                                fetchSystemCommands = { vm.fetchSystemQuickCommands(session.id) },
                                fetchCurrentStatus = { vm.fetchCurrentStatus(session.id) },
                                onResummarize = { vm.resummmarizeSession(session.id) },
                                whisperConfigured = state.whisperConfigured,
                                deleteSupported = state.deleteSupported,
                                selectionMode = selectionMode,
                                isSelected = session.id in selectedIds,
                                dragOffsetY = dragOffsetY,
                                isDragging = isDragging,
                                onDragStart = {
                                    // Ignore drags while multi-select is
                                    // active — long-press is repurposed for
                                    // selection toggle there.
                                    if (selectionMode) return@SessionRow
                                    isDragging = true
                                },
                                onDrag = { delta -> dragOffsetY += delta },
                                onDragEnd = {
                                    if (!selectionMode && isDragging) {
                                        val shift = (dragOffsetY / rowHeightPx).toInt()
                                        if (shift != 0) {
                                            vm.moveSessionByOffset(session.id, shift)
                                        }
                                    }
                                    dragOffsetY = 0f
                                    isDragging = false
                                },
                                onClick = {
                                    val selectable =
                                        session.state == SessionState.Completed ||
                                            session.state == SessionState.Killed ||
                                            session.state == SessionState.Error
                                    if (selectionMode && selectable) {
                                        selectedIds = selectedIds.toggle(session.id)
                                    } else {
                                        onOpenSession(session.id)
                                    }
                                },
                                onLongPress = {
                                    // Parity D15a: long-press no longer enters
                                    // selection (PWA uses the ☑ button); outside
                                    // select mode the gesture is the drag handle.
                                    if (selectionMode) selectedIds = selectedIds.toggle(session.id)
                                },
                                onSwipeMute = {
                                    if (!selectionMode) vm.toggleMute(session.id, session.muted)
                                },
                                onRename = { newName -> vm.rename(session.id, newName) },
                                onRestart = { vm.restart(session.id) },
                                onKill = { vm.kill(session.id) },
                                onDelete = { vm.delete(session.id) },
                                isWatched = session.id in watchedIds,
                                onWatchToggle = { vm.toggleWatch(session.id) },
                            )
                            }
                        }
                    } // LazyColumn close
                    androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
                        state = pullState,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                } // watermark Box close
            }
        }
    }

    if (bulkDeleteConfirmOpen) {
        val ids = selectedIds.toList()
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirmOpen = false },
            title = { Text(stringResource(R.string.dialog_delete_sessions_title, ids.size)) },
            text = {
                Text(
                    stringResource(R.string.dialog_delete_sessions_body),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteMany(ids)
                        selectedIds = emptySet()
                        selectionMode = false
                        bulkDeleteConfirmOpen = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirmOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item

/**
 * Parity D15a — PWA `.select-bar`: fixed bar above the bottom nav with
 * `☑ All/None (N)` · `🗑 Delete (N)` (red when enabled) · Cancel.
 */
@Composable
private fun SessionsSelectBar(
    selectedCount: Int,
    selectableCount: Int,
    allSelected: Boolean,
    canDelete: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onToggleAll, modifier = Modifier.weight(1f)) {
                Text(
                    "☑ ${if (allSelected) "None" else "All"} ($selectableCount)",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val deleteEnabled = canDelete && selectedCount > 0
            OutlinedButton(onClick = onDelete, enabled = deleteEnabled, modifier = Modifier.weight(1f)) {
                Text(
                    "🗑 ${stringResource(R.string.action_delete)} ($selectedCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (deleteEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * PWA-matching Sessions toolbar. Three stacked rows (when present):
 *   1. Free-text filter input (search by name/task/id/backend).
 *   2. Backend chip row — one `FilterChip` per unique backend across
 *      the session pool with an inline count. Tap to filter; tap
 *      again to clear.
 *   3. Show/Hide history toggle button labelled with the done-session
 *      count.
 *
 * The old v0.11 quick-state filter chips (All / Running / Waiting /
 * Completed / Error) were dropped — the PWA doesn't have them and
 * the partitioned pool (active + recent → full history) covers the
 * same use case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsToolbar(
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    backendCounts: List<Pair<String, Int>>,
    activeBackendFilter: String?,
    onToggleBackend: (String) -> Unit,
    showHistory: Boolean,
    historyCount: Int,
    onToggleShowHistory: () -> Unit,
    expanded: Boolean,
    onCollapse: () -> Unit,
    // Parity D12a: PWA `State (N)` collapsible chips over the 7 real states.
    stateChip: String = SessionsViewModel.UiState.STATE_CHIP_ALL,
    stateCounts: Map<String, Int> = emptyMap(),
    visibleStateChips: List<String> = listOf(SessionsViewModel.UiState.STATE_CHIP_ALL),
    onStateChipChange: (String) -> Unit = {},
    // Parity D15a: ☑ toggles select mode (shown only with History on).
    selectMode: Boolean = false,
    onToggleSelectMode: () -> Unit = {},
    treeView: Boolean = false,
    onToggleTreeView: () -> Unit = {},
    pendingSchedules: List<com.dmzs.datawatchclient.domain.Schedule> = emptyList(),
    onCancelSchedule: (String) -> Unit = {},
) {
    // Toolbar is rendered only when expanded (user toggled search) OR
    // something filter-related is active (stale state we don't want
    // to hide). Collapsed state = nothing renders here; the search
    // icon lives on the TopAppBar above.
    val show =
        expanded || filterText.isNotEmpty() ||
            activeBackendFilter != null || showHistory || treeView
    if (!show) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        run {
            // BL-SL-3: PWA layout — text input + LLM button on same row; state chips always visible.
            var llmExpanded by remember { mutableStateOf(false) }
            var stateExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = onFilterTextChange,
                    placeholder = { Text(stringResource(R.string.sessions_filter_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { onFilterTextChange("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.sessions_clear_filter),
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                onFilterTextChange("")
                                onCollapse()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.sessions_collapse_toolbar),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { llmExpanded = !llmExpanded },
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                ) {
                    Text(
                        stringResource(R.string.llm_filter_btn_tip, backendCounts.size + 1),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Icon(
                        if (llmExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            // Parity D12a — PWA `State (N) ▸` button; chips for every real
            // state with a count > 0 (plus All and the selected one).
            val stateActive = stateChip != SessionsViewModel.UiState.STATE_CHIP_ALL
            OutlinedButton(
                onClick = { stateExpanded = !stateExpanded },
                modifier = Modifier.padding(top = 4.dp),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 4.dp,
                    ),
            ) {
                Text(
                    if (stateActive) "State: $stateChip" else "State (${visibleStateChips.size - 1})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stateActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    if (stateExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            if (stateExpanded) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleStateChips) { key ->
                        val dotColor = stateChipColor(key)
                        FilterChip(
                            selected = stateChip == key,
                            onClick = { onStateChipChange(key) },
                            label = {
                                Text(
                                    "${stateChipLabel(key)} ${stateCounts[key] ?: 0}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(dotColor, androidx.compose.foundation.shape.CircleShape),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            AnimatedVisibility(visible = llmExpanded) {
                val councilLabel = stringResource(R.string.council_session_filter)
                LazyRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    items(backendCounts) { (backend, count) ->
                        FilterChip(
                            selected = activeBackendFilter == backend,
                            onClick = { onToggleBackend(backend) },
                            label = { Text("$backend · $count", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                    // v0.74.0 S5-7 — Council virtual session filter chip
                    item {
                        FilterChip(
                            selected = activeBackendFilter == "council-virtual",
                            onClick = { onToggleBackend("council-virtual") },
                            label = { Text(councilLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                // PWA `🕒 N` pending-schedules badge + dropdown with per-item cancel.
                if (pendingSchedules.isNotEmpty()) {
                    var schedOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { schedOpen = !schedOpen },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("🕒 ${pendingSchedules.size}", style = MaterialTheme.typography.labelSmall)
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = schedOpen,
                            onDismissRequest = { schedOpen = false },
                        ) {
                            Text(
                                stringResource(R.string.sessions_pending_schedules),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                            pendingSchedules.forEach { sc ->
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).widthIn(min = 240.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        sc.sessionId ?: sc.task.take(40),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalDatawatchColors.current.accent2,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    Text(
                                        sc.runAt?.let { relativeTimeLabel(it.toEpochMilliseconds()) } ?: sc.cron.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                    IconButton(onClick = { onCancelSchedule(sc.id) }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_cancel),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // BL348 — Tree toggle (PWA `btn-toggle-history` styled, `.active` when on).
                OutlinedButton(
                    onClick = onToggleTreeView,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors =
                        if (treeView) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                ) {
                    Text(
                        stringResource(R.string.session_tree_btn),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (treeView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (historyCount > 0) {
                    OutlinedButton(
                        onClick = onToggleShowHistory,
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp,
                            ),
                    ) {
                        Text(
                            // v0.35.7 — drop the verb churn, match
                            // PWA v5.1.0 ("History (N)").
                            stringResource(R.string.sessions_history_count, historyCount),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    // Parity D15a — PWA ☑ toggles select mode (History on only).
                    if (showHistory) {
                        IconButton(onClick = onToggleSelectMode, modifier = Modifier.size(32.dp)) {
                            Text(
                                "☑",
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (selectMode) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stateChipLabel(key: String): String =
    when (key) {
        "running" -> stringResource(R.string.session_chip_running)
        "waiting_input" -> stringResource(R.string.session_filter_waiting)
        "rate_limited" -> stringResource(R.string.session_chip_rate_limited)
        "complete" -> stringResource(R.string.session_chip_complete)
        "failed" -> stringResource(R.string.session_chip_failed)
        "killed" -> stringResource(R.string.session_chip_killed)
        else -> stringResource(R.string.session_filter_all)
    }

/** PWA `realStateChips` colours. */
@Composable
private fun stateChipColor(key: String): Color {
    val dw = LocalDatawatchColors.current
    return when (key) {
        "running" -> dw.success
        "waiting_input" -> dw.warning
        "rate_limited", "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun SessionSkeletonList() {
    val alpha by com.dmzs.datawatchclient.ui.theme.rememberDwPulse(
        initial = 0.3f,
        target = 0.7f,
        durationMs = 900,
        staticValue = 0.5f,
        label = "skeleton",
    )
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        repeat(5) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it % 2 == 0) 0.65f else 0.80f)
                        .height(14.dp)
                        .background(shimmer, RoundedCornerShape(4.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .background(shimmer, RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

/** Parity D35a — PWA empty state: 💬 "No active sessions" + hint. */
@Composable
private fun EmptyState(
    showHint: Boolean = true,
    noServer: Boolean = false,
    onAddServer: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (noServer) {
            // Parity D86c — minimal first run: no onboarding screen; the
            // Sessions tab itself says no server is connected and offers Add.
            NoServerEmptyState(onAddServer)
            return@Box
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "💬",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                stringResource(R.string.sessions_empty_state),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (showHint) {
                Text(
                    stringResource(R.string.sessions_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun NoServerEmptyState(onAddServer: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🖥",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            stringResource(R.string.first_run_no_server),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.first_run_no_server_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        androidx.compose.material3.Button(onClick = onAddServer, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.sessions_add_server))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: Session,
    backend: String?,
    onExpand: () -> Unit = {},
    deleteSupported: Boolean,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeMute: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onRestart: () -> Unit = {},
    onKill: () -> Unit = {},
    onDelete: () -> Unit = {},
    onQuickReply: (String) -> Unit = {},
    isWatched: Boolean = false,
    onWatchToggle: () -> Unit = {},
    fetchSavedCommands: suspend () -> List<Pair<String, String>> = { emptyList() },
    fetchSystemCommands: suspend () -> List<com.dmzs.datawatchclient.transport.QuickCommandItem> = { emptyList() },
    fetchCurrentStatus: suspend () -> CurrentStatusDto? = { null },
    onResummarize: suspend () -> CurrentStatusDto? = { null },
    whisperConfigured: Boolean = false,
    reorderMode: Boolean = false,
    showHostname: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    // Drag-drop state — passed from SessionsScreen so the caller owns
    // the offset (per-row state would reset on LazyColumn re-key).
    dragOffsetY: Float = 0f,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    var quickCmdsOpen by remember { mutableStateOf(false) }
    var responseOpen by remember { mutableStateOf(false) }
    var currentStatusOpen by remember { mutableStateOf(false) }
    var currentStatusText by remember { mutableStateOf<String?>(null) }
    var currentStatusLongText by remember { mutableStateOf<String?>(null) }
    var currentStatusLoading by remember { mutableStateOf(false) }
    var summaryExpanded by remember { mutableStateOf(false) }
    val currentStatusScope = rememberCoroutineScope()
    val noChangeStr = stringResource(R.string.no_change_since_last_refresh)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var restartConfirmOpen by remember { mutableStateOf(false) }
    var killConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    val colors = LocalDatawatchColors.current
    val timeLabel = relativeTimeLabel(session.lastActivityAt.toEpochMilliseconds())
    val isDoneState =
        session.state == SessionState.Completed ||
            session.state == SessionState.Killed ||
            session.state == SessionState.Error

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .alpha(if (isDoneState) 0.6f else 1.0f)
                .graphicsLayer {
                    // While being dragged, the row floats vertically
                    // and sits above its neighbours — neighbours stay
                    // put (no live reordering). Release applies the
                    // shift in one shot via moveSessionByOffset.
                    translationY = if (isDragging) dragOffsetY else 0f
                    shadowElevation = if (isDragging) 12f else 0f
                }
                .pointerInput(session.id, selectionMode) {
                    if (selectionMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { _: androidx.compose.ui.geometry.Offset ->
                            onDragStart()
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { _, delta -> onDrag(delta.y) },
                    )
                }
                .pwaCard()
                .pwaStateEdge(session.state)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        )
                    } else {
                        Modifier
                    },
                )
                // v0.33.15 (B3): pointerInput BEFORE combinedClickable
                // so the horizontal-drag detector sees events first.
                // combinedClickable's internal pointerInput consumed
                // drag gestures on the main-pass in the previous order,
                // so swipe-to-mute never fired — the finger-up just
                // looked like a tap that Compose swallowed via the
                // press-release cycle.
                .pointerInput(session.id, selectionMode) {
                    if (selectionMode) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (dx.absoluteValue >= swipeThresholdPx) onSwipeMute() },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Header row: name/id + state pill + mute/more actions.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // PWA parity: show checkbox for non-active sessions when in select mode
            val isActive = session.state == SessionState.Running || session.state == SessionState.Waiting || session.state == SessionState.RateLimited
            if (selectionMode && !isActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (reorderMode) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Parity D16a — PWA line 1: name, else task (80 chars), else "(no task)".
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                val line1 =
                    (session.name?.takeIf { it.isNotBlank() } ?: session.taskSummary?.takeIf { it.isNotBlank() })
                        ?.let { if (it.length > 80) it.take(80) + "…" else it }
                        ?: "(no task)"
                Text(
                    line1,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
            }
            PwaStatePill(session.state)
            if (!backend.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                PwaMetaBadge(text = backend)
            }
            // v0.42.6 — Container Workers provenance chip (PWA v5.26.58).
            // Purple ⬡ when this session was spawned by a worker agent.
            // Hidden for user-spawned sessions (the common case).
            if (!session.agentId.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                WorkerPill(agentId = session.agentId!!)
            }
            // v0.74.0 S5-7 — Council virtual session badge
            val isCouncil = backend == "council-virtual" || session.fullId.startsWith("council-")
            if (isCouncil) {
                Spacer(modifier = Modifier.width(6.dp))
                PwaMetaBadge(text = "🎭")
            }
            if (reorderMode) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.sessions_move_up))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.sessions_move_down))
                }
            }
            if (!reorderMode && !selectionMode) {
                // BL303 — PWA `sess-maximize-btn`: open in Dashboard expand mode.
                IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.dash_expand_session),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onWatchToggle,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (isWatched) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                        contentDescription = stringResource(if (isWatched) R.string.session_watch_on else R.string.session_watch_off),
                        modifier = Modifier.size(18.dp),
                        tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }

        // Meta row: short-id pill (+ hostname only with several servers);
        // [📄 Response] + time on the right (parity D16a, PWA layout).
        Row(
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionIdPill(session.id)
            Spacer(modifier = Modifier.width(6.dp))
            val hostname = session.hostnamePrefix
            if (showHostname && !hostname.isNullOrBlank()) {
                Text(
                    hostname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  ·  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // PWA server badge (federated row from a non-local server).
            val serverName = session.server
            if (!serverName.isNullOrBlank() && serverName != "local") {
                OutlineBadge(serverName, colors.accent2)
                Spacer(modifier = Modifier.width(4.dp))
            }
            // PWA `↳ child of [host]` parent badge (BL347 lineage).
            val parentId = session.parentId
            if (!parentId.isNullOrBlank()) {
                OutlineBadge(
                    "↳ " + stringResource(R.string.session_child_of) + " [" + parentId.substringBefore('-') + "]",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.7f),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            // PWA `⚠ zombie` (claude_alive === false).
            if (session.claudeAlive == false) {
                OutlineBadge("⚠ zombie", Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            // BL383 live elapsed clock on active cards (1 s tick, tabular, accent2).
            val isActiveRow =
                session.state == SessionState.Running || session.state == SessionState.Waiting ||
                    session.state == SessionState.RateLimited
            val createdMs = session.createdAt.toEpochMilliseconds()
            if (isActiveRow && createdMs > 0L) {
                val nowMs by androidx.compose.runtime.produceState(System.currentTimeMillis(), session.id) {
                    while (true) {
                        kotlinx.coroutines.delay(1000)
                        value = System.currentTimeMillis()
                    }
                }
                val elapsed = nowMs - createdMs
                if (elapsed >= 0) {
                    Text(
                        formatElapsed(elapsed),
                        fontSize = 10.sp,
                        color = colors.accent2.copy(alpha = 0.85f),
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            if (!session.lastResponse.isNullOrBlank()) {
                TextButton(
                    onClick = { responseOpen = true },
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 6.dp,
                            vertical = 0.dp,
                        ),
                    modifier = Modifier.height(20.dp),
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = stringResource(R.string.sessions_view_response),
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(R.string.sessions_view_response), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            val summaryAt = session.summaryGeneratedAt
            if (summaryAt != null) {
                Text(
                    "AI " + relativeTimeLabel(summaryAt.toEpochMilliseconds()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Waiting-input context preview — PWA-style quote block under rows
        // that block on user input. Prefers the multi-line
        // `prompt_context` payload (PWA behaviour) so trust prompts show
        // the imperative line *and* the action line; falls back to
        // `last_prompt` on older servers. Last 4 lines, 100 chars per.
        val ctxLines: List<String> =
            when {
                !session.promptContext.isNullOrBlank() ->
                    session.promptContext!!.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                !session.lastPrompt.isNullOrBlank() ->
                    listOf(session.lastPrompt!!.trim())
                else -> emptyList()
            }
        if (session.state == SessionState.Waiting && ctxLines.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .wrapContentHeight()
                            .padding(end = 8.dp),
                ) {
                    Surface(
                        color = colors.waiting,
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                }
                Column {
                    ctxLines.takeLast(4).forEach { line ->
                        Text(
                            if (line.length > 100) line.take(100) + "…" else line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Waiting-input long summary — expandable panel mirrors PWA v8.9.7 ▼/✕ behaviour.
        // Shows AI narrative for the current waiting prompt when lastSummaryLong is available.
        if (session.state == SessionState.Waiting && !session.lastSummaryLong.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { summaryExpanded = !summaryExpanded },
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 4.dp,
                            vertical = 0.dp,
                        ),
                    modifier = Modifier.height(24.dp),
                ) {
                    Icon(
                        if (summaryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        if (summaryExpanded) "Less" else "Full summary",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (summaryExpanded) {
                Row(
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        session.lastSummaryLong!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { summaryExpanded = false },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Collapse", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Inline quick-actions — Stop for running, Restart+Delete for done.
        if (!selectionMode) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (session.state) {
                    SessionState.Running -> {
                        OutlinedButton(
                            onClick = { killConfirmOpen = true },
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_stop), color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                if (!currentStatusLoading) {
                                    currentStatusLoading = true
                                    currentStatusScope.launch {
                                        try {
                                            val dto = fetchCurrentStatus()
                                            if (dto != null) {
                                                currentStatusText = if (dto.noChange) noChangeStr else dto.currentStatus
                                                currentStatusLongText = dto.currentStatusLong.takeIf { it.isNotBlank() }
                                                currentStatusOpen = true
                                            }
                                        } finally {
                                            currentStatusLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = !currentStatusLoading,
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        ) {
                            if (currentStatusLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                )
                            } else {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.sessions_current_status_btn))
                        }
                        if (currentStatusOpen && currentStatusText != null) {
                            CurrentStatusSheet(
                                status = currentStatusText!!,
                                statusLong = currentStatusLongText,
                                onDismiss = {
                                    currentStatusOpen = false
                                    currentStatusLongText = null
                                },
                                onResummarize = onResummarize,
                            )
                        }
                    }
                    SessionState.Waiting -> {
                        OutlinedButton(
                            onClick = { killConfirmOpen = true },
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_stop), color = MaterialTheme.colorScheme.error)
                        }
                        // Quick commands — only visible on waiting_input
                        // rows (PWA shows the ▶ triangle only when a
                        // prompt is actually blocking).
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = { quickCmdsOpen = true },
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        ) {
                            Icon(
                                Icons.Filled.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.sessions_quick_commands))
                        }
                    }
                    SessionState.Completed,
                    SessionState.Killed,
                    SessionState.Error,
                    -> {
                        OutlinedButton(
                            onClick = { restartConfirmOpen = true },
                            modifier = Modifier.alpha(1.0f),
                            contentPadding =
                                androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp,
                                ),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_restart))
                        }
                        if (deleteSupported) {
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedButton(
                                onClick = { deleteConfirmOpen = true },
                                modifier = Modifier.alpha(1.0f),
                                contentPadding =
                                    androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 4.dp,
                                    ),
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (responseOpen) {
        LastResponseSheet(
            response = session.lastResponse.orEmpty(),
            onDismiss = { responseOpen = false },
        )
    }
    if (quickCmdsOpen) {
        QuickCommandsSheet(
            fetchSavedCommands = fetchSavedCommands,
            fetchSystemCommands = fetchSystemCommands,
            onSend = { text ->
                onQuickReply(text)
                quickCmdsOpen = false
            },
            onDismiss = { quickCmdsOpen = false },
            whisperConfigured = whisperConfigured,
        )
    }
    if (restartConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_restart_session_title),
            body = stringResource(R.string.dialog_restart_session_body),
            confirmLabel = stringResource(R.string.action_restart),
            onConfirm = {
                restartConfirmOpen = false
                onRestart()
            },
            onDismiss = { restartConfirmOpen = false },
            destructive = false,
        )
    }
    if (killConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_stop_session_title),
            body = stringResource(R.string.dialog_stop_session_body),
            confirmLabel = stringResource(R.string.action_stop),
            onConfirm = {
                killConfirmOpen = false
                onKill()
            },
            onDismiss = { killConfirmOpen = false },
            destructive = true,
        )
    }
    if (deleteConfirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_session_title),
            body = stringResource(R.string.dialog_delete_session_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleteConfirmOpen = false
                onDelete()
            },
            onDismiss = { deleteConfirmOpen = false },
            destructive = true,
        )
    }
}

@Composable
private fun RenameSessionDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_rename_session)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text(confirmLabel) }
            } else {
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SessionsHeaderTitle(
    active: ServerProfile?,
    allMode: Boolean,
) {
    // Parity D1a/D9a + D2a: brand title "datawatch" (PWA `nav_home`) with the
    // active server as a muted sub-line (the PWA's server-indicator chip).
    // Switching lives in the ServerPickerBar under the header.
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.nav_home))
        Text(
            if (allMode) {
                stringResource(R.string.sessions_all_servers)
            } else {
                (active?.displayName ?: stringResource(R.string.sessions_no_server))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 8 dp status dot next to the server-picker title. Reflects the current
 * active profile's [com.dmzs.datawatchclient.transport.TransportClient.isReachable]:
 *   - green:  reachable (last probe succeeded)
 *   - grey:   reachability still unknown (no probe completed yet after start
 *             or profile switch, per ADR-0013's "probing, not failed" state)
 *   - red:    reachable flipped to false (last probe failed)
 *
 * Tap opens a bottom sheet with the last-probe timestamp plus a retry button.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReachabilityDot(
    reachable: Boolean?,
    lastProbeEpochMs: Long?,
    onRetry: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val reconnectMsg = stringResource(R.string.status_dot_reconnecting)
    val color =
        when (reachable) {
            true -> Color(0xFF10B981)
            false -> Color(0xFFEF4444)
            null -> Color(0xFFF59E0B)
        }
    val description =
        when (reachable) {
            true -> stringResource(R.string.sessions_server_online)
            false -> stringResource(R.string.sessions_server_unreachable)
            null -> stringResource(R.string.sessions_probing)
        }
    // v0.36.2 — pulse the dot when actively probing (reachable == null)
    // so the user sees that work is happening rather than a static
    // amber. Steady green / red doesn't pulse — those are settled
    // states.
    val scale by com.dmzs.datawatchclient.ui.theme.rememberDwPulse(
        initial = 1f,
        target = 1.4f,
        durationMs = 900,
        staticValue = 1f,
        active = reachable == null,
        label = "probe-pulse",
    )
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(24.dp)
                // Parity D38a: tap opens the status sheet; long-press
                // force-refreshes the connection (PWA forceRefreshConnection).
                .combinedClickable(
                    onClick = { sheetOpen = true },
                    onLongClick = { com.dmzs.datawatchclient.ui.common.forceRefreshConnection(reconnectMsg, onRetry) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = color,
            modifier =
                Modifier
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            shape = CircleShape,
        ) {}
    }

    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(description, style = MaterialTheme.typography.titleMedium)
                val relLabel = lastProbeEpochMs?.let { relativeTimeLabel(it) } ?: stringResource(R.string.sessions_never)
                Text(
                    "Last successful probe: $relLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        onRetry()
                        sheetOpen = false
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(stringResource(R.string.sessions_retry_now)) }
            }
        }
    }
}

private fun relativeTimeLabel(epochMs: Long): String {
    val deltaMs = System.currentTimeMillis() - epochMs
    val seconds = deltaMs / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}

/**
 * Last-response viewer. PWA renders the most-recent LLM response
 * in a modal overlay when the user taps the 📄 icon on a session
 * row. Mobile mirrors the behaviour with a ModalBottomSheet —
 * scrollable for long responses, monospace to preserve code
 * indentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LastResponseSheet(
    response: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // v0.36.1 (issue #15) — apply the same noise filter the PWA's
    // 💾 Response viewer landed in v5.26.31 so spinners, status
    // timers, footer hints, and box-drawing borders don't bury the
    // actual LLM prose.
    val cleaned =
        com.dmzs.datawatchclient.util.ResponseNoiseFilter.strip(response)
            .ifBlank { response }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }
    val tts =
        remember {
            var instance: android.speech.tts.TextToSpeech? = null
            instance =
                android.speech.tts.TextToSpeech(context) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        instance?.language = java.util.Locale.getDefault()
                    }
                }
            instance
        }
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    ModalBottomSheet(onDismissRequest = {
        tts?.stop()
        onDismiss()
    }, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sessions_last_response_sheet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (isSpeaking) {
                        tts?.stop()
                        isSpeaking = false
                    } else {
                        isSpeaking = true
                        tts?.speak(cleaned, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "lr")
                    }
                }) {
                    Icon(
                        if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop" else "Play",
                    )
                }
            }
            Text(
                cleaned,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrentStatusSheet(
    status: String,
    statusLong: String? = null,
    onDismiss: () -> Unit,
    onResummarize: suspend () -> CurrentStatusDto? = { null },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isResummarizing by remember { mutableStateOf(false) }
    var displayStatus by remember { mutableStateOf(status) }
    var displayLong by remember { mutableStateOf(statusLong) }
    val scope = rememberCoroutineScope()
    val tts =
        remember {
            var instance: android.speech.tts.TextToSpeech? = null
            instance =
                android.speech.tts.TextToSpeech(context) { s ->
                    if (s == android.speech.tts.TextToSpeech.SUCCESS) instance?.language = java.util.Locale.getDefault()
                }
            instance
        }
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    ModalBottomSheet(onDismissRequest = {
        tts?.stop()
        onDismiss()
    }, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sessions_current_status_sheet),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isResummarizing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        scope.launch {
                            isResummarizing = true
                            val dto = onResummarize()
                            if (dto != null) {
                                displayStatus = dto.currentStatus
                                displayLong = dto.currentStatusLong.takeIf { it.isNotBlank() }
                            }
                            isResummarizing = false
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Re-summarize")
                    }
                }
                IconButton(onClick = {
                    val text = if (expanded && displayLong != null) displayLong!! else displayStatus
                    if (isSpeaking) {
                        tts?.stop()
                        isSpeaking = false
                    } else {
                        isSpeaking = true
                        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "cs")
                    }
                }) {
                    Icon(
                        if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop" else "Play",
                    )
                }
            }
            Text(displayStatus, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            if (displayLong != null) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (expanded) "▲ Less" else "▼ More detail")
                }
                if (expanded) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            displayLong!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { expanded = false },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Collapse", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * PWA-parity quick-commands sheet. Opens from the ▶ "Commands"
 * button on waiting_input rows. Mirrors
 * `showCardCmds(fullId)` / `cardHandleQuickCmd` / `cardSendCustom`
 * in `internal/server/web/app.js`.
 *
 * Three stacks:
 *   1. **System** — yes / no / continue / skip / /exit.
 *   2. **Saved** — server's saved-command library from
 *      `GET /api/commands`, lazy-loaded when the sheet opens.
 *   3. **Custom** — free-form text input with Send.
 *
 * ESC and Ctrl-b (tmux prefix) are deferred — they need the WS
 * `command` channel which the Sessions tab doesn't subscribe to
 * today; users needing those open the session detail. Tracked for
 * a later batch.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun QuickCommandsSheet(
    fetchSavedCommands: suspend () -> List<Pair<String, String>>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    fetchSystemCommands: suspend () -> List<com.dmzs.datawatchclient.transport.QuickCommandItem> = { emptyList() },
    sessionId: String? = null,
    whisperConfigured: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saved by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var systemCmds by remember {
        mutableStateOf<List<com.dmzs.datawatchclient.transport.QuickCommandItem>>(
            emptyList(),
        )
    }
    var customText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        saved = fetchSavedCommands()
        systemCmds = fetchSystemCommands()
    }
    // Hard-coded fallback list used when server doesn't expose quick_commands (pre-datawatch#28 daemons).
    val fallbackSystemCmds =
        listOf(
            com.dmzs.datawatchclient.transport.QuickCommandItem("approve", "yes"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("reject", "no"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("continue", "continue"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("skip", "skip"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("quit", "/exit"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("Enter", "\n"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("ESC", ""),
            com.dmzs.datawatchclient.transport.QuickCommandItem("Ctrl-b", ""),
            com.dmzs.datawatchclient.transport.QuickCommandItem("↑", "[A"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("↓", "[B"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("→", "[C"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("←", "[D"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("PgUp", "[5~"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("PgDn", "[6~"),
            com.dmzs.datawatchclient.transport.QuickCommandItem("Tab", "	"),
        )
    val effectiveSystemCmds = systemCmds.ifEmpty { fallbackSystemCmds }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(stringResource(R.string.sessions_quick_commands_sheet), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.sessions_cmd_system),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                effectiveSystemCmds.forEach { cmd ->
                    FilterChip(
                        selected = false,
                        onClick = { onSend(cmd.value) },
                        label = { Text(cmd.label) },
                    )
                }
            }
            if (saved.isNotEmpty()) {
                Text(
                    stringResource(R.string.sessions_cmd_saved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                Column {
                    saved.forEach { (name, cmd) ->
                        TextButton(
                            onClick = { onSend(cmd) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    cmd.take(80),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.sessions_cmd_custom),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = rememberCoroutineScope()
            var recorder by remember {
                mutableStateOf<com.dmzs.datawatchclient.voice.VoiceRecorder?>(null)
            }
            var transcribing by remember { mutableStateOf(false) }
            val recording = recorder != null
            val micLauncher =
                androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) {
                        val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                        runCatching { r.start() }
                            .onSuccess { recorder = r }
                            .onFailure { e ->
                                AlertDockChannel.post(
                                    "Recording failed: ${e.message ?: e::class.simpleName}",
                                    DockLevel.Error,
                                )
                            }
                    } else {
                        AlertDockChannel.post(
                            "Microphone permission denied — enable it in Settings.",
                            DockLevel.Error,
                        )
                    }
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = {
                        Text(
                            if (recording) {
                                stringResource(
                                    R.string.sessions_listening,
                                )
                            } else {
                                stringResource(R.string.sessions_reply_hint)
                            },
                        )
                    },
                    singleLine = true,
                    enabled = !recording,
                    modifier = Modifier.weight(1f),
                )
                if (whisperConfigured) {
                    IconButton(
                        onClick = {
                            if (recording) {
                                val r = recorder ?: return@IconButton
                                recorder = null
                                val captured = r.stop() ?: return@IconButton
                                transcribing = true
                                scope.launch {
                                    val activeId =
                                        com.dmzs.datawatchclient.di.ServiceLocator
                                            .activeServerStore.get()
                                    val profiles =
                                        com.dmzs.datawatchclient.di.ServiceLocator
                                            .profileRepository.observeAll().first()
                                    val profile =
                                        profiles.firstOrNull { it.id == activeId && it.enabled }
                                            ?: profiles.firstOrNull { it.enabled }
                                    if (profile != null) {
                                        com.dmzs.datawatchclient.di.ServiceLocator
                                            .transportFor(profile)
                                            .transcribeAudio(
                                                audio = captured.first,
                                                audioMime = captured.second,
                                                sessionId = sessionId,
                                                autoExec = false,
                                            ).fold(
                                                onSuccess = { result ->
                                                    customText =
                                                        (customText + " " + result.transcript)
                                                            .trim()
                                                },
                                                onFailure = { err ->
                                                    AlertDockChannel.post(
                                                        "Transcribe failed on ${profile.displayName}: " +
                                                            "${err.message ?: err::class.simpleName}",
                                                        DockLevel.Error,
                                                    )
                                                },
                                            )
                                    }
                                    transcribing = false
                                }
                            } else {
                                val granted =
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                                    runCatching { r.start() }
                                        .onSuccess { recorder = r }
                                        .onFailure { e ->
                                            AlertDockChannel.post(
                                                "Recording failed: ${e.message ?: e::class.simpleName}",
                                                DockLevel.Error,
                                            )
                                        }
                                } else {
                                    micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !transcribing,
                    ) {
                        if (transcribing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(4.dp),
                            )
                        } else {
                            Icon(
                                if (recording) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.Filled.Mic
                                },
                                contentDescription =
                                    if (recording) {
                                        stringResource(
                                            R.string.sessions_stop_recording,
                                        )
                                    } else {
                                        stringResource(R.string.sessions_voice_reply)
                                    },
                                tint =
                                    if (recording) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        val t = customText.trim()
                        if (t.isNotEmpty()) onSend(t)
                    },
                    enabled = customText.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

/**
 * Small uppercase meta-chip used for the per-row backend badge
 * (e.g. "claude-code"). Pill matches [PwaStatePill]'s metrics but uses
 * the neutral `accent2`-tinted bg so it reads as informational rather
 * than state-bearing.
 */
@Composable
private fun PwaMetaBadge(text: String) {
    val colors = LocalDatawatchColors.current
    Surface(
        color = colors.accent2.copy(alpha = 0.12f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent2,
        )
    }
}

/**
 * v0.42.6 — Container Workers provenance pill (PWA v5.26.58 parity).
 * Purple ⬡ glyph + worker id when the session was spawned by a worker
 * agent. The agentId is shown in full because PWA does too — workers
 * are user-named and short.
 */
@Composable
private fun WorkerPill(agentId: String) {
    val purple = Color(0xFFA855F7)
    Surface(
        color = purple.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    ) {
        Text(
            "⬡ $agentId",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = purple,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionState.labelColor(): Color =
    when (this) {
        SessionState.Running -> MaterialTheme.colorScheme.primary
        SessionState.Waiting -> MaterialTheme.colorScheme.tertiary
        SessionState.RateLimited -> MaterialTheme.colorScheme.secondary
        SessionState.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Killed -> MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Error -> MaterialTheme.colorScheme.error
        SessionState.New -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** PWA `.id` pill: mono, bg3, 1px border, 600 weight. */
@Composable
private fun SessionIdPill(id: String) {
    val dw = LocalDatawatchColors.current
    Box(
        modifier =
            Modifier
                .background(dw.bg3, RoundedCornerShape(4.dp))
                .border(1.dp, dw.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            id,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** PWA `formatElapsed` (BL383): `Hh Mm` / `Mm SSs` / `Ss`. */
internal fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec.toString().padStart(2, '0')}s"
        else -> "${sec}s"
    }
}

/** Outlined meta badge (PWA server / parent / zombie badges): 10 sp, 8 dp radius, tinted fill. */
@Composable
private fun OutlineBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier =
            modifier
                .border(1.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
