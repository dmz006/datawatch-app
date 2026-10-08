package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    // PWA D61 `sessionWatchFilter`: show watched sessions only (not persisted).
    var watchFilter by remember { mutableStateOf(false) }
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
    // PWA `cs_filters_collapsed`: collapsed by default, choice persisted.
    val toolbarCtx = androidx.compose.ui.platform.LocalContext.current
    val toolbarPrefs =
        remember { android.preference.PreferenceManager.getDefaultSharedPreferences(toolbarCtx) }
    var toolbarExpanded by remember {
        mutableStateOf(!toolbarPrefs.getBoolean(PREF_FILTERS_COLLAPSED, true))
    }
    LaunchedEffect(toolbarExpanded) {
        toolbarPrefs.edit().putBoolean(PREF_FILTERS_COLLAPSED, !toolbarExpanded).apply()
    }
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
        // Tab screens sit inside the app shell Scaffold, which already
        // reserves the system bars + bottom nav; the default systemBars
        // insets here doubled the nav-bar gap above the bottom menu.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                        DocsLinkAction(com.dmzs.datawatchclient.docs.DocsLinks.forKey("view_sessions"))
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
                    // The old offset(y = 36.dp) compensated for the doubled
                    // nav-bar inset (contentWindowInsets fix above); without
                    // it the FAB now sits at the M3 default just above the menu.
                    modifier = Modifier.padding(end = 4.dp),
                    // PWA `.fab` fill = accent2 (D4a: M3 shape, PWA colour).
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
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
            // #236.2 — a proxied remote with nothing on screen yet shows its real
            // connection status instead of an endless skeleton (PWA v8.73.2).
            val fedStatus =
                com.dmzs.datawatchclient.ui.common.rememberFedConnStatus(
                    state.activeProfile?.id,
                    state.allServersMode,
                )
            // While a proxied remote shows its connection status, that pane says what's
            // wrong; the generic "Disconnected" banner would repeat it with raw URLs.
            state.banner?.takeIf { fedStatus == null }?.let {
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
                watchFilter = watchFilter,
                watchedCount = watchedIds.size,
                onToggleWatchFilter = { watchFilter = !watchFilter },
            )

            val visible =
                if (watchFilter) state.visibleSessions.filter { it.id in watchedIds } else state.visibleSessions
            if (visible.isEmpty()) {
                if (fedStatus != null) {
                    com.dmzs.datawatchclient.ui.common.FedConnStatusPane(fedStatus)
                } else if (state.refreshing) {
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
                // clipToBounds: the resting pull-to-refresh indicator sits just above the
                // list and otherwise drew a dark disc over the Server chip bar.
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .nestedScroll(pullState.nestedScrollConnection),
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
                                .alpha(0.045f), // PWA `.sessions-watermark` opacity .045
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
                                // PWA llmDisplay = llm_ref || backend_family.
                                backend = session.llmRef?.takeIf { it.isNotBlank() }
                                    ?: session.backend ?: state.backendByProfileId[session.serverProfileId],
                                reorderMode = state.reorderMode,
                                showHostname = state.allServersMode,
                                onMoveUp = { vm.moveUp(session.id) },
                                onMoveDown = { vm.moveDown(session.id) },
                                onQuickReply = { text -> vm.quickReply(session.id, text) },
                                fetchSavedCommands = { vm.fetchSavedCommands(session.id) },
                                fetchCurrentStatus = { vm.fetchCurrentStatus(session.id) },
                                fetchFreshResponse = { vm.fetchFreshResponse(session.id) },
                                summarizerEnabled = state.summarizerEnabled,
                                summarizing = session.id in state.summarizingIds,
                                onManualSummarize = { vm.manualSummarize(session.id) },
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
    // PWA D61: `👁 N` toggles the watched-only filter.
    watchFilter: Boolean = false,
    watchedCount: Int = 0,
    onToggleWatchFilter: () -> Unit = {},
) {
    // Toolbar is rendered only when expanded (user toggled search) OR
    // something filter-related is active (stale state we don't want
    // to hide). Collapsed state = nothing renders here; the search
    // icon lives on the TopAppBar above.
    val show =
        expanded || filterText.isNotEmpty() ||
            activeBackendFilter != null || showHistory || treeView || watchFilter
    if (!show) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        run {
            // BL-SL-3: PWA layout — text input + LLM button on same row; state chips always visible.
            var llmExpanded by remember { mutableStateOf(false) }
            var stateExpanded by remember { mutableStateOf(false) }
            val showLlmFilter = backendCounts.size > 1 || activeBackendFilter != null
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
                // PWA parity: the LLM button only renders when the session
                // pool spans more than one backend (app.js `backendTypes.length > 1`);
                // kept visible while a backend filter is active so it can be cleared.
                if (showLlmFilter) {
                    OutlinedButton(
                        onClick = { llmExpanded = !llmExpanded },
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 4.dp,
                            ),
                    ) {
                        Text(
                            stringResource(R.string.llm_filter_btn_tip, backendCounts.size),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Icon(
                            if (llmExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            // Parity D12a — PWA `State (N) ▸` button; chips for every real
            // state with a count > 0 (plus All and the selected one).
            val stateActive = stateChip != SessionsViewModel.UiState.STATE_CHIP_ALL
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(6.dp))
                // PWA D61 backend-filter-badge `👁 N` (active = accent).
                val watchDesc = stringResource(R.string.session_watch_filter_tip)
                OutlinedButton(
                    onClick = onToggleWatchFilter,
                    modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = watchDesc },
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (watchFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                ) {
                    Text(
                        "👁 $watchedCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (watchFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
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
            AnimatedVisibility(visible = llmExpanded && showLlmFilter) {
                val councilLabel = stringResource(R.string.council_session_filter)
                LazyRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    items(backendCounts) { (backend, count) ->
                        FilterChip(
                            selected = activeBackendFilter == backend,
                            onClick = { onToggleBackend(backend) },
                            label = {
                                Text(
                                    "${backendShortLabel(backend)} $count",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
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
    fetchCurrentStatus: suspend () -> CurrentStatusDto? = { null },
    fetchFreshResponse: (suspend () -> Result<String>)? = null,
    summarizerEnabled: Boolean = false,
    summarizing: Boolean = false,
    onManualSummarize: () -> Unit = {},
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
    // Parity D14a — current status renders inline in the card (PWA
    // `state.currentStatus[fullId]`), not in a bottom sheet.
    var currentStatusText by remember { mutableStateOf<String?>(null) }
    var currentStatusLongText by remember { mutableStateOf<String?>(null) }
    var currentStatusAtMs by remember { mutableStateOf<Long?>(null) }
    var currentStatusLongExpanded by remember { mutableStateOf(false) }
    var currentStatusLoading by remember { mutableStateOf(false) }
    var summaryExpanded by remember { mutableStateOf(false) }
    val currentStatusScope = rememberCoroutineScope()
    val noChangeStr = stringResource(R.string.no_change_since_last_refresh)
    val unavailableStr = stringResource(R.string.current_status_unavailable)
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var restartConfirmOpen by remember { mutableStateOf(false) }
    var killConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    val colors = LocalDatawatchColors.current
    val timeLabel = relativeTimeLabel(session.lastActivityAt.toEpochMilliseconds())
    // PWA `.session-card.state-complete` opacity .7, `.state-killed` .5,
    // `.state-failed` none. (The PWA's `.card-actions { opacity: 1 }` override
    // cannot lift a parent's CSS group opacity, so the whole card renders dimmed.)
    val doneAlpha = sessionCardAlpha(session.state)

    // One fetch path for the inline current status (button, 🤖 Summary on
    // running rows, and the ↻ refresh) — PWA fetchCurrentStatus.
    val refreshCurrentStatus: () -> Unit = {
        if (!currentStatusLoading) {
            currentStatusLoading = true
            currentStatusScope.launch {
                try {
                    val dto = fetchCurrentStatus()
                    currentStatusText =
                        when {
                            dto == null -> "($unavailableStr)"
                            dto.noChange -> "($noChangeStr)"
                            else -> dto.currentStatus
                        }
                    currentStatusLongText = dto?.currentStatusLong?.takeIf { it.isNotBlank() }
                    currentStatusAtMs = System.currentTimeMillis()
                    currentStatusLongExpanded = false
                } finally {
                    currentStatusLoading = false
                }
            }
        }
    }
    val isActive =
        session.state == SessionState.Running || session.state == SessionState.Waiting ||
            session.state == SessionState.RateLimited
    val isDone =
        session.state == SessionState.Completed || session.state == SessionState.Killed ||
            session.state == SessionState.Error

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .alpha(doneAlpha)
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
                // PWA `.session-card`: bg2, radius 12, 4px state edge, no outline.
                .pwaCard(bordered = false)
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
                // PWA `.session-card { padding: 12px 14px }` (+4dp for the edge).
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // ── Line 1 — PWA header row (phone width): task | STATE 👁 🔔 ⋮⋮ ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode && !isActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            // Parity D16a — PWA line 1: name, else task (80 chars), else "(no task)".
            val line1 =
                (session.name?.takeIf { it.isNotBlank() } ?: session.taskSummary?.takeIf { it.isNotBlank() })
                    ?.let { if (it.length > 80) it.take(80) + "…" else it }
                    ?: "(no task)"
            Text(
                line1,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (reorderMode) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.sessions_move_up))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.sessions_move_down))
                }
            }
            // PWA `|` divider between the action group and the state pill.
            Text(
                "|",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 8.dp),
            )
            PwaStatePill(session.state)
            if (!reorderMode && !selectionMode) {
                // PWA 👁 watch toggle (accent2 when on, .4 opacity when off).
                val watchDesc = stringResource(if (isWatched) R.string.session_watch_on else R.string.session_watch_off)
                Text(
                    "👁",
                    fontSize = 14.sp,
                    color = if (isWatched) colors.accent2 else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .alpha(if (isWatched) 1f else 0.4f)
                            .clickable(onClick = onWatchToggle)
                            .semantics { contentDescription = watchDesc },
                )
                // PWA 🔔/🔕 mute toggle (warning tint when muted). Swipe still mutes too.
                val muteDesc = stringResource(if (session.muted) R.string.sessions_unmute else R.string.sessions_mute)
                Text(
                    if (session.muted) "🔕" else "🔔",
                    fontSize = 14.sp,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .alpha(if (session.muted) 1f else 0.4f)
                            .clickable(onClick = onSwipeMute)
                            .semantics { contentDescription = muteDesc },
                )
            }
            SessionDragHandle(
                enabled = !selectionMode,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
        }

        // ── Line 2 — PWA `.card-actions` (wraps under the header on phones) ──
        if (!selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    PwaCardActionButton(
                        "■ " + stringResource(R.string.action_stop),
                        color = MaterialTheme.colorScheme.error,
                        onClick = { killConfirmOpen = true },
                    )
                    if (session.state == SessionState.Waiting) {
                        PwaCardActionButton(
                            "▶",
                            contentDescription = stringResource(R.string.sessions_quick_commands),
                            onClick = { quickCmdsOpen = true },
                        )
                    }
                } else if (isDone) {
                    PwaCardActionButton(
                        "↻ " + stringResource(R.string.action_restart),
                        onClick = { restartConfirmOpen = true },
                    )
                    if (deleteSupported) {
                        PwaCardActionButton(
                            "🗑",
                            color = MaterialTheme.colorScheme.error,
                            contentDescription = stringResource(R.string.action_delete),
                            onClick = { deleteConfirmOpen = true },
                        )
                    }
                }
                // Parity D43a — PWA `🤖 Summary` (only when session.summarizer is
                // enabled). Running sessions take the current-status path.
                if (summarizerEnabled) {
                    val busy = summarizing || (session.state == SessionState.Running && currentStatusLoading)
                    PwaCardActionButton(
                        if (busy) {
                            "⏳ " + stringResource(R.string.current_status_summarizing)
                        } else {
                            "🤖 " + stringResource(R.string.session_summary_btn)
                        },
                        fontSize = 10,
                        enabled = !busy,
                        onClick = {
                            if (session.state == SessionState.Running) refreshCurrentStatus() else onManualSummarize()
                        },
                    )
                }
                // BL303 — PWA `sess-maximize-btn` ☷: open in Dashboard expand mode.
                PwaCardActionButton(
                    "☷",
                    fontSize = 12,
                    contentDescription = stringResource(R.string.dash_expand_session),
                    onClick = onExpand,
                )
            }
        }

        // ── Line 3 — PWA meta row: id, badges … [📄 Response] elapsed ago ──
        Row(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SessionIdPill(session.id)
            val hostname = session.hostnamePrefix
            if (showHostname && !hostname.isNullOrBlank()) {
                Text(
                    hostname,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isCouncil = backend == "council-virtual" || session.fullId.startsWith("council-")
            if (isCouncil) OutlineBadge("🎭 " + stringResource(R.string.session_council_badge), Color(0xFFF59E0B))
            if (!backend.isNullOrBlank() && !isCouncil) OutlineBadge(backend, colors.accent2)
            // PWA server badge (federated row from a non-local server).
            val serverName = session.server
            if (!serverName.isNullOrBlank() && serverName != "local") OutlineBadge(serverName, colors.accent2)
            // v0.42.6 — Container Workers provenance chip (PWA `⬡ worker`).
            if (!session.agentId.isNullOrBlank()) WorkerPill(agentId = session.agentId!!)
            // PWA `↳ child of [host]` parent badge (BL347 lineage).
            val parentId = session.parentId
            if (!parentId.isNullOrBlank()) {
                OutlineBadge(
                    "↳ " + stringResource(R.string.session_child_of) + " [" + parentId.substringBefore('-') + "]",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.7f),
                )
            }
            // PWA `⚠ zombie` (claude_alive === false).
            if (session.claudeAlive == false) OutlineBadge("⚠ zombie", Color(0xFFF59E0B))
            Spacer(modifier = Modifier.weight(1f))
            if (!session.lastResponse.isNullOrBlank()) {
                PwaCardActionButton(
                    "📄 " + stringResource(R.string.sessions_response_btn),
                    fontSize = 10,
                    onClick = { responseOpen = true },
                )
            }
            // BL383 live elapsed clock on active cards (1 s tick, tabular, accent2).
            val createdMs = session.createdAt.toEpochMilliseconds()
            if (isActive && createdMs > 0L) {
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
                }
            }
            Text(
                timeLabel,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── PWA `.card-waiting-row` — amber-tinted prompt box ──
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
        if (session.state == SessionState.Waiting) {
            val warning = Color(0xFFF59E0B)
            // PWA `card-waiting-label`: last 4 context lines (≤100 chars), or "Input needed".
            val shownLines =
                ctxLines.takeLast(4).map { if (it.length > 100) it.take(100) + "…" else it }
                    .ifEmpty { listOf(stringResource(R.string.sessions_input_needed)) }
            Column(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(warning.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, warning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                shownLines.forEach { line ->
                    Text(
                        line,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // PWA waiting row: short summary (`last_response`, italic, ≤180
                // chars) with a ▼/▲ toggle for `last_summary_long` and a ✕ panel.
                // The PWA renders it in a <span>, so HTML collapses newlines and
                // runs of whitespace into one wrapped paragraph — mirror that.
                val shortSummary =
                    session.lastResponse?.takeIf { it.isNotBlank() }
                        ?.replace(Regex("\\s+"), " ")?.trim()
                val longSummary = session.lastSummaryLong?.takeIf { it.isNotBlank() }
                if (shortSummary != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (shortSummary.length > 180) shortSummary.take(180) + "…" else shortSummary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (longSummary != null) {
                            val toggleDesc =
                                stringResource(
                                    if (summaryExpanded) R.string.sessions_summary_collapse else R.string.sessions_summary_show,
                                )
                            Text(
                                if (summaryExpanded) "▲" else "▼",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 2.dp)
                                        .clickable { summaryExpanded = !summaryExpanded }
                                        .semantics { contentDescription = toggleDesc },
                            )
                        }
                        // PWA: `AI <age>` after the toggle (9px, text2 @ .7).
                        val summaryAt = session.summaryGeneratedAt
                        if (summaryAt != null) {
                            Text(
                                "AI " + relativeTimeLabel(summaryAt.toEpochMilliseconds()),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                    if (longSummary != null && summaryExpanded) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth()
                                    .background(colors.bg3, RoundedCornerShape(4.dp))
                                    .drawBehind {
                                        drawRect(
                                            color = colors.accent2,
                                            size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                                        )
                                    }
                                    .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                longSummary,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { summaryExpanded = false },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.sessions_summary_collapse),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── PWA running row: `▶ What's it doing?` or the inline current status ──
        // Same `.card-waiting-row` amber box as the waiting prompt (PWA wraps both).
        if (!selectionMode && session.state == SessionState.Running) {
            val warning = Color(0xFFF59E0B)
            Column(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(warning.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, warning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (currentStatusLoading || currentStatusText != null) {
                    InlineCurrentStatus(
                        loading = currentStatusLoading,
                        text = currentStatusText.orEmpty(),
                        longText = currentStatusLongText,
                        generatedAtMs = currentStatusAtMs,
                        longExpanded = currentStatusLongExpanded,
                        onToggleLong = { currentStatusLongExpanded = !currentStatusLongExpanded },
                        onRefresh = refreshCurrentStatus,
                    )
                } else {
                    PwaCardActionButton(
                        "▶ " + stringResource(R.string.sessions_current_status_btn),
                        fontSize = 10,
                        onClick = refreshCurrentStatus,
                    )
                }
            }
        }
    }

    if (responseOpen) {
        LastResponseSheet(
            response = session.lastResponse.orEmpty(),
            onDismiss = { responseOpen = false },
            fetchFresh = fetchFreshResponse,
            title = session.name ?: session.id,
        )
    }
    if (quickCmdsOpen) {
        QuickCommandsSheet(
            fetchSavedCommands = fetchSavedCommands,
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
 * PWA `.drag-handle` (style.css:2176): always-visible `⋮⋮`, text2 colour at
 * opacity .4, 14 sp, last item of the card's title row. Dragging it reorders
 * immediately — no long-press and no separate reorder mode (operator
 * 2026-10-05); the whole-card long-press drag still works too.
 */
@Composable
private fun SessionDragHandle(
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val desc = stringResource(R.string.session_drag_handle)
    Text(
        "⋮⋮",
        fontSize = 14.sp,
        letterSpacing = (-1).sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier =
            Modifier
                .padding(start = 4.dp)
                .alpha(0.4f)
                .widthIn(min = 24.dp)
                .semantics { contentDescription = desc }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { _: androidx.compose.ui.geometry.Offset -> onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, delta ->
                            change.consume()
                            onDrag(delta.y)
                        },
                    )
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * 8 dp status dot next to the server-picker title. Reflects the current
 * active profile's [com.dmzs.datawatchclient.transport.TransportClient.isReachable]:
 *   - green:  reachable (last probe succeeded)
 *   - red:    not connected — last probe failed or none completed yet
 *             (PWA .status-dot has no separate probing state)
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
    // PWA .status-dot: red until connected, green when connected — no
    // separate probing colour (operator 2026-10-05). 300 ms colour
    // transition mirrors `transition: background 0.3s ease`.
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (reachable == true) Color(0xFF10B981) else Color(0xFFEF4444),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "status-dot",
    )
    val description =
        when (reachable) {
            true -> stringResource(R.string.sessions_server_online)
            false -> stringResource(R.string.sessions_server_unreachable)
            null -> stringResource(R.string.sessions_probing)
        }
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
                Modifier.size(12.dp),
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
    fetchFresh: (suspend () -> Result<String>)? = null,
    title: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Parity D43a — PWA `showResponseViewer`: paint the cached copy, then
    // always re-fetch GET /api/sessions/response and replace it; "(updating…)"
    // marks the cached copy while the fetch is in flight.
    var live by remember { mutableStateOf(response) }
    var updating by remember { mutableStateOf(fetchFresh != null) }
    val noResponse = stringResource(R.string.response_viewer_none)
    val loadFailed = stringResource(R.string.response_viewer_failed)
    LaunchedEffect(fetchFresh) {
        val fetch = fetchFresh ?: return@LaunchedEffect
        fetch()
            .onSuccess { live = it.ifBlank { noResponse } }
            .onFailure { if (response.isBlank()) live = loadFailed }
        updating = false
    }
    // v0.36.1 (issue #15) — apply the same noise filter the PWA's
    // 💾 Response viewer landed in v5.26.31 so spinners, status
    // timers, footer hints, and box-drawing borders don't bury the
    // actual LLM prose.
    val cleaned =
        com.dmzs.datawatchclient.util.ResponseNoiseFilter.strip(live)
            .ifBlank { live }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.response_viewer_copied)
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
                    stringResource(R.string.sessions_last_response_sheet) + (title?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (updating) {
                    Text(
                        stringResource(R.string.response_viewer_updating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(cleaned))
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(copiedMsg)
                }) { Text("📋") }
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
            com.dmzs.datawatchclient.ui.autonomous.MarkdownView(
                text = cleaned,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * Parity D14a — PWA inline current-status row (app.js:2476-2497): "Summarizing…"
 * while loading, then the short status with ▼/▲ for the long form and a
 * `↻ <age>` refresh button. The long form opens in a bg3 box with an accent2
 * left rule and a ✕ collapse.
 */
@Composable
private fun InlineCurrentStatus(
    loading: Boolean,
    text: String,
    longText: String?,
    generatedAtMs: Long?,
    longExpanded: Boolean,
    onToggleLong: () -> Unit,
    onRefresh: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (loading) {
            Text(stringResource(R.string.current_status_summarizing), style = small, color = dim)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = small, color = dim, modifier = Modifier.weight(1f, fill = false))
            if (longText != null) {
                Text(
                    if (longExpanded) "▲" else "▼",
                    style = small,
                    color = dim,
                    modifier = Modifier.clickable(onClick = onToggleLong).padding(horizontal = 4.dp),
                )
            }
            Text(
                "↻ " + (generatedAtMs?.let { com.dmzs.datawatchclient.ui.common.relativeTimeLabel(it) } ?: ""),
                style = small,
                color = dim,
                modifier = Modifier.clickable(onClick = onRefresh).padding(start = 4.dp),
            )
        }
        if (longText != null && longExpanded) {
            val dw = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current
            Box(
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .background(dw.bg3, RoundedCornerShape(4.dp))
                        .drawBehind {
                            drawRect(color = dw.accent2, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                        }
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 22.dp),
            ) {
                Text(longText, style = small, color = dim)
                Text(
                    "✕",
                    style = small,
                    color = dim,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp).clickable(onClick = onToggleLong),
                )
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
 *   1. **System** — the web UI's card set ([QuickCommandSets.CARD_SYSTEM]):
 *      approve / reject / continue / skip / ESC / tmux prefix (Ctrl-b) / quit.
 *      ESC and Ctrl-b go out as tmux keys over the sessions socket.
 *   2. **Saved** — every saved command from `GET /api/commands`.
 *   3. **Custom** — free-form text input with Send.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun QuickCommandsSheet(
    fetchSavedCommands: suspend () -> List<Pair<String, String>>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    sessionId: String? = null,
    whisperConfigured: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saved by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var customText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        saved = fetchSavedCommands()
    }
    // Web UI session-card "Commands…" System set, verbatim (shared with iOS).
    val effectiveSystemCmds = com.dmzs.datawatchclient.transport.QuickCommandSets.CARD_SYSTEM
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
                                            .profilesWithProxied().first()
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
/**
 * PWA card action button (`btnStyle` in renderSessionCard): 1px --border,
 * bg2 fill, 4px radius, 11px text, 3px×8px padding. [color] tints border +
 * text (Stop/Delete use --error).
 */
@Composable
private fun PwaCardActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color? = null,
    fontSize: Int = 11,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val dw = LocalDatawatchColors.current
    val fg = color ?: MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(4.dp))
                .background(dw.bg2)
                .border(1.dp, color ?: dw.border, RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = fontSize.sp, lineHeight = (fontSize + 2).sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun PwaMetaBadge(text: String) {
    val colors = LocalDatawatchColors.current
    Surface(
        // PWA .backend-badge on the card: accent2 .12 fill + 1px accent2 border,
        // radius 8, monospace 10px/600, label shown as-is (not uppercased).
        color = colors.accent2.copy(alpha = 0.12f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accent2),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
            color = colors.accent2,
        )
    }
}

/**
 * v0.42.6 — Container Workers provenance pill (PWA v5.26.58 parity).
 * PWA `agent-badge` (app.js renderSessionCard): fixed "⬡ worker" label,
 * 1px purple border, .15 purple tint; the agent id is only in the
 * tooltip, so here it's the accessibility description.
 */
@Composable
private fun WorkerPill(agentId: String) {
    val purple = Color(0xFFA855F7)
    val desc = stringResource(R.string.sessions_worker_badge_desc, agentId)
    Surface(
        color = purple.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, purple),
        modifier = Modifier.semantics { contentDescription = desc },
    ) {
        Text(
            "⬡ " + stringResource(R.string.sessions_worker_badge),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = purple,
            maxLines = 1,
        )
    }
}

/**
 * PWA `backendShort` map (app.js renderSessionsView) — compact labels for
 * the LLM filter badges; unknown backends fall through unchanged.
 */
private fun backendShortLabel(backend: String): String =
    when (backend) {
        "claude-code" -> "claude"
        "opencode" -> "oc"
        "opencode-acp" -> "acp"
        "opencode-prompt" -> "oc-p"
        "openwebui" -> "owui"
        "ollama" -> "olla"
        "gemini" -> "gem"
        "shell" -> "sh"
        else -> backend
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

/** PWA done-card dimming: complete .7, killed .5, everything else (incl. failed) 1.0. */
internal fun sessionCardAlpha(state: SessionState): Float =
    when (state) {
        SessionState.Completed -> 0.7f
        SessionState.Killed -> 0.5f
        else -> 1.0f
    }

/** PWA localStorage key `cs_filters_collapsed` (true = toolbar hidden). */
private const val PREF_FILTERS_COLLAPSED: String = "cs_filters_collapsed"
