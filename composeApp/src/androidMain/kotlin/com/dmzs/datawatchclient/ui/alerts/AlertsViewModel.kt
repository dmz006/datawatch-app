package com.dmzs.datawatchclient.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Alerts tab. Fetches `/api/alerts` from the active server,
 * groups rows by `session_id` (or a synthetic `"__system__"` bucket
 * when the alert has none), and classifies each group as **active**
 * (session exists and state is not Completed/Killed/Error) or
 * **inactive** (terminal or orphaned).
 *
 * The PWA keeps chat-history in memory; we do the same for alerts —
 * poll every 5 s, cache the latest snapshot, let the UI re-render.
 * No SQLDelight table for alerts (yet); adding one would just
 * duplicate what the server already persists.
 *
 * Bottom-nav badge count: sum of **active** alerts only. Matches the
 * PWA's `Active (N)` label prior to the tab click.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class AlertsViewModel : ViewModel() {
    public enum class Tab { Active, Historical, System }

    public enum class ChipFilter { All, Prompt, Error, Warn, Info }
    public enum class SortMode { BySession, Chronological }

    public data class AlertGroup(
        val sessionId: String,
        val session: Session?,
        val alerts: List<Alert>,
    ) {
        /** Display label: user-assigned name first, then session short id, then system bucket. */
        public val label: String
            get() =
                when {
                    sessionId == SYSTEM_BUCKET -> "System"
                    session?.name?.isNotBlank() == true -> session.name ?: ""
                    else -> session?.id ?: sessionId.substringAfterLast('-')
                }

        /** Current session state, if known. Drives the per-group state pill. */
        public val state: SessionState? get() = session?.state

        /**
         * True when the PWA would file the group under the Active tab:
         * the session exists in the live list and is in a live state.
         */
        public val isActive: Boolean
            get() {
                if (sessionId == SYSTEM_BUCKET) return false
                val s = session ?: return false
                return s.state == SessionState.Running ||
                    s.state == SessionState.Waiting ||
                    s.state == SessionState.RateLimited ||
                    s.state == SessionState.New
            }

        public companion object {
            public const val SYSTEM_BUCKET: String = "__system__"
            /** Synthetic session id for the flat chronological view. */
            public const val CHRONO_BUCKET: String = "__chrono__"
        }
    }

    public data class UiState(
        val active: List<AlertGroup> = emptyList(),
        val historical: List<AlertGroup> = emptyList(),
        val system: List<AlertGroup> = emptyList(),
        val selectedTab: Tab = Tab.Active,
        val expandedSessionIds: Set<String> = emptySet(),
        val refreshing: Boolean = false,
        val banner: String? = null,
        val chipFilter: ChipFilter = ChipFilter.All,
        val sortMode: SortMode = SortMode.BySession,
        val searchQuery: String = "",
        /** Populated when sortMode == Chronological: flat list newest-first. */
        val flatChronoAlerts: List<Alert> = emptyList(),
        /**
         * When any sessions are watched, this is the badge count from watched
         * sessions only. When no sessions are watched (empty set), equals [count]
         * (backward-compat: all alerts contribute to the badge).
         */
        val watchedAlertCount: Int = 0,
        /** Per-chip counts computed from the current tab's alerts (search-filtered, not chip-filtered). */
        val chipCounts: Map<ChipFilter, Int> = emptyMap(),
        /** All enabled profiles for the server picker. */
        val allProfiles: List<ServerProfile> = emptyList(),
        /** True when the user has selected "All servers" mode. */
        val allServersMode: Boolean = false,
        /** The currently active single-server profile (null in all-servers mode). */
        val activeProfile: ServerProfile? = null,
        /** sessionId (or SYSTEM_BUCKET) → profile displayName; populated only in all-servers mode. */
        val groupProfileNames: Map<String, String> = emptyMap(),
    ) {
        /** Bottom-nav badge — **active-only** count matches the PWA label `Active (N)`. */
        public val count: Int
            get() = active.sumOf { it.alerts.size }

        public val visibleGroups: List<AlertGroup>
            get() =
                when (selectedTab) {
                    Tab.Active -> active
                    Tab.Historical -> historical
                    Tab.System -> system
                }
    }

    private val _refreshing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)
    private val _selectedTab = MutableStateFlow(Tab.Active)
    private val _expanded = MutableStateFlow<Set<String>>(emptySet())

    // Authoritative alerts snapshot. Refreshed on a short interval
    // while the tab is mounted (5 s matches the Sessions-tab poll
    // cadence; PWA rerenders on every WS update, which is finer-
    // grained but overkill on mobile).
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())

    // Sprint 22 (#115) filter / sort / search flows.
    private val _chipFilter = MutableStateFlow(ChipFilter.All)
    private val _sortMode = MutableStateFlow(SortMode.BySession)
    private val _search = MutableStateFlow("")

    private val _allProfiles =
        ServiceLocator.profileRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _allServersModeFlow =
        ServiceLocator.activeServerStore.observe()
            .map { it == ActiveServerStore.SENTINEL_ALL_SERVERS }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _groupProfileNames = MutableStateFlow<Map<String, String>>(emptyMap())

    // Active profile — alerts are per-server, so we re-fetch whenever
    // the user flips the active server.
    private val activeProfileFlow =
        combine(
            ServiceLocator.profileRepository.observeAll(),
            ServiceLocator.activeServerStore.observe(),
        ) { profiles, id ->
            if (id == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine null
            profiles.firstOrNull { it.id == id && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        }

    // Sprint 23 (#116) — watched-session filter. Re-emits whenever the
    // operator watches/unwatches a session on the active profile.
    private val _watchedIds =
        activeProfileFlow.flatMapLatest { profile ->
            if (profile == null) {
                flowOf(emptySet())
            } else {
                ServiceLocator.watchedSessionsStore.watchedFlow(profile.id)
            }
        }

    init {
        // Restore persisted tab + per-tab filter state on startup.
        loadPersistedTabState()
        // Polling loop. Cancelled automatically on VM clear.
        viewModelScope.launch {
            combine(
                activeProfileFlow,
                _allServersModeFlow,
            ) { profile, allMode -> Pair(profile, allMode) }.collect { (profile, allMode) ->
                _alerts.value = emptyList()
                _groupProfileNames.value = emptyMap()
                while (true) {
                    _refreshing.value = true
                    if (allMode) {
                        val profiles = _allProfiles.value.filter { it.enabled }
                        val mergedAlerts = mutableListOf<Alert>()
                        val nameMap = mutableMapOf<String, String>()
                        val errors = mutableListOf<String>()
                        coroutineScope {
                            profiles.map { p ->
                                async {
                                    ServiceLocator.transportFor(p).listAlerts().fold(
                                        onSuccess = { result ->
                                            synchronized(mergedAlerts) {
                                                result.alerts.forEach { alert ->
                                                    mergedAlerts.add(alert)
                                                    val key = alert.sessionId ?: AlertGroup.SYSTEM_BUCKET
                                                    nameMap[key] = p.displayName
                                                }
                                            }
                                        },
                                        onFailure = { err ->
                                            synchronized(errors) { errors += "${p.displayName}: ${err.message ?: err::class.simpleName}" }
                                        },
                                    )
                                }
                            }.awaitAll()
                        }
                        _alerts.value = mergedAlerts.sortedByDescending { it.createdAt }
                        _groupProfileNames.value = nameMap.toMap()
                        _banner.value = if (errors.isEmpty()) null else "Some unreachable: " + errors.take(2).joinToString("; ")
                    } else {
                        if (profile == null) { _refreshing.value = false; break }
                        ServiceLocator.transportFor(profile).listAlerts().fold(
                            onSuccess = { _alerts.value = it.alerts; _groupProfileNames.value = emptyMap(); _banner.value = null },
                            onFailure = { err -> _banner.value = "Alerts fetch failed — ${err.message ?: err::class.simpleName}" },
                        )
                    }
                    _refreshing.value = false
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun prefs() =
        android.preference.PreferenceManager.getDefaultSharedPreferences(ServiceLocator.context())

    private fun loadPersistedTabState() {
        val p = prefs()
        val tabName = p.getString(PREF_ACTIVE_TAB, Tab.Active.name) ?: Tab.Active.name
        val tab = runCatching { Tab.valueOf(tabName) }.getOrDefault(Tab.Active)
        _selectedTab.value = tab
        _chipFilter.value = runCatching {
            ChipFilter.valueOf(p.getString("alerts_${tab.name.lowercase()}_chip", ChipFilter.All.name) ?: ChipFilter.All.name)
        }.getOrDefault(ChipFilter.All)
        _sortMode.value = runCatching {
            SortMode.valueOf(p.getString("alerts_${tab.name.lowercase()}_sort", SortMode.BySession.name) ?: SortMode.BySession.name)
        }.getOrDefault(SortMode.BySession)
        _search.value = p.getString("alerts_${tab.name.lowercase()}_search", "") ?: ""
    }

    private fun saveTabState(tab: Tab) {
        prefs().edit()
            .putString("alerts_${tab.name.lowercase()}_chip", _chipFilter.value.name)
            .putString("alerts_${tab.name.lowercase()}_sort", _sortMode.value.name)
            .putString("alerts_${tab.name.lowercase()}_search", _search.value)
            .apply()
    }

    /**
     * Observes the active profile's session list so we can classify
     * each alert group as Active vs Inactive. When the session is
     * missing from the list (deleted, orphaned, cross-server), we
     * treat the group as Inactive — mirrors PWA app.js:5558-5566.
     */
    private val sessionsFlow =
        combine(activeProfileFlow, _allServersModeFlow, _allProfiles) { profile, allMode, profiles ->
            Triple(profile, allMode, profiles)
        }.flatMapLatest { (profile, allMode, profiles) ->
            when {
                allMode -> {
                    val enabled = profiles.filter { it.enabled }
                    if (enabled.isEmpty()) flowOf(emptyList())
                    else combine(
                        enabled.map { ServiceLocator.sessionRepository.observeForProfile(it.id) }
                    ) { arrays: Array<List<Session>> -> arrays.flatMap { it } }
                }
                profile == null -> flowOf(emptyList())
                else -> ServiceLocator.sessionRepository.observeForProfile(profile.id)
            }
        }

    /**
     * Inner combine (5 flows) produces the base grouped state.
     * Outer combine folds in the 3 new Sprint-22 filter flows.
     */
    private val innerState =
        combine(
            _alerts,
            sessionsFlow,
            _selectedTab,
            _expanded,
            _refreshing,
        ) { alerts, sessions, tab, expanded, refreshing ->
            val sessionByFullId: Map<String, Session> =
                sessions.associateBy { it.fullId }
            val sessionByShortId: Map<String, Session> =
                sessions.associateBy { it.id }

            // Group alerts by session_id. Null session ids go into
            // the synthetic SYSTEM_BUCKET.
            val grouped: Map<String, List<Alert>> =
                alerts.groupBy { it.sessionId ?: AlertGroup.SYSTEM_BUCKET }

            val groups =
                grouped.map { (sid, groupAlerts) ->
                    val session =
                        sessionByFullId[sid] ?: sessionByShortId[sid]
                    AlertGroup(
                        sessionId = sid,
                        session = session,
                        alerts = groupAlerts.sortedByDescending { it.createdAt },
                    )
                }

            // Separate system-bucket alerts into their own list for the System tab.
            val (sysGroups, sessionGroups) = groups.partition { it.sessionId == AlertGroup.SYSTEM_BUCKET }
            val (active, historical) = sessionGroups.partition { it.isActive }
            // Return a partial UiState; chip/sort/search applied in outer combine.
            UiState(
                active = active.sortedByDescending { it.alerts.maxOfOrNull { a -> a.createdAt } },
                historical = historical.sortedByDescending { it.alerts.maxOfOrNull { a -> a.createdAt } },
                system = sysGroups.sortedByDescending { it.alerts.maxOfOrNull { a -> a.createdAt } },
                selectedTab = tab,
                expandedSessionIds = expanded,
                refreshing = refreshing,
                banner = _banner.value,
            )
        }

    /**
     * Combined state: chip/sort/search filters + watched-session filter.
     * Two-level combine to stay within the 5-argument `combine` limit.
     */
    private val filteredState =
        combine(
            innerState,
            _chipFilter,
            _sortMode,
            _search,
        ) { inner, chip, sort, search ->
            fun matchesSearch(alert: Alert): Boolean =
                search.isBlank() ||
                    alert.title.contains(search, ignoreCase = true) ||
                    alert.message.contains(search, ignoreCase = true)

            val promptRegex = Regex("\\b(needs input|prompt|waiting)\\b", RegexOption.IGNORE_CASE)
            fun matchesChip(alert: Alert, sessState: SessionState?): Boolean =
                when (chip) {
                    ChipFilter.All -> true
                    ChipFilter.Prompt ->
                        sessState == SessionState.Waiting ||
                            alert.type.contains("input", ignoreCase = true) ||
                            alert.type == "needs_input" ||
                            alert.type == "input_needed" ||
                            promptRegex.containsMatchIn(alert.title)
                    ChipFilter.Error ->
                        alert.severity == AlertSeverity.Error ||
                            alert.type.contains("error", ignoreCase = true)
                    ChipFilter.Warn ->
                        alert.severity == AlertSeverity.Warning
                    ChipFilter.Info ->
                        alert.severity == AlertSeverity.Info
                }

            fun filterGroup(group: AlertGroup): AlertGroup? {
                val filtered = group.alerts.filter { matchesChip(it, group.state) && matchesSearch(it) }
                return if (filtered.isEmpty()) null else group.copy(alerts = filtered)
            }

            val filteredActive = inner.active.mapNotNull { filterGroup(it) }
            val filteredHistorical = inner.historical.mapNotNull { filterGroup(it) }
            val filteredSystem = inner.system.mapNotNull { filterGroup(it) }

            // Chip counts: count from current tab's alerts filtered by search only (not chip).
            // Matches PWA catOf() which also considers sessState === 'waiting_input'.
            val rawTabGroups = when (inner.selectedTab) {
                Tab.Active -> inner.active
                Tab.Historical -> inner.historical
                Tab.System -> inner.system
            }
            val rawTabGroupedAlerts = rawTabGroups.flatMap { group ->
                group.alerts.filter { matchesSearch(it) }.map { Pair(it, group.state) }
            }
            fun isPromptAlert(alert: Alert, sessState: SessionState?): Boolean =
                sessState == SessionState.Waiting ||
                    alert.type.contains("input", ignoreCase = true) ||
                    alert.type == "needs_input" || alert.type == "input_needed" ||
                    promptRegex.containsMatchIn(alert.title)
            val chipCounts = mapOf(
                ChipFilter.All to rawTabGroupedAlerts.size,
                ChipFilter.Prompt to rawTabGroupedAlerts.count { (a, s) -> isPromptAlert(a, s) },
                ChipFilter.Error to rawTabGroupedAlerts.count { (a, _) ->
                    a.severity == AlertSeverity.Error || a.type.contains("error", ignoreCase = true)
                },
                ChipFilter.Warn to rawTabGroupedAlerts.count { (a, _) -> a.severity == AlertSeverity.Warning },
                ChipFilter.Info to rawTabGroupedAlerts.count { (a, _) -> a.severity == AlertSeverity.Info },
            )

            val flatChrono: List<Alert> =
                if (sort == SortMode.Chronological) {
                    (filteredActive + filteredHistorical + filteredSystem)
                        .flatMap { it.alerts }
                        .sortedByDescending { it.createdAt }
                } else {
                    emptyList()
                }

            inner.copy(
                active = filteredActive,
                historical = filteredHistorical,
                system = filteredSystem,
                chipFilter = chip,
                sortMode = sort,
                searchQuery = search,
                flatChronoAlerts = flatChrono,
                chipCounts = chipCounts,
            )
        }

    private val _computedActiveProfile =
        combine(_allProfiles, ServiceLocator.activeServerStore.observe()) { profiles, id ->
            if (id == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine null
            profiles.firstOrNull { it.id == id && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        }

    public val reachable: StateFlow<Boolean?> = _computedActiveProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf<Boolean?>(null)
            else ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public val lastProbeEpochMs: StateFlow<Long?> = reachable
        .runningFold(null as Long?) { acc, r -> if (r == true) System.currentTimeMillis() else acc }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public val state: StateFlow<UiState> =
        combine(
            combine(filteredState, _watchedIds) { filtered, watchedIds ->
                // Sprint 23 (#116): when any sessions are watched, badge shows only
                // those sessions' active-alert count. When nothing is watched
                // (operator hasn't opted in), badge falls back to total count so
                // existing behavior is preserved.
                val watchedActiveCount =
                    if (watchedIds.isEmpty()) {
                        filtered.count
                    } else {
                        filtered.active
                            .filter { group -> group.sessionId in watchedIds }
                            .sumOf { it.alerts.size }
                    }
                filtered.copy(watchedAlertCount = watchedActiveCount)
            },
            _allProfiles,
            _allServersModeFlow,
            _groupProfileNames,
            _computedActiveProfile,
        ) { watched, profiles, allMode, groupNames, activeProf ->
            watched.copy(
                allProfiles = profiles.filter { it.enabled },
                allServersMode = allMode,
                groupProfileNames = groupNames,
                activeProfile = activeProf,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    public fun selectTab(tab: Tab) {
        saveTabState(_selectedTab.value)
        _selectedTab.value = tab
        prefs().edit().putString(PREF_ACTIVE_TAB, tab.name).apply()
        val p = prefs()
        _chipFilter.value = runCatching {
            ChipFilter.valueOf(p.getString("alerts_${tab.name.lowercase()}_chip", ChipFilter.All.name) ?: ChipFilter.All.name)
        }.getOrDefault(ChipFilter.All)
        _sortMode.value = runCatching {
            SortMode.valueOf(p.getString("alerts_${tab.name.lowercase()}_sort", SortMode.BySession.name) ?: SortMode.BySession.name)
        }.getOrDefault(SortMode.BySession)
        _search.value = p.getString("alerts_${tab.name.lowercase()}_search", "") ?: ""
    }

    public fun setChipFilter(f: ChipFilter) { _chipFilter.value = f }
    public fun setSortMode(m: SortMode) { _sortMode.value = m }
    public fun setSearch(q: String) { _search.value = q }

    /** Toggle a per-session group's expanded state. */
    public fun toggleExpanded(sessionId: String) {
        _expanded.value =
            _expanded.value.toMutableSet().also {
                if (!it.add(sessionId)) it.remove(sessionId)
            }
    }

    /**
     * Mute the underlying session so it drops from the active list.
     * Preserves the alert rows themselves — the PWA Inactive tab
     * keeps the history of muted / terminal-state sessions.
     */
    public fun dismissSession(sessionId: String) {
        viewModelScope.launch {
            // Try the short-id side first; the Session table is keyed
            // on short id and that's what the Sessions list uses.
            ServiceLocator.sessionRepository.setMuted(sessionId, muted = true)
        }
    }

    /**
     * Mark a single alert as read server-side. The PWA uses this to
     * drop rows from its unread-count badge.
     */
    public fun markAlertRead(alertId: String) {
        viewModelScope.launch {
            val profile =
                ServiceLocator.profileRepository.observeAll().firstOrNull()
                    ?.firstOrNull { it.enabled } ?: return@launch
            ServiceLocator.transportFor(profile).markAlertRead(alertId, all = false)
            // Optimistic local drop so the UI updates before next poll.
            _alerts.value =
                _alerts.value.map {
                    if (it.id == alertId) it.copy(read = true) else it
                }
        }
    }

    /**
     * Dismiss all alerts server-side. Mirrors PWA alpha.30 "dismiss all" action.
     */
    public fun dismissAll() {
        viewModelScope.launch {
            val profile =
                ServiceLocator.profileRepository.observeAll().firstOrNull()
                    ?.firstOrNull { it.enabled } ?: return@launch
            ServiceLocator.transportFor(profile).markAlertRead(alertId = null, all = true)
            _alerts.value = emptyList()
        }
    }

    public fun dismissBanner() {
        _banner.value = null
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun selectAllServers() {
        ServiceLocator.activeServerStore.set(ActiveServerStore.SENTINEL_ALL_SERVERS)
    }

    /** Trigger an immediate poll (e.g. after tapping the reachability-dot retry button). */
    public fun refresh() {
        viewModelScope.launch { _refreshing.value = true }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val PREF_ACTIVE_TAB = "alerts_active_tab"
    }
}
