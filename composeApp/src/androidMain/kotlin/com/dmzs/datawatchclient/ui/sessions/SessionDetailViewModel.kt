package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.storage.observeForProfileAny
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * ViewModel for a single session's detail screen. Subscribes to the
 * WebSocket event stream and surfaces a unified [UiState] combining:
 *   - cached session metadata (from SessionRepository)
 *   - live event stream (from SessionEventRepository, fed by WebSocket)
 *   - send-in-flight states (reply, kill, override)
 *
 * Per ADR-0013 the UI fails fast on disconnected actions — no queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** Outlives a closing session screen so its scroll-mode exit still reaches the server. */
private val scrollExitScope =
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

public class SessionDetailViewModel(
    public val sessionId: String,
) : ViewModel() {
    public data class UiState(
        val session: Session? = null,
        val events: List<SessionEvent> = emptyList(),
        val banner: String? = null,
        /** Non-error informational message (e.g. image-processing status). Rendered in primary colours. */
        val infoBanner: String? = null,
        val replying: Boolean = false,
        val killing: Boolean = false,
        val renaming: Boolean = false,
        val replyText: String = "",
        /**
         * Reachability of the *owning* profile's transport. `null` until the
         * first probe completes (UI shows nothing); `false` when the last
         * probe failed (UI shows a banner above the terminal).
         */
        val reachable: Boolean? = null,
        /**
         * Server-wide messaging backend name, fetched from `/api/info`.
         * Populates the channel badge in the detail header so users
         * can see which channel (signal / telegram / etc.) the server
         * is bound to — the PWA surfaces this same field.
         */
        val messagingBackend: String? = null,
        /** True when the owning server has `whisper.backend` configured; hides mic button when false. */
        val whisperConfigured: Boolean = false,
    ) {
        public val needsInput: Boolean
            // BL-T3-4: previously required a live PromptDetected WS event, which
            // meant the banner never appeared if the prompt fired before the WS
            // subscription started. Session.needsInput (state == Waiting) is the
            // authoritative signal; pendingPromptText falls back to lastPrompt.
            get() = session?.needsInput == true

        /**
         * Most-recent prompt text, used by the "input required" banner that
         * sits above the terminal when the session is in `waiting_input`.
         *
         * Priority matches PWA `app.js:1574-1578`:
         *  1. `session.promptContext` (multi-line — the captured last ~6
         *     lines before the prompt detector fired); richest payload.
         *  2. Live `PromptDetected` event text (from WS real-time stream).
         *  3. `session.lastPrompt` (REST-cached single-line fallback).
         */
        public val pendingPromptText: String?
            get() {
                val ctx = session?.promptContext?.takeIf { it.isNotBlank() }
                if (ctx != null) return ctx
                val live =
                    (
                        events.asReversed().firstOrNull { it is SessionEvent.PromptDetected }
                            as? SessionEvent.PromptDetected
                    )?.prompt?.text
                return live ?: session?.lastPrompt
            }
    }

    private val _replyText = MutableStateFlow("")
    private val _replying = MutableStateFlow(false)
    private val _killing = MutableStateFlow(false)
    private val _renaming = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)
    private val _infoBanner = MutableStateFlow<String?>(null)
    private val _reachable = MutableStateFlow<Boolean?>(null)
    private val _contentReady = MutableStateFlow(false)
    private val _messagingBackend = MutableStateFlow<String?>(null)
    private val _whisperConfigured = MutableStateFlow(false)

    public val contentReady: StateFlow<Boolean> = _contentReady.asStateFlow()

    private var streamJob: Job? = null
    private var profileCache: ServerProfile? = null
    // Debounce job that delays _reachable → false to absorb transient blips (<3.5s).
    private var offlineDebounceJob: Job? = null
    // True when a pane_capture arrived before state.session was populated — triggers
    // contentReady once the session lands from the REST refresh.
    @Volatile private var paneCaptureArrived = false

    public val state: StateFlow<UiState> by lazy { buildState() }

    private fun buildState(): StateFlow<UiState> {
        val sessionsFlow =
            ServiceLocator.sessionRepository
                .observeForProfileAny(sessionId)
        val eventsFlow = ServiceLocator.sessionEventRepository.observe(sessionId)

        return combine(
            sessionsFlow,
            eventsFlow,
            _replyText,
            _replying,
            _killing,
            _banner,
            _renaming,
            _reachable,
            _messagingBackend,
            _whisperConfigured,
            _infoBanner,
        ) { args ->
            val session = args[0] as Session?
            val events =
                @Suppress("UNCHECKED_CAST")
                (args[1] as List<SessionEvent>)
            val replyText = args[2] as String
            val replying = args[3] as Boolean
            val killing = args[4] as Boolean
            val banner = args[5] as String?
            val renaming = args[6] as Boolean
            val reachable = args[7] as Boolean?
            val messagingBackend = args[8] as String?
            val whisperConfigured = args[9] as Boolean
            val infoBanner = args[10] as String?
            UiState(
                session = session,
                events = events,
                replyText = replyText,
                replying = replying,
                killing = killing,
                banner = banner,
                infoBanner = infoBanner,
                renaming = renaming,
                reachable = reachable,
                messagingBackend = messagingBackend,
                whisperConfigured = whisperConfigured,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            profileCache = profile

            // resolveProfile() already queried the session from DB; capture the
            // fullId ("hostname-shortid") now so startStream's subscribe frame
            // uses it. state.value.session is null at this point — the lazy
            // StateFlow hasn't had time to emit — so fullIdOrShort() would
            // return the bare shortId, causing the server to drop pane_capture
            // frames (it keys them on fullId).
            val cachedFullId = runCatching {
                ServiceLocator.sessionRepository.observeForProfileAny(sessionId).first()?.fullId
            }.getOrNull()

            // Start WS connection immediately — don't block on REST refresh.
            startStream(profile, cachedFullId)

            // REST refresh runs in parallel — updates DB which feeds sessionsFlow.
            launch { doRefreshFromServer(profile) }

            // Safety net for cold-start (empty DB cache): if pane_capture arrived
            // before state.session was populated, set contentReady once session lands.
            state
                .filter { it.session != null }
                .take(1)
                .onEach { if (paneCaptureArrived && !_contentReady.value) _contentReady.value = true }
                .launchIn(viewModelScope)

            // Mirror the owning profile's transport reachability into the VM.
            // Only delay the offline transition — online is immediate.
            ServiceLocator.transportFor(profile).isReachable
                .onEach { reachable ->
                    if (reachable) {
                        offlineDebounceJob?.cancel()
                        offlineDebounceJob = null
                        _reachable.value = true
                    } else if (offlineDebounceJob?.isActive != true) {
                        offlineDebounceJob = viewModelScope.launch {
                            delay(OFFLINE_GRACE_MS)
                            _reachable.value = false
                        }
                    }
                }
                .launchIn(viewModelScope)

            // Single fetchInfo call for both messagingBackend and whisperConfigured.
            // Previously two back-to-back calls to the same endpoint.
            ServiceLocator.transportFor(profile).fetchInfo().onSuccess { info ->
                info.messagingBackend?.takeIf { it.isNotBlank() }?.let {
                    _messagingBackend.value = it
                }
                if (info.whisperConfigured) _whisperConfigured.value = true
            }
        }
    }

    private suspend fun resolveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        // Prefer the profile that actually owns this session (its cached row in
        // SessionRepository has the server_profile_id). Falls back to the
        // user's active-server selection, then to the first enabled profile.
        // Without this, opening a session on profile B while profile A is
        // "active" would connect WS to A and silently receive zero frames.
        val owningId =
            runCatching {
                val s = ServiceLocator.sessionRepository.observeForProfileAny(sessionId).first()
                s?.serverProfileId
            }.getOrNull()
        if (owningId != null) {
            profiles.firstOrNull { it.id == owningId }?.let { return it }
        }
        val activeId = ServiceLocator.activeServerStore.get()
        if (activeId != null) {
            profiles.firstOrNull { it.id == activeId && it.enabled }?.let { return it }
        }
        return profiles.firstOrNull { it.enabled }
    }

    /**
     * B60 — pause WS stream when screen goes to background (ON_STOP).
     * Sets dot to gray (null = not established) so the UI doesn't show
     * a stale green or confusing red while intentionally paused.
     */
    public fun pauseStream() {
        streamJob?.cancel()
        streamJob = null
        _reachable.value = null
        _contentReady.value = false
        paneCaptureArrived = false
    }

    /** PWA connect watchdog re-subscribe: drop and reopen the session stream. */
    public fun restartStream() {
        pauseStream()
        resumeStream()
    }

    /**
     * B60 — resume WS stream when screen comes to foreground (ON_START).
     * No-op if the stream is already running (guards against double-resume
     * on orientation change where ON_STOP/ON_START fire in quick succession).
     */
    public fun resumeStream() {
        if (streamJob?.isActive == true) return
        val profile = profileCache ?: return
        startStream(profile)
    }

    private var wsSessionRefreshFired = false
    private var wsWasDisconnected = false

    /**
     * Sprint 3 S3-2 (#62, #64, #65, #66, #67) — terminal dimensions for
     * resize_term first-frame send on reconnect. Updated by the SessionDetailScreen
     * whenever the session's server-resolved console size (`console_cols`/`console_rows`) is applied.
     */
    public var terminalCols: Int = 80
    public var terminalRows: Int = 24

    private fun startStream(profile: ServerProfile, knownFullId: String? = null) {
        streamJob?.cancel()
        wsSessionRefreshFired = false
        _contentReady.value = false
        // Initialize to `true` so the FIRST live event after first connect
        // triggers the resize_term + state-refresh path. Previously this was
        // only sent on reconnect, which meant the server pane stayed at
        // whatever size some OTHER browser client requested — the Android
        // app would render the (wrong-size) pane_capture forever without
        // ever announcing its own size requirements.
        // [WsOutbound has replay=0, so a JS-fit-triggered resize_term that
        //  fires before WS subscription is dropped — must re-send from VM
        //  once we know the WS is up.]
        wsWasDisconnected = true
        val transport = ServiceLocator.wsTransportFor(profile)
        // Subscribe with fullId (hostname-shortid format), not just short sessionId.
        // The server keys all pane_capture frames on fullId, so we must subscribe with it.
        // But store events with short sessionId for the UI to query.
        // knownFullId is pre-fetched in init from the DB to avoid the race where
        // state.value.session is null and fullIdOrShort() would return the bare shortId.
        val subscriptionId = knownFullId ?: fullIdOrShort()
        streamJob =
            transport.events(subscriptionId, sessionId)
                .onEach { ev ->
                    // B59 — WS-connection state drives the reachability dot.
                    // Error events mean the WS disconnected; any live event means
                    // it's connected. REST-based isReachable also writes _reachable
                    // on poll success, so a transient WS blip (while server is still
                    // REST-reachable) correctly recovers to green after the next poll.
                    // Offline transition is debounced (OFFLINE_GRACE_MS) so brief
                    // heartbeat blips don't flash the banner; online is immediate.
                    val isError = ev is com.dmzs.datawatchclient.domain.SessionEvent.Error
                    if (isError) {
                        if (offlineDebounceJob?.isActive != true) {
                            offlineDebounceJob = viewModelScope.launch {
                                delay(OFFLINE_GRACE_MS)
                                _reachable.value = false
                            }
                        }
                    } else {
                        offlineDebounceJob?.cancel()
                        offlineDebounceJob = null
                        _reachable.value = true
                    }
                    // Sprint 3 S3-2 (#62, #64, #65, #66, #67) — full re-render on reconnect.
                    // When we receive the first live event after a disconnect:
                    //  1. Clear pane-capture dedup so the next frame is treated as a first
                    //     frame (fresh terminal paint — not skipped as a "seen" duplicate).
                    //  2. REST GET refresh BEFORE re-subscribing further (already in flight
                    //     via the collect loop; refreshFromServer fires here).
                    //  3. Send resize_term as the first outbound WS frame so the daemon
                    //     knows the current terminal size and sends a correctly-sized
                    //     pane_capture (#65 — post-restart screen size).
                    //  4. Re-enable input: _replying is reset to false so the composer
                    //     is interactive again (#64 — tmux input bar restored).
                    // BL249 basis — session auto-refresh on reconnect (PWA v6.5.1).
                    if (!isError && wsWasDisconnected) {
                        wsWasDisconnected = false
                        // 1. Clear pane-capture dedup for this session.
                        com.dmzs.datawatchclient.transport.ws.resetPaneCaptureSeen(sessionId)
                        // 2. Fresh REST GET before WS events are acted on.
                        doRefreshFromServer(profile)
                        // 3. Send resize_term as the first outbound WS frame (#65).
                        com.dmzs.datawatchclient.transport.ws.WsOutbound.sendResizeTerm(
                            sessionId, terminalCols, terminalRows,
                        )
                        // 4. Re-enable input (#64 — input bar must not stay locked after reconnect).
                        if (_replying.value) _replying.value = false
                        // Stale-state gate (#66): if the session has a terminal state but
                        // updatedAt is older than 10 seconds, the daemon is authoritative —
                        // do not freeze the terminal from client-side; trust the incoming frames.
                        // TerminalController.setFrozen(false) is driven by session state in the
                        // screen composable via LaunchedEffect(state.session?.state) — the REST
                        // refresh above will update session.state so the composable re-evaluates.
                        // No additional action needed here; the full re-render path handles #66.
                    }
                    if (isError) wsWasDisconnected = true
                    // Clear the image-processing info banner on any live server event —
                    // the session is visibly active so the user no longer needs the hint.
                    if (!isError && _infoBanner.value != null) {
                        _infoBanner.value = null
                    }
                    if (!_contentReady.value && ev is SessionEvent.PaneCapture) {
                        paneCaptureArrived = true
                        // Only mark content ready when session metadata is also available
                        // (guards against the cold-start edge case where session isn't in
                        // the local DB cache yet and REST is still in flight).
                        if (state.value.session != null) _contentReady.value = true
                        // else: the state.filter{session!=null}.take(1) observer in init will
                        // set contentReady once the REST refresh completes and session arrives.
                    }
                    ServiceLocator.sessionEventRepository.insert(ev)
                    // v0.35.8 — mirror PWA v5.26.49 fix:
                    // bulk-session WS pushes can flip a session to
                    // waiting_input without going through the
                    // single-session `session_state` channel, leaving
                    // the input-required banner out of date until the
                    // user exits + re-enters. On every state change
                    // observed in the WS stream, force a fresh
                    // session re-read so the banner + prompt context
                    // update immediately.
                    if (ev is com.dmzs.datawatchclient.domain.SessionEvent.StateChange) {
                        refreshFromServer()
                    }
                    // Safety net: if the initial REST refresh failed (network
                    // blip) and the session is still absent from the DB when
                    // the first WS frame arrives, trigger one more refresh so
                    // state.session populates and GeneratingIndicator can render.
                    if (!wsSessionRefreshFired && state.value.session == null) {
                        wsSessionRefreshFired = true
                        refreshFromServer()
                    }
                }
                .launchIn(viewModelScope)
    }

    public fun onReplyTextChange(v: String) {
        _replyText.value = v
    }

    public fun sendReply() {
        val text = _replyText.value.trim()
        if (text.isEmpty() || _replying.value) return
        _replying.value = true
        _banner.value = null
        // v0.33.22: send via WS `send_input` (PWA path at app.js:2341)
        // instead of the old REST `POST /api/sessions/reply` which the
        // server doesn't expose (404). WsOutbound emits the frame on
        // the open hub socket — fire-and-forget since the server
        // doesn't ack replies; success is observed via the next
        // pane_capture frame showing the input landing.
        // Append \r so the shell executes the command (PTY requires CR).
        val ok =
            com.dmzs.datawatchclient.transport.ws.WsOutbound
                .sendInput(sessionId, text + "\r")
        if (ok) {
            _replyText.value = ""
            _replying.value = false
            if (text.contains("[image:")) {
                _infoBanner.value = IMAGE_PROCESSING_BANNER
            }
        } else {
            _replying.value = false
            _banner.value = "Reply failed: WS not connected (open session once more)."
        }
    }

    /**
     * One-shot quick reply that bypasses the composer. Used by the
     * chat-mode prompt quick-buttons ("Yes" / "No" / "Stop") so users
     * can answer a `waiting_input` prompt without typing. Does not
     * touch [_replyText] so any in-flight composer draft is preserved.
     * Callers must include \r if they want shell execution (e.g. "yes\r").
     * Whitespace-only strings like "\r" are intentional terminal input and
     * are NOT trimmed — only truly empty strings are rejected.
     */
    /**
     * PWA `sendChannelMessage` (`▶ ch`): POST the composer text to the MCP
     * channel instead of tmux. Failures surface on the banner like sendReply.
     */
    public fun sendViaChannel() {
        val profile = profileCache ?: return
        val text = _replyText.value.trim()
        if (text.isEmpty() || _replying.value) return
        _replying.value = true
        _banner.value = null
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).sendChannelMessage(fullIdOrShort(), text).fold(
                onSuccess = {
                    _replyText.value = ""
                    _replying.value = false
                },
                onFailure = { err ->
                    _replying.value = false
                    _banner.value = "Channel send failed: ${err.describe()}"
                },
            )
        }
    }

    public fun sendQuickReply(text: String) {
        val profile = profileCache ?: return
        if (text.isEmpty() || _replying.value) return
        _replying.value = true
        _banner.value = null
        viewModelScope.launch {
            // Arrow/ESC keys must go through tmux send-keys (matches PWA sendkey path)
            // so they bypass PTY line buffering. Raw escape sequences via send_input
            // get buffered until a CR arrives, causing arrows to appear to require Enter.
            // Strip trailing CR before matching: QuickCommandsSheet.onSend appends "\r"
            // for shell execution, which would otherwise break ESC/arrow/page-key detection.
            val stripped = text.trimEnd('\r')
            val sendKeyName =
                when (stripped) {
                    "[A", "[A" -> "Up"
                    "[B", "[B" -> "Down"
                    "[C", "[C" -> "Right"
                    "[D", "[D" -> "Left"
                    "" -> "Escape"
                    "\t" -> "Tab"
                    "\u0003" -> "C-c" // PWA abort
                    "\u0002" -> "C-b" // PWA tmux prefix
                    "[5~" -> "PgUp"
                    "[6~" -> "PgDn"
                    else -> null
                }
            val ok =
                if (sendKeyName != null) {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendCommand(sessionId, "sendkey $sessionId: $sendKeyName")
                } else {
                    com.dmzs.datawatchclient.transport.ws.WsOutbound
                        .sendInput(sessionId, text)
                }
            _replying.value = false
            if (!ok) {
                _banner.value = "Quick reply failed: WS not connected."
            }
        }
    }

    /**
     * Resolves the server-scoped full id (`"hostname-shortid"`) the
     * daemon's session store keys on. Every mutation endpoint on the
     * server (`kill`, `state`, `rename`, `restart`, `delete`) rejects
     * the short id with a 404. Falls back to [sessionId] when no cached
     * session is available yet — the server will still 404 in that
     * rare case, but at least the request is well-formed.
     */
    private fun fullIdOrShort(): String = state.value.session?.fullId ?: sessionId

    /**
     * Load the active server's saved-command list so the Saved Commands
     * sheet (opened from the composer) renders its "Saved" group.
     * Mirrors [SessionsViewModel.fetchSavedCommands] but scoped to the
     * detail VM's profile cache.
     */
    public suspend fun fetchSavedCommands(): List<Pair<String, String>> {
        val profile = profileCache ?: return emptyList()
        return com.dmzs.datawatchclient.di.ServiceLocator.transportFor(profile)
            .listCommands().fold(
                onSuccess = { list -> list.map { it.name to it.command } },
                onFailure = { emptyList() },
            )
    }

    /**
     * Suspend-able core of the server refresh. Finds this session in
     * the server list and upserts its record so [state.session] is
     * populated with the latest REST state. Failure is silently absorbed.
     */
    private suspend fun doRefreshFromServer(profile: ServerProfile) {
        ServiceLocator.transportFor(profile).listSessions().onSuccess { list ->
            list.firstOrNull { it.id == sessionId || it.fullId == sessionId }
                ?.let { fresh -> ServiceLocator.sessionRepository.upsert(fresh) }
        }
    }

    /**
     * Fire-and-forget wrapper around [doRefreshFromServer]. Used by
     * state-change WS events and the Response button tap so callers
     * don't need to manage the coroutine themselves.
     */
    public fun refreshFromServer() {
        val profile = profileCache ?: return
        viewModelScope.launch { doRefreshFromServer(profile) }
    }

    /**
     * Parity D45a — PWA `loadSessionStats`: this session's process envelope from
     * GET /api/stats envelopes (kind == session, full id starts with envelope id).
     */
    public suspend fun fetchProcessEnvelope(): com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto? {
        val profile = profileCache ?: return null
        val full = fullIdOrShort()
        return ServiceLocator.transportFor(profile).getAllEnvelopes().getOrNull()
            ?.let { envs -> sessionEnvelopeFor(full, envs) }
    }

    /** Parity D43a — live last response for the viewer (GET /api/sessions/response). */
    /**
     * Scroll mode (tmux copy-mode) enter / exit. Sent over REST `/api/command`
     * so the result is acknowledged — a WS `command` frame is dropped silently
     * while the socket reconnects (e.g. after the phone slept on the scrollback),
     * which left tmux in copy-mode with the app showing the live composer: the
     * terminal looked hung until ESC was pressed by hand. Falls back to the WS
     * frame only when REST is unavailable. Returns true when the server accepted it.
     */
    public suspend fun scrollModeCommand(enter: Boolean): Boolean {
        val id = fullIdOrShort()
        val text = if (enter) "tmux-copy-mode $id" else "sendkey $id: Escape"
        val profile = profileCache
        if (profile != null) {
            val r = ServiceLocator.transportFor(profile).runCommand(text)
            if (r.isSuccess) {
                val out = r.getOrNull().orEmpty()
                return !(out.startsWith("Error") || out.contains("not found"))
            }
        }
        return com.dmzs.datawatchclient.transport.ws.WsOutbound.sendCommand(sessionId, text)
    }

    /**
     * Leaving the session while still in scroll mode: take tmux out of copy-mode
     * so the pane isn't frozen next time. Runs outside [viewModelScope], which
     * is cancelled as the screen goes away.
     */
    public fun exitScrollModeDetached() {
        scrollExitScope.launch { scrollModeCommand(enter = false) }
    }

    public suspend fun fetchFreshResponse(): Result<String> {
        val profile = profileCache ?: return Result.failure<String>(IllegalStateException("no server"))
        return ServiceLocator.transportFor(profile).getSessionResponse(fullIdOrShort())
    }

    public fun kill() {
        val profile = profileCache ?: return
        if (_killing.value) return
        _killing.value = true
        _banner.value = null
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.killSession(fullIdOrShort()).fold(
                onSuccess = { _killing.value = false },
                onFailure = { err ->
                    _killing.value = false
                    _banner.value = "Stop failed: ${err.describe()}"
                },
            )
        }
    }

    /**
     * Inline-rename from the detail screen header tap. Same wire as the
     * Sessions-list overflow rename — `POST /api/sessions/rename`.
     */
    public fun rename(newName: String) {
        val profile = profileCache ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank() || _renaming.value) return
        _renaming.value = true
        _banner.value = null
        viewModelScope.launch {
            // PWA toasts "Session renamed" / "Rename failed" — parity D41a routes
            // toasts to the alert dock.
            ServiceLocator.transportFor(profile).renameSession(fullIdOrShort(), trimmed).fold(
                onSuccess = {
                    _renaming.value = false
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        ServiceLocator.context().getString(com.dmzs.datawatchclient.R.string.session_renamed),
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Success,
                    )
                },
                onFailure = { err ->
                    _renaming.value = false
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        ServiceLocator.context().getString(com.dmzs.datawatchclient.R.string.session_rename_failed) +
                            ": ${err.describe()}",
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                    )
                },
            )
        }
    }

    public fun overrideState(to: SessionState) {
        val profile = profileCache ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile)
                .overrideSessionState(fullIdOrShort(), to)
                .fold(
                    onSuccess = { _banner.value = null },
                    onFailure = { err ->
                        _banner.value = "State override failed: ${err.describe()}"
                    },
                )
        }
    }

    /**
     * Delete a terminal-state session from the server. Fired from the
     * detail screen's post-kill Delete action. Server takes the full
     * id + an optional `delete_data` flag which we leave false here;
     * the PWA's "Delete data" is a separate two-step confirmation that
     * we surface as its own UI iteration.
     */
    public fun delete(
        onDeleted: () -> Unit,
        memoryStrategy: String? = null,
        archiveRoleFilter: List<String> = emptyList(),
        archiveToScope: String? = null,
    ) {
        val profile = profileCache ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile)
                .deleteSession(fullIdOrShort(), memoryStrategy, archiveRoleFilter, archiveToScope)
                .fold(
                    onSuccess = {
                        _banner.value = null
                        onDeleted()
                    },
                    onFailure = { err ->
                        _banner.value = "Delete failed: ${err.describe()}"
                    },
                )
        }
    }

    public fun toggleMute() {
        val session = state.value.session ?: return
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(session.id, !session.muted)
        }
    }

    public fun dismissBanner() {
        _banner.value = null
        // BL250 — refresh session state after dismiss so UI doesn't show
        // stale cues (session is usually already past waiting_input by the
        // time the operator dismisses the banner).
        viewModelScope.launch { refreshFromServer() }
    }

    override fun onCleared() {
        streamJob?.cancel()
    }
}

