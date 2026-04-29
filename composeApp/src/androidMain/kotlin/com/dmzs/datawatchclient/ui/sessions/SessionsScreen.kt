package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaStatePill
import com.dmzs.datawatchclient.ui.theme.pwaCard
import com.dmzs.datawatchclient.ui.theme.pwaStateEdge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
    vm: SessionsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var bulkDeleteConfirmOpen by remember { mutableStateOf(false) }
    // Search / filter / sort toolbar is collapsed by default — user
    // 2026-04-24 (dmz006/datawatch#23). The top-app-bar search icon
    // toggles this. Stays implicitly "expanded" when filter text or
    // history are active so typed queries / visible state aren't hidden.
    var toolbarExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionTopAppBar(
                    count = selectedIds.size,
                    canDelete = state.deleteSupported,
                    onCancel = { selectedIds = emptySet() },
                    onDelete = { bulkDeleteConfirmOpen = true },
                )
            } else {
                TopAppBar(
                    title = {
                        ServerPickerTitle(
                            active = state.activeProfile,
                            allMode = state.allServersMode,
                            open = pickerOpen,
                            onToggle = { pickerOpen = !pickerOpen },
                            onDismiss = { pickerOpen = false },
                            profiles = state.allProfiles,
                            onSelectAll = {
                                vm.selectAllServers()
                                pickerOpen = false
                            },
                            onSelect = {
                                vm.selectProfile(it)
                                pickerOpen = false
                            },
                            onEdit = {
                                pickerOpen = false
                                onEditServer(it)
                            },
                            onAdd = {
                                pickerOpen = false
                                onAddServer()
                            },
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
                        // User direction 2026-04-24 + dmz006/datawatch#23
                        // — search icon lives on the top app bar, left
                        // of the reachability dot. Tapping toggles the
                        // filter toolbar underneath.
                        IconButton(onClick = { toolbarExpanded = !toolbarExpanded }) {
                            Icon(
                                if (toolbarExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription =
                                    if (toolbarExpanded) "Collapse filter" else "Filter / sort sessions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    Icon(Icons.Filled.Add, contentDescription = "New session")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
            if (!selectionMode) {
                SessionsToolbar(
                    filterText = state.filterText,
                    onFilterTextChange = vm::setFilterText,
                    backendCounts = state.backendCounts,
                    activeBackendFilter = state.backendFilter,
                    onToggleBackend = vm::toggleBackendFilter,
                    showHistory = state.showHistory,
                    historyCount = state.historyCount,
                    onToggleShowHistory = vm::toggleShowHistory,
                    sortOrder = state.sortOrder,
                    onSortOrderChange = vm::setSortOrder,
                    expanded = toolbarExpanded,
                    onCollapse = { toolbarExpanded = false },
                )
            }

            val visible = state.visibleSessions
            if (visible.isEmpty()) {
                EmptyState()
            } else {
                // v0.33.15 (B9): datawatch eye watermark behind the
                // sessions list. PWA centers the brand icon at ~85%
                // page width as a faint background. Uses the shared
                // launcher-foreground vector which already draws the
                // eye + matrix + arcs; clipped to the list bounds
                // and painted at 10% alpha so it doesn't compete with
                // the row content.
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
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
                        items(visible, key = { "${it.serverProfileId}:${it.id}" }) { session ->
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
                            SessionRow(
                                session = session,
                                backend = session.backend ?: state.backendByProfileId[session.serverProfileId],
                                reorderMode = state.reorderMode,
                                onMoveUp = { vm.moveUp(session.id) },
                                onMoveDown = { vm.moveDown(session.id) },
                                onQuickReply = { text -> vm.quickReply(session.id, text) },
                                fetchSavedCommands = { vm.fetchSavedCommands(session.id) },
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
                                    if (selectionMode) {
                                        selectedIds = selectedIds.toggle(session.id)
                                    } else {
                                        onOpenSession(session.id)
                                    }
                                },
                                onLongPress = {
                                    // In multi-select, long-press selects the
                                    // row. Outside multi-select the gesture is
                                    // reclaimed by the drag detector in
                                    // SessionRow itself.
                                    selectedIds = selectedIds + session.id
                                },
                                onSwipeMute = {
                                    if (!selectionMode) vm.toggleMute(session.id, session.muted)
                                },
                                onRename = { newName -> vm.rename(session.id, newName) },
                                onRestart = { vm.restart(session.id) },
                                onKill = { vm.kill(session.id) },
                                onDelete = { vm.delete(session.id) },
                            )
                        }
                    } // LazyColumn close
                } // watermark Box close
            }
        }
    }

    if (bulkDeleteConfirmOpen) {
        val ids = selectedIds.toList()
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirmOpen = false },
            title = { Text("Delete ${ids.size} sessions?") },
            text = {
                Text(
                    "These sessions will be removed from the server. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteMany(ids)
                        selectedIds = emptySet()
                        bulkDeleteConfirmOpen = false
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    count: Int,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete selected",
                    tint =
                        if (canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    )
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
    sortOrder: SessionsViewModel.SortOrder,
    onSortOrderChange: (SessionsViewModel.SortOrder) -> Unit,
    expanded: Boolean,
    onCollapse: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    // Toolbar is rendered only when expanded (user toggled search) OR
    // something filter-related is active (stale state we don't want
    // to hide). Collapsed state = nothing renders here; the search
    // icon lives on the TopAppBar above.
    val show =
        expanded || filterText.isNotEmpty() ||
            activeBackendFilter != null || showHistory
    if (!show) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        run {
            OutlinedTextField(
                value = filterText,
                onValueChange = onFilterTextChange,
                placeholder = { Text("Filter sessions…") },
                singleLine = true,
                trailingIcon = {
                    Row {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { onFilterTextChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                            }
                        }
                        IconButton(onClick = {
                            onFilterTextChange("")
                            onCollapse()
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Collapse toolbar",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (backendCounts.size > 1) {
                LazyRow(
                    modifier = Modifier.padding(top = 6.dp),
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
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
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
                            "History ($historyCount)",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { sortMenuOpen = true },
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp,
                            ),
                    ) {
                        Text("Sort: ${sortOrder.label}", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        SessionsViewModel.SortOrder.entries.forEach { o ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(o.label, modifier = Modifier.weight(1f))
                                        if (o == sortOrder) {
                                            Icon(
                                                Icons.Filled.Check,
                                                "selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSortOrderChange(o)
                                    sortMenuOpen = false
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
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "No sessions yet. Use `new: <task>` from a messaging backend " +
                "to start one, or wait for the daemon to push one here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: Session,
    backend: String?,
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
    fetchSavedCommands: suspend () -> List<Pair<String, String>> = { emptyList() },
    reorderMode: Boolean = false,
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
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var restartConfirmOpen by remember { mutableStateOf(false) }
    var killConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    val colors = LocalDatawatchColors.current
    val timeLabel = relativeTimeLabel(session.lastActivityAt.toEpochMilliseconds())

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
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
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
    ) {
        // Header row: id + state pill + mute/more actions.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                session.id,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 8.dp),
            )
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
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                if (session.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                contentDescription = if (session.muted) "Muted" else "Unmuted",
                tint =
                    if (session.muted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
            if (reorderMode) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
            } else if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                renameOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Restart") },
                            onClick = {
                                menuOpen = false
                                restartConfirmOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete",
                                    color =
                                        if (deleteSupported && session.state != SessionState.Running) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                            enabled = deleteSupported && session.state != SessionState.Running,
                            onClick = {
                                menuOpen = false
                                deleteConfirmOpen = true
                            },
                        )
                    }
                }
            }
        }

        // Task / display text — row body. PWA prefers `name` (user-
        // assigned label from rename) over `task` (original prompt); we
        // do the same. Truncate matches the PWA's 80-char cap.
        val displayText = session.name?.takeIf { it.isNotBlank() } ?: session.taskSummary
        val taskText =
            when {
                displayText == null -> "(no task)"
                displayText.length > 80 -> displayText.take(80) + "…"
                else -> displayText
            }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                taskText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // "View last response" icon — only when the server has
            // captured a response for this session. Mirrors the PWA's
            // 📄 icon next to the task body.
            if (!session.lastResponse.isNullOrBlank()) {
                IconButton(
                    onClick = { responseOpen = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = "View last response",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Meta row: hostname · time ago. Small, muted, PWA-style.
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hostname = session.hostnamePrefix
            if (!hostname.isNullOrBlank()) {
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
                modifier = Modifier.padding(top = 8.dp),
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

        // Inline quick-actions — Stop for running, Restart for terminal.
        if (!selectionMode) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (session.state) {
                    SessionState.Running, SessionState.Waiting -> {
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
                            Text("Stop", color = MaterialTheme.colorScheme.error)
                        }
                        // Quick commands — only visible on waiting_input
                        // rows (PWA shows the ▶ triangle only when a
                        // prompt is actually blocking).
                        if (session.state == SessionState.Waiting) {
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
                                Text("Commands")
                            }
                        }
                    }
                    SessionState.Completed,
                    SessionState.Killed,
                    SessionState.Error,
                    -> {
                        OutlinedButton(
                            onClick = { restartConfirmOpen = true },
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
                            Text("Restart")
                        }
                        if (deleteSupported) {
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedButton(
                                onClick = { deleteConfirmOpen = true },
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
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (renameOpen) {
        RenameSessionDialog(
            initial = session.name ?: session.taskSummary ?: session.id,
            onConfirm = { newName ->
                renameOpen = false
                onRename(newName)
            },
            onDismiss = { renameOpen = false },
        )
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
            onSend = { text ->
                onQuickReply(text)
                quickCmdsOpen = false
            },
            onDismiss = { quickCmdsOpen = false },
        )
    }
    if (restartConfirmOpen) {
        ConfirmDialog(
            title = "Restart session?",
            body =
                "Warm-resume this session on the server. Any in-progress " +
                    "prompt may be interrupted.",
            confirmLabel = "Restart",
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
            title = "Stop session?",
            body =
                "Signal the server to terminate this running session. " +
                    "Unsaved in-flight work may be lost.",
            confirmLabel = "Stop",
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
            title = "Delete session?",
            body =
                "The session history will be removed from the server. " +
                    "This cannot be undone.",
            confirmLabel = "Delete",
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
        title = { Text("Rename session") },
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
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ServerPickerTitle(
    active: ServerProfile?,
    allMode: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    profiles: List<ServerProfile>,
    onSelectAll: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Box {
        // User 2026-04-24: "top header has space above host name" —
        // drop the title Row's vertical padding; the TopAppBar already
        // centre-aligns the title within its own fixed height, so any
        // extra vertical padding here just pushes the server-name down
        // from the visible centre line and makes the header feel like
        // it has a dead strip above the label.
        Row(
            modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (allMode) "All servers" else (active?.displayName ?: "No server"))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Switch server",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
            if (profiles.size > 1) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "All servers",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (allMode) {
                                Icon(
                                    Icons.Filled.Check,
                                    "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    onClick = onSelectAll,
                )
                HorizontalDivider()
            }
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No servers configured") },
                    onClick = onDismiss,
                    enabled = false,
                )
            } else {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(enabled = p.enabled)
                                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(p.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        p.baseUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (p.id == active?.id) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                TextButton(onClick = { onEdit(p.id) }) { Text("Edit") }
                            }
                        },
                        onClick = { onSelect(p.id) },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add server…") },
                onClick = onAdd,
            )
        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReachabilityDot(
    reachable: Boolean?,
    lastProbeEpochMs: Long?,
    onRetry: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val color =
        when (reachable) {
            true -> Color(0xFF22C55E)
            false -> Color(0xFFEF4444)
            null -> Color(0xFFF59E0B)
        }
    val description =
        when (reachable) {
            true -> "Server online"
            false -> "Server unreachable"
            null -> "Probing…"
        }
    // v0.36.2 — pulse the dot when actively probing (reachable == null)
    // so the user sees that work is happening rather than a static
    // amber. Steady green / red doesn't pulse — those are settled
    // states.
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "probe-pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (reachable == null) 1.4f else 1f,
        animationSpec =
            androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(900),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            ),
        label = "probe-pulse-scale",
    )
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .size(24.dp)
                .clickable(onClick = { sheetOpen = true }),
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
                val relLabel = lastProbeEpochMs?.let { relativeTimeLabel(it) } ?: "never"
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
                ) { Text("Retry now") }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text("Last response", style = MaterialTheme.typography.titleMedium)
            Text(
                cleaned,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp),
            )
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
    sessionId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saved by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var customText by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        saved = fetchSavedCommands()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Quick commands", style = MaterialTheme.typography.titleMedium)
            Text(
                "System",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                // PWA's cardSendCmd dispatches ESC / Ctrl-b as WS
                // `command` frames with `sendkey` payloads. Mobile
                // doesn't subscribe to a general-purpose WS command
                // channel from the list surface; the parent
                // /api/sessions/reply endpoint accepts raw control
                // bytes so we send the ASCII literal and the TUI
                // receives it the same way. ESC = 0x1B, Ctrl-b = 0x02.
                listOf(
                    "yes" to "approve",
                    "no" to "reject",
                    "continue" to "continue",
                    "skip" to "skip",
                    "/exit" to "quit",
                    "\u001B" to "ESC",
                    "\u0002" to "Ctrl-b",
                    // Arrow keys + PageUp/Down as ANSI escape sequences.
                    // Matches PWA f00f534 (v0.13.6 seed esc/up/down keys).
                    "\u001B[A" to "↑",
                    "\u001B[B" to "↓",
                    "\u001B[C" to "→",
                    "\u001B[D" to "←",
                    "\u001B[5~" to "PgUp",
                    "\u001B[6~" to "PgDn",
                    "\u0009" to "Tab",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = false,
                        onClick = { onSend(value) },
                        label = { Text(label) },
                    )
                }
            }
            if (saved.isNotEmpty()) {
                Text(
                    "Saved",
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
                "Custom",
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
                                android.widget.Toast.makeText(
                                    context,
                                    "Recording failed: ${e.message ?: e::class.simpleName}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Microphone permission denied — enable it in Settings.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = {
                        Text(if (recording) "Listening…" else "Type a reply…")
                    },
                    singleLine = true,
                    enabled = !recording,
                    modifier = Modifier.weight(1f),
                )
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
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Transcribe failed on ${profile.displayName}: " +
                                                        "${err.message ?: err::class.simpleName}",
                                                    android.widget.Toast.LENGTH_LONG,
                                                ).show()
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
                                        android.widget.Toast.makeText(
                                            context,
                                            "Recording failed: ${e.message ?: e::class.simpleName}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
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
                                if (recording) "Stop recording" else "Voice reply",
                            tint =
                                if (recording) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
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
        )
    }
}

@Composable
private fun StatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .padding(0.dp),
    ) {
        Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
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
