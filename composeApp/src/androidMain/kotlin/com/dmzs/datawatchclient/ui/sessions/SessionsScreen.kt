package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.absoluteValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsScreen(
    onOpenSession: (String) -> Unit = {},
    onEditServer: (String) -> Unit = {},
    onAddServer: () -> Unit = {},
    vm: SessionsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ServerPickerTitle(
                        active = state.activeProfile,
                        open = pickerOpen,
                        onToggle = { pickerOpen = !pickerOpen },
                        onDismiss = { pickerOpen = false },
                        profiles = state.allProfiles,
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
                    IconButton(onClick = vm::refresh) {
                        if (state.refreshing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
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
            FilterChipRow(current = state.filter, onSelect = vm::setFilter)

            val visible = state.visibleSessions
            if (visible.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn {
                    items(visible, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            onClick = { onOpenSession(session.id) },
                            onSwipeMute = { vm.toggleMute(session.id, session.muted) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    current: SessionsViewModel.Filter,
    onSelect: (SessionsViewModel.Filter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
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

@Composable
private fun SessionRow(
    session: Session,
    onClick: () -> Unit = {},
    onSwipeMute: () -> Unit = {},
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .pointerInput(session.id) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx.absoluteValue >= swipeThresholdPx) onSwipeMute() },
                    onDragCancel = { dx = 0f },
                ) { _, delta -> dx += delta }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.id, style = MaterialTheme.typography.titleSmall)
            Text(
                session.taskSummary ?: "(no summary)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            AssistChip(
                onClick = {},
                label = { Text(session.state.name) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = session.state.labelColor(),
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Icon(
            if (session.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
            contentDescription = if (session.muted) "Muted" else "Unmuted",
            tint = if (session.muted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(start = 8.dp).size(20.dp),
        )
    }
}

@Composable
private fun ServerPickerTitle(
    active: ServerProfile?,
    open: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    profiles: List<ServerProfile>,
    onSelect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Box {
        Row(
            modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(active?.displayName ?: "No server")
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Switch server",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
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

@Composable
private fun StatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .padding(0.dp),
    ) {
        Surface(color = color, modifier = Modifier.size(8.dp), shape = CircleShape) {}
    }
}

@Composable
private fun SessionState.labelColor(): Color = when (this) {
    SessionState.Running -> MaterialTheme.colorScheme.primary
    SessionState.Waiting -> MaterialTheme.colorScheme.tertiary
    SessionState.RateLimited -> MaterialTheme.colorScheme.secondary
    SessionState.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    SessionState.Killed -> MaterialTheme.colorScheme.onSurfaceVariant
    SessionState.Error -> MaterialTheme.colorScheme.error
    SessionState.New -> MaterialTheme.colorScheme.onSurfaceVariant
}
