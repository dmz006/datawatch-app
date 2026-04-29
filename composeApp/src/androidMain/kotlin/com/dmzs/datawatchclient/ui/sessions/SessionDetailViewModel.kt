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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
public class SessionDetailViewModel(
    public val sessionId: String,
) : ViewModel() {
    public data class UiState(
        val session: Session? = null,
        val events: List<SessionEvent> = emptyList(),
        val banner: String? = null,
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
    ) {
        public val needsInput: Boolean
            get() =
                events.asReversed().firstOrNull {
                    it is SessionEvent.PromptDetected
                } != null && session?.needsInput == true

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
    private val _reachable = MutableStateFlow<Boolean?>(null)
    private val _messagingBackend = MutableStateFlow<String?>(null)

    private var streamJob: Job? = null
    private var profileCache: ServerProfile? = null

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
            UiState(
                session = session,
                events = events,
                replyText = replyText,
                replying = replying,
                killing = killing,
                banner = banner,
                renaming = renaming,
                reachable = reachable,
                messagingBackend = messagingBackend,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Kick off the WebSocket stream once we know the profile.
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            profileCache = profile
            startStream(profile)
            // v0.35.10 — force a fresh server read on detail open so
            // last_response / state are current the moment the screen
            // appears (matches PWA load-on-open behavior). Without
            // this the user would see whatever the 5-second list
            // poll last cached. Mirrors the user's
            // "should refresh to get the current state like going in
            // from session list" complaint 2026-04-28.
            refreshFromServer()
            // Mirror the owning profile's transport reachability into the
            // VM so the detail screen can render a connection banner.
            ServiceLocator.transportFor(profile).isReachable
                .onEach { _reachable.value = it }
                .launchIn(viewModelScope)
            // Fetch /api/info once for the messaging-backend badge in
            // the header. Best-effort; silent on failure.
            ServiceLocator.transportFor(profile).fetchInfo().onSuccess { info ->
                info.messagingBackend?.takeIf { it.isNotBlank() }?.let {
                    _messagingBackend.value = it
                }
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

    private fun startStream(profile: ServerProfile) {
        streamJob?.cancel()
        val transport = ServiceLocator.wsTransportFor(profile)
        streamJob =
            transport.events(sessionId)
                .onEach { ev ->
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
        val ok =
            com.dmzs.datawatchclient.transport.ws.WsOutbound
                .sendInput(sessionId, text)
        if (ok) {
            _replyText.value = ""
            _replying.value = false
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
     */
    public fun sendQuickReply(text: String) {
        val trimmed = text.trim()
        val profile = profileCache ?: return
        if (trimmed.isEmpty() || _replying.value) return
        _replying.value = true
        _banner.value = null
        viewModelScope.launch {
            val ok =
                com.dmzs.datawatchclient.transport.ws.WsOutbound
                    .sendInput(sessionId, trimmed)
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
     * Force-refresh this session's record from the server.
     *
     * v0.35.8 (BL178 + dmz006/datawatch-app#9): for sessions in
     * `running` / `waiting_input`, the daemon's `Manager.GetLastResponse`
     * re-captures from live tmux on every read. Cached `last_response`
     * from the last 5-second list-poll is therefore stale by up to 5 s
     * — fine for a glance, not fine when the user just tapped the
     * 💾 Response viewer expecting the very latest snippet.
     *
     * Triggered by:
     *   - Tap on the Response button in SessionInfoBar.
     *   - Any state-transition WS frame (running→complete etc.)
     *
     * Fire-and-forget — failure is silently absorbed; the user keeps
     * whatever cached value the repository already had.
     */
    public fun refreshFromServer() {
        val profile = profileCache ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).listSessions().onSuccess { list ->
                list.firstOrNull { it.id == sessionId || it.fullId == sessionId }
                    ?.let { fresh ->
                        ServiceLocator.sessionRepository.upsert(fresh)
                    }
            }
        }
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
                    _banner.value = "Kill failed: ${err.describe()}"
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
            ServiceLocator.transportFor(profile).renameSession(fullIdOrShort(), trimmed).fold(
                onSuccess = { _renaming.value = false },
                onFailure = { err ->
                    _renaming.value = false
                    _banner.value = "Rename failed: ${err.describe()}"
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
    public fun delete(onDeleted: () -> Unit) {
        val profile = profileCache ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).deleteSession(fullIdOrShort()).fold(
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
    }

    override fun onCleared() {
        streamJob?.cancel()
    }
}

private fun Throwable.describe(): String =
    when (this) {
        is TransportError.Unauthorized -> "unauthorized"
        is TransportError.Unreachable -> "server unreachable"
        is TransportError.RateLimited -> "rate-limited"
        is TransportError.ServerError -> "server error $status"
        else -> message ?: this::class.simpleName ?: "unknown"
    }
