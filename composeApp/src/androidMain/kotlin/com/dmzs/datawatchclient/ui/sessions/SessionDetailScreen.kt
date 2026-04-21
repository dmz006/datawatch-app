package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.dmzs.datawatchclient.storage.observeForProfileAny
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
    // Mark this session as foreground while the detail screen is
    // composed; NotificationPoster uses this to suppress redundant
    // wake notifications for the session the user is already viewing.
    androidx.compose.runtime.DisposableEffect(sessionId) {
        com.dmzs.datawatchclient.push.ForegroundSessionTracker.enter(sessionId)
        onDispose {
            com.dmzs.datawatchclient.push.ForegroundSessionTracker.leave(sessionId)
        }
    }
    val schedulesVm: com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel = viewModel()
    val sessionSchedulesVm: SessionSchedulesViewModel =
        viewModel(
            key = "session-schedules-$sessionId",
            factory = viewModelFactory { initializer { SessionSchedulesViewModel(sessionId) } },
        )
    val sessionSchedules by sessionSchedulesVm.state.collectAsState()
    var killConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var stateMenuOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var timelineOpen by remember { mutableStateOf(false) }

    // Persistent mode preference — Terminal is the default (matches the
    // PWA), Chat re-renders the existing event list with quick-reply
    // buttons under prompts. Mode survives app restarts.
    val context = LocalContext.current
    val modePrefs =
        remember(context) {
            context.getSharedPreferences(
                "dw.session.detail.v1",
                android.content.Context.MODE_PRIVATE,
            )
        }
    var chatMode by remember {
        mutableStateOf(modePrefs.getBoolean("chat_mode", false))
    }
    androidx.compose.runtime.LaunchedEffect(chatMode) {
        modePrefs.edit().putBoolean("chat_mode", chatMode).apply()
    }

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
                        // Prefer user-assigned name over the raw task
                        // prompt, matching PWA sessionCard displayText.
                        Text(
                            state.session?.name?.takeIf { it.isNotBlank() }
                                ?: state.session?.taskSummary
                                ?: sessionId,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sessionId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.session?.backend?.takeIf { it.isNotBlank() }?.let { b ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    b.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            state.messagingBackend?.takeIf { it.isNotBlank() }?.let { ch ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "· $ch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            state.session?.hostnamePrefix?.takeIf { it.isNotBlank() }?.let { h ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "· $h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                    // Stop — promoted from the overflow menu so it's
                    // one tap for a running/waiting session, matching
                    // the PWA header Stop button.
                    val isActive =
                        state.session?.state == SessionState.Running ||
                            state.session?.state == SessionState.Waiting
                    if (isActive) {
                        IconButton(onClick = { killConfirm = true }) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop session",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // Timeline — promoted from overflow so users don't
                    // have to tap-then-pick to see session history.
                    IconButton(onClick = { timelineOpen = true }) {
                        Icon(Icons.Filled.Timeline, contentDescription = "Timeline")
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
            // PWA-style output-surface tabs: tmux (terminal) / chat.
            // Replaces the v0.14 icon-toggle with proper tabs so the
            // active surface is always visible at a glance.
            androidx.compose.material3.TabRow(
                selectedTabIndex = if (chatMode) 1 else 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.Tab(
                    selected = !chatMode,
                    onClick = { chatMode = false },
                    text = { Text("tmux") },
                )
                androidx.compose.material3.Tab(
                    selected = chatMode,
                    onClick = { chatMode = true },
                    text = { Text("channel") },
                )
            }
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

            if (chatMode) {
                // Chat mode — render the event-stream list with quick-reply
                // buttons under the latest prompt. Mirrors the PWA chat
                // pane that activates when you collapse the terminal.
                ChatEventList(
                    events = state.events,
                    onQuickReply = vm::sendQuickReply,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                val terminalController = rememberTerminalController()
                TerminalToolbar(controller = terminalController, sessionId = sessionId)
                TerminalView(
                    sessionId = sessionId,
                    events = state.events,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    controller = terminalController,
                )
                // Backend-specific minimum cols/rows. Matches parent
                // v0.14.1 per-LLM console-size rule (claude-code = 120×40).
                // Without this, claude's TUI wraps on phone widths.
                androidx.compose.runtime.LaunchedEffect(state.session?.backend) {
                    val backend = state.session?.backend?.lowercase()
                    when (backend) {
                        "claude-code", "claude" -> terminalController.setMinSize(120, 40)
                        else -> terminalController.setMinSize(0, 0)
                    }
                }
                // Freeze writes when session reaches a terminal state so
                // the final screenshot isn't overpainted by subsequent
                // shell-prompt pane_captures (PWA behaviour).
                androidx.compose.runtime.LaunchedEffect(state.session?.state) {
                    val st = state.session?.state
                    val frozen =
                        st == SessionState.Completed ||
                            st == SessionState.Killed ||
                            st == SessionState.Error
                    terminalController.setFrozen(frozen)
                }
                InlineNotices(state.events)
            }

            // Per-session "Scheduled" strip — mirrors PWA
            // loadSessionSchedules() in app.js. Hidden when no pending
            // schedules or when the server predates the session_id filter.
            if (sessionSchedules.supported && sessionSchedules.schedules.isNotEmpty()) {
                SessionSchedulesStrip(
                    schedules = sessionSchedules.schedules,
                    onCancel = sessionSchedulesVm::cancel,
                )
            }

            ReplyComposer(
                text = state.replyText,
                onTextChange = vm::onReplyTextChange,
                onSend = vm::sendReply,
                sending = state.replying,
                sessionId = sessionId,
                onTranscribed = { vm.onReplyTextChange(it) },
                onSchedule = { scheduleOpen = true },
                waitingInput = state.session?.state == SessionState.Waiting,
                onQuickReply = vm::sendQuickReply,
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

    if (timelineOpen) {
        TimelineSheet(
            sessionId = sessionId,
            events = state.events,
            onDismiss = { timelineOpen = false },
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
        // Pre-seed the schedule task. Priority:
        //   1. Typed reply text (the user opened Schedule from the
        //      composer with a draft already in flight).
        //   2. Latest live prompt — the common intent for an open
        //      `waiting_input` session.
        //   3. Session task summary, then id, as a last-resort label.
        val seededTask =
            remember(state.events, state.replyText) {
                if (state.replyText.isNotBlank()) return@remember state.replyText
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
                // Attach the new schedule to this session so it shows up in
                // the per-session "Scheduled" strip below. Refresh the
                // strip optimistically — the PWA waits for the list
                // round-trip, which looks sluggish on mobile.
                schedulesVm.create(task, cron, enabled, sessionId = sessionId)
                sessionSchedulesVm.refresh()
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
    // Wire-format labels matching PWA (see PwaComponents.label()).
    val (label, color) =
        when (state) {
            SessionState.New -> "new" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Running -> "running" to MaterialTheme.colorScheme.primary
            SessionState.Waiting -> "waiting_input" to MaterialTheme.colorScheme.tertiary
            SessionState.RateLimited -> "rate_limited" to MaterialTheme.colorScheme.secondary
            SessionState.Completed -> "complete" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Killed -> "killed" to MaterialTheme.colorScheme.onSurfaceVariant
            SessionState.Error -> "failed" to MaterialTheme.colorScheme.error
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

/**
 * Bottom-sheet session timeline. Prefers the parent server's
 * `/api/sessions/timeline?id=` feed (pipe-delimited lines:
 * `"<ts> | <event> | <detail>"`); falls back to a local filter of
 * cached WS events when the server can't be reached or the endpoint
 * hasn't been pre-populated yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSheet(
    sessionId: String,
    events: List<SessionEvent>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var serverLines by remember { mutableStateOf<List<String>?>(null) }
    var fetchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId) {
        val profiles =
            com.dmzs.datawatchclient.di.ServiceLocator.profileRepository.observeAll().first()
        val owningId =
            runCatching {
                com.dmzs.datawatchclient.di.ServiceLocator.sessionRepository
                    .observeForProfileAny(sessionId)
                    .first()
                    ?.serverProfileId
            }.getOrNull()
        val profile =
            profiles.firstOrNull { it.id == owningId }
                ?: profiles.firstOrNull { it.enabled }
        if (profile == null) {
            fetchFailed = true
            return@LaunchedEffect
        }
        com.dmzs.datawatchclient.di.ServiceLocator.transportFor(profile)
            .fetchTimeline(sessionId)
            .fold(
                onSuccess = { serverLines = it },
                onFailure = { fetchFailed = true },
            )
    }
    val localItems =
        remember(events) {
            events.filter { it !is SessionEvent.Output && it !is SessionEvent.PaneCapture }
                .sortedBy { it.ts }
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Timeline", style = MaterialTheme.typography.titleMedium)
            val usingServer = serverLines != null && serverLines!!.isNotEmpty()
            val subtitle =
                when {
                    usingServer -> "${serverLines!!.size} events (server feed)"
                    fetchFailed -> "${localItems.size} events (local cache — server feed unavailable)"
                    else -> "${localItems.size} events (local cache)"
                }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (usingServer) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(serverLines!!, key = { it }) { line -> TimelineServerRow(line) }
                }
            } else if (localItems.isEmpty()) {
                Text(
                    "No events yet — open a session that has produced output to see " +
                        "state transitions, prompts, and completions here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(localItems, key = { it.ts.toEpochMilliseconds().toString() + it.hashCode() }) { ev ->
                        TimelineRow(ev)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Parses one PWA-shape timeline line (`"<ts> | <event> | <detail>"`)
 * into a styled row. Colour-codes by event keyword the same way the
 * PWA does (state → accent, input → success, rate → warning).
 */
@Composable
private fun TimelineServerRow(line: String) {
    val parts = line.split(" | ", limit = 3)
    val ts = parts.getOrNull(0).orEmpty()
    val event = parts.getOrNull(1).orEmpty()
    val detail = parts.getOrNull(2).orEmpty()
    val color =
        when {
            event.contains("state") -> MaterialTheme.colorScheme.tertiary
            event.contains("input") -> MaterialTheme.colorScheme.primary
            event.contains("rate") -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            ts.substringAfter('T').substringBefore('Z').substringBefore('.'),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).width(72.dp),
        )
        Text(
            event,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(end = 8.dp).width(96.dp),
        )
        Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 4)
    }
}

@Composable
private fun TimelineRow(event: SessionEvent) {
    val (label, body, color) =
        when (event) {
            is SessionEvent.StateChange ->
                Triple(
                    "STATE",
                    "${event.from.name.lowercase()} → ${event.to.name.lowercase()}",
                    MaterialTheme.colorScheme.tertiary,
                )
            is SessionEvent.PromptDetected ->
                Triple(
                    "PROMPT",
                    event.prompt.text.take(160),
                    MaterialTheme.colorScheme.tertiary,
                )
            is SessionEvent.RateLimited ->
                Triple(
                    "RATE-LIMIT",
                    event.retryAfter?.let { "retry $it" } ?: "throttled",
                    MaterialTheme.colorScheme.secondary,
                )
            is SessionEvent.Completed ->
                Triple(
                    "DONE",
                    "exit ${event.exitCode ?: "?"}",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is SessionEvent.Error ->
                Triple("ERROR", event.message, MaterialTheme.colorScheme.error)
            is SessionEvent.Unknown ->
                Triple("(${event.type})", "—", MaterialTheme.colorScheme.onSurfaceVariant)
            is SessionEvent.Output, is SessionEvent.PaneCapture ->
                // filtered out upstream, but exhaustiveness requires a branch
                Triple("OUTPUT", "(filtered)", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            event.ts.toString().substringAfter('T').substringBefore('.'),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp).width(72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 4)
        }
    }
}

/**
 * PWA parity: the per-session "Scheduled" strip that lives just above
 * the composer. Shows `run_at` (or `cron`) + command body + a red ✕
 * cancel button per row, matching the PWA's app.js
 * loadSessionSchedules() render.
 */
@Composable
private fun SessionSchedulesStrip(
    schedules: List<com.dmzs.datawatchclient.domain.Schedule>,
    onCancel: (String) -> Unit,
) {
    HorizontalDivider()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            "SCHEDULED (${schedules.size})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        schedules.forEach { sc ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val when_ =
                    sc.runAt?.toString()?.substringBefore('.')?.replace('T', ' ')
                        ?: sc.cron
                        ?: "on input"
                Text(
                    when_,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    sc.task,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onCancel(sc.id) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel schedule",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Chat-mode event list — same EventRow renderer, but the latest
 * `PromptDetected` row gets quick-reply buttons appended (Yes / No /
 * Stop). Tap fires [onQuickReply] without touching the composer
 * draft.
 */
@Composable
private fun ChatEventList(
    events: List<SessionEvent>,
    onQuickReply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(events.size - 1)
    }
    if (events.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No messages yet. Waiting for session output…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val latestPromptIndex =
        events.indexOfLast { it is SessionEvent.PromptDetected }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        items(
            events,
            key = { e -> "${e.sessionId}-${e.ts.toEpochMilliseconds()}-${e.hashCode()}" },
        ) { ev ->
            EventRow(ev)
            if (ev is SessionEvent.PromptDetected && events.indexOf(ev) == latestPromptIndex) {
                QuickReplyButtons(onQuickReply = onQuickReply)
            }
            HorizontalDivider()
        }
    }
}

/**
 * Three pill buttons under the latest prompt: Yes / No / Stop. PWA
 * surfaces the same triad to let you blast through approval prompts
 * without typing. Stop sends "stop" rather than killing the session
 * — the parent treats it as a graceful "halt this step" reply that
 * the LLM can interpret in-context.
 */
@Composable
private fun QuickReplyButtons(onQuickReply: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickReplyChip("Yes", onClick = { onQuickReply("yes") })
        QuickReplyChip("No", onClick = { onQuickReply("no") })
        QuickReplyChip("Stop", onClick = { onQuickReply("stop") })
    }
}

@Composable
private fun QuickReplyChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
    )
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
    onSchedule: () -> Unit,
    waitingInput: Boolean = false,
    onQuickReply: (String) -> Unit = {},
) {
    HorizontalDivider()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recorder by remember { mutableStateOf<com.dmzs.datawatchclient.voice.VoiceRecorder?>(null) }
    var transcribing by remember { mutableStateOf(false) }
    val recording = recorder != null

    // When the session is waiting_input, show a compact row of quick-
    // reply chips above the text field — PWA composer shows the same
    // yes/no/continue/skip shortcuts so users can answer a prompt
    // without typing.
    if (waitingInput) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            items(listOf("yes", "no", "continue", "skip", "/exit")) { cmd ->
                androidx.compose.material3.AssistChip(
                    onClick = { onQuickReply(cmd) },
                    label = {
                        Text(
                            when (cmd) {
                                "yes" -> "approve"
                                "no" -> "reject"
                                "/exit" -> "quit"
                                else -> cmd
                            },
                        )
                    },
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    when {
                        recording -> "Listening…"
                        waitingInput -> "Reply (input required)…"
                        else -> "Reply…"
                    },
                )
            },
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
                                    onSuccess = { result ->
                                        val text = result.transcript.trim()
                                        // Voice-to-new-session: detect "new:" or "new "
                                        // prefix (case-insensitive) and route through
                                        // startSession instead of putting it in the
                                        // reply composer. Matches PWA behaviour for
                                        // voice commands from inside an existing
                                        // session.
                                        val newPrefix =
                                            Regex("^new[:\\s]+(.+)", RegexOption.IGNORE_CASE)
                                                .matchEntire(text)?.groupValues?.get(1)?.trim()
                                        if (!newPrefix.isNullOrEmpty()) {
                                            com.dmzs.datawatchclient.di.ServiceLocator
                                                .transportFor(profile)
                                                .startSession(task = newPrefix)
                                                .fold(
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Started new session: $newPrefix",
                                                            android.widget.Toast.LENGTH_SHORT,
                                                        ).show()
                                                    },
                                                    onFailure = {
                                                        // Fall back to normal composer
                                                        // insert so the user doesn't
                                                        // lose their dictation.
                                                        onTranscribed(text)
                                                    },
                                                )
                                        } else {
                                            onTranscribed(text)
                                        }
                                    },
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
        // Schedule-as-cron — preserves the typed reply text as the
        // schedule task, so "draft → schedule" is a single tap.
        IconButton(onClick = onSchedule, enabled = !sending) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = "Schedule reply",
                tint = MaterialTheme.colorScheme.primary,
            )
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
