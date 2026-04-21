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
    ) {
        public val needsInput: Boolean
            get() =
                events.asReversed().firstOrNull {
                    it is SessionEvent.PromptDetected
                } != null && session?.needsInput == true

        /**
         * Most-recent prompt text, used by the "input required" banner that
         * sits above the terminal when the session is in `waiting_input`.
         * Prefers a live PromptDetected event over the cached
         * `Session.lastPrompt` so realtime updates beat REST cache.
         */
        public val pendingPromptText: String?
            get() {
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
            UiState(
                session = session,
                events = events,
                replyText = replyText,
                replying = replying,
                killing = killing,
                banner = banner,
                renaming = renaming,
                reachable = reachable,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Kick off the WebSocket stream once we know the profile.
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            profileCache = profile
            startStream(profile)
            // Mirror the owning profile's transport reachability into the
            // VM so the detail screen can render a connection banner.
            ServiceLocator.transportFor(profile).isReachable
                .onEach { _reachable.value = it }
                .launchIn(viewModelScope)
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
                .onEach { ev -> ServiceLocator.sessionEventRepository.insert(ev) }
                .launchIn(viewModelScope)
    }

    public fun onReplyTextChange(v: String) {
        _replyText.value = v
    }

    public fun sendReply() {
        val text = _replyText.value.trim()
        val profile = profileCache ?: return
        if (text.isEmpty() || _replying.value) return
        _replying.value = true
        _banner.value = null
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.replyToSession(sessionId, text).fold(
                onSuccess = {
                    _replyText.value = ""
                    _replying.value = false
                },
                onFailure = { err ->
                    _replying.value = false
                    _banner.value = "Reply failed: ${err.describe()}"
                },
            )
        }
    }

    public fun kill() {
        val profile = profileCache ?: return
        if (_killing.value) return
        _killing.value = true
        _banner.value = null
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.killSession(sessionId).fold(
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
            ServiceLocator.transportFor(profile).renameSession(sessionId, trimmed).fold(
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
                .overrideSessionState(sessionId, to)
                .fold(
                    onSuccess = { _banner.value = null },
                    onFailure = { err ->
                        _banner.value = "State override failed: ${err.describe()}"
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
