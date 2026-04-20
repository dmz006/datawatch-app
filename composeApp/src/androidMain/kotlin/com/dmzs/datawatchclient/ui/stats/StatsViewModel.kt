package com.dmzs.datawatchclient.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stats tab VM. Polls `/api/stats` every [REFRESH_INTERVAL_MS] for the
 * currently active server profile (Sprint 3 keeps it single-server; the
 * federation/all-servers path adds aggregated stats in v0.5.0).
 *
 * Errors are non-fatal — the last good payload stays on screen with a
 * banner string explaining the disconnect.
 */
public class StatsViewModel : ViewModel() {
    public data class UiState(
        val stats: StatsDto? = null,
        val info: ServerInfo? = null,
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val profile = profiles.firstOrNull { it.enabled }
            if (profile == null) {
                _state.value =
                    _state.value.copy(
                        stats = null,
                        refreshing = false,
                        banner = "No enabled server. Add or enable one in Settings.",
                        serverName = null,
                    )
                return@launch
            }
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            val transport = ServiceLocator.transportFor(profile)
            // /api/info is cheap and rarely changes; fetch alongside /api/stats
            // so the server-identity header is populated.
            val infoResult = transport.fetchInfo()
            transport.stats().fold(
                onSuccess = { dto ->
                    _state.value =
                        UiState(
                            stats = dto,
                            info = infoResult.getOrNull() ?: _state.value.info,
                            refreshing = false,
                            banner = null,
                            serverName = profile.displayName,
                        )
                },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            refreshing = false,
                            info = infoResult.getOrNull() ?: _state.value.info,
                            banner = "Disconnected — last reading shown. (${err.message ?: err::class.simpleName})",
                        )
                },
            )
        }
    }

    public companion object {
        public const val REFRESH_INTERVAL_MS: Long = 5_000L
    }
}
