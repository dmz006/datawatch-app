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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
            if (state.sessions.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(session, onClick = { onOpenSession(session.id) })
                        HorizontalDivider()
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

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
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
