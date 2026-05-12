package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.transport.dto.LastEventDto
import com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto
import com.dmzs.datawatchclient.transport.dto.SprintStatusDto
import kotlinx.serialization.json.Json
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

@Composable
public fun SessionStatusPanel(
    sessionId: String,
    modifier: Modifier = Modifier,
    vm: SessionStatusViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionStatusViewModel(sessionId) } },
        key = "session-status-$sessionId",
    ),
) {
    val uiState by vm.state.collectAsState()

    DisposableEffect(sessionId) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (uiState.loading && uiState.board == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            return@Column
        }

        val board = uiState.board
        if (board == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    uiState.error ?: stringResource(R.string.status_no_focus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        HookHealthPill(hookHealth = board.hookHealth, onClick = vm::refreshStatus)
        Spacer(Modifier.height(4.dp))

        board.currentFocus?.let { FocusCard(it, board.lastEvent, board.idleSince) }
        board.sprint?.let { SprintCard(it) }
        board.tests?.let { TestsCard(it.passing, it.failing, it.total) }
        board.git?.let { GitCard(it.branch, it.uncommitted, it.ahead) }

        if (board.currentFocus == null && board.sprint == null && board.tests == null && board.git == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.status_no_focus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HookHealthPill(hookHealth: String, onClick: () -> Unit) {
    val dw = LocalDatawatchColors.current
    val uriHandler = LocalUriHandler.current
    val (color, label) = when (hookHealth) {
        "alive" -> dw.success to stringResource(R.string.status_hooks_alive)
        "stale" -> dw.warning to stringResource(R.string.status_hooks_stale)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.status_hooks_missing)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("●", style = MaterialTheme.typography.labelSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
        if (hookHealth != "alive") {
            TextButton(
                onClick = { uriHandler.openUri("https://docs.anthropic.com/en/docs/claude-code/hooks") },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(stringResource(R.string.status_hook_docs_link), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FocusCard(focus: String, lastEvent: LastEventDto?, idleSince: Long?) {
    val dw = LocalDatawatchColors.current
    StatusCard(title = stringResource(R.string.status_card_focus)) {
        Text(focus, style = MaterialTheme.typography.bodySmall)
        if (lastEvent != null) {
            val parts = listOfNotNull(lastEvent.event, lastEvent.tool).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                val nowMs = System.currentTimeMillis()
                val tsStr = lastEvent.ts?.let { ts -> timeAgo(nowMs - ts) }
                val subtitle = (parts + listOfNotNull(tsStr)).joinToString(" · ")
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (idleSince != null) {
            val idleMs = System.currentTimeMillis() - idleSince
            if (idleMs > 5 * 60 * 1_000L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.status_idle_since, timeAgo(idleMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = dw.warning,
                )
            }
        }
    }
}

private fun timeAgo(elapsedMs: Long): String = when {
    elapsedMs < 60_000L -> "${elapsedMs / 1_000}s ago"
    elapsedMs < 3_600_000L -> "${elapsedMs / 60_000}m ago"
    else -> "${elapsedMs / 3_600_000}h ago"
}

@Composable
private fun SprintCard(sprint: SprintStatusDto) {
    val prettyJson = remember(sprint) {
        Json { prettyPrint = true }.encodeToString(SprintStatusDto.serializer(), sprint)
    }
    StatusCard(title = stringResource(R.string.status_card_sprint)) {
        SelectionContainer {
            Text(
                prettyJson,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun TestsCard(passing: Int, failing: Int, total: Int) {
    val dw = LocalDatawatchColors.current
    StatusCard(title = stringResource(R.string.status_card_tests)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatChip(label = "✓", value = passing.toString(), color = dw.success)
            StatChip(
                label = "✗",
                value = failing.toString(),
                color = if (failing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatChip(label = "∑", value = total.toString(), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GitCard(branch: String, uncommitted: Int, ahead: Int) {
    StatusCard(title = stringResource(R.string.status_card_git)) {
        StatusRow("Branch", branch)
        if (uncommitted > 0) StatusRow("Uncommitted", uncommitted.toString())
        if (ahead > 0) StatusRow("Ahead", ahead.toString())
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle(title)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

/** Returns the coloured dot for the Status tab label based on board state. */
public fun statusTabBadge(board: SessionStatusBoardDto?): String = when (board?.state) {
    "running" -> "🟢"
    "waiting", "waiting_input" -> "🟠"
    else -> "⚪"
}
