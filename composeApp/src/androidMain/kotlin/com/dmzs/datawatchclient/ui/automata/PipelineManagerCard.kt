package com.dmzs.datawatchclient.ui.automata

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dmzs.datawatchclient.transport.dto.PipelineListItemDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun PipelineManagerCard() {
    var pipelines by remember { mutableStateOf<List<PipelineListItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        val activeId = ServiceLocator.activeServerStore.get() ?: run { loading = false; return }
        val sp = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == activeId && it.enabled } ?: run { loading = false; return }
        ServiceLocator.transportFor(sp).getPipelines()
            .onSuccess { pipelines = it }
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
            PwaSectionTitle(stringResource(R.string.pipeline_manager_title), modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }

        if (pipelines.isEmpty() && !loading) {
            Text(
                stringResource(R.string.pipeline_no_pipelines),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            pipelines.forEachIndexed { idx, p ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                PipelineRow(p, onCancel = {
                    scope.launch {
                        runCatching {
                            // Cancel: placeholder — see dmz006/datawatch issue for proper cancel endpoint
                        }
                        runCatching { load() }
                    }
                })
            }
        }
    }
}

@Composable
private fun PipelineRow(p: PipelineListItemDto, onCancel: () -> Unit) {
    val statusColor = when (p.state) {
        "running" -> Color(0xFF6366F1)
        "completed" -> Color(0xFF10B981)
        "failed" -> MaterialTheme.colorScheme.error
        "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tasksDone = p.tasks.count { it.state == "completed" }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp)) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = statusColor,
            ) {}
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                p.name.ifBlank { p.id.take(12) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                buildString {
                    append(p.state)
                    if (p.tasks.isNotEmpty()) append(" · $tasksDone/${p.tasks.size} tasks")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val canCancel = p.state == "running" || p.state == "pending"
        if (canCancel) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.pipeline_cancel), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
