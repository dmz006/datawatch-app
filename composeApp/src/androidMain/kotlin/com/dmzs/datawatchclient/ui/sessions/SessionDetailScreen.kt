package com.dmzs.datawatchclient.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    var deleteConfirm by remember { mutableStateOf(false) }
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
                    // Every Text uses softWrap=false + TextOverflow.Ellipsis so
                    // a narrow title column truncates instead of wrapping each
                    // character onto its own line.
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        // G11 — inline tap-to-edit header. Click the
                        // title to switch to a TextField in-place; Enter
                        // or blur commits via vm.rename (PWA
                        // startHeaderRename mirror). Retains the old
                        // RenameDialog as a long-press fallback via the
                        // overflow menu for users who prefer modal edit.
                        val headerTitle =
                            state.session?.name?.takeIf { it.isNotBlank() }
                                ?: state.session?.taskSummary
                                ?: sessionId
                        var editingHeader by remember(sessionId) { mutableStateOf(false) }
                        var draft by remember(editingHeader, headerTitle) {
                            mutableStateOf(headerTitle)
                        }
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        if (editingHeader) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                singleLine = true,
                                textStyle =
                                    MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.primary,
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        val trimmed = draft.trim()
                                        if (trimmed.isNotBlank() && trimmed != headerTitle) {
                                            vm.rename(trimmed)
                                        }
                                        editingHeader = false
                                    },
                                ),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .headerRenameFocusChain(
                                            focusRequester = focusRequester,
                                            onBlurCommit = {
                                                val trimmed = draft.trim()
                                                if (trimmed.isNotBlank() && trimmed != headerTitle) {
                                                    vm.rename(trimmed)
                                                }
                                                editingHeader = false
                                            },
                                        ),
                            )
                            LaunchedEffect(editingHeader) {
                                if (editingHeader) focusRequester.requestFocus()
                            }
                        } else {
                            Text(
                                headerTitle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable {
                                    draft = headerTitle
                                    editingHeader = true
                                },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sessionId,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.session?.backend?.takeIf { it.isNotBlank() }?.let { b ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    b.uppercase(),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            state.messagingBackend?.takeIf { it.isNotBlank() }?.let { ch ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "· $ch",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            state.session?.hostnamePrefix?.takeIf { it.isNotBlank() }?.let { h ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "· $h",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                // v0.33.22: connection dot moved from SessionInfoBar to
                // the right side of the TopAppBar, matching PWA's header
                // connection indicator. Everything else (state pill,
                // Stop, Timeline, chips) still lives in SessionInfoBar
                // below the tabs.
                actions = {
                    Box(
                        modifier =
                            Modifier
                                .padding(end = 12.dp)
                                .size(10.dp)
                                .background(
                                    color =
                                        when (state.reachable) {
                                            true -> Color(0xFF22C55E) // green-500
                                            false -> Color(0xFFEF4444) // red-500
                                            null -> Color(0xFF94A3B8) // slate-400
                                        },
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                    )
                },
            )
        },
    ) { padding ->
        // Window-inset handling for the soft keyboard (user report
        // 2026-04-23: "tmux input window is hidden behind the keyboard").
        //
        // Prior iteration applied `imePadding()` to the outer Column,
        // which shrunk the *entire* content (terminal + banners +
        // composer) whenever the IME opened. On SDK 35+ edge-to-edge
        // that combined with Scaffold's content insets in ways that
        // left the composer partially occluded — the composer would
        // lift a bit but not enough to clear the keyboard.
        //
        // New approach: let the terminal / chat surface keep its full
        // height and move only the composer row above the keyboard
        // via `imePadding()` on ReplyComposer directly. This mirrors
        // how the PWA handles it — the output area stays fixed; only
        // the input bar lifts.
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
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
            // v0.33.19 — PWA-style `.session-info-bar` row matching
            // `app.js:1672-1680`: backend chip, mode chip, state pill
            // (clickable to override), Stop / Restart / Delete action,
            // Timeline button. Mirrors PWA's nicely-presented badges.
            var responseOpen by remember { mutableStateOf(false) }
            SessionInfoBar(
                backend = state.session?.backend,
                sessionMode = state.messagingBackend ?: "tmux",
                state = state.session?.state,
                reachable = state.reachable,
                onStateClick = { stateMenuOpen = true },
                onStop = { killConfirm = true },
                onRestart = { /* parent-level reschedule not wired here yet */ },
                onTimeline = { timelineOpen = true },
                onDelete = { deleteConfirm = true },
                stateMenuOpen = stateMenuOpen,
                onStateMenuDismiss = { stateMenuOpen = false },
                onPickState = { s ->
                    stateMenuOpen = false
                    vm.overrideState(s)
                },
                hasResponse = !state.session?.lastResponse.isNullOrBlank(),
                onResponse = { responseOpen = true },
            )
            if (responseOpen) {
                LastResponseSheet(
                    response = state.session?.lastResponse.orEmpty(),
                    onDismiss = { responseOpen = false },
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

            // Server-reported chat-mode sessions (output_mode=chat, e.g.
            // OpenWebUI / Ollama) emit structured `chat_message` WS frames
            // instead of `pane_capture` — no terminal exists. When on, the
            // transcript panel is the only sensible output surface; user's
            // Terminal/Chat view toggle doesn't apply.
            val serverChatMode = state.session?.isChatMode == true
            if (serverChatMode) {
                ChatTranscriptPanel(
                    sessionId = sessionId,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else if (chatMode) {
                // User's view-mode toggle (Terminal vs Chat-style bubbles
                // for any non-chat session). Renders the event stream as
                // bubbles but is different from server-side chat mode.
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
                    // PWA defaults every terminal to `configCols || 80`
                    // (app.js:2057). Giving mobile an 80-col floor for
                    // every backend forces horizontal scroll on the phone
                    // viewport (~55 cols at 11 px font) and matches how
                    // TUIs render on the web. Claude-code keeps its
                    // 120×40 enforcement per parent v0.14.1.
                    when (backend) {
                        "claude-code", "claude" -> terminalController.setMinSize(120, 40)
                        else -> terminalController.setMinSize(80, 24)
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

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete session?") },
            text = {
                Text(
                    "This removes the session record from the server. " +
                        "Tracking data on disk is preserved. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirm = false
                    vm.delete(onDeleted = onBack)
                }) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    // v0.35.1: stateMenuOpen is now handled inline by SessionInfoBar's
    // DropdownMenu anchored to the state pill. The old AlertDialog
    // (StateOverrideDialog) is kept as a no-longer-called composable
    // for back-compat; it can be removed in a later cleanup release.

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
/**
 * Byte-for-byte mirror of PWA's `.session-info-bar > .meta` (app.js:1672-1680):
 * backend chip, mode chip, clickable state pill, primary action button
 * (Stop when active, Restart when done), and a Timeline button. Sits below
 * the tmux/channel TabRow, above any banners + the terminal.
 */
@Composable
private fun SessionInfoBar(
    backend: String?,
    sessionMode: String,
    state: SessionState?,
    reachable: Boolean?,
    onStateClick: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onTimeline: () -> Unit,
    onDelete: () -> Unit = {},
    // G12: dropdown anchored to the state pill rather than a full-screen
    // AlertDialog (matches PWA showStateOverride app.js:2206). Hosted
    // inside the pill's Box so the menu appears directly under the
    // badge. Pass `stateMenuOpen=true` to show the menu, and wire
    // `onStateMenuDismiss` / `onPickState` to handle interaction.
    stateMenuOpen: Boolean = false,
    onStateMenuDismiss: () -> Unit = {},
    onPickState: (SessionState) -> Unit = {},
    // G13: optional Response button — renders when the session has
    // a non-blank lastResponse. Taps open the response viewer sheet.
    hasResponse: Boolean = false,
    onResponse: () -> Unit = {},
) {
    val isActive =
        state == SessionState.Running || state == SessionState.Waiting ||
            state == SessionState.RateLimited
    val isDone =
        state == SessionState.Completed || state == SessionState.Killed ||
            state == SessionState.Error
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            // v0.33.22 — dot moved to TopAppBar right side. SessionInfoBar
            // now starts straight with the backend chip like PWA.
            if (!backend.isNullOrBlank()) {
                InfoBadge(text = backend.lowercase(), color = MaterialTheme.colorScheme.primary)
            }
            InfoBadge(text = sessionMode.lowercase(), color = MaterialTheme.colorScheme.secondary)
            state?.let {
                Box {
                    Box(
                        modifier =
                            Modifier
                                .clickable(onClick = onStateClick)
                                .background(
                                    color = stateBgColor(it),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stateLabel(it),
                            fontSize = 10.sp,
                            color = stateFgColor(it),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            letterSpacing = 0.3.sp,
                        )
                    }
                    // DropdownMenu anchored to the pill's Box —
                    // appears directly under the badge when open.
                    androidx.compose.material3.DropdownMenu(
                        expanded = stateMenuOpen,
                        onDismissRequest = onStateMenuDismiss,
                    ) {
                        SessionState.values().forEach { target ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stateLabel(target),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                onClick = {
                                    onStateMenuDismiss()
                                    onPickState(target)
                                },
                            )
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            if (isActive) {
                TextButton(onClick = onStop, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Text(
                        "■ Stop",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (isDone) {
                TextButton(onClick = onRestart, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Text("↻ Restart", style = MaterialTheme.typography.labelSmall)
                }
                // Delete is only offered after the session has reached a terminal
                // state — same gate the PWA uses (app.js delete button only
                // appears on non-active rows). Prevents accidental removal of
                // live sessions from the UI.
                TextButton(
                    onClick = onDelete,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        "🗑 Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onTimeline, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                Text("⏱ Timeline", style = MaterialTheme.typography.labelSmall)
            }
            if (hasResponse) {
                TextButton(
                    onClick = onResponse,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp,
                    ),
                ) {
                    Text("💾 Response", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun InfoBadge(text: String, color: Color) {
    Box(
        modifier =
            Modifier
                .background(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            fontSize = 10.sp,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun stateBgColor(s: SessionState): Color {
    val dw = LocalDatawatchColors.current
    return when (s) {
        SessionState.Running -> dw.success.copy(alpha = 0.15f)
        SessionState.Waiting -> dw.waiting.copy(alpha = 0.15f)
        SessionState.RateLimited -> dw.warning.copy(alpha = 0.15f)
        SessionState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    }
}

@Composable
private fun stateFgColor(s: SessionState): Color {
    val dw = LocalDatawatchColors.current
    return when (s) {
        SessionState.Running -> dw.success
        SessionState.Waiting -> dw.waiting
        SessionState.RateLimited -> dw.warning
        SessionState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun stateLabel(s: SessionState): String =
    when (s) {
        SessionState.Running -> "running"
        SessionState.Waiting -> "waiting_input"
        SessionState.RateLimited -> "rate_limited"
        SessionState.Completed -> "complete"
        SessionState.Killed -> "killed"
        SessionState.Error -> "failed"
        SessionState.New -> "new"
    }

@Composable
private fun InlineNotices(events: List<SessionEvent>) {
    val latestPrompt =
        events.asReversed().firstOrNull { it is SessionEvent.PromptDetected }
            as? SessionEvent.PromptDetected
    val latestRateLimit =
        events.asReversed().firstOrNull { it is SessionEvent.RateLimited }
            as? SessionEvent.RateLimited
    if (latestPrompt == null && latestRateLimit == null) return
    // v0.33.15 (B26): PWA-style `.needs-input-banner` — distinct
    // amber/yellow with an X dismiss button. Each banner kind can be
    // individually closed; closure is per-recomposition (re-appears
    // when a fresh prompt/rate-limit event arrives, matching PWA
    // where the X just hides the current display).
    var promptDismissed by remember(latestPrompt?.ts) { mutableStateOf(false) }
    var rateDismissed by remember(latestRateLimit?.ts) { mutableStateOf(false) }
    val showPrompt = latestPrompt != null && !promptDismissed
    val showRate = latestRateLimit != null && !rateDismissed
    if (!showPrompt && !showRate) return
    val yellow = androidx.compose.ui.graphics.Color(0xFFFEF3C7) // amber-100
    val yellowText = androidx.compose.ui.graphics.Color(0xFF92400E) // amber-800
    Surface(
        color = yellow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (showPrompt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Waiting for input — ${latestPrompt!!.prompt.text.take(60)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = yellowText,
                    )
                    IconButton(
                        onClick = { promptDismissed = true },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = yellowText,
                        )
                    }
                }
            }
            if (showRate) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Rate-limited" + (latestRateLimit!!.retryAfter?.let { ts -> " · retry at $ts" } ?: ""),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = yellowText,
                    )
                    IconButton(
                        onClick = { rateDismissed = true },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = yellowText,
                        )
                    }
                }
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
    // v0.33.22 — big yellow PWA-style `.needs-input-banner` block
    // (app.js:1584-1589). Left-aligned "Input Required" pill, multi-
    // line prompt body (last ~6 lines), ✕ dismiss on the right. Big
    // amber fill + bold header so it's unmissable, matching the PWA
    // treatment.
    var dismissed by remember(prompt) { mutableStateOf(false) }
    if (dismissed) return
    val lines =
        prompt?.trim().orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            .takeLast(PROMPT_CTX_LINES)
    val amberBg = Color(0xFFFEF3C7) // amber-100
    val amberFg = Color(0xFF92400E) // amber-800
    val amberEdge = Color(0xFFD97706) // amber-600
    Surface(
        color = amberBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .background(
                                color = amberEdge,
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "Input Required",
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.3.sp,
                    )
                }
                if (lines.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        lines.forEach { l ->
                            Text(
                                l,
                                style = MaterialTheme.typography.bodySmall,
                                color = amberFg,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                } else {
                    Text(
                        "Session is waiting on a reply.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = amberFg,
                    )
                }
            }
            IconButton(onClick = { dismissed = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = amberFg,
                )
            }
        }
    }
}

private const val PROMPT_CTX_LINES = 6

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
            is SessionEvent.Output, is SessionEvent.PaneCapture, is SessionEvent.ChatMessage ->
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
 * Chat-mode event list — renders Output events as chat bubbles
 * (user / assistant / system) with avatar + role + timestamp +
 * body. Mirrors PWA `renderChatBubble` (app.js ~line 1737). The
 * latest `PromptDetected` row gets quick-reply buttons appended
 * (Yes / No / Stop). Tap fires [onQuickReply] without touching
 * the composer draft.
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
        // v0.33.23: key by list index. The previous
        // "${sessionId}-${ts}-${hashCode}" composite collided when the
        // live PaneCapture SharedFlow replayed the same event twice
        // (replay=1 + a subsequent live emit) → LazyColumn crashed
        // with "Key … was already used". Index-based keys are the
        // Compose-canonical fallback when no stable identifier exists.
        itemsIndexed(events) { idx, ev ->
            ChatBubbleRow(ev)
            if (ev is SessionEvent.PromptDetected && idx == latestPromptIndex) {
                QuickReplyButtons(onQuickReply = onQuickReply)
            }
        }
    }
}

/**
 * Chat-style row: avatar + role label + timestamp header over a
 * bubble-like body. Mirrors PWA CSS `.chat-bubble` styling —
 * user rows right-aligned with accent fill, assistant rows
 * left-aligned with surface fill, system rows centred italic.
 */
@Composable
private fun ChatBubbleRow(event: SessionEvent) {
    val (avatar, label, body, role) =
        when (event) {
            is SessionEvent.Output ->
                when (event.stream) {
                    SessionEvent.Output.Stream.Stdout ->
                        Quad("AI", "Assistant", event.body, "assistant")
                    SessionEvent.Output.Stream.Stderr ->
                        Quad("!", "Error", event.body, "system")
                    SessionEvent.Output.Stream.System ->
                        Quad("S", "System", event.body, "system")
                }
            is SessionEvent.PromptDetected ->
                Quad("?", "Prompt", event.prompt.text, "prompt")
            is SessionEvent.StateChange ->
                Quad(
                    "S",
                    "State",
                    "${event.from.name.lowercase()} → ${event.to.name.lowercase()}",
                    "system",
                )
            is SessionEvent.Completed ->
                Quad("✓", "Done", "exit ${event.exitCode ?: ""}", "system")
            is SessionEvent.Error -> Quad("✕", "Error", event.message, "error")
            is SessionEvent.RateLimited ->
                Quad("⏳", "Rate", event.retryAfter?.let { "retry $it" } ?: "throttled", "system")
            is SessionEvent.Unknown -> Quad("?", event.type, "", "system")
            is SessionEvent.PaneCapture -> return
            is SessionEvent.ChatMessage ->
                // Chat frames render via ChatTranscriptPanel, not the event
                // bubble list. Exhaustiveness only — skip.
                return
        }
    val bubbleBg =
        when (role) {
            "assistant" -> MaterialTheme.colorScheme.surfaceVariant
            "prompt" -> MaterialTheme.colorScheme.tertiaryContainer
            "error" -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val bubbleFg =
        when (role) {
            "prompt" -> MaterialTheme.colorScheme.onTertiaryContainer
            "error" -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                avatar,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bubbleBg,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    body,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = bubbleFg,
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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
        // Chat frames render via ChatTranscriptPanel when the session is in
        // chat mode; the timeline/event-row surface never sees them.
        is SessionEvent.ChatMessage -> Unit
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

    // RECORD_AUDIO is a runtime permission on Android 6+; without it
    // MediaRecorder.start() throws and the tap appears to do nothing
    // (2026-04-22 user report). Request at first use, start recording
    // once granted — denial surfaces as a toast so the button never
    // silently no-ops.
    val micPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                runCatching { r.start() }
                    .onSuccess { recorder = r }
                    .onFailure { e ->
                        android.widget.Toast.makeText(
                            context,
                            "Recording failed: ${e.message ?: e::class.simpleName}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Microphone permission denied — enable it in Settings.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

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
        // `imePadding()` here (not on the outer Column) lifts only the
        // composer row above the soft keyboard. Mirrors PWA behaviour —
        // output area keeps its bounds; input bar floats above the IME.
        // User-flagged 2026-04-23: the text field was being occluded by
        // the keyboard when typing; SDK 35 edge-to-edge requires explicit
        // IME handling and the prior Column-level imePadding was fighting
        // Scaffold's content insets.
        modifier = Modifier.fillMaxWidth().imePadding().padding(8.dp),
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
            // v0.33.23: explicit onSurface text color. Without this the
            // composer inherited LocalContentColor from whichever Surface
            // was closest in the tree — when the amber InputRequiredBanner
            // was above it, the banner's contentColor tinted the text
            // dark-amber-on-dark-surface → invisible black-on-black.
            textStyle =
                LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
        IconButton(
            onClick = {
                if (recording) {
                    val r = recorder ?: return@IconButton
                    recorder = null
                    val captured = r.stop() ?: return@IconButton
                    transcribing = true
                    scope.launch {
                        // Honour ActiveServerStore so voice lands on the
                        // server the user is actually viewing, not the
                        // first-enabled profile by DB order. Before
                        // 2026-04-22 this silently routed transcription
                        // to whichever server the DB happened to return
                        // first — baffling when multiple servers are
                        // configured and only the non-active one has
                        // Whisper enabled.
                        val activeId =
                            com.dmzs.datawatchclient.di.ServiceLocator
                                .activeServerStore.get()
                        val profiles =
                            com.dmzs.datawatchclient.di.ServiceLocator
                                .profileRepository.observeAll().first()
                        val profile =
                            profiles.firstOrNull { it.id == activeId && it.enabled }
                                ?: profiles.firstOrNull { it.enabled }
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
                                    onFailure = { err ->
                                        // Surface transcription failure as a
                                        // toast with the server's response so
                                        // the user knows voice is disabled /
                                        // unreachable instead of a silent
                                        // no-op (2026-04-22 user report).
                                        android.widget.Toast.makeText(
                                            context,
                                            "Transcribe failed on ${profile.displayName}: " +
                                                "${err.message ?: err::class.simpleName}",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                )
                        }
                        transcribing = false
                    }
                } else {
                    // Check runtime grant first. If already granted, start
                    // immediately; otherwise route through the launcher,
                    // which will start on grant or toast on deny.
                    val granted =
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                        runCatching { r.start() }
                            .onSuccess { recorder = r }
                            .onFailure { e ->
                                android.widget.Toast.makeText(
                                    context,
                                    "Recording failed: ${e.message ?: e::class.simpleName}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                    } else {
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
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

/**
 * Chains `focusRequester` + `onFocusChanged` onto a modifier so the
 * inline-rename TextField can auto-focus on entry and auto-commit
 * on blur. Split out because the fluent chain was too noisy inline.
 */
private fun Modifier.headerRenameFocusChain(
    focusRequester: FocusRequester,
    onBlurCommit: () -> Unit,
): Modifier =
    this
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            if (!focusState.isFocused) onBlurCommit()
        }
