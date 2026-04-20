package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.ui.theme.PwaStatePill
import com.dmzs.datawatchclient.ui.theme.pwaCard
import com.dmzs.datawatchclient.ui.theme.pwaStateEdge
import kotlin.math.absoluteValue

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            // ADR-0013 — reachability is a visible state, never hidden.
                            // Shown for single-server mode only; all-servers mode
                            // deliberately hides it since we track many profiles.
                            if (!state.allServersMode && state.activeProfile != null) {
                                ReachabilityDot(
                                    reachable = state.activeReachable,
                                    lastProbeEpochMs = state.lastProbeEpochMs,
                                    onRetry = vm::refresh,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh) {
                            if (state.refreshing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && state.activeProfile != null) {
                FloatingActionButton(onClick = onNewSession) {
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
            if (!selectionMode) FilterChipRow(current = state.filter, onSelect = vm::setFilter)

            val visible = state.visibleSessions
            if (visible.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn {
                    // Key = profile:id to avoid LazyColumn duplicate-key crashes
                    // when the same session id appears under both a server's
                    // primary list and another server's federation fan-out.
                    items(visible, key = { "${it.serverProfileId}:${it.id}" }) { session ->
                        SessionRow(
                            session = session,
                            deleteSupported = state.deleteSupported,
                            selectionMode = selectionMode,
                            isSelected = session.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggle(session.id)
                                } else {
                                    onOpenSession(session.id)
                                }
                            },
                            onLongPress = {
                                // Enter multi-select on first long-press, then
                                // subsequent taps toggle. Long-press on an
                                // already-selected row is idempotent.
                                selectedIds = selectedIds + session.id
                            },
                            onSwipeMute = {
                                if (!selectionMode) vm.toggleMute(session.id, session.muted)
                            },
                            onRename = { newName -> vm.rename(session.id, newName) },
                            onRestart = { vm.restart(session.id) },
                            onDelete = { vm.delete(session.id) },
                        )
                    }
                }
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

@Composable
private fun FilterChipRow(
    current: SessionsViewModel.Filter,
    onSelect: (SessionsViewModel.Filter) -> Unit,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        items(SessionsViewModel.Filter.entries.toList()) { f ->
            FilterChip(
                selected = current == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = FilterChipDefaults.filterChipColors(),
            )
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
    deleteSupported: Boolean,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeMute: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onRestart: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var restartConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }

    val rowBg =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard()
                .pwaStateEdge(session.state)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                )
                .pointerInput(session.id, selectionMode) {
                    if (selectionMode) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = { if (dx.absoluteValue >= swipeThresholdPx) onSwipeMute() },
                        onDragCancel = { dx = 0f },
                    ) { _, delta -> dx += delta }
                }
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.id,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                PwaStatePill(session.state)
            }
            Text(
                session.taskSummary ?: "(no summary)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(
            if (session.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
            contentDescription = if (session.muted) "Muted" else "Unmuted",
            tint =
                if (session.muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
        )
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
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

    if (renameOpen) {
        RenameSessionDialog(
            initial = session.taskSummary ?: session.id,
            onConfirm = { newName ->
                renameOpen = false
                onRename(newName)
            },
            onDismiss = { renameOpen = false },
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
        Row(
            modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 4.dp),
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
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.outline
        }
    val description =
        when (reachable) {
            true -> "Reachable"
            false -> "Unreachable"
            null -> "Probing"
        }
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
            modifier = Modifier.size(8.dp),
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
                TextButton(
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
