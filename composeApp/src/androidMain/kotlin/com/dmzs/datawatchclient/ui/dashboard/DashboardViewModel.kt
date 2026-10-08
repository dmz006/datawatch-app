package com.dmzs.datawatchclient.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AnalyticsDto
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.SmokeProgressDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** Parity D34a — status boards of active sessions (telemetry tasks + verdicts). */
    val boards: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    /** Parity D34a — today's cost from GET /api/cost (0 when unknown). */
    val costTodayUsd: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
public class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())

    private val _allProfiles =
        ServiceLocator.profileRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeId =
        ServiceLocator.activeServerStore.observe()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _computedActiveProfile: StateFlow<ServerProfile?> =
        // #234 — resolve over real profiles + proxied remotes.
        combine(ServiceLocator.profilesWithProxied(), _activeId) { profiles, storedId ->
            val enabled = profiles.filter { it.enabled }
            if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine enabled.firstOrNull()
            storedId?.let { id -> enabled.firstOrNull { it.id == id } } ?: enabled.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DashboardState> =
        combine(_state, _allProfiles, _computedActiveProfile) { s, profiles, active ->
            s.copy(allProfiles = profiles.filter { it.enabled }, activeProfile = active)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardState())

    public val reachable: StateFlow<Boolean?> =
        _computedActiveProfile
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf<Boolean?>(null)
                } else {
                    ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public val lastProbeEpochMs: StateFlow<Long?> =
        reachable
            .runningFold(null as Long?) { acc, r -> if (r == true) System.currentTimeMillis() else acc }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            // #236.7 — re-keyed on the resolved profile id (proxied remotes
            // included), so a switch refetches at once; a display-name refresh
            // of the same server doesn't restart the poll.
            _computedActiveProfile
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collectLatest { profile ->
                    _state.value =
                        _state.value.copy(
                            cardsLoaded = false,
                            cards = emptyList(),
                            sessions = emptyList(),
                            stats = null,
                            prds = emptyList(),
                            boards = emptyList(),
                            error = null,
                        )
                    start(profile)
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

    /**
     * Transport for the resolved active profile. #234/#236 — this used to
     * look the stored id up among real profiles only, so a proxied remote
     * (and "All", which falls back to the first server) never loaded.
     */
    private fun resolveTransport(): TransportClient? = _computedActiveProfile.value?.let { ServiceLocator.transportFor(it) }

    private suspend fun start(profile: ServerProfile?) {
        val transport = profile?.let { ServiceLocator.transportFor(it) }
        transport?.listDashboardCards()?.onSuccess { cards ->
            _state.value = _state.value.copy(cards = cards)
        }
        _state.value = _state.value.copy(cardsLoaded = true)

        var slowPollTick = 0
        while (currentCoroutineContext().isActive) {
            val t = transport
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
                    // Parity D34a stat strip inputs: cost + active-session boards.
                    t.fetchCostSummaryJson().onSuccess { c -> _state.value = _state.value.copy(costTodayUsd = costTotalUsd(c)) }
                    val active =
                        _state.value.sessions.filter {
                            it.state == com.dmzs.datawatchclient.domain.SessionState.Running ||
                                it.state == com.dmzs.datawatchclient.domain.SessionState.Waiting
                        }.take(MAX_BOARDS)
                    val boards = active.mapNotNull { s -> t.fetchSessionStatusJson(s.fullId ?: s.id).getOrNull() }
                    _state.value = _state.value.copy(boards = boards)
                }
            }
            slowPollTick++
            delay(POLL_MS)
        }
    }

    private companion object {
        const val MAX_BOARDS = 12
        const val POLL_MS = 10_000L
        val ACTIVE_PRD_STATUSES = setOf("running", "decomposing", "planning", "approved")
    }
}

/** PWA `_dashUpdateStatBar` aggregate for the Dashboard header strip (D34a). */
public data class DashStatStrip(
    val sessions: Int,
    val active: Int,
    val tasksDone: Int,
    val tasksTotal: Int,
    val verdictBlock: Int,
    val verdictWarn: Int,
    val costUsd: Double,
    val automata: Int,
)

private fun kotlinx.serialization.json.JsonObject.arr(key: String): List<kotlinx.serialization.json.JsonObject> =
    (this[key] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? kotlinx.serialization.json.JsonObject }

private fun kotlinx.serialization.json.JsonObject.str(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

/** `total_cost_usd`, else the sum of `sessions[].est_cost_usd` (PWA). */
internal fun costTotalUsd(c: kotlinx.serialization.json.JsonObject): Double =
    c.str("total_cost_usd")?.toDoubleOrNull()
        ?: c.arr("sessions").sumOf { it.str("est_cost_usd")?.toDoubleOrNull() ?: 0.0 }

internal fun dashStatStrip(
    sessions: List<Session>,
    boards: List<kotlinx.serialization.json.JsonObject>,
    costUsd: Double,
    automata: Int,
): DashStatStrip {
    var done = 0
    var total = 0
    var block = 0
    var warn = 0
    boards.forEach { b ->
        val tel = b["telemetry"] as? kotlinx.serialization.json.JsonObject ?: return@forEach
        val tasks = tel.arr("tasks")
        total += tasks.size
        done += tasks.count { it.str("status") == "completed" }
        val gv = tel.arr("guardrail_verdicts")
        block += gv.count { it.str("outcome") == "block" }
        warn += gv.count { it.str("outcome") == "warn" }
    }
    val active =
        sessions.count {
            it.state == com.dmzs.datawatchclient.domain.SessionState.Running ||
                it.state == com.dmzs.datawatchclient.domain.SessionState.Waiting
        }
    return DashStatStrip(sessions.size, active, done, total, block, warn, costUsd, automata)
}
