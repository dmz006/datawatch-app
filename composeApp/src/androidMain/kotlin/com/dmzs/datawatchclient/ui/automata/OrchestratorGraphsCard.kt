package com.dmzs.datawatchclient.ui.automata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.OrchestratorGraphListItemDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun OrchestratorGraphsCard() {
    var graphs by remember { mutableStateOf<List<OrchestratorGraphListItemDto>>(emptyList()) }
    var titleInput by remember { mutableStateOf("") }
    var dirInput by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        val activeId = ServiceLocator.activeServerStore.get()
        val sp = ServiceLocator.profileRepository.observeAll()
            .first { list -> list.any { it.enabled } }
            .let { list ->
                if (activeId == null) list.firstOrNull { it.enabled }
                else list.firstOrNull { it.id == activeId && it.enabled }
                    ?: list.firstOrNull { it.enabled }
            } ?: run { loading = false; return }
        ServiceLocator.transportFor(sp).getOrchestratorGraphsList()
            .onSuccess { graphs = it.graphs }
        loading = false
    }

    LaunchedEffect(Unit) { runCatching { load() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle(stringResource(R.string.orchestrator_graphs_title), modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }

        // Create form
        OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it; titleError = false },
            label = { Text(stringResource(R.string.orchestrator_graph_title_hint)) },
            isError = titleError,
            supportingText = if (titleError) ({ Text(stringResource(R.string.orchestrator_title_required)) }) else null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
        )
        OutlinedTextField(
            value = dirInput,
            onValueChange = { dirInput = it },
            label = { Text(stringResource(R.string.orchestrator_graph_dir_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
        )
        Button(
            onClick = {
                if (titleInput.isBlank()) { titleError = true; return@Button }
                scope.launch {
                    runCatching {
                        val activeId = ServiceLocator.activeServerStore.get()
                        val sp = ServiceLocator.profileRepository.observeAll()
                            .first { list -> list.any { it.enabled } }
                            .let { list ->
                                if (activeId == null) list.firstOrNull { it.enabled }
                                else list.firstOrNull { it.id == activeId && it.enabled }
                                    ?: list.firstOrNull { it.enabled }
                            } ?: return@runCatching
                        ServiceLocator.transportFor(sp).createOrchestratorGraph(titleInput.trim(), dirInput.trim())
                            .onSuccess {
                                titleInput = ""
                                dirInput = ""
                                load()
                            }
                    }
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.orchestrator_create_graph)) }

        // Graphs list
        Spacer(Modifier.height(8.dp))
        if (graphs.isEmpty() && !loading) {
            Text(
                stringResource(R.string.orchestrator_no_graphs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            graphs.forEachIndexed { idx, g ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                OrchestratorGraphRow(
                    g = g,
                    onRun = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.firstOrNull { it.enabled }
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                            ?: list.firstOrNull { it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).runOrchestratorGraph(g.id)
                                    .onSuccess { load() }
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            runCatching {
                                val activeId = ServiceLocator.activeServerStore.get()
                                val sp = ServiceLocator.profileRepository.observeAll()
                                    .first { list -> list.any { it.enabled } }
                                    .let { list ->
                                        if (activeId == null) list.firstOrNull { it.enabled }
                                        else list.firstOrNull { it.id == activeId && it.enabled }
                                            ?: list.firstOrNull { it.enabled }
                                    } ?: return@runCatching
                                ServiceLocator.transportFor(sp).deleteOrchestratorGraph(g.id)
                                    .onSuccess { load() }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OrchestratorGraphRow(
    g: OrchestratorGraphListItemDto,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (g.status) {
        "running" -> Color(0xFF6366F1)
        "done" -> Color(0xFF10B981)
        "failed" -> MaterialTheme.colorScheme.error
        "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = statusColor,
        ) {}
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                g.title.ifBlank { g.id.take(12) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    append(g.status.ifBlank { "pending" })
                    if (g.prdIds.isNotEmpty()) append(" · ${g.prdIds.size} automata")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onRun, modifier = Modifier.size(32.dp)) {
                Text("▶", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
