package com.dmzs.datawatchclient.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.AnalyticsDto
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.SmokeProgressDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public data class DashboardState(
    val cards: List<DashboardCardDto> = emptyList(),
    val cardsLoaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val stats: StatsDto? = null,
    val smokeProgress: SmokeProgressDto? = null,
    val prds: List<PrdDto> = emptyList(),
    val analytics: AnalyticsDto? = null,
    val error: String? = null,
    val activeProfile: ServerProfile? = null,
    val allProfiles: List<ServerProfile> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
public class DashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())

    private val _allProfiles = ServiceLocator.profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeId = ServiceLocator.activeServerStore.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _computedActiveProfile: StateFlow<ServerProfile?> =
        combine(_allProfiles, _activeId) { profiles, storedId ->
            val enabled = profiles.filter { it.enabled }
            if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine enabled.firstOrNull()
            storedId?.let { id -> enabled.firstOrNull { it.id == id } } ?: enabled.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DashboardState> =
        combine(_state, _allProfiles, _computedActiveProfile) { s, profiles, active ->
            s.copy(allProfiles = profiles.filter { it.enabled }, activeProfile = active)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardState())

    public val reachable: StateFlow<Boolean?> = _computedActiveProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf<Boolean?>(null)
            else ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public val lastProbeEpochMs: StateFlow<Long?> = reachable
        .runningFold(null as Long?) { acc, r -> if (r == true) System.currentTimeMillis() else acc }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            _computedActiveProfile.collectLatest { _ ->
                _state.value = _state.value.copy(cardsLoaded = false, cards = emptyList())
                start()
            }
        }
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun refreshCards() {
        viewModelScope.launch {
            resolveTransport()?.listDashboardCards()?.onSuccess { cards ->
                _state.value = _state.value.copy(cards = cards)
            }
        }
    }

    private suspend fun resolveTransport(): TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        return runCatching {
            ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                }
                ?.let { ServiceLocator.transportFor(it) }
        }.getOrNull()
    }

    private suspend fun start() {
        val transport = resolveTransport()
        transport?.listDashboardCards()?.onSuccess { cards ->
            _state.value = _state.value.copy(cards = cards)
        }
        _state.value = _state.value.copy(cardsLoaded = true)

        var slowPollTick = 0
        while (currentCoroutineContext().isActive) {
            val t = resolveTransport()
            if (t != null) {
                t.listSessions()
                    .onSuccess { sessions -> _state.value = _state.value.copy(sessions = sessions, error = null) }
                    .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Error") }
                t.stats().onSuccess { stats -> _state.value = _state.value.copy(stats = stats) }
                t.getSmokeProgress().onSuccess { sp -> _state.value = _state.value.copy(smokeProgress = sp) }
                if (slowPollTick % 3 == 0) {
                    t.listPrds().onSuccess { list ->
                        _state.value = _state.value.copy(prds = list.prds.filter { it.status in ACTIVE_PRD_STATUSES })
                    }
                    t.getAnalytics(30).onSuccess { a -> _state.value = _state.value.copy(analytics = a) }
                }
            }
            slowPollTick++
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 10_000L
        val ACTIVE_PRD_STATUSES = setOf("running", "decomposing", "planning", "approved")
    }
}
