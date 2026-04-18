package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Sessions tab VM. Observes cached sessions for the currently active server profile,
 * and triggers a live refresh over REST when the server is reachable. Multi-server
 * selection UI arrives in Sprint 3; for Phase 3 we show the first enabled profile.
 */
public class SessionsViewModel : ViewModel() {

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val sessions: List<Session> = emptyList(),
        val refreshing: Boolean = false,
        val banner: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        ServiceLocator.profileRepository.observeAll()
            .combine(ServiceLocator.sessionRepository.run {
                // Phase 3 placeholder: observe nothing when no profile yet.
                kotlinx.coroutines.flow.flowOf(emptyList<Session>())
            }) { profiles, _ ->
                profiles.firstOrNull { it.enabled }
            }
            .onEach { active ->
                _state.value = _state.value.copy(activeProfile = active)
                if (active != null) {
                    observeSessions(active)
                    refresh()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSessions(profile: ServerProfile) {
        ServiceLocator.sessionRepository.observeForProfile(profile.id)
            .onEach { sessions ->
                _state.value = _state.value.copy(sessions = sessions)
            }
            .launchIn(viewModelScope)
    }

    public fun refresh() {
        val profile = _state.value.activeProfile ?: return
        _state.value = _state.value.copy(refreshing = true, banner = null)
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.listSessions().fold(
                onSuccess = { sessions ->
                    ServiceLocator.sessionRepository.replaceAll(profile.id, sessions)
                    _state.value = _state.value.copy(refreshing = false, banner = null)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        refreshing = false,
                        banner = "Disconnected — showing cached data. (${err.message ?: err::class.simpleName})",
                    )
                },
            )
        }
    }
}
