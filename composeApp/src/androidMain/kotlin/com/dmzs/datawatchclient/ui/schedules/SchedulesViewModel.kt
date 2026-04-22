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
import kotlinx.coroutines.isActive
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
        // Re-fetch whenever the *active* profile changes (user switched
        // servers via the picker or 3-finger-swipe), AND whenever the
        // current active profile becomes reachable after a network blip.
        ServiceLocator.activeProfileFlow()
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(null)
                } else {
                    ServiceLocator.transportFor(profile).isReachable
                        .map { reachable -> if (reachable) profile else null }
                }
            }
            .filterNotNull()
            .distinctUntilChanged { a, b -> a.id == b.id }
            .onEach { refresh() }
            .launchIn(viewModelScope)

        // 15-second poll so Scheduled Events stays live without the
        // user hitting a Refresh button — matches PWA's ticking
        // behaviour (B16). Invisible to the UI; refreshing flag is
        // only flipped inside [refresh] while the HTTP call is in
        // flight, so there's no visible loading flicker every tick.
        viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(SCHEDULES_POLL_MS)
                refresh()
            }
        }
    }

    private companion object {
        const val SCHEDULES_POLL_MS: Long = 15_000L
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
        sessionId: String? = null,
    ) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).createSchedule(
                task = task,
                cron = cron,
                enabled = enabled,
                sessionId = sessionId,
            ).fold(
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
