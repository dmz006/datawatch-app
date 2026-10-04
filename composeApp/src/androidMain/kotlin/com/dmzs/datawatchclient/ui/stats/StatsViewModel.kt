package com.dmzs.datawatchclient.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.transport.dto.WebSearchStatsDto
import com.dmzs.datawatchclient.transport.dto.WebSearchStatsV2Dto
import com.dmzs.datawatchclient.transport.ws.StatsHub
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        val webSearchStats: WebSearchStatsDto? = null,
        /** BL391 multi-provider stats (v8.39.0+). Null on older server versions. */
        val webSearchStatsV2: WebSearchStatsV2Dto? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    // Last live-list session counts — kept so WS stat frames can be patched with
    // the same values the REST poll used, preventing the server's lifetime counter
    // (sessionsTotal in /api/stats) from flashing in whenever a WS frame arrives.
    // Null = no successful list fetch yet; use dto as-is.
    private var cachedSessionCounts: Triple<Int, Int, Int>? = null // total, running, waiting

    private val _visible = MutableStateFlow(false)
    private var restCycleCount = 0

    init {
        // WS is the primary update path (like the PWA). `stats` frames arrive at the
        // server's own broadcast cadence (~5 s) and update the UI without any REST round-trip.
        viewModelScope.launch {
            StatsHub.flow.collect { liveDto ->
                val current = _state.value
                if (current.stats != null) {
                    val patched = cachedSessionCounts?.let { (total, running, waiting) ->
                        liveDto.copy(
                            sessionsTotal = total,
                            sessionsRunning = running,
                            sessionsWaiting = waiting,
                        )
                    } ?: liveDto
                    _state.value =
                        current.copy(
                            stats = patched,
                            refreshing = false,
                            banner = null,
                        )
                    ServiceLocator.refreshHomeWidgets()
                }
            }
        }
        // REST poll only runs while the card is on screen — WS handles background updates.
        // On becoming visible: immediate refresh, then every 30 s while still visible.
        viewModelScope.launch {
            _visible.collect { visible ->
                if (visible) {
                    doRefresh()
                    while (_visible.value && isActive) {
                        delay(REFRESH_INTERVAL_MS)
                        if (_visible.value) doRefresh()
                    }
                }
            }
        }
    }

    /** Called by the composable when it enters/leaves the screen. */
    public fun setVisible(visible: Boolean) {
        _visible.value = visible
    }

    /** Trigger a one-shot manual refresh (e.g. pull-to-refresh). */
    public fun refresh() {
        viewModelScope.launch { doRefresh() }
    }

    private suspend fun doRefresh() {
        val profile = ServiceLocator.activeProfileFlow().first()
        if (profile == null) {
            _state.value =
                _state.value.copy(
                    stats = null,
                    refreshing = false,
                    banner = "No enabled server. Add or enable one in Settings.",
                    serverName = null,
                )
            return
        }
        _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
        val transport = ServiceLocator.transportFor(profile)
        val infoResult = transport.fetchInfo()
        val maxSessions: Int? =
            _state.value.maxSessions
                ?: transport.fetchConfig().getOrNull()?.let { cfg ->
                    runCatching { cfg.raw["session.max_sessions"]?.jsonPrimitive?.content?.toInt() }.getOrNull()
                }
        // Fetch webSearch stats every 6th REST cycle (~3 min at 30s interval).
        // These cards don't need real-time freshness; keep them in state between cycles.
        val fetchWebSearch = (restCycleCount % WEB_SEARCH_POLL_EVERY == 0)
        restCycleCount++
        val (sessionsResult, webSearchStatsResult, webSearchStatsV2Result) =
            coroutineScope {
                val sessions = async { transport.listSessions() }
                val webSearch = async {
                    if (fetchWebSearch) transport.fetchWebSearchStats().getOrNull()
                    else _state.value.webSearchStats
                }
                val webSearchV2 = async {
                    if (fetchWebSearch) transport.fetchWebSearchStatsV2().getOrNull()
                    else _state.value.webSearchStatsV2
                }
                Triple(sessions.await(), webSearch.await(), webSearchV2.await())
            }
        val sessionsList = sessionsResult.getOrNull()
        val sessionsTotal = sessionsList?.size ?: 0
        val sessionsRunning = sessionsList?.count { it.state == SessionState.Running } ?: 0
        val sessionsWaiting = sessionsList?.count { it.state == SessionState.Waiting } ?: 0
        if (sessionsList != null) {
            cachedSessionCounts = Triple(sessionsTotal, sessionsRunning, sessionsWaiting)
        }
        transport.stats().fold(
            onSuccess = { dto ->
                val patched =
                    if (sessionsList != null) {
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
                        webSearchStats = webSearchStatsResult,
                        webSearchStatsV2 = webSearchStatsV2Result,
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

    public companion object {
        // REST poll is a fallback/reconnect path; WS `stats` frames drive real-time updates.
        // 30 s matches the PWA's scoped polling cadence.
        public const val REFRESH_INTERVAL_MS: Long = 30_000L

        // Fetch webSearch supplemental stats every Nth REST cycle (~3 min).
        private const val WEB_SEARCH_POLL_EVERY = 6
    }
}
