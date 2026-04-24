package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sessions tab VM. Observes cached sessions for the currently active server profile.
 *
 * "Active" is resolved by combining the persisted [ActiveServerStore] selection with
 * the live profile list: if the stored id still points at an enabled profile it wins;
 * otherwise we fall back to the first enabled profile (so deleting the active server
 * degrades gracefully). Sprint 3 Phase 3 will add all-servers fan-out as a separate mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SessionsViewModel : ViewModel() {
    public enum class SortOrder(public val label: String) {
        RecentActivity("Recent activity"),
        StartedAt("Started"),
        Name("Name"),
        Custom("Custom"),
    }

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val allProfiles: List<ServerProfile> = emptyList(),
        val allServersMode: Boolean = false,
        val sessions: List<Session> = emptyList(),
        val sortOrder: SortOrder = SortOrder.RecentActivity,
        /**
         * Backend chip text keyed by server-profile id. Per-session
         * backend now comes from [Session.backend] directly; this map
         * is retained only as a per-server fallback for rows whose
         * session payload omitted the field.
         */
        val backendByProfileId: Map<String, String> = emptyMap(),
        /**
         * Free-text filter applied on top of [partitionedSessions].
         * Matches PWA: case-insensitive substring across name / task /
         * id / backend.
         */
        val filterText: String = "",
        /**
         * Optional backend-name chip filter. Null means no backend
         * filter; otherwise only rows where `session.backend == it`
         * show. Populated by the backend badges in the toolbar.
         */
        val backendFilter: String? = null,
        /**
         * When false (default), the list shows only active + recently-
         * completed sessions (done within [RECENT_WINDOW_MINUTES]).
         * When true, every session including deep history is shown.
         */
        val showHistory: Boolean = false,
        /**
         * In-memory user-arranged ordering for sessions on the
         * current profile. Only consulted when [sortOrder] is
         * [SortOrder.Custom]. Session ids not in the list fall to
         * the tail (sorted by lastActivityAt among themselves).
         */
        val customOrder: List<String> = emptyList(),
        /**
         * True while the user is in reorder-mode — the list row
         * composable swaps its action bar for up / down arrows
         * that shift session position in the [customOrder] list.
         */
        val reorderMode: Boolean = false,
        val refreshing: Boolean = false,
        val banner: String? = null,
        /**
         * Active profile's cached reachability. `null` when no profile is
         * active or when we are in all-servers mode (the indicator hides).
         * Reflects the most recent transport activity — a bare `false`
         * initial value before any probe returns is rendered as "probing"
         * (grey) in the UI, not "unreachable" (red).
         */
        val activeReachable: Boolean? = null,
        /** Wall-clock timestamp of the most recent successful ping/refresh. */
        val lastProbeEpochMs: Long? = null,
        /**
         * True once the active server has confirmed it supports
         * `POST /api/sessions/delete`. Starts true (optimistic); flips to
         * false when a delete attempt returns [TransportError.NotFound].
         * The UI greys the Delete menu item when false.
         */
        val deleteSupported: Boolean = true,
    ) {
        /**
         * Unique backend names across the current session pool, with
         * their counts, used to render the PWA-style backend filter
         * badges in the toolbar. Sorted for stable layout.
         */
        public val backendCounts: List<Pair<String, Int>>
            get() =
                sessions.mapNotNull { it.backend?.takeIf { b -> b.isNotBlank() } }
                    .groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedBy { it.first }

        /**
         * Sessions after partitioning + filter application. Matches the
         * PWA's `renderSessionsView` pool computation:
         *   1. Compute active / recent / history buckets;
         *   2. Default pool = active + recent; `showHistory` swaps in everything;
         *   3. Apply text + backend filters;
         *   4. Order: active → waiting_input first (PWA sorts by state),
         *      then most-recent activity.
         */
        public val visibleSessions: List<Session>
            get() {
                val nowMs = System.currentTimeMillis()
                val doneStates =
                    setOf(
                        com.dmzs.datawatchclient.domain.SessionState.Completed,
                        com.dmzs.datawatchclient.domain.SessionState.Killed,
                        com.dmzs.datawatchclient.domain.SessionState.Error,
                    )
                val recentWindowMs = RECENT_WINDOW_MINUTES * 60_000L
                val pool =
                    if (showHistory) {
                        sessions
                    } else {
                        sessions.filter { s ->
                            s.state !in doneStates ||
                                (nowMs - s.lastActivityAt.toEpochMilliseconds()) < recentWindowMs
                        }
                    }
                val q = filterText.trim().lowercase()
                val filtered =
                    pool.asSequence()
                        .filter { s ->
                            q.isEmpty() ||
                                (s.name?.lowercase()?.contains(q) == true) ||
                                (s.taskSummary?.lowercase()?.contains(q) == true) ||
                                s.id.lowercase().contains(q) ||
                                (s.backend?.lowercase()?.contains(q) == true)
                        }
                        .filter { s -> backendFilter == null || s.backend == backendFilter }
                        .toList()
                // State-bucket sort always wins (waiting → running → …)
                // then within-bucket applies the user-selected sort order.
                val stateBucket =
                    compareBy<Session> { s ->
                        when (s.state) {
                            com.dmzs.datawatchclient.domain.SessionState.Waiting -> 0
                            com.dmzs.datawatchclient.domain.SessionState.Running -> 1
                            com.dmzs.datawatchclient.domain.SessionState.RateLimited -> 2
                            com.dmzs.datawatchclient.domain.SessionState.New -> 3
                            com.dmzs.datawatchclient.domain.SessionState.Completed,
                            com.dmzs.datawatchclient.domain.SessionState.Killed,
                            com.dmzs.datawatchclient.domain.SessionState.Error,
                            -> 4
                        }
                    }
                val withinBucket: Comparator<Session> =
                    when (sortOrder) {
                        SortOrder.RecentActivity -> compareByDescending { it.lastActivityAt }
                        SortOrder.StartedAt -> compareByDescending { it.createdAt }
                        SortOrder.Name ->
                            compareBy {
                                (it.name?.takeIf { n -> n.isNotBlank() } ?: it.taskSummary ?: it.id)
                                    .lowercase()
                            }
                        SortOrder.Custom -> {
                            val orderIx = customOrder.withIndex().associate { (i, id) -> id to i }
                            compareBy<Session> { orderIx[it.id] ?: Int.MAX_VALUE }
                                .thenByDescending { it.lastActivityAt }
                        }
                    }
                return filtered.sortedWith(stateBucket.then(withinBucket))
            }

        public val historyCount: Int
            get() {
                val doneStates =
                    setOf(
                        com.dmzs.datawatchclient.domain.SessionState.Completed,
                        com.dmzs.datawatchclient.domain.SessionState.Killed,
                        com.dmzs.datawatchclient.domain.SessionState.Error,
                    )
                return sessions.count { it.state in doneStates }
            }

        private companion object {
            const val RECENT_WINDOW_MINUTES: Long = 5
        }
    }

    private val allProfiles: StateFlow<List<ServerProfile>> =
        ServiceLocator.profileRepository
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    private val activeId: StateFlow<String?> =
        ServiceLocator.activeServerStore.observe()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val activeProfile: StateFlow<ServerProfile?> =
        combine(
            allProfiles,
            activeId,
        ) { profiles, storedId ->
            val enabled = profiles.filter { it.enabled }
            if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine null
            storedId?.let { id -> enabled.firstOrNull { it.id == id } }
                ?: enabled.firstOrNull()
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val allServersMode: StateFlow<Boolean> =
        activeId
            .map { it == ActiveServerStore.SENTINEL_ALL_SERVERS }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val _refreshing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)
    private val _filterText = MutableStateFlow("")
    private val _backendFilter = MutableStateFlow<String?>(null)
    private val _showHistory = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.RecentActivity)

    /**
     * Per-profile custom ordering: list of session ids in the order
     * the user arranged them. In-memory only for now (Compose doesn't
     * ship a drag-reorder LazyColumn; we add up/down arrow mode
     * activated from the toolbar). Persistence across app restarts
     * TBD.
     */
    private val _customOrder: MutableStateFlow<List<String>> =
        MutableStateFlow(emptyList())
    private val _reorderMode = MutableStateFlow(false)
    private val _allServersSessions = MutableStateFlow<List<Session>>(emptyList())
    private val _lastProbeEpochMs = MutableStateFlow<Long?>(null)
    private val _deleteSupported = MutableStateFlow(true)
    private val _backendByProfileId = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * Per-active-profile reachability. Flattens into `null` when the active
     * profile is null (no server selected) or when the user is in all-servers
     * mode — the TopAppBar indicator hides in those cases rather than
     * misrepresenting any one server's state.
     */
    private val activeReachable: StateFlow<Boolean?> =
        activeProfile
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf<Boolean?>(null)
                } else {
                    ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    public val state: StateFlow<UiState> = combineLatestState()

    private fun combineLatestState(): StateFlow<UiState> {
        val perProfileSessionsFlow =
            activeProfile.flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(emptyList())
                } else {
                    ServiceLocator.sessionRepository.observeForProfile(profile.id)
                }
            }
        // Choose source by mode — the all-servers list is fed by refresh()
        // since there's no per-profile cache merge layer.
        val sessionsFlow =
            combine(
                allServersMode,
                perProfileSessionsFlow,
                _allServersSessions,
            ) { all, single, federated ->
                if (all) federated else single
            }
        return combine(
            activeProfile,
            allProfiles,
            sessionsFlow,
            _refreshing,
            _banner,
            _filterText,
            _backendFilter,
            _showHistory,
            _sortOrder,
            allServersMode,
            activeReachable,
            _lastProbeEpochMs,
            _deleteSupported,
            _backendByProfileId,
            _customOrder,
            _reorderMode,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            UiState(
                activeProfile = args[0] as ServerProfile?,
                allProfiles = args[1] as List<ServerProfile>,
                sessions = args[2] as List<Session>,
                refreshing = args[3] as Boolean,
                banner = args[4] as String?,
                filterText = args[5] as String,
                backendFilter = args[6] as String?,
                showHistory = args[7] as Boolean,
                sortOrder = args[8] as SortOrder,
                allServersMode = args[9] as Boolean,
                activeReachable = args[10] as Boolean?,
                lastProbeEpochMs = args[11] as Long?,
                deleteSupported = args[12] as Boolean,
                backendByProfileId = args[13] as Map<String, String>,
                customOrder = args[14] as List<String>,
                reorderMode = args[15] as Boolean,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Auto-refresh whenever the active profile identity changes (non-null).
        activeProfile
            .onEach { if (it != null) refresh() }
            .launchIn(viewModelScope)
        // Periodic poll — PWA refreshes on every WS `session_update` tick;
        // mobile doesn't subscribe to WS at the list level yet, so we poll
        // REST instead. 5 s matches the StatsViewModel cadence and keeps
        // the reachability dot + state pills live-ish without draining
        // battery. Ref: `internal/server/web/app.js` loadSessions().
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(AUTO_REFRESH_MS)
                if (!_refreshing.value) refresh()
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_MS: Long = 5_000L
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun selectAllServers() {
        ServiceLocator.activeServerStore.set(ActiveServerStore.SENTINEL_ALL_SERVERS)
    }

    public fun setFilterText(text: String) {
        _filterText.value = text
    }

    /**
     * Toggles the backend chip filter — tapping the same chip twice
     * clears it, matching the PWA's `setBackendFilter` behaviour.
     */
    public fun toggleBackendFilter(backend: String) {
        _backendFilter.value = if (_backendFilter.value == backend) null else backend
    }

    public fun toggleShowHistory() {
        _showHistory.value = !_showHistory.value
    }

    public fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    public fun toggleReorderMode() {
        _reorderMode.value = !_reorderMode.value
        // Entering reorder snaps sort to Custom so the ordering the
        // user produces is what shows next. Seed customOrder with
        // the current visible order so the first moves work.
        if (_reorderMode.value && _sortOrder.value != SortOrder.Custom) {
            val snap = state.value.visibleSessions.map { it.id }
            _customOrder.value = snap
            _sortOrder.value = SortOrder.Custom
        }
    }

    /** Move the session [sessionId] one slot toward the top of the custom ordering. */
    public fun moveUp(sessionId: String) {
        val list = _customOrder.value.toMutableList()
        val idx = list.indexOf(sessionId)
        if (idx <= 0) return
        list.removeAt(idx)
        list.add(idx - 1, sessionId)
        _customOrder.value = list
    }

    /** Move the session [sessionId] one slot toward the bottom. */
    public fun moveDown(sessionId: String) {
        val list = _customOrder.value.toMutableList()
        val idx = list.indexOf(sessionId)
        if (idx < 0 || idx >= list.size - 1) return
        list.removeAt(idx)
        list.add(idx + 1, sessionId)
        _customOrder.value = list
    }

    public fun toggleMute(
        sessionId: String,
        currentlyMuted: Boolean,
    ) {
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(sessionId, !currentlyMuted)
        }
    }

    /** Rename a session on the server; refresh the list on success. */
    public fun rename(
        sessionId: String,
        newName: String,
    ) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).renameSession(fullIdFor(sessionId), newName).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _banner.value = "Rename failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /**
     * Send a plain-text reply to a session without opening detail.
     * Powers the Sessions-list quick-commands popup (System / Saved /
     * Custom) on waiting_input rows. ESC / Ctrl-b special keys are
     * out of scope here — they need the WS `command` channel which
     * the list view doesn't currently subscribe to; users needing
     * those open the session detail.
     */
    public fun quickReply(
        sessionId: String,
        text: String,
    ) {
        val profile = profileForSession(sessionId) ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).replyToSession(sessionId, trimmed).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _banner.value = "Reply failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /**
     * Load the server's saved-command list so the Sessions-list
     * quick-commands popup can render the "Saved" optgroup matching
     * the PWA. Cached per-profile; resolves null on fetch failure so
     * the popup just hides the Saved section.
     */
    public suspend fun fetchSavedCommands(sessionId: String): List<Pair<String, String>> {
        val profile = profileForSession(sessionId) ?: return emptyList()
        return ServiceLocator.transportFor(profile).listCommands().fold(
            onSuccess = { list -> list.map { it.name to it.command } },
            onFailure = { emptyList() },
        )
    }

    /**
     * Signal the server to kill a running session. Uses the same confirm-
     * path as the detail screen (ADR-0019), but surfaced inline so the
     * list-row quick-action button doesn't force a detail navigation.
     */
    public fun kill(sessionId: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).killSession(fullIdFor(sessionId)).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _banner.value = "Kill failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /** Warm-resume a completed or failed session. Refresh on success. */
    public fun restart(sessionId: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).restartSession(fullIdFor(sessionId)).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _banner.value = "Restart failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /**
     * Delete a session on the server. Flips [_deleteSupported] to false on a
     * [TransportError.NotFound] so the UI greys the Delete menu item across
     * the remainder of this VM's lifetime (i.e. this server session).
     */
    public fun delete(sessionId: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).deleteSession(fullIdFor(sessionId)).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _deleteSupported.value = false
                        _banner.value =
                            "This server doesn't support session delete. Contact dmz006/datawatch."
                    } else {
                        _banner.value = "Delete failed — ${err.message ?: err::class.simpleName}"
                    }
                },
            )
        }
    }

    /** Bulk delete (used by multi-select mode). Same NotFound handling. */
    public fun deleteMany(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) return
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).deleteSessions(fullIdsFor(sessionIds)).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _deleteSupported.value = false
                        _banner.value =
                            "This server doesn't support session delete. Contact dmz006/datawatch."
                    } else {
                        _banner.value = "Delete failed — ${err.message ?: err::class.simpleName}"
                    }
                },
            )
        }
    }

    private fun profileForSession(sessionId: String): ServerProfile? {
        // Match by current visible-session snapshot; single-server mode implies
        // activeProfile, all-servers mode may resolve to any enabled profile
        // that currently owns this row.
        val session = state.value.sessions.firstOrNull { it.id == sessionId }
        val profiles = state.value.allProfiles
        return profiles.firstOrNull { it.id == session?.serverProfileId }
            ?: activeProfile.value
    }

    /**
     * Resolve the server-scoped full id the daemon's session mutation
     * endpoints match on. Falls back to [sessionId] when the row isn't
     * in the cached snapshot (shouldn't happen in normal flow — the
     * caller always resolved a session to hit this path — but keeps
     * the call well-formed if it does).
     */
    private fun fullIdFor(sessionId: String): String {
        val session = state.value.sessions.firstOrNull { it.id == sessionId }
        return session?.fullId ?: sessionId
    }

    /** Bulk counterpart for multi-select deletion. */
    private fun fullIdsFor(sessionIds: List<String>): List<String> =
        sessionIds.map { fullIdFor(it) }

    public fun refresh() {
        if (allServersMode.value) {
            refreshAllServers()
            return
        }
        val profile = activeProfile.value ?: return
        _refreshing.value = true
        _banner.value = null
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.listSessions().fold(
                onSuccess = { sessions ->
                    ServiceLocator.sessionRepository.replaceAll(profile.id, sessions)
                    _refreshing.value = false
                    _banner.value = null
                    _lastProbeEpochMs.value = System.currentTimeMillis()
                    // Push the fresh counts to the home-screen widgets
                    // so they don't wait for AppWidgetManager's
                    // 30-minute cadence to catch up.
                    ServiceLocator.refreshHomeWidgets()
                },
                onFailure = { err ->
                    _refreshing.value = false
                    _banner.value = "Disconnected — showing cached data. " +
                        "(${err.message ?: err::class.simpleName})"
                },
            )
            // Refresh the backend badge alongside — best-effort, silent on
            // failure (a stale chip is better than a blocking banner).
            transport.fetchInfo().onSuccess { info ->
                info.llmBackend?.takeIf { it.isNotBlank() }?.let { backend ->
                    _backendByProfileId.value = _backendByProfileId.value + (profile.id to backend)
                }
            }
        }
    }

    /**
     * All-servers refresh: hits `/api/federation/sessions` on every enabled
     * profile in parallel and merges the results, deduping by id with most-
     * recent-wins. Per-profile failures degrade silently into a banner — we
     * still render whichever profiles did respond.
     */
    private fun refreshAllServers() {
        _refreshing.value = true
        _banner.value = null
        viewModelScope.launch {
            val profiles =
                ServiceLocator.profileRepository.observeAll()
                    .first().filter { it.enabled }
            val errors = mutableListOf<String>()
            val merged = linkedMapOf<String, Session>()
            kotlinx.coroutines.coroutineScope {
                profiles.map { p ->
                    async {
                        // Also fan out /api/info so each server's backend chip
                        // is available to the row composable.
                        ServiceLocator.transportFor(p).fetchInfo().onSuccess { info ->
                            info.llmBackend?.takeIf { it.isNotBlank() }?.let { backend ->
                                synchronized(_backendByProfileId) {
                                    _backendByProfileId.value =
                                        _backendByProfileId.value + (p.id to backend)
                                }
                            }
                        }
                        ServiceLocator.transportFor(p).federationSessions().fold(
                            onSuccess = { view ->
                                val combined =
                                    view.primary +
                                        view.proxied.values.flatten()
                                synchronized(merged) {
                                    combined.forEach { s ->
                                        val existing = merged[s.id]
                                        if (existing == null || s.lastActivityAt > existing.lastActivityAt) {
                                            merged[s.id] = s
                                        }
                                    }
                                }
                                view.errors.forEach { (n, e) ->
                                    synchronized(errors) { errors += "$n: $e" }
                                }
                            },
                            onFailure = { err ->
                                synchronized(errors) {
                                    errors += "${p.displayName}: ${err.message ?: err::class.simpleName}"
                                }
                            },
                        )
                    }
                }.awaitAll()
            }
            _allServersSessions.value = merged.values.sortedByDescending { it.lastActivityAt }
            _refreshing.value = false
            _banner.value =
                if (errors.isEmpty()) {
                    null
                } else {
                    "Some servers unreachable: " + errors.take(3).joinToString("; ")
                }
        }
    }
}
