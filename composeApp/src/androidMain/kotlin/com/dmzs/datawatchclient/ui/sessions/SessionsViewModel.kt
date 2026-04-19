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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sessions tab VM. Observes cached sessions for the currently active server profile.
 *
 * "Active" is resolved by combining the persisted [ActiveServerStore] selection with
 * the live profile list: if the stored id still points at an enabled profile it wins;
 * otherwise we fall back to the first enabled profile (so deleting the active server
 * degrades gracefully). Sprint 3 Phase 3 will add all-servers fan-out as a separate mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SessionsViewModel : ViewModel() {

    public enum class Filter(public val label: String) {
        All("All"), Running("Running"), Waiting("Waiting"),
        Completed("Completed"), Error("Error")
    }

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val allProfiles: List<ServerProfile> = emptyList(),
        val sessions: List<Session> = emptyList(),
        val filter: Filter = Filter.All,
        val refreshing: Boolean = false,
        val banner: String? = null,
    ) {
        public val visibleSessions: List<Session>
            get() = when (filter) {
                Filter.All -> sessions
                Filter.Running -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Running }
                Filter.Waiting -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Waiting }
                Filter.Completed -> sessions.filter {
                    it.state == com.dmzs.datawatchclient.domain.SessionState.Completed ||
                        it.state == com.dmzs.datawatchclient.domain.SessionState.Killed
                }
                Filter.Error -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Error }
            }
    }

    private val allProfiles: StateFlow<List<ServerProfile>> = ServiceLocator.profileRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    private val activeProfile: StateFlow<ServerProfile?> = combine(
        allProfiles,
        ServiceLocator.activeServerStore.observe().distinctUntilChanged(),
    ) { profiles, storedId ->
        val enabled = profiles.filter { it.enabled }
        storedId?.let { id -> enabled.firstOrNull { it.id == id } }
            ?: enabled.firstOrNull()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val _refreshing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)
    private val _filter = MutableStateFlow(Filter.All)

    public val state: StateFlow<UiState> = combineLatestState()

    private fun combineLatestState(): StateFlow<UiState> {
        val sessionsFlow = activeProfile.flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList())
            else ServiceLocator.sessionRepository.observeForProfile(profile.id)
        }
        return combine(
            activeProfile, allProfiles, sessionsFlow, _refreshing, _banner, _filter,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            UiState(
                activeProfile = args[0] as ServerProfile?,
                allProfiles = args[1] as List<ServerProfile>,
                sessions = args[2] as List<Session>,
                refreshing = args[3] as Boolean,
                banner = args[4] as String?,
                filter = args[5] as Filter,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Auto-refresh whenever the active profile identity changes (non-null).
        activeProfile
            .onEach { if (it != null) refresh() }
            .launchIn(viewModelScope)
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    public fun toggleMute(sessionId: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(sessionId, !currentlyMuted)
        }
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
