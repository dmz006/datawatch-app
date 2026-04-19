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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
        val replyText: String = "",
    ) {
        public val needsInput: Boolean
            get() = events.asReversed().firstOrNull {
                it is SessionEvent.PromptDetected
            } != null && session?.needsInput == true
    }

    private val _replyText = MutableStateFlow("")
    private val _replying = MutableStateFlow(false)
    private val _killing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)

    private var streamJob: Job? = null
    private var profileCache: ServerProfile? = null

    public val state: StateFlow<UiState> by lazy { buildState() }

    private fun buildState(): StateFlow<UiState> {
        val sessionsFlow = ServiceLocator.sessionRepository
            .observeForProfileAny(sessionId)
        val eventsFlow = ServiceLocator.sessionEventRepository.observe(sessionId)

        return combine(
            sessionsFlow, eventsFlow, _replyText, _replying, _killing, _banner,
        ) { args ->
            val session = args[0] as Session?
            val events = @Suppress("UNCHECKED_CAST") (args[1] as List<SessionEvent>)
            val replyText = args[2] as String
            val replying = args[3] as Boolean
            val killing = args[4] as Boolean
            val banner = args[5] as String?
            UiState(
                session = session, events = events,
                replyText = replyText, replying = replying,
                killing = killing, banner = banner,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Kick off the WebSocket stream once we know the profile.
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            profileCache = profile
            startStream(profile)
        }
    }

    private suspend fun resolveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        // Sprint 2 Phase 1: assume the currently-enabled profile owns the session.
        // Sprint 3 adds cross-profile session addressing via federation fan-out.
        return profiles.firstOrNull { it.enabled }
    }

    private fun startStream(profile: ServerProfile) {
        streamJob?.cancel()
        val transport = ServiceLocator.wsTransportFor(profile)
        streamJob = transport.events(sessionId)
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

private fun Throwable.describe(): String = when (this) {
    is TransportError.Unauthorized -> "unauthorized"
    is TransportError.Unreachable -> "server unreachable"
    is TransportError.RateLimited -> "rate-limited"
    is TransportError.ServerError -> "server error $status"
    else -> message ?: this::class.simpleName ?: "unknown"
}
