package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    vm: SessionDetailViewModel =
        viewModel(
            key = sessionId,
            factory =
                viewModelFactory {
                    initializer { SessionDetailViewModel(sessionId) }
                },
        ),
) {
    val state by vm.state.collectAsState()
    val schedulesVm: com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel = viewModel()
    var killConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var stateMenuOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tap title to rename — same wire as Sessions-list overflow.
                    Column(
                        modifier =
                            Modifier
                                .clickable { renameOpen = true }
                                .padding(vertical = 4.dp),
                    ) {
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
                    state.session?.state?.let { s ->
                        // PWA: tap state pill → state-override menu.
                        StatePill(s, onClick = { stateMenuOpen = true })
                    }
                    IconButton(onClick = vm::toggleMute) {
                        val muted = state.session?.muted == true
                        Icon(
                            if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                            contentDescription = if (muted) "Unmute" else "Mute",
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Kill session") },
                            leadingIcon = { Icon(Icons.Filled.Stop, null) },
                            onClick = {
                                menuOpen = false
                                killConfirm = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Override state…") },
                            onClick = {
                                menuOpen = false
                                stateMenuOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Schedule reply…") },
                            onClick = {
                                menuOpen = false
                                scheduleOpen = true
                            },
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

            // Connection banner — shows when the owning profile's transport
            // last-probe failed. PWA renders an equivalent strip when WS or
            // REST drops; ours doubles as a hint that the live event stream
            // is also degraded (REST + WS share the trust-anchor wiring).
            if (state.reachable == false) {
                ConnectionBanner(onRetry = vm::dismissBanner)
            }
            // Input-required banner — top-of-terminal callout when the
            // session is `waiting_input`, mirroring the PWA's amber prompt
            // strip. Body shows the latest prompt text so users can decide
            // before scrolling the terminal.
            state.session?.takeIf { it.state == SessionState.Waiting }?.let {
                InputRequiredBanner(prompt = state.pendingPromptText)
            }

            val terminalController = rememberTerminalController()
            TerminalToolbar(controller = terminalController, sessionId = sessionId)
            TerminalView(
                sessionId = sessionId,
                events = state.events,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                controller = terminalController,
            )

            InlineNotices(state.events)

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
                TextButton(onClick = {
                    killConfirm = false
                    vm.kill()
                }) {
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
            onPick = { s ->
                stateMenuOpen = false
                vm.overrideState(s)
            },
        )
    }

    if (renameOpen) {
        val initial = state.session?.taskSummary ?: sessionId
        RenameDialog(
            initial = initial,
            onConfirm = { newName ->
                renameOpen = false
                vm.rename(newName)
            },
            onDismiss = { renameOpen = false },
        )
    }

    if (scheduleOpen) {
        // Pre-seed the task with the latest prompt text when one is open —
        // the common "schedule reply" intent is to auto-answer whatever the
        // session is currently blocking on. Fallback: task summary, then id.
        val seededTask =
            remember(state.events) {
                val latestPrompt =
                    state.events.asReversed().firstOrNull { it is SessionEvent.PromptDetected }
                        as? SessionEvent.PromptDetected
                latestPrompt?.prompt?.text
                    ?: state.session?.taskSummary
                    ?: sessionId
            }
        com.dmzs.datawatchclient.ui.schedules.ScheduleDialog(
            initialTask = seededTask,
            title = "Schedule reply",
            onConfirm = { task, cron, enabled ->
                schedulesVm.create(task, cron, enabled)
                scheduleOpen = false
            },
            onDismiss = { scheduleOpen = false },
        )
    }
}

/**
 * Compact row under the terminal that surfaces non-output events: the most
 * recent prompt, rate-limit notice, and unknown-type forward-compat entries.
 * Kept intentionally terse — the terminal carries the main narrative; this
 * is a status strip.
 */
@Composable
private fun InlineNotices(events: List<SessionEvent>) {
    val latestPrompt =
        events.asReversed().firstOrNull { it is SessionEvent.PromptDetected }
            as? SessionEvent.PromptDetected
    val latestRateLimit =
        events.asReversed().firstOrNull { it is SessionEvent.RateLimited }
            as? SessionEvent.RateLimited
    if (latestPrompt == null && latestRateLimit == null) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            latestPrompt?.let {
                Text(
                    "⌨ prompt: ${it.prompt.text.take(120)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            latestRateLimit?.let {
                Text(
                    "⏳ rate-limited" + (it.retryAfter?.let { ts -> " · retry at $ts" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatePill(state: SessionState, onClick: () -> Unit = {}) {
    val (label, color) =
        when (state) {
            SessionState.New -> "new" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Running -> "running" to MaterialTheme.colorScheme.primary
            SessionState.Waiting -> "waiting" to MaterialTheme.colorScheme.tertiary
            SessionState.RateLimited -> "rate-limited" to MaterialTheme.colorScheme.secondary
            SessionState.Completed -> "completed" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Killed -> "killed" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Error -> "error" to MaterialTheme.colorScheme.error
        }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        modifier = Modifier.padding(end = 4.dp),
    )
}

/**
 * Inline rename dialog spawned from a tap on the session-detail header.
 * Mirrors the row-level rename in SessionsScreen so behaviour is
 * consistent — single-line input, "Save" disabled while blank.
 */
@Composable
private fun RenameDialog(
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

/**
 * Top-of-terminal banner that surfaces when the active profile's
 * transport is unreachable. Non-dismissable on purpose — it self-clears
 * the moment a probe succeeds. The Retry button just nudges the VM to
 * drop any sticky error banner so the next refresh repaints cleanly.
 */
@Composable
private fun ConnectionBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Server unreachable — terminal stream paused, last frame shown.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * Amber strip right above the terminal when the session is
 * `waiting_input`. Body shows the latest prompt text (live event
 * preferred, falling back to `Session.lastPrompt`) so the user can
 * decide-then-reply without scrolling backlog.
 */
@Composable
private fun InputRequiredBanner(prompt: String?) {
    val accent = MaterialTheme.colorScheme.tertiary
    Surface(
        color = accent.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .padding(end = 8.dp)
                        .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "INPUT REQUIRED",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(
                    prompt?.takeIf { it.isNotBlank() } ?: "Session is waiting on a reply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EventList(
    events: List<SessionEvent>,
    modifier: Modifier = Modifier,
) {
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
        is SessionEvent.Output ->
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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
        is SessionEvent.StateChange ->
            Text(
                "state: ${event.from.name.lowercase()} → ${event.to.name.lowercase()}",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        is SessionEvent.PromptDetected ->
            Surface(
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
        is SessionEvent.RateLimited ->
            Text(
                "⏳ rate-limited" + (event.retryAfter?.let { " — retry at $it" } ?: ""),
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
        is SessionEvent.Completed ->
            Text(
                "✓ completed" + (event.exitCode?.let { " (exit $it)" } ?: ""),
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        is SessionEvent.Error ->
            Text(
                "✕ ${event.message}",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        is SessionEvent.Unknown ->
            Text(
                "(${event.type})",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        // Pane captures are rendered directly into the xterm WebView, not
        // into the event-stream chat surface, so the chat row is intentionally
        // a no-op here.
        is SessionEvent.PaneCapture -> Unit
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
                        val profiles =
                            com.dmzs.datawatchclient.di.ServiceLocator
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
                    tint =
                        if (recording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
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
private fun StateOverrideDialog(
    onDismiss: () -> Unit,
    onPick: (SessionState) -> Unit,
) {
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
