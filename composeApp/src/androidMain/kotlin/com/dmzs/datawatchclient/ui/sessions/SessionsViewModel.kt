package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sessions tab VM. Observes cached sessions for the currently active server profile
 * and triggers a live refresh over REST when the profile changes or the user taps
 * refresh. Multi-server selection UI arrives in Sprint 3; for Phase 3 we show the
 * first enabled profile.
 *
 * Uses `flatMapLatest` so switching profile cancels the previous observation — no
 * leaked collector coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SessionsViewModel : ViewModel() {

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val sessions: List<Session> = emptyList(),
        val refreshing: Boolean = false,
        val banner: String? = null,
    )

    private val activeProfile: StateFlow<ServerProfile?> = ServiceLocator.profileRepository
        .observeAll()
        .map { profiles -> profiles.firstOrNull { it.enabled } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val _refreshing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)

    public val state: StateFlow<UiState> = combineLatestState()

    private fun combineLatestState(): StateFlow<UiState> {
        val sessionsFlow = activeProfile.flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList())
            else ServiceLocator.sessionRepository.observeForProfile(profile.id)
        }
        // Fan-in: combine active profile + sessions + refreshing + banner into a single UiState.
        return kotlinx.coroutines.flow.combine(
            activeProfile, sessionsFlow, _refreshing, _banner,
        ) { profile, sessions, refreshing, banner ->
            UiState(
                activeProfile = profile,
                sessions = sessions,
                refreshing = refreshing,
                banner = banner,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Auto-refresh whenever the active profile identity changes (non-null).
        activeProfile
            .onEach { if (it != null) refresh() }
            .launchIn(viewModelScope)
    }

    public fun refresh() {
        val profile = activeProfile.value ?: return
        _refreshing.value = true
        _banner.value = null
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.listSessions().fold(
                onSuccess = { sessions ->
                    ServiceLocator.sessionRepository.replaceAll(profile.id, sessions)
                    _refreshing.value = false
                    _banner.value = null
                },
                onFailure = { err ->
                    _refreshing.value = false
                    _banner.value = "Disconnected — showing cached data. " +
                        "(${err.message ?: err::class.simpleName})"
                },
            )
        }
    }
}