private const val IMAGE_PROCESSING_BANNER = "Image sent — processing with vision model…"
// Grace period before showing the offline banner — absorbs heartbeat blips under ~3.5s.
private const val OFFLINE_GRACE_MS = 3_500L

private fun Throwable.describe(): String =
    when (this) {
        is TransportError.Unauthorized -> "unauthorized"
        is TransportError.Unreachable -> "server unreachable"
        is TransportError.RateLimited -> "rate-limited"
        is TransportError.ServerError -> "server error $status"
        else -> message ?: this::class.simpleName ?: "unknown"
    }

/** PWA match rule: `e.kind === 'session' && e.id && sessionId.startsWith(e.id)`. */
internal fun sessionEnvelopeFor(
    sessionFullId: String,
    envelopes: List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>,
): com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto? =
    envelopes.firstOrNull { it.kind == "session" && it.id.isNotBlank() && sessionFullId.startsWith(it.id) }

/** PWA byte formatting for the stats bar RAM cell (GB / MB / KB). */
internal fun statsBarBytes(b: Long): String =
    when {
        b > 1_000_000_000L -> "%.1fGB".format(b / 1e9)
        b > 1_000_000L -> "%.0fMB".format(b / 1e6)
        else -> "%.0fKB".format(b / 1e3)
    }

/** PWA rate formatting for the stats bar Net cell (MB/s else KB/s). */
internal fun statsBarRate(bps: Long): String = if (bps > 1_000_000L) "%.1fMB/s".format(bps / 1e6) else "%.0fKB/s".format(bps / 1e3)
