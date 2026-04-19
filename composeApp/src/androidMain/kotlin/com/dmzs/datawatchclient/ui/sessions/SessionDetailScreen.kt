package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    vm: SessionDetailViewModel = viewModel(
        key = sessionId,
        factory = viewModelFactory {
            initializer { SessionDetailViewModel(sessionId) }
        },
    ),
) {
    val state by vm.state.collectAsState()
    var killConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var stateMenuOpen by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.session?.taskSummary ?: sessionId,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            sessionId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    state.session?.state?.let { s -> StatePill(s) }
                    IconButton(onClick = vm::toggleMute) {
                        val muted = state.session?.muted == true
                        Icon(
                            if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                            contentDescription = if (muted) "Unmute" else "Mute",
                        )
                    }
                    IconButton(onClick = { terminalOpen = true }) {
                        Icon(Icons.Filled.Terminal, contentDescription = "Terminal view")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Kill session") },
                            leadingIcon = { Icon(Icons.Filled.Stop, null) },
                            onClick = { menuOpen = false; killConfirm = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Override state…") },
                            onClick = { menuOpen = false; stateMenuOpen = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            state.banner?.let { banner ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            banner,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = vm::dismissBanner) { Text("Dismiss") }
                    }
                }
            }

            EventList(state.events, modifier = Modifier.weight(1f))

            ReplyComposer(
                text = state.replyText,
                onTextChange = vm::onReplyTextChange,
                onSend = vm::sendReply,
                sending = state.replying,
                sessionId = sessionId,
                onTranscribed = { vm.onReplyTextChange(it) },
            )
        }
    }

    if (killConfirm) {
        AlertDialog(
            onDismissRequest = { killConfirm = false },
            title = { Text("Kill session?") },
            text = {
                Text(
                    "This stops the tmux session on the server. The session cannot " +
                        "be resumed (a new session would need to be started).",
                )
            },
            confirmButton = {
                TextButton(onClick = { killConfirm = false; vm.kill() }) {
                    Text(
                        "Kill",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { killConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (stateMenuOpen) {
        StateOverrideDialog(
            onDismiss = { stateMenuOpen = false },
            onPick = { s -> stateMenuOpen = false; vm.overrideState(s) },
        )
    }

    if (terminalOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { terminalOpen = false },
            sheetState = sheetState,
        ) {
            TerminalView(
                events = state.events,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun StatePill(state: SessionState) {
    val (label, color) = when (state) {
        SessionState.New -> "new" to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Running -> "running" to MaterialTheme.colorScheme.primary
        SessionState.Waiting -> "waiting" to MaterialTheme.colorScheme.tertiary
        SessionState.RateLimited -> "rate-limited" to MaterialTheme.colorScheme.secondary
        SessionState.Completed -> "completed" to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Killed -> "killed" to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Error -> "error" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = Modifier.padding(end = 4.dp),
    )
}

@Composable
private fun EventList(events: List<SessionEvent>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(events.size - 1)
    }
    if (events.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No messages yet. Waiting for session output…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(events, key = { e -> "${e.sessionId}-${e.ts.toEpochMilliseconds()}-${e.hashCode()}" }) { ev ->
            EventRow(ev)
            HorizontalDivider()
        }
    }
}

@Composable
private fun EventRow(event: SessionEvent) {
    when (event) {
        is SessionEvent.Output -> Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                when (event.stream) {
                    SessionEvent.Output.Stream.Stdout -> "llm "
                    SessionEvent.Output.Stream.Stderr -> "err "
                    SessionEvent.Output.Stream.System -> "sys "
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                event.body,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is SessionEvent.StateChange -> Text(
            "state: ${event.from.name.lowercase()} → ${event.to.name.lowercase()}",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        is SessionEvent.PromptDetected -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "⚡ prompt awaiting reply",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(event.prompt.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        is SessionEvent.RateLimited -> Text(
            "⏳ rate-limited" + (event.retryAfter?.let { " — retry at ${it}" } ?: ""),
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelMedium,
        )
        is SessionEvent.Completed -> Text(
            "✓ completed" + (event.exitCode?.let { " (exit $it)" } ?: ""),
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        is SessionEvent.Error -> Text(
            "✕ ${event.message}",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
        )
        is SessionEvent.Unknown -> Text(
            "(${event.type})",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ReplyComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    sessionId: String,
    onTranscribed: (String) -> Unit,
) {
    HorizontalDivider()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recorder by remember { mutableStateOf<com.dmzs.datawatchclient.voice.VoiceRecorder?>(null) }
    var transcribing by remember { mutableStateOf(false) }
    val recording = recorder != null

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(if (recording) "Listening…" else "Reply…") },
            modifier = Modifier.weight(1f),
            singleLine = false,
            maxLines = 4,
            enabled = !sending && !recording,
        )
        IconButton(
            onClick = {
                if (recording) {
                    val r = recorder ?: return@IconButton
                    recorder = null
                    val captured = r.stop() ?: return@IconButton
                    transcribing = true
                    scope.launch {
                        val profiles = com.dmzs.datawatchclient.di.ServiceLocator
                            .profileRepository.observeAll().first()
                        val profile = profiles.firstOrNull { it.enabled }
                        if (profile != null) {
                            com.dmzs.datawatchclient.di.ServiceLocator
                                .transportFor(profile)
                                .transcribeAudio(
                                    audio = captured.first,
                                    audioMime = captured.second,
                                    sessionId = sessionId,
                                    autoExec = false,
                                ).fold(
                                    onSuccess = { onTranscribed(it.transcript) },
                                    onFailure = { /* non-fatal — banner would go here */ },
                                )
                        }
                        transcribing = false
                    }
                } else {
                    val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                    runCatching { r.start() }.onSuccess { recorder = r }
                }
            },
            enabled = !sending && !transcribing,
        ) {
            if (transcribing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
            } else {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Stop recording" else "Voice reply",
                    tint = if (recording) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onSend, enabled = !sending && text.isNotBlank()) {
            if (sending) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
            } else {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun StateOverrideDialog(onDismiss: () -> Unit, onPick: (SessionState) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Override state") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SessionState.values().forEach { s ->
                    TextButton(
                        onClick = { onPick(s) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.name.lowercase(), modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
