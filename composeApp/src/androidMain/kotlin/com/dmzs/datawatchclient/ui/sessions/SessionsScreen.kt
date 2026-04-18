package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionsScreen(vm: SessionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.activeProfile?.displayName ?: "No server"
                    Text(name)
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
                        SessionRow(session)
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
private fun SessionRow(session: Session) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
private fun SessionState.labelColor(): Color = when (this) {
    SessionState.Running -> MaterialTheme.colorScheme.primary
    SessionState.Waiting -> MaterialTheme.colorScheme.tertiary
    SessionState.RateLimited -> MaterialTheme.colorScheme.secondary
    SessionState.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    SessionState.Killed -> MaterialTheme.colorScheme.onSurfaceVariant
    SessionState.Error -> MaterialTheme.colorScheme.error
    SessionState.New -> MaterialTheme.colorScheme.onSurfaceVariant
}
