package com.dmzs.datawatchclient.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.transport.ws.StatsHub
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

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
        /**
         * `session.max_sessions` pulled from `/api/config`. Cached across
         * polls so the Session Statistics ring has a stable denominator.
         * Null until the first successful config fetch or when a server
         * omits the key.
         */
        val maxSessions: Int? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // B10: subscribe to live stats frames arriving on any active
        // session WS connection. These overlay REST poll values —
        // typically arrive at the server's own broadcast cadence
        // (~5 s on most configs) and bypass the REST round-trip.
        viewModelScope.launch {
            StatsHub.flow.collect { liveDto ->
                val current = _state.value
                if (current.stats != null) {
                    _state.value =
                        current.copy(
                            stats = liveDto,
                            refreshing = false,
                            banner = null,
                        )
                    ServiceLocator.refreshHomeWidgets()
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = ServiceLocator.activeProfileFlow().first()
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
            // Fetch `session.max_sessions` once, then keep the cached value.
            // Config is heavy relative to stats, so only hit it when we
            // don't already have a denominator for the Sessions ring.
            val maxSessions: Int? =
                _state.value.maxSessions
                    ?: transport.fetchConfig().getOrNull()?.let { cfg ->
                        runCatching { cfg.raw["session.max_sessions"]?.jsonPrimitive?.content?.toInt() }.getOrNull()
                    }
            // PWA derives Monitor's session counts from the live
            // `/api/sessions` list, not from `/api/stats`. Many server
            // builds either don't populate `sessions_total` or lag by
            // a full poll cycle; the list is the authoritative source.
            // Pull it here alongside stats so the card never shows 0
            // when there are live sessions (2026-04-22 user report).
            val sessionsList = transport.listSessions().getOrNull().orEmpty()
            val sessionsTotal = sessionsList.size
            val sessionsRunning = sessionsList.count { it.state == SessionState.Running }
            val sessionsWaiting = sessionsList.count { it.state == SessionState.Waiting }
            transport.stats().fold(
                onSuccess = { dto ->
                    // Override the stats-reported counts when the session
                    // list returns something richer. `.copy` leaves the
                    // stats fields as-is when the list was empty so we
                    // don't clobber a v4.1.0 envelope-only payload.
                    val patched =
                        if (sessionsTotal > 0) {
                            dto.copy(
                                sessionsTotal = sessionsTotal,
                                sessionsRunning = sessionsRunning,
                                sessionsWaiting = sessionsWaiting,
                            )
                        } else {
                            dto
                        }
                    _state.value =
                        UiState(
                            stats = patched,
                            info = infoResult.getOrNull() ?: _state.value.info,
                            refreshing = false,
                            banner = null,
                            serverName = profile.displayName,
                            maxSessions = maxSessions,
                        )
                    ServiceLocator.refreshHomeWidgets()
                },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            refreshing = false,
                            info = infoResult.getOrNull() ?: _state.value.info,
                            banner = "Disconnected — last reading shown. (${err.message ?: err::class.simpleName})",
                            maxSessions = maxSessions,
                        )
                },
            )
        }
    }

    public companion object {
        public const val REFRESH_INTERVAL_MS: Long = 5_000L
    }
}
