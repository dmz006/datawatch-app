package com.dmzs.datawatchclient.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Drives the Settings → Schedules card. List + create + delete against
 * `/api/schedule`. Observes the active profile's reachability so a
 * transient network blip auto-recovers the next time the flow reports
 * reachable — no more sticky "server unreachable" banner after the user
 * re-connects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SchedulesViewModel : ViewModel() {
    public data class UiState(
        val schedules: List<Schedule> = emptyList(),
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
        val supported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Auto-refresh whenever the active profile's reachability flips to true.
        // Drops the sticky-unreachable banner the moment the network recovers.
        ServiceLocator.profileRepository.observeAll()
            .flatMapLatest { profiles ->
                val profile = profiles.firstOrNull { it.enabled } ?: return@flatMapLatest flowOf(null)
                ServiceLocator.transportFor(profile).isReachable
                    .map { reachable -> if (reachable) profile else null }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).listSchedules().fold(
                onSuccess = { list ->
                    _state.value =
                        _state.value.copy(
                            schedules = list,
                            refreshing = false,
                            banner = null,
                            supported = true,
                        )
                },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _state.value =
                            _state.value.copy(
                                refreshing = false,
                                supported = false,
                                banner =
                                    "This server doesn't expose /api/schedule. " +
                                        "Upgrade datawatch to v4.0.3+ to use schedules.",
                            )
                    } else {
                        _state.value =
                            _state.value.copy(
                                refreshing = false,
                                banner =
                                    "Couldn't load schedules — ${err.message ?: err::class.simpleName}",
                            )
                    }
                },
            )
        }
    }

    public fun create(
        task: String,
        cron: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).createSchedule(task, cron, enabled).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Create failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun delete(scheduleId: String) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).deleteSchedule(scheduleId).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Delete failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    private suspend fun resolveActiveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            }
                ?: profiles.firstOrNull { it.enabled }
        if (profile == null) {
            _state.value = UiState(banner = "No enabled server. Add or enable one in Settings.")
        }
        return profile
    }
}
