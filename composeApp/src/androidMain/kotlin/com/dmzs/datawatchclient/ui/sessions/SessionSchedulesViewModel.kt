package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.storage.observeForProfileAny
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Scoped VM for the per-session "Scheduled" strip rendered above the
 * composer in [SessionDetailScreen]. Mirrors the PWA's
 * `loadSessionSchedules(sessionId)` path — calls
 * `GET /api/schedules?session_id=<id>&state=pending` and renders the
 * list as inline rows with per-row cancel buttons.
 *
 * Separate from [com.dmzs.datawatchclient.ui.schedules.SchedulesViewModel]
 * (which is server-wide, used by Settings → General) because the session
 * strip needs its own per-session refresh cadence and an owning-profile
 * lookup keyed on the session id rather than the user's active-server
 * selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SessionSchedulesViewModel(
    public val sessionId: String,
) : ViewModel() {
    public data class UiState(
        val schedules: List<Schedule> = emptyList(),
        val banner: String? = null,
        /**
         * False once the server returns 404 on `?session_id=` — older
         * parents that predate the filter param. The strip hides.
         */
        val supported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            ServiceLocator.transportFor(profile)
                .listSchedules(sessionId = sessionId, state = "pending")
                .fold(
                    onSuccess = { list ->
                        _state.value = UiState(schedules = list, supported = true)
                    },
                    onFailure = { err ->
                        if (err is TransportError.NotFound) {
                            _state.value = UiState(supported = false)
                        } else {
                            _state.value =
                                _state.value.copy(
                                    banner =
                                        "Couldn't load session schedules — " +
                                            (err.message ?: err::class.simpleName),
                                )
                        }
                    },
                )
        }
    }

    public fun cancel(scheduleId: String) {
        viewModelScope.launch {
            val profile = resolveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).deleteSchedule(scheduleId).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Cancel failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    private suspend fun resolveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        // Prefer the session's owning profile (same logic as
        // SessionDetailViewModel.resolveProfile) so "All servers" mode works.
        val owningId =
            runCatching {
                ServiceLocator.sessionRepository
                    .observeForProfileAny(sessionId)
                    .first()?.serverProfileId
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
}
