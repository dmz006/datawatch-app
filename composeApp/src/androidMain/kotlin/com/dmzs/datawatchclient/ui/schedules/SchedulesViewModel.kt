package com.dmzs.datawatchclient.ui.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the Settings → Schedules card. List + create + delete against
 * `/api/schedule` on the active server profile. Parallels
 * [com.dmzs.datawatchclient.ui.channels.ChannelsViewModel]'s one-shot
 * fetch-on-init + user-triggered refresh pattern — no WS subscription for
 * schedules today (parent doesn't push change events for them yet).
 */
public class SchedulesViewModel : ViewModel() {
    public data class UiState(
        val schedules: List<Schedule> = emptyList(),
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
        /**
         * `true` until we observe a [TransportError.NotFound] on /api/schedule —
         * flips false against a pre-v4.0.3 server that never added the endpoint.
         * Parent v4.0.3+ always has it.
         */
        val supported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    public fun refresh() {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).listSchedules().fold(
                onSuccess = { list ->
                    _state.value = _state.value.copy(
                        schedules = list,
                        refreshing = false,
                        banner = null,
                        supported = true,
                    )
                },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _state.value = _state.value.copy(
                            refreshing = false,
                            supported = false,
                            banner =
                                "This server doesn't expose /api/schedule yet. " +
                                    "Upgrade datawatch to v4.0.3+ to use schedules.",
                        )
                    } else {
                        _state.value = _state.value.copy(
                            refreshing = false,
                            banner =
                                "Couldn't load schedules — ${err.message ?: err::class.simpleName}",
                        )
                    }
                },
            )
        }
    }

    public fun create(task: String, cron: String, enabled: Boolean) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).createSchedule(task, cron, enabled).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value = _state.value.copy(
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
                    _state.value = _state.value.copy(
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
            profiles.firstOrNull { it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS }
                ?: profiles.firstOrNull { it.enabled }
        if (profile == null) {
            _state.value = UiState(banner = "No enabled server. Add or enable one in Settings.")
        }
        return profile
    }
}
