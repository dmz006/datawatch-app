package com.dmzs.datawatchclient.ui.sessions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.storage.observeForProfileAny
import com.dmzs.datawatchclient.ui.common.VoiceRecordingDialog
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    isNew: Boolean = false,
    openInStatusMode: Boolean = false,
    onNavigateToSettings: ((tab: String) -> Unit)? = null,
    /** Opens another session's detail (parent-session link on the Status tab). */
    onOpenSession: ((String) -> Unit)? = null,
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
    val contentReady by vm.contentReady.collectAsState()
    // Once the first pane_capture arrives the overlay should never come back,
    // even if pauseStream() resets contentReady to false during back-navigation.
    var hadContent by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(contentReady) { if (contentReady) hadContent = true }
    // Mark this session as foreground while the detail screen is
    // composed; NotificationPoster uses this to suppress redundant
    // wake notifications for the session the user is already viewing.
    androidx.compose.runtime.DisposableEffect(sessionId) {
        com.dmzs.datawatchclient.push.ForegroundSessionTracker.enter(sessionId)
        onDispose {
            com.dmzs.datawatchclient.push.ForegroundSessionTracker.leave(sessionId)
        }
    }
    // B58 + B60 — lifecycle-aware stream control.
    // ON_RESUME: refresh session state so stale cache doesn't confuse the user.
    //            Sprint 3 S3-2 (#62, #67): if WS is currently disconnected
    //            (reachable == false), also resume the stream so the reconnect
    //            flow in startStream fires and sends resize_term + clears dedup.
    // ON_STOP: pause the WS stream to avoid background reconnect storms
    //          when the screen is locked or the app is backgrounded (Tailscale
    //          may be disconnected; reconnect retries are wasteful and noisy).
    // ON_START: resume the stream so live events flow as soon as the user returns.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        vm.refreshFromServer()
                        // Sprint 3 S3-2: if disconnected, trigger a full reconnect so
                        // pane-capture dedup is cleared and resize_term is sent as the
                        // first outbound WS frame after the stream re-establishes.
                        if (vm.state.value.reachable == false) {
                            vm.resumeStream()
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.pauseStream()
                    androidx.lifecycle.Lifecycle.Event.ON_START -> vm.resumeStream()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val schedulesVm: com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel = viewModel()
    val sessionSchedulesVm: SessionSchedulesViewModel =
        viewModel(
            key = "session-schedules-$sessionId",
            factory = viewModelFactory { initializer { SessionSchedulesViewModel(sessionId) } },
        )
    val sessionSchedules by sessionSchedulesVm.state.collectAsState()
    // Loading overlay — shown for new sessions until the first content arrives
    // (pane_capture), OR for any session if it has no pane_capture yet and is
    // still running/waiting (reconnection case). Minimum 1s display on isNew,
    // 500ms on reconnect so the eye is always visible.
    var sessionLoaded by remember { mutableStateOf(!isNew) }
    var overlayMinWaitDone by remember { mutableStateOf(!isNew) }
    var overlayDataArrived by remember {
        mutableStateOf(
            state.events.any { it is com.dmzs.datawatchclient.domain.SessionEvent.PaneCapture },
        )
    }

    // Minimum display time depends on whether this is a new session or reconnect
    val minWaitMs = if (isNew) 2_000 else 500
    val maxWaitMs = if (isNew) 15_000 else 8_000

    // Minimum wait + safety-net dismiss
    androidx.compose.runtime.LaunchedEffect(isNew, sessionLoaded) {
        if (!sessionLoaded) {
            delay(minWaitMs.toLong())
            overlayMinWaitDone = true
            delay((maxWaitMs - minWaitMs).toLong())
            sessionLoaded = true
        }
    }

    // Watch for first pane_capture (means session is streaming data)
    androidx.compose.runtime.LaunchedEffect(state.events.size) {
        if (!overlayDataArrived &&
            state.events.any {
                it is com.dmzs.datawatchclient.domain.SessionEvent.PaneCapture
            }
        ) {
            overlayDataArrived = true
        }
    }

    // Dismiss once both the minimum time AND first data have arrived
    androidx.compose.runtime.LaunchedEffect(overlayMinWaitDone, overlayDataArrived) {
        if (overlayMinWaitDone && overlayDataArrived) sessionLoaded = true
    }

    // Also dismiss if session reaches terminal state
    androidx.compose.runtime.LaunchedEffect(state.session?.state) {
        if (!sessionLoaded) {
            val st = state.session?.state
            if (st == SessionState.Completed || st == SessionState.Killed || st == SessionState.Error) {
                sessionLoaded = true
            }
        }
    }

    var killConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var deleteMemoryStrategy by remember { mutableStateOf("keep") }
    var deleteArchiveRoles by remember { mutableStateOf("") }
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
    // statsMode is now a sub-tab inside Status (G6 — PWA alpha.36 gate).
    // statusMode = top-level Status tab active; statusSubStats = inner Stats sub-tab.
    var statusMode by remember { mutableStateOf(openInStatusMode) }
    var statusSubStats by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(chatMode) {
        modePrefs.edit().putBoolean("chat_mode", chatMode).apply()
    }

    val statusVm: SessionStatusViewModel =
        viewModel(
            factory = viewModelFactory { initializer { SessionStatusViewModel(sessionId) } },
            key = "session-status-$sessionId",
        )
    val statusState by statusVm.state.collectAsState()
    // PWA fetches /api/sessions/{id}/status on mount so the Status tab badge is
    // populated before the tab is opened; and replays a pending needs-input
    // popup (D41a: toast → alert dock) ~200 ms after entry.
    LaunchedEffect(sessionId) {
        statusVm.refreshStatus()
        delay(200)
        com.dmzs.datawatchclient.push.SessionStateWatcher.consumePendingNeedsInput(sessionId)?.let { prompt ->
            com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post("[$sessionId] needs input — ${prompt.take(80)}")
        }
    }
    var connBannerDismissed by remember(sessionId) { mutableStateOf(false) }
    var channelHelpOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val hookInstallToastStr = stringResource(R.string.status_hooks_installed_toast)
    val coroutineScope = rememberCoroutineScope()
    // One-time hook-install toast for claude-code sessions when daemon signals hooks installed
    LaunchedEffect(state.session?.backend, state.session?.id) {
        val backend = state.session?.backend?.lowercase() ?: return@LaunchedEffect
        if (backend == "claude-code" && state.session?.id != null) {
            val prefs = modePrefs
            val shown = prefs.getBoolean("hook_toast_$sessionId", false)
            if (!shown) {
                prefs.edit().putBoolean("hook_toast_$sessionId", true).apply()
                val project = state.session?.taskSummary?.take(30) ?: sessionId.take(8)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(hookInstallToastStr.replace("{path}", project))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    // Tap title to rename — same wire as Sessions-list overflow.
                    // fillMaxWidth() lets Compose apply the TopAppBar's width
                    // constraint so Ellipsis kicks in before the dot is pushed out.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp),
                    ) {
                        val headerTitle =
                            state.session?.name?.takeIf { it.isNotBlank() }
                                ?: state.session?.taskSummary
                                ?: sessionId
                        // BL-T3-2: inline BasicTextField lost focus immediately — WebView
                        // recaptured it via headerRenameFocusChain/onBlurCommit. Use modal
                        // RenameDialog instead (renameOpen state wired below).
                        Text(
                            headerTitle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { renameOpen = true },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                            // Parity D66a — agent ⬡ and "Chrome" header badges.
                            state.session?.agentId?.takeIf { it.isNotBlank() }?.let { agent ->
                                Spacer(modifier = Modifier.width(6.dp))
                                SessionHeaderBadge("⬡ ${agent.take(12)}")
                            }
                            if (state.session?.chrome == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                SessionHeaderBadge(stringResource(R.string.session_chrome))
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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_back))
                    }
                },
                // v0.33.22: connection dot moved from SessionInfoBar to
                // the right side of the TopAppBar, matching PWA's header
                // connection indicator. Everything else (state pill,
                // Stop, Timeline, chips) still lives in SessionInfoBar
                // below the tabs.
                actions = {
                    com.dmzs.datawatchclient.ui.common.DocsLinkAction(
                        com.dmzs.datawatchclient.docs.DocsLinks.forKey("view_session_detail"),
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                    val alertsVm: com.dmzs.datawatchclient.ui.alerts.AlertsViewModel =
                        viewModel()
                    val alertsState by alertsVm.state.collectAsState()
                    com.dmzs.datawatchclient.ui.common.AlertsBellAction(
                        alertsBadge = alertsState.watchedAlertCount,
                    )
                    val sessionsVm: SessionsViewModel = viewModel()
                    val watchedIds by sessionsVm.watchedIds.collectAsState()
                    val isWatched = sessionId in watchedIds
                    IconButton(onClick = { sessionsVm.toggleWatch(sessionId) }) {
                        Icon(
                            if (isWatched) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = stringResource(if (isWatched) R.string.session_watch_on else R.string.session_watch_off),
                            tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(14.dp))
                    Box(
                        modifier =
                            Modifier
                                .padding(end = 14.dp)
                                .size(10.dp)
                                .background(
                                    color =
                                        when (state.reachable) {
                                            true -> Color(0xFF10B981) // green-500
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
        // Split the layout: terminal/content in a scrollable area with
        // imePadding(), and the composer in a separate Box below that
        // responds to keyboard insets independently. This ensures the
        // composer always scrolls up above the keyboard, even when the
        // terminal content is large.
        // v0.35.9 — badges row moves ABOVE the tmux/channel tabs
        // (user direction 2026-04-28). PWA carries the chips at
        // the top of the session-info-bar; mobile aligning here
        // makes the most-used actions (Stop, Timeline, state
        // override) reachable without scrolling past the tabs.
        // The Last Response button stays here on the badge bar
        // — Description-glyph is the single canonical icon used
        // across SessionInfoBar + the quick-actions row below.
        var responseOpen by remember { mutableStateOf(false) }
        // Parity D43a: PWA always shows the Response button; the viewer
        // fetches the live response itself.
        val hasResponse = state.session != null
        val isCouncilVirtual =
            state.session?.backend == "council-virtual" ||
                state.session?.fullId?.startsWith("council-") == true
        val terminalController = rememberTerminalController()
        val toolbarState = rememberTerminalToolbarState(terminalController, sessionId)
        toolbarState.scrollCommand = { enter -> vm.scrollModeCommand(enter) }
        // Leaving the session in scroll mode would otherwise leave tmux in
        // copy-mode, so the pane looks frozen next time it's opened.
        androidx.compose.runtime.DisposableEffect(toolbarState) {
            onDispose { if (toolbarState.scrollMode) vm.exitScrollModeDetached() }
        }

        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            val tabRowBorderColor = LocalDatawatchColors.current.border

            // Under enableEdgeToEdge (set in MainActivity), the system does
            // NOT auto-resize the window for the IME or system bars. Compose
            // is the sole owner of inset handling via these modifiers:
            //   navigationBarsPadding — reserves nav bar area
            //   imePadding — reserves keyboard area when open
            // Single application here at the outermost layout level avoids
            // the double-counting that produced the keyboard-up black gap
            // before enableEdgeToEdge was enabled (builds 277-281).
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SessionInfoBar(
                        backend = state.session?.backend,
                        llmRef = state.session?.llmRef,
                        computeNodeRef = state.session?.computeNodeRef,
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
                        hasResponse = hasResponse,
                        onResponse = {
                            vm.refreshFromServer()
                            responseOpen = true
                        },
                        lastActivityAt = state.session?.lastActivityAt,
                    )

                    // Fixed tab row - stays below SessionInfoBar while content scrolls below
                    if (!isCouncilVirtual) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 8.dp)
                                    .drawBehind {
                                        drawLine(
                                            color = tabRowBorderColor,
                                            start = Offset(0f, size.height),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            val sessionBackend = state.session?.backend
                            val showChannelTab =
                                sessionBackend?.let {
                                    it == "claude" || it == "claude-code" || it == "opencode-acp"
                                } == true
                            SessionModeTab(
                                label =
                                    stringResource(
                                        R.string.session_detail_tab_tmux,
                                    ),
                                selected = !chatMode && !statusMode,
                                onClick = {
                                    chatMode = false
                                    statusMode = false
                                },
                            )
                            if (showChannelTab) {
                                SessionModeTab(
                                    label =
                                        stringResource(
                                            R.string.session_detail_tab_channel,
                                        ),
                                    selected = chatMode && !statusMode,
                                    onClick = {
                                        chatMode = true
                                        statusMode = false
                                    },
                                )
                            }
                            // PWA tabStatusBadge: hook-health dot (alive green / stale amber) + state symbol.
                            val hookDot =
                                when (statusState.board?.hookHealth) {
                                    "alive" -> LocalDatawatchColors.current.success
                                    "stale" -> LocalDatawatchColors.current.warning
                                    else -> null
                                }
                            SessionModeTab(
                                label = stringResource(R.string.session_detail_tab_status),
                                trailing =
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        if (hookDot != null) {
                                            append(" ")
                                            pushStyle(androidx.compose.ui.text.SpanStyle(color = hookDot, fontSize = 10.sp))
                                            append("●")
                                            pop()
                                        }
                                        if (statusState.board != null) append(" " + statusTabBadge(statusState.board))
                                    },
                                selected = statusMode,
                                onClick = {
                                    statusMode = true
                                    statusSubStats = false
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            // PWA `channelHelpBtn` — "?" only while the Channel tab is active.
                            if (showChannelTab && chatMode && !statusMode) {
                                TextButton(
                                    onClick = { channelHelpOpen = true },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Text(
                                        "?",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            val showToolbar = !chatMode && !statusMode && state.session?.isChatMode != true
                            if (showToolbar) {
                                TerminalToolbarControls(toolbarState)
                            }
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds(),
                ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (responseOpen) {
                        LastResponseSheet(
                            response = state.session?.lastResponse.orEmpty(),
                            onDismiss = { responseOpen = false },
                            fetchFresh = { vm.fetchFreshResponse() },
                            title = state.session?.let { it.name ?: it.id },
                        )
                    }
                    // Parity D45a — inline process-stats bar above the output.
                    if (state.session?.state == SessionState.Running || state.session?.state == SessionState.Waiting) {
                        ProcessStatsBar(fetch = { vm.fetchProcessEnvelope() })
                    }
                    // PWA conn-status-banner: channel/ACP sessions that are active
                    // but whose MCP channel / ACP server isn't connected yet.
                    val sess = state.session
                    val connMode =
                        when (sess?.backend) {
                            "opencode-acp" -> "acp"
                            "claude", "claude-code" -> "channel"
                            else -> null
                        }
                    val sessActive =
                        sess?.state == SessionState.Running || sess?.state == SessionState.Waiting ||
                            sess?.state == SessionState.RateLimited
                    // PWA state.channelReady[full_id]: also cleared by the WS
                    // `channel_ready` frame and the output-marker scan.
                    val readyIds by com.dmzs.datawatchclient.transport.ws.ChannelReadyHub.ready.collectAsState()
                    val hubReady = sess != null && (readyIds.contains(sess.fullId) || readyIds.contains(sess.id))
                    if (connMode != null && sessActive && sess?.channelReady != true && !hubReady && !connBannerDismissed) {
                        ConnStatusBanner(
                            modeLabel =
                                if (connMode == "channel") {
                                    stringResource(R.string.session_conn_mcp_channel)
                                } else {
                                    stringResource(R.string.session_conn_acp_server)
                                },
                            waitingInput = sess.state == SessionState.Waiting,
                            onDismiss = { connBannerDismissed = true },
                        )
                    }
                    state.infoBanner?.let { info ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    info,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
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
                                TextButton(
                                    onClick = vm::dismissBanner,
                                ) { Text(stringResource(R.string.action_dismiss)) }
                            }
                        }
                    }

                    // Server-reported chat-mode sessions (output_mode=chat, e.g.
                    // OpenWebUI / Ollama) emit structured `chat_message` WS frames
                    // instead of `pane_capture` — no terminal exists. When on, the
                    // transcript panel is the only sensible output surface; user's
                    // Terminal/Chat view toggle doesn't apply.
                    val serverChatMode = state.session?.isChatMode == true
                    // v0.74.0 S5-7 — Council virtual sessions show proposal + response transcript only
                    if (isCouncilVirtual) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                        ) {
                            item {
                                Text(
                                    stringResource(R.string.council_session_proposal_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    state.session?.lastPrompt ?: "",
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    state.session?.lastResponse
                                        ?: stringResource(R.string.council_session_in_progress),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        if (state.session?.lastResponse.isNullOrBlank()) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                            }
                        }
                    } else if (statusMode) {
                        // G6: Status top-level tab hosts Status | Stats sub-tab strip (PWA alpha.36)
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .drawBehind {
                                            drawLine(
                                                color = tabRowBorderColor,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx(),
                                            )
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SessionModeTab(
                                    label = stringResource(R.string.session_detail_status_subtab_status),
                                    selected = !statusSubStats,
                                    onClick = { statusSubStats = false },
                                )
                                SessionModeTab(
                                    label = stringResource(R.string.session_detail_status_subtab_stats),
                                    selected = statusSubStats,
                                    onClick = { statusSubStats = true },
                                )
                            }
                            if (statusSubStats) {
                                SessionStatsPanel(
                                    sessionId = sessionId,
                                    session = state.session,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    onNavigateToComputeTab = onNavigateToSettings?.let { cb -> { cb("compute") } },
                                    onNavigateToLlmTab = onNavigateToSettings?.let { cb -> { cb("llm") } },
                                )
                            } else {
                                SessionStatusPanel(
                                    sessionId = sessionId,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    vm = statusVm,
                                    onOpenSession = { pid -> onOpenSession?.invoke(pid.substringAfterLast('-')) },
                                )
                            }
                        }
                    } else if (serverChatMode) {
                        ChatTranscriptPanel(
                            sessionId = sessionId,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onQuickCmd = vm::onReplyTextChange,
                        )
                    } else if (chatMode) {
                        // User's view-mode toggle (Terminal vs Chat-style bubbles
                        // for any non-chat session). Renders the event stream as
                        // bubbles but is different from server-side chat mode.
                        // BL-T3-3: Filter PaneCapture + ChatMessage before passing —
                        // ChatBubbleRow returns early for both, producing zero-height
                        // items that make the list appear blank when all recent events
                        // are pane snapshots (typical for active tmux sessions).
                        ChatEventList(
                            events =
                                state.events.filter {
                                    it !is SessionEvent.PaneCapture && it !is SessionEvent.ChatMessage
                                },
                            onQuickReply = vm::sendQuickReply,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else if (state.session?.outputMode == "log") {
                        // PWA log viewer (output_mode=log, ACP/headless sessions).
                        LogModeView(
                            events = state.events,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    } else {
                        // v0.42.0 — controller + toolbar state are hoisted
                        // above the tabs row so the font / scroll buttons
                        // render inline next to the tmux/channel pills.
                        // Scroll-mode nav strip (PgUp / PgDn / ↑ / ↓ / ESC)
                        // appears directly under the terminal viewport so
                        // the keys land where the user is reading.
                        // TerminalView must take a direct weight so its inner
                        // AndroidView/WebView gets a finite Compose height to
                        // bind MATCH_PARENT against. The extra wrapper Column
                        // that lived here (commits 0dac78b → 4b4c371) gave the
                        // wrapper the weight but left TerminalView unbounded,
                        // collapsing the WebView and producing "text up off the
                        // top of the screen" + broken IME resize.
                        TerminalView(
                            sessionId = sessionId,
                            events = state.events,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            controller = terminalController,
                        )
                        TerminalSearchBar(toolbarState)
                        TerminalScrollModeStrip(toolbarState)
                        // Minimum cols from the session's server-resolved console
                        // size — PWA initXterm(configCols = sess.console_cols,
                        // configRows = sess.console_rows); minCols = configCols || 80.
                        // Falls back to the server's per-backend default (claude-code
                        // 120×40) when the server doesn't report it. Without this,
                        // claude's TUI wraps on phone widths.
                        // Sprint 3 S3-2 (#65): resolved cols/rows are also written to
                        // vm.terminalCols/terminalRows so the reconnect handler can send
                        // resize_term with current dimensions as the first outbound WS frame.
                        val termSession = state.session
                        androidx.compose.runtime.LaunchedEffect(
                            termSession?.backend,
                            termSession?.consoleCols,
                            termSession?.consoleRows,
                        ) {
                            val resolvedCols =
                                termSession?.terminalMinCols
                                    ?: com.dmzs.datawatchclient.domain.Session.defaultConsoleSize(null).first
                            val resolvedRows =
                                termSession?.terminalRows
                                    ?: com.dmzs.datawatchclient.domain.Session.defaultConsoleSize(null).second
                            // Enforce MIN COLS only (TUIs like Claude Code need 120 cols
                            // for their layout). Rows are NOT enforced as a minimum on
                            // mobile — when the keyboard opens, the WebView area can
                            // shrink to far fewer rows than 40, and forcing xterm to
                            // 40 rows would render content TALLER than the viewport,
                            // clipping the live tail (the bottom rows) off-screen and
                            // making it impossible to see the cursor while typing.
                            // (The PWA likewise only enforces cols after fit().)
                            // Pass 0 for rows so dwSetMinCols treats it as "no minimum".
                            terminalController.setMinSize(resolvedCols, 0)
                            // VM still tracks the configured row count for the
                            // reconnect handler's initial resize_term frame.
                            vm.terminalCols = resolvedCols
                            vm.terminalRows = resolvedRows
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
                }
                // Connecting overlay — datawatch splash covers the black terminal until both
                // the WS connects (reachable != null) AND the first pane_capture arrives.
                // Stays up through the full "WS handshake → resize_term → first frame" sequence.
                // PWA connect watchdog (startTermConnectWatchdog): every 5 s
                // without a first frame, re-send subscribe on the open socket
                // (never reconnect — that aborted slow handshakes), up to 3 times; then show
                // "Unable to connect…" with Retry / Use without terminal.
                var watchdogEpoch by remember { mutableStateOf(0) }
                var watchdogAttempt by remember { mutableStateOf(0) }
                var watchdogFailed by remember { mutableStateOf(false) }
                var withoutTerminal by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(watchdogEpoch, hadContent) {
                    if (hadContent) return@LaunchedEffect
                    watchdogAttempt = 0
                    watchdogFailed = false
                    while (!hadContent) {
                        kotlinx.coroutines.delay(TERM_CONNECT_TIMEOUT_MS)
                        if (hadContent) break
                        if (watchdogAttempt >= TERM_CONNECT_MAX_RETRIES) {
                            watchdogFailed = true
                            break
                        }
                        watchdogAttempt++
                        vm.resubscribe()
                    }
                }
                val reconnectingLabel = stringResource(R.string.term_reconnecting_attempt, watchdogAttempt, TERM_CONNECT_MAX_RETRIES)
                val connectStatus = when {
                    watchdogAttempt > 0 -> reconnectingLabel
                    state.reachable == null -> "connecting…"
                    !contentReady -> "waiting for terminal…"
                    else -> "connecting…"
                }
                // Parity D46b — no in-session disconnect banner after this
                // overlay; the header reachability dot is the only disconnect
                // signal (PWA minimal).
                SessionLoadingOverlay(
                    visible = !hadContent && !withoutTerminal && !watchdogFailed &&
                        (state.reachable == null || !contentReady),
                    statusText = connectStatus,
                )
                if (watchdogFailed && !hadContent && !withoutTerminal) {
                    TermConnectFailedPanel(
                        onRetry = {
                            vm.restartStream()
                            watchdogEpoch++
                        },
                        onUseWithout = { withoutTerminal = true },
                    )
                }
                } // close Box(weight(1f))

                // Composer in its own layer responding to keyboard insets separately.
                // In scroll mode the big PgUp/PgDn overlay replaces the composer.
                if (!toolbarState.scrollMode) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    ) {
                        Column {
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
                                // Last Response button moved to SessionInfoBar (header)
                                // 2026-05-25 per user request — no longer duplicated
                                // in the composer toolbar.
                                onResponse = {},
                                hasResponse = false,
                                fetchSavedCommands = { vm.fetchSavedCommands() },
                                onGuardrail = vm::runNamedGuardrail,
                                whisperConfigured = state.whisperConfigured,
                                // PWA `▶ ch`: Channel tab active + not waiting → send via MCP channel.
                                channelSend =
                                    chatMode && !statusMode && state.session?.state != SessionState.Waiting &&
                                        state.session?.backend.let { it == "claude" || it == "claude-code" || it == "opencode-acp" },
                                onSendChannel = vm::sendViaChannel,
                                connReady = state.reachable == true,
                                channelMode = chatMode && !statusMode,
                            )
                        }
                    }
                }
            }
        }
    }

    if (killConfirm) {
        AlertDialog(
            onDismissRequest = { killConfirm = false },
            title = { Text(stringResource(R.string.session_detail_kill_title)) },
            text = {
                Text(stringResource(R.string.session_detail_kill_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    killConfirm = false
                    vm.kill()
                }) {
                    Text(
                        stringResource(R.string.action_stop),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { killConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.session_detail_delete_title)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.session_detail_delete_body))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.prd_delete_memory_strategy),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf("keep", "purge", "archive").forEach { strategy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { deleteMemoryStrategy = strategy },
                        ) {
                            RadioButton(
                                selected = deleteMemoryStrategy == strategy,
                                onClick = { deleteMemoryStrategy = strategy },
                            )
                            Text(
                                when (strategy) {
                                    "keep" -> stringResource(R.string.prd_delete_memory_keep)
                                    "purge" -> stringResource(R.string.prd_delete_memory_purge)
                                    else -> stringResource(R.string.prd_delete_memory_archive)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (deleteMemoryStrategy == "archive") {
                        OutlinedTextField(
                            value = deleteArchiveRoles,
                            onValueChange = { deleteArchiveRoles = it },
                            label = { Text(stringResource(R.string.prd_delete_memory_archive_roles)) },
                            placeholder = { Text(stringResource(R.string.prd_delete_memory_archive_roles_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            maxLines = 2,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val strategy = deleteMemoryStrategy.takeIf { it != "keep" }
                    val roles = if (deleteMemoryStrategy == "archive")
                        deleteArchiveRoles.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else emptyList()
                    val scope = if (deleteMemoryStrategy == "archive") "project-shared" else null
                    deleteConfirm = false
                    deleteMemoryStrategy = "keep"
                    deleteArchiveRoles = ""
                    vm.delete(onDeleted = onBack, memoryStrategy = strategy, archiveRoleFilter = roles, archiveToScope = scope)
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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

    if (channelHelpOpen) {
        ChannelHelpDialog(onDismiss = { channelHelpOpen = false })
    }

    if (renameOpen) {
        val initial =
            state.session?.name?.takeIf { it.isNotBlank() }
                ?: state.session?.taskSummary
                ?: sessionId
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
            title = stringResource(R.string.session_detail_schedule_reply),
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
    // Loading overlay — rendered on top of the Scaffold; fades out when
    // the first pane_capture arrives (sessionLoaded = true).
    val newSessionStatus = when {
        state.reachable == null -> "connecting…"
        !contentReady -> "waiting for terminal…"
        else -> "connecting…"
    }
    SessionLoadingOverlay(visible = !sessionLoaded, statusText = newSessionStatus)
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
    llmRef: String? = null,
    computeNodeRef: String? = null,
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
    lastActivityAt: Instant? = null,
) {
    val isActive =
        state == SessionState.Running || state == SessionState.Waiting ||
            state == SessionState.RateLimited
    val isDone =
        state == SessionState.Completed || state == SessionState.Killed ||
            state == SessionState.Error

    // Pulse the Running badge — PWA dw-running-pulse (0.55-1.0, 700 ms
    // ease-in-out, alternate; static under reduced motion). Parity D18: kept.
    // Waiting / RateLimited are static — they already have distinct colour cues.
    val runBadgeAlpha by com.dmzs.datawatchclient.ui.theme.rememberRunningPulseAlpha(state == SessionState.Running)

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            // v0.33.22 — dot moved to TopAppBar right side. SessionInfoBar
            // now starts straight with the backend chip like PWA.
            if (!backend.isNullOrBlank()) {
                InfoBadge(text = backend.lowercase(), color = MaterialTheme.colorScheme.primary)
            }
            // v0.83.0: LLMRef (green ⚡) and ComputeNodeRef (purple ⚙) badges
            if (!llmRef.isNullOrBlank()) {
                InfoBadge(text = "⚡ $llmRef", color = Color(0xFF10B981))
            }
            if (!computeNodeRef.isNullOrBlank()) {
                InfoBadge(text = "⚙ $computeNodeRef", color = com.dmzs.datawatchclient.ui.theme.DwAccent) // PWA var(--accent), D6b
            }
            // Parity D17a — PWA rule: the mode badge shows only for plain
            // tmux sessions (channel/acp/chat modes are conveyed by the tab
            // strip, app.js v5.23.0).
            if (sessionMode.lowercase() == "tmux") {
                InfoBadge(text = sessionMode.lowercase(), color = MaterialTheme.colorScheme.secondary)
            }
            state?.let {
                Box {
                    val effectiveAlpha = if (it == SessionState.Running) runBadgeAlpha else 1f
                    Box(
                        modifier =
                            Modifier
                                .clickable(onClick = onStateClick)
                                .background(
                                    color = stateBgColor(it).copy(alpha = stateBgColor(it).alpha * effectiveAlpha),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stateLabel(it),
                            fontSize = 10.sp,
                            color = stateFgColor(it).copy(alpha = effectiveAlpha),
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
                        PWA_OVERRIDE_STATES.forEach { target ->
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
            // Last-activity dot — green/amber/red + relative time, shown for headless sessions.
            if (lastActivityAt != null) {
                var nowMs by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
                LaunchedEffect(Unit) { while (true) { delay(1_000L); nowMs = Clock.System.now().toEpochMilliseconds() } }
                val ageSeconds = ((nowMs - lastActivityAt.toEpochMilliseconds()) / 1000L).coerceAtLeast(0L)
                val dotColor = when {
                    ageSeconds < 30 -> Color(0xFF22C55E)
                    ageSeconds < 300 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                val ageText = if (ageSeconds < 60) "${ageSeconds}s ago" else "${ageSeconds / 60}m ago"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(modifier = Modifier.size(6.dp).background(dotColor, RoundedCornerShape(3.dp)))
                    Text(ageText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Stop / Restart / Delete are LEFT-aligned next to the state
            // pill (user request 2026-05-25). Spacer pushes the Timeline +
            // Response cluster to the RIGHT side.
            if (isActive) {
                TextButton(
                    onClick = onStop,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        "■ Stop",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (isDone) {
                TextButton(
                    onClick = onRestart,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                ) {
                    Text("↻ Restart", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        "🗑 Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            // Right-aligned cluster: Timeline FIRST, then Last Response.
            // User request 2026-05-25: Last Response uses the same
            // `Description` icon as it did in the composer toolbar — the
            // single canonical glyph for "view last response".
            TextButton(
                onClick = onTimeline,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text("🕐", style = MaterialTheme.typography.labelSmall)
            }
            if (hasResponse) {
                IconButton(
                    onClick = onResponse,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = "View last response",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBadge(
    text: String,
    color: Color,
) {
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
    val latestRateLimit =
        events.asReversed().firstOrNull { it is SessionEvent.RateLimited }
            as? SessionEvent.RateLimited ?: return
    var rateDismissed by remember(latestRateLimit.ts) { mutableStateOf(false) }
    if (rateDismissed) return
    val yellow = androidx.compose.ui.graphics.Color(0xFFFEF3C7)
    val yellowText = androidx.compose.ui.graphics.Color(0xFF92400E)
    Surface(color = yellow, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rate-limited" + (latestRateLimit.retryAfter?.let { ts -> " · retry at $ts" } ?: ""),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = yellowText,
            )
            IconButton(onClick = { rateDismissed = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.action_dismiss),
                    modifier = Modifier.size(16.dp),
                    tint = yellowText,
                )
            }
        }
    }
}

@Composable
private fun StatePill(
    state: SessionState,
    onClick: () -> Unit = {},
) {
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}


/**
 * Amber strip right above the terminal when the session is
 * `waiting_input`. Body shows the latest prompt text (live event
 * preferred, falling back to `Session.lastPrompt`) so the user can
 * decide-then-reply without scrolling backlog.
 */

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
            com.dmzs.datawatchclient.di.ServiceLocator.profilesWithProxied().first()
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
            Text(
                stringResource(R.string.session_detail_timeline),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // PWA toggleTimeline copy: "Loading timeline…" → rows, or
            // "No timeline events recorded yet." / "Failed to load timeline.".
            // Locally cached events fill in when the server feed is empty/unavailable.
            val usingServer = serverLines != null && serverLines!!.isNotEmpty()
            val loading = serverLines == null && !fetchFailed
            when {
                usingServer ->
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        // key = index: server may return duplicate lines (same event
                        // logged twice), so using content as key would crash with
                        // "Key already used" IllegalArgumentException.
                        itemsIndexed(serverLines!!) { idx, line -> TimelineServerRow(line) }
                    }
                loading ->
                    com.dmzs.datawatchclient.ui.common.DatawatchLoadingContent(
                        label = stringResource(R.string.timeline_loading),
                        eyeSize = 32.dp,
                        verticalPadding = 12.dp,
                    )
                else -> {
                    if (fetchFailed) {
                        Text(
                            stringResource(R.string.timeline_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (localItems.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            // key includes index to guard against duplicate ts+hashCode.
                            itemsIndexed(localItems) { idx, ev ->
                                TimelineRow(ev)
                                HorizontalDivider()
                            }
                        }
                    } else if (!fetchFailed) {
                        Text(
                            stringResource(R.string.timeline_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                val onInputLabel = stringResource(R.string.session_detail_on_input)
                val when_ =
                    sc.runAt?.toString()?.substringBefore('.')?.replace('T', ' ')
                        ?: sc.cron
                        ?: onInputLabel
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
                        contentDescription = stringResource(R.string.session_detail_cancel_schedule),
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
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            last == null || last >= events.size - 1
        }
    }
    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && isAtBottom) listState.animateScrollToItem(events.size - 1)
    }
    if (events.isEmpty()) {
        // PWA `.chat-empty`: 💬 + chat_empty_hint + chat_memory_hint.
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("💬", fontSize = 36.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Text(
                stringResource(R.string.chat_empty_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.chat_memory_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
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
        QuickReplyChip(stringResource(R.string.session_detail_quick_reply_yes), onClick = { onQuickReply("yes\r") })
        QuickReplyChip(stringResource(R.string.session_detail_quick_reply_no), onClick = { onQuickReply("no\r") })
        QuickReplyChip("Stop", onClick = { onQuickReply("stop\r") })
    }
}

@Composable
private fun QuickReplyChip(
    label: String,
    onClick: () -> Unit,
) {
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
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            last == null || last >= events.size - 1
        }
    }
    LaunchedEffect(events.size) {
        if (events.isNotEmpty() && isAtBottom) listState.animateScrollToItem(events.size - 1)
    }
    if (events.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.session_detail_no_messages),
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onResponse: () -> Unit = {},
    hasResponse: Boolean = false,
    fetchSavedCommands: suspend () -> List<Pair<String, String>> = { emptyList() },
    onGuardrail: (String) -> Unit = {},
    whisperConfigured: Boolean = false,
    channelSend: Boolean = false,
    onSendChannel: () -> Unit = {},
    connReady: Boolean = true,
    channelMode: Boolean = false,
) {
    HorizontalDivider()
    // Parity D21b — PWA keys strip: "Commands…" dropdown + inline custom input.
    var customCmdOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recorder by remember { mutableStateOf<com.dmzs.datawatchclient.voice.VoiceRecorder?>(null) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    val recording = recorder != null

    // Image attachment state (issue #158 — PWA v8.19.0 parity).
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var pendingImageName by remember { mutableStateOf<String?>(null) }
    var imageUploading by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun uploadUri(
        uri: android.net.Uri,
        fallbackName: String,
        fallbackMime: String,
    ) {
        imageUploading = true
        scope.launch {
            val bytes =
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.readBytes()
                    }
                }.getOrNull()
            if (bytes == null) {
                imageUploading = false
                AlertDockChannel.post(
                    "Could not read image.",
                    DockLevel.Error,
                )
                return@launch
            }
            val displayName =
                runCatching {
                    val cursor =
                        context.contentResolver.query(
                            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
                        )
                    cursor?.use {
                        it.moveToFirst()
                        it.getString(0)
                    }
                }.getOrNull() ?: fallbackName
            val mimeType = context.contentResolver.getType(uri)?.takeIf { it != "image/*" } ?: fallbackMime
            val timestamp = System.currentTimeMillis()
            val safeDisplayName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destName = "dw_attach_${timestamp}_$safeDisplayName"

            val sessionRow =
                com.dmzs.datawatchclient.di.ServiceLocator
                    .sessionRepository.observeForProfileAny(sessionId).first()
            val profiles =
                com.dmzs.datawatchclient.di.ServiceLocator
                    .profilesWithProxied().first()
            val profile =
                sessionRow?.serverProfileId
                    ?.let { pid -> profiles.firstOrNull { it.id == pid && it.enabled } }
                    ?: profiles.firstOrNull { it.enabled }
            if (profile == null) {
                imageUploading = false
                AlertDockChannel.post(
                    "No server connected.",
                    DockLevel.Error,
                )
                return@launch
            }
            val transport = com.dmzs.datawatchclient.di.ServiceLocator.transportFor(profile)
            val root = transport.getFileServiceMeta().getOrNull()?.root?.trimEnd('/')
            if (root == null) {
                imageUploading = false
                AlertDockChannel.post(
                    "Could not resolve server file root.",
                    DockLevel.Error,
                )
                return@launch
            }
            val fullPath = "$root/$destName"
            transport.uploadImageAttachment(bytes, destName, mimeType, fullPath)
                .onSuccess { serverPath ->
                    pendingImagePath = serverPath
                    pendingImageName = displayName
                    imageUploading = false
                }
                .onFailure {
                    imageUploading = false
                    AlertDockChannel.post(
                        "Image upload failed: ${it.message}",
                        DockLevel.Error,
                    )
                }
        }
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            uploadUri(uri, "image.jpg", "image/jpeg")
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) cameraUri?.let { uploadUri(it, "camera_${System.currentTimeMillis()}.jpg", "image/jpeg") }
        }

    // Clean up the uploaded image when the composer leaves composition (session switch, back nav).
    DisposableEffect(sessionId) {
        onDispose {
            val path = pendingImagePath ?: return@onDispose
            scope.launch {
                val profiles = com.dmzs.datawatchclient.di.ServiceLocator.profilesWithProxied().first()
                val sessionRow =
                    com.dmzs.datawatchclient.di.ServiceLocator
                        .sessionRepository.observeForProfileAny(sessionId).first()
                val profile =
                    sessionRow?.serverProfileId
                        ?.let { pid -> profiles.firstOrNull { it.id == pid && it.enabled } }
                        ?: profiles.firstOrNull { it.enabled } ?: return@launch
                com.dmzs.datawatchclient.di.ServiceLocator.transportFor(profile)
                    .deleteFile(path)
            }
        }
    }

    // RECORD_AUDIO is a runtime permission on Android 6+. Request at first
    // tap — on grant start recording and show the PWA-style recording dialog;
    // on denial surface a toast so the button never silently no-ops.
    val micPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                runCatching { r.start() }
                    .onSuccess {
                        recorder = r
                        showRecordingDialog = true
                    }
                    .onFailure { e ->
                        AlertDockChannel.post(
                            "Recording failed: ${e.message ?: e::class.simpleName}",
                            DockLevel.Error,
                        )
                    }
            } else {
                AlertDockChannel.post(
                    "Microphone permission denied — enable it in Settings.",
                    DockLevel.Error,
                )
            }
        }

    // PWA-style recording dialog: shown while mic is active.
    if (showRecordingDialog) {
        VoiceRecordingDialog(
                level = { recorder?.level() ?: 0f },
            onCancel = {
                recorder?.cancel()
                recorder = null
                showRecordingDialog = false
            },
            onSend = {
                val r =
                    recorder ?: run {
                        showRecordingDialog = false
                        return@VoiceRecordingDialog
                    }
                recorder = null
                showRecordingDialog = false
                val captured = r.stop() ?: return@VoiceRecordingDialog
                transcribing = true
                scope.launch {
                    val sessionRow =
                        com.dmzs.datawatchclient.di.ServiceLocator
                            .sessionRepository
                            .observeForProfileAny(sessionId)
                            .first()
                    val profiles =
                        com.dmzs.datawatchclient.di.ServiceLocator
                            .profilesWithProxied().first()
                    val profile =
                        sessionRow?.serverProfileId
                            ?.let { pid -> profiles.firstOrNull { it.id == pid && it.enabled } }
                            ?: run {
                                val activeId =
                                    com.dmzs.datawatchclient.di.ServiceLocator
                                        .activeServerStore.get()
                                profiles.firstOrNull { it.id == activeId && it.enabled }
                                    ?: profiles.firstOrNull { it.enabled }
                            }
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
                                    val newPrefix =
                                        Regex("^new[:\\s]+(.+)", RegexOption.IGNORE_CASE)
                                            .matchEntire(text)?.groupValues?.get(1)?.trim()
                                    if (!newPrefix.isNullOrEmpty()) {
                                        com.dmzs.datawatchclient.di.ServiceLocator
                                            .transportFor(profile)
                                            .startSession(task = newPrefix)
                                            .fold(
                                                onSuccess = {
                                                    AlertDockChannel.post(
                                                        "Started new session: $newPrefix",
                                                        DockLevel.Success,
                                                    )
                                                },
                                                onFailure = { onTranscribed(text) },
                                            )
                                    } else {
                                        onTranscribed(text)
                                    }
                                },
                                onFailure = { err ->
                                    val cause =
                                        generateSequence(err as Throwable?) { it.cause }
                                            .take(3)
                                            .joinToString(" ← ") {
                                                "${it::class.simpleName}: ${it.message?.take(120)}"
                                            }
                                    AlertDockChannel.post(
                                        "Transcribe failed: $cause",
                                        DockLevel.Error,
                                    )
                                },
                            )
                    } else {
                        AlertDockChannel.post(
                            "No enabled server profile — voice reply aborted.",
                            DockLevel.Error,
                        )
                    }
                    transcribing = false
                }
            },
        )
    }

    // v0.42.10 — quick-reply chip row removed (user direction
    // 2026-04-29). Saved Commands sheet (⌨ button below) already
    // exposes yes / no / continue / skip / quit alongside saved
    // and custom commands; the redundant chip row was eating ~40 dp
    // of vertical space the terminal viewport could use instead.

    // Quick-actions row above the composer: Commands… dropdown + ESC +
    // arrow keys + Enter (PWA savedCmdsQuick).
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp),
    ) {
        SavedCommandsDropdown(
            enabled = !sending,
            fetchSavedCommands = fetchSavedCommands,
            onSend = onQuickReply,
            onCustom = { customCmdOpen = true },
            onGuardrail = onGuardrail,
        )
        Spacer(modifier = Modifier.weight(1f))
        // ESC — matches PWA savedCmdsQuick ␛ button
        TextButton(
            onClick = { onQuickReply("\u001B") },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("␛", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        // PWA arrow order: ↑ ↓ ← →
        RepeatArrowButton(
            onFire = { onQuickReply("\u001B[A") },
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.session_detail_up_arrow),
        )
        RepeatArrowButton(
            onFire = { onQuickReply("\u001B[B") },
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.session_detail_down_arrow),
        )
        RepeatArrowButton(
            onFire = { onQuickReply("\u001B[D") },
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.session_detail_left_arrow),
        )
        RepeatArrowButton(
            onFire = { onQuickReply("\u001B[C") },
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.session_detail_right_arrow),
        )
        // Enter — matches PWA savedCmdsQuick ⏎ button
        TextButton(
            onClick = { onQuickReply("\r") },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("⏎", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    if (customCmdOpen) {
        CustomCommandRow(
            onSend = { cmd ->
                onQuickReply(cmd + "\r")
                customCmdOpen = false
            },
            onCancel = { customCmdOpen = false },
        )
    }

    // Pending image chip — shown when an image is queued for attachment.
    if (pendingImagePath != null || imageUploading) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (imageUploading) "Uploading…" else "✓ ${pendingImageName ?: "image"}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                trailingIcon =
                    if (!imageUploading && pendingImagePath != null) {
                        {
                            IconButton(
                                onClick = {
                                    val path = pendingImagePath
                                    pendingImagePath = null
                                    pendingImageName = null
                                    if (path != null) {
                                        scope.launch {
                                            val profiles = com.dmzs.datawatchclient.di.ServiceLocator.profilesWithProxied().first()
                                            val sessionRow =
                                                com.dmzs.datawatchclient.di.ServiceLocator
                                                    .sessionRepository.observeForProfileAny(sessionId).first()
                                            val profile =
                                                sessionRow?.serverProfileId
                                                    ?.let { pid -> profiles.firstOrNull { it.id == pid && it.enabled } }
                                                    ?: profiles.firstOrNull { it.enabled } ?: return@launch
                                            com.dmzs.datawatchclient.di.ServiceLocator.transportFor(profile)
                                                .deleteFile(path)
                                        }
                                    }
                                },
                                modifier = Modifier.size(16.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                modifier = Modifier.height(28.dp),
            )
        }
    }

    if (transcribing) {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Transcribing voice message…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    // PWA input-bar placeholder (app.js:2988).
                    when {
                        transcribing -> "Transcribing…"
                        !connReady -> stringResource(R.string.input_ph_waiting)
                        waitingInput -> stringResource(R.string.input_ph_response)
                        channelMode -> stringResource(R.string.input_ph_message)
                        else -> stringResource(R.string.input_ph_command)
                    },
                )
            },
            modifier = Modifier.weight(1f),
            singleLine = false,
            maxLines = 3,
            enabled = !sending && !recording && !transcribing,
            textStyle =
                LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
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
                // Append pending image reference before sending (issue #158).
                val imgPath = pendingImagePath
                if (imgPath != null) {
                    onTextChange(text.trimEnd() + "\n[image:$imgPath]")
                    pendingImagePath = null
                    pendingImageName = null
                }
                if (channelSend) onSendChannel() else onSend()
            },
            enabled = !sending && (text.isNotBlank() || pendingImagePath != null),
            modifier = Modifier.size(36.dp),
        ) {
            if (sending) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(6.dp))
            } else if (channelSend) {
                Text(
                    "▶ ch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (text.isNotBlank() || pendingImagePath != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        },
                )
            } else {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = stringResource(R.string.action_send),
                    modifier = Modifier.size(18.dp),
                    tint =
                        if (text.isNotBlank() || pendingImagePath != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        },
                )
            }
        }
        IconButton(onClick = onSchedule, enabled = !sending, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = stringResource(R.string.session_detail_schedule_action),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (whisperConfigured) {
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = {
                    val granted =
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val r = com.dmzs.datawatchclient.voice.VoiceRecorder(context)
                        runCatching { r.start() }
                            .onSuccess {
                                recorder = r
                                showRecordingDialog = true
                            }
                            .onFailure { e ->
                                AlertDockChannel.post(
                                    "Recording failed: ${e.message ?: e::class.simpleName}",
                                    DockLevel.Error,
                                )
                            }
                    } else {
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !sending && !transcribing,
            ) {
                if (transcribing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                } else {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // Image attachment button (issue #158 — PWA v8.19.0 parity).
        IconButton(
            onClick = { showImageSourceSheet = true },
            enabled = !sending && !imageUploading && pendingImagePath == null,
            modifier = Modifier.size(40.dp),
        ) {
            if (imageUploading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
            } else {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = "Attach image",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showImageSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImageSourceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Choose from gallery") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            showImageSourceSheet = false
                            galleryLauncher.launch("image/*")
                        },
                )
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Take a photo") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            showImageSourceSheet = false
                            val file =
                                java.io.File(
                                    context.cacheDir,
                                    "dw_camera_${System.currentTimeMillis()}.jpg",
                                )
                            val uri =
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
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
        title = { Text(stringResource(R.string.session_detail_override_state)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PWA_OVERRIDE_STATES.forEach { s ->
                    TextButton(
                        onClick = { onPick(s) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.name.lowercase(), modifier = Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * PWA-style mode tab for the tmux / channel toggle.
 *
 * Selected tab: surface background with 3-sided border (top + sides, no
 * bottom) so it visually "connects" to the content below — the parent
 * Row draws a full-width bottom border, which the selected tab's surface
 * fill covers in that region, creating the classic browser-tab join.
 * Unselected tabs are transparent with muted text.
 */
@Composable
private fun SessionModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: androidx.compose.ui.text.AnnotatedString? = null,
) {
    val dw = LocalDatawatchColors.current
    val surfaceBg = MaterialTheme.colorScheme.surface
    val borderColor = dw.border
    val textColor = if (selected) dw.accent2 else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .then(
                    if (selected) {
                        Modifier.drawBehind {
                            val stroke = 1.dp.toPx()
                            drawRect(surfaceBg)
                            drawLine(borderColor, Offset(stroke / 2f, 0f), Offset(stroke / 2f, size.height), stroke)
                            drawLine(
                                borderColor,
                                Offset(size.width - stroke / 2f, 0f),
                                Offset(size.width - stroke / 2f, size.height),
                                stroke,
                            )
                            drawLine(borderColor, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            if (trailing == null) {
                androidx.compose.ui.text.AnnotatedString(label)
            } else {
                androidx.compose.ui.text.AnnotatedString(label) + trailing
            },
            fontSize = 12.sp,
            color = textColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * Parity D21b — PWA `savedCmdsQuick` `<select>`: System commands, the
 * user's saved commands (server-seeded ones hidden), a Guardrails group
 * (`▶ sast-scan` …, runs that guardrail on the session) and "Custom…"
 * (opens [CustomCommandRow]). Sets come from [QuickCommandSets].
 */
@Composable
private fun SavedCommandsDropdown(
    enabled: Boolean,
    fetchSavedCommands: suspend () -> List<Pair<String, String>>,
    onSend: (String) -> Unit,
    onCustom: () -> Unit,
    onGuardrail: (String) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(open) {
        if (open) saved = fetchSavedCommands()
    }
    // Web UI detail System set (QuickCommandSets.DETAIL_SYSTEM), mapped to what the
    // app's send path expects: text + CR, or a control byte it turns into sendkey.
    val system =
        com.dmzs.datawatchclient.transport.QuickCommandSets.DETAIL_SYSTEM.map { c ->
            c.label to
                when (c.value) {
                    com.dmzs.datawatchclient.transport.QuickCommandSets.ESC -> "\u001B"
                    com.dmzs.datawatchclient.transport.QuickCommandSets.CTRL_B -> "\u0002"
                    "\n" -> "\r"
                    "\u0003" -> "\u0003"
                    else -> c.value + "\r"
                }
        }
    Box {
        androidx.compose.material3.OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            modifier = Modifier.height(30.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                stringResource(R.string.session_detail_commands_select),
                style = MaterialTheme.typography.labelSmall,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownGroupLabel(stringResource(R.string.sessions_cmd_system))
            system.forEach { (label, value) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        open = false
                        onSend(value)
                    },
                )
            }
            if (saved.isNotEmpty()) {
                DropdownGroupLabel(stringResource(R.string.sessions_cmd_saved))
                saved.forEach { (name, cmd) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name.ifBlank { cmd }, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        onClick = {
                            open = false
                            onSend(cmd + "\r")
                        },
                    )
                }
            }
            DropdownGroupLabel(stringResource(R.string.session_detail_commands_guardrails))
            com.dmzs.datawatchclient.transport.QuickCommandSets.GUARDRAILS.forEach { g ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("▶ $g", style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        open = false
                        onGuardrail(g)
                    },
                )
            }
            HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.session_detail_commands_custom),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                onClick = {
                    open = false
                    onCustom()
                },
            )
        }
    }
}

@Composable
private fun DropdownGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** PWA `customCmdWrap`: inline text field + ➤ send + ✕ cancel. */
@Composable
private fun CustomCommandRow(
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.session_detail_commands_custom_ph)) },
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                ),
            keyboardActions =
                androidx.compose.foundation.text.KeyboardActions(
                    onSend = { if (text.isNotBlank()) onSend(text) },
                ),
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        IconButton(onClick = { if (text.isNotBlank()) onSend(text) }, enabled = text.isNotBlank()) {
            Text("➤", color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
        }
    }
}

/**
 * PWA `startArrowRepeat`: fire once on press, then after 250 ms repeat every
 * 80 ms until release.
 */
@Composable
private fun RepeatArrowButton(
    onFire: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    val latest by androidx.compose.runtime.rememberUpdatedState(onFire)
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(32.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    this.role = androidx.compose.ui.semantics.Role.Button
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            latest()
                            val job =
                                scope.launch {
                                    delay(ARROW_REPEAT_DELAY_MS)
                                    while (true) {
                                        latest()
                                        delay(ARROW_REPEAT_INTERVAL_MS)
                                    }
                                }
                            tryAwaitRelease()
                            job.cancel()
                        },
                    )
                },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

internal const val ARROW_REPEAT_DELAY_MS: Long = 250L
internal const val ARROW_REPEAT_INTERVAL_MS: Long = 80L

/** PWA `.conn-status-banner`: "Waiting for {mode}… [— answer the input prompt below first] ✕". */
@Composable
private fun ConnStatusBanner(
    modeLabel: String,
    waitingInput: Boolean,
    onDismiss: () -> Unit,
) {
    Surface(color = LocalDatawatchColors.current.waiting.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.session_conn_waiting_for, modeLabel) +
                    if (waitingInput) " " + stringResource(R.string.session_conn_answer_first) else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.session_conn_dismiss),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** PWA `showChannelHelp` popup ("Channel Commands"). */
@Composable
private fun ChannelHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.channel_help_heading)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.channel_help_intro), fontSize = 13.sp)
                Text(stringResource(R.string.channel_help_you_can_send), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.channel_help_send_items), fontSize = 13.sp)
                Text(stringResource(R.string.channel_help_slash_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "/mcp — " + stringResource(R.string.channel_help_slash_mcp) + "\n" +
                        "/effort — " + stringResource(R.string.channel_help_slash_effort) + "\n" +
                        "/help — " + stringResource(R.string.channel_help_slash_help) + "\n" +
                        "/compact — " + stringResource(R.string.channel_help_slash_compact) + "\n" +
                        "/clear — " + stringResource(R.string.channel_help_slash_clear),
                    fontSize = 13.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(stringResource(R.string.channel_help_llm_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.channel_help_llm_items), fontSize = 13.sp)
                Text(
                    stringResource(R.string.channel_help_footer),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** PWA log-line classes (`output_mode=log`); later CSS rules win, so error > ready > processing > acp-status. */
internal enum class LogLineKind { Plain, AcpStatus, Processing, Ready, Error }

internal fun classifyLogLine(line: String): LogLineKind =
    when {
        line.contains("error") || line.contains("failed") -> LogLineKind.Error
        line.contains("ready") || line.contains("awaiting input") -> LogLineKind.Ready
        line.contains("thinking") || line.contains("processing") -> LogLineKind.Processing
        line.contains("[opencode-acp]") -> LogLineKind.AcpStatus
        else -> LogLineKind.Plain
    }

private val AnsiRegex = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

/** PWA log viewer: output lines, ANSI-stripped, blank lines dropped, colour-classed, monospace. */
@Composable
private fun LogModeView(
    events: List<SessionEvent>,
    modifier: Modifier = Modifier,
) {
    val dw = LocalDatawatchColors.current
    val lines =
        remember(events) {
            events.filterIsInstance<SessionEvent.Output>()
                .flatMap { it.body.split('\n') }
                .map { it.replace(AnsiRegex, "").trimEnd('\r') }
                .filter { it.isNotBlank() }
        }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(lines.size) { i ->
            val line = lines[i]
            val kind = classifyLogLine(line)
            Text(
                line,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = if (line.contains("[opencode-acp]")) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    when (kind) {
                        LogLineKind.Error -> MaterialTheme.colorScheme.error
                        LogLineKind.Ready -> dw.success
                        LogLineKind.Processing -> dw.warning
                        LogLineKind.AcpStatus -> MaterialTheme.colorScheme.primary
                        LogLineKind.Plain -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.padding(vertical = 1.dp),
            )
        }
    }
}

/**
 * PWA `showStateOverride` options (app.js): running · waiting_input · complete ·
 * killed · failed — no New / Rate limited.
 */
internal val PWA_OVERRIDE_STATES: List<SessionState> =
    listOf(SessionState.Running, SessionState.Waiting, SessionState.Completed, SessionState.Killed, SessionState.Error)

/** Small accent2-outlined pill for the header metadata row (D66a agent / Chrome). */
@Composable
private fun SessionHeaderBadge(label: String) {
    val accent2 = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.accent2
    Text(
        label,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.labelSmall,
        color = accent2,
        modifier =
            Modifier
                .border(1.dp, accent2, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp),
    )
}

/**
 * Parity D45a — PWA `session-stats-bar`: CPU (PWA thresholds) · RAM · Threads ·
 * FDs · Net (when moving) · GPU (when used), polled every 5 s; hidden while
 * the server reports no envelope for this session.
 */
@Composable
private fun ProcessStatsBar(fetch: suspend () -> com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto?) {
    var env by remember { mutableStateOf<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            env = fetch()
            kotlinx.coroutines.delay(5_000L)
        }
    }
    val e = env ?: return
    val dw = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current
    val cpuColor =
        when {
            e.cpuPct > 80 -> MaterialTheme.colorScheme.error
            e.cpuPct > 50 -> dw.warning
            else -> dw.success
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(dw.bg2)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatsBarCell("CPU", "%.1f%%".format(e.cpuPct), cpuColor)
        StatsBarCell("RAM", statsBarBytes(e.rssBytes))
        StatsBarCell("Threads", e.threads.toString())
        StatsBarCell("FDs", e.fds.toString())
        if (e.netRxBps > 0 || e.netTxBps > 0) {
            StatsBarCell("Net", "↓${statsBarRate(e.netRxBps)} ↑${statsBarRate(e.netTxBps)}")
        }
        if (e.gpuPct > 0) {
            val mem = if (e.gpuMemBytes > 0) " / %.1fGB".format(e.gpuMemBytes / 1e9) else ""
            StatsBarCell("GPU", "%.1f%%".format(e.gpuPct) + mem)
        }
    }
}

@Composable
private fun StatsBarCell(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** PWA watchdog: 5 s per attempt, 3 re-subscribes before giving up. */
internal const val TERM_CONNECT_TIMEOUT_MS: Long = 5_000L
internal const val TERM_CONNECT_MAX_RETRIES: Int = 3

/** PWA "Unable to connect to session terminal" panel with Retry / Use without terminal. */
@Composable
private fun TermConnectFailedPanel(
    onRetry: () -> Unit,
    onUseWithout: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(stringResource(R.string.term_connect_failed), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.term_connect_retries_failed_n, TERM_CONNECT_MAX_RETRIES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.term_retry)) }
                androidx.compose.material3.OutlinedButton(onClick = onUseWithout) { Text(stringResource(R.string.term_use_without)) }
            }
        }
    }
}
