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

    init {
        // B10: subscribe to live stats frames arriving on any active
        // session WS connection. These overlay REST poll values —
        // typically arrive at the server's own broadcast cadence
        // (~5 s on most configs) and bypass the REST round-trip.
        viewModelScope.launch {
            StatsHub.flow.collect { liveDto ->
                val current = _state.value
                if (current.stats != null) {
                    // Re-apply the last known session-list counts so the WS frame
                    // doesn't clobber our patched values with the server's lifetime counter.
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
            //
            // Track the Result (not .orEmpty()) so we can distinguish
            // "request succeeded with 0 sessions" from "request failed".
            // The old `if (sessionsTotal > 0)` guard caused the server's
            // lifetime counter (e.g. 2373) to flash in whenever the list
            // returned an empty page.
            val (sessionsResult, webSearchStatsResult, webSearchStatsV2Result) =
                coroutineScope {
                    val sessions = async { transport.listSessions() }
                    val webSearch = async { transport.fetchWebSearchStats().getOrNull() }
                    val webSearchV2 = async { transport.fetchWebSearchStatsV2().getOrNull() }
                    Triple(sessions.await(), webSearch.await(), webSearchV2.await())
                }
            val sessionsList = sessionsResult.getOrNull()  // null = request failed
            val sessionsTotal = sessionsList?.size ?: 0
            val sessionsRunning = sessionsList?.count { it.state == SessionState.Running } ?: 0
            val sessionsWaiting = sessionsList?.count { it.state == SessionState.Waiting } ?: 0
            // Cache for WS-frame patching (only update when the request succeeded).
            if (sessionsList != null) {
                cachedSessionCounts = Triple(sessionsTotal, sessionsRunning, sessionsWaiting)
            }
            val webSearchStats = webSearchStatsResult
            val webSearchStatsV2 = webSearchStatsV2Result
            transport.stats().fold(
                onSuccess = { dto ->
                    // Always use the list-derived counts when the request succeeded
                    // (even when the list is empty — 0 active sessions is the correct
                    // value). Only fall back to dto when the list request itself failed.
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
                            webSearchStats = webSearchStats,
                            webSearchStatsV2 = webSearchStatsV2,
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
