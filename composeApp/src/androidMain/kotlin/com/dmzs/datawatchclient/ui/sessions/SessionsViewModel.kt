package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.push.SessionStateWatcher
import com.dmzs.datawatchclient.transport.QuickCommandItem
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import com.dmzs.datawatchclient.transport.ws.SessionsHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    // Parity D42a: the Sort menu (Recent / Started / Name / Custom) was
    // dropped — ordering is the PWA rule (manual order, then updated_at).

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val allProfiles: List<ServerProfile> = emptyList(),
        val allServersMode: Boolean = false,
        val sessions: List<Session> = emptyList(),
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
         * User-arranged (drag) ordering — PWA `cs_session_order`. Ids in
         * this list come first in this order; the rest follow by
         * last activity (PWA `updated_at`) descending.
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
        /** True when the active server has `whisper.backend` configured; hides mic button when false. */
        val whisperConfigured: Boolean = false,
        /**
         * Parity D12a — PWA state chip key (`cs_session_state_chip`):
         * "all" or a wire state ([STATE_CHIP_KEYS]).
         */
        val stateChip: String = STATE_CHIP_ALL,
        /** BL348 — PWA tree view (`cs_session_tree_view`): parent/child lineage grouping. */
        val treeView: Boolean = false,
        /** PWA `🕒 N` badge: `GET /api/schedules?state=pending` on the active server. */
        val pendingSchedules: List<com.dmzs.datawatchclient.domain.Schedule> = emptyList(),
    ) {
        /** [visibleSessions] flattened into the BL348 tree (depth + orphan flag per row). */
        public val treeRows: List<TreeRow>
            get() = flattenTree(visibleSessions)

        /** Per-state counts over the whole session list (PWA `stateCounts`). */
        public val stateCounts: Map<String, Int>
            get() {
                val byKey = sessions.groupingBy { stateChipKey(it.state) }.eachCount()
                return STATE_CHIP_KEYS.associateWith { k ->
                    if (k == STATE_CHIP_ALL) sessions.size else byKey[k] ?: 0
                }
            }

        /** Chips shown when the State row is open: All + count>0 + the selected one. */
        public val visibleStateChips: List<String>
            get() {
                val counts = stateCounts
                return STATE_CHIP_KEYS.filter { k ->
                    k == STATE_CHIP_ALL || (counts[k] ?: 0) > 0 || k == stateChip
                }
            }

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
                                (s.backend?.lowercase()?.contains(q) == true) ||
                                (s.llmRef?.lowercase()?.contains(q) == true) ||
                                (s.computeNodeRef?.lowercase()?.contains(q) == true)
                        }
                        .filter { s ->
                            backendFilter == null ||
                                s.backend == backendFilter ||
                                // v0.74.0 S5-7 — council-virtual filter also matches by fullId prefix
                                (backendFilter == "council-virtual" && s.fullId.startsWith("council-"))
                        }
                        // Parity D12a: real-state chip filter (PWA setSessionStateChip)
                        .filter { s -> stateChip == STATE_CHIP_ALL || stateChipKey(s.state) == stateChip }
                        .toList()
                // Parity D42a — PWA sortSessionsByOrder: manual order first,
                // then updated_at (last activity) descending. No state buckets.
                return sortByManualOrder(filtered, customOrder)
            }

        public val historyCount: Int
            get() = historySessionIds.size

        public val historySessionIds: List<String>
            get() {
                val doneStates =
                    setOf(
                        com.dmzs.datawatchclient.domain.SessionState.Completed,
                        com.dmzs.datawatchclient.domain.SessionState.Killed,
                        com.dmzs.datawatchclient.domain.SessionState.Error,
                    )
                return sessions.filter { it.state in doneStates }.map { it.id }
            }

        public val visibleDoneCount: Int
            get() {
                val doneStates =
                    setOf(
                        com.dmzs.datawatchclient.domain.SessionState.Completed,
                        com.dmzs.datawatchclient.domain.SessionState.Killed,
                        com.dmzs.datawatchclient.domain.SessionState.Error,
                    )
                return visibleSessions.count { s -> s.state in doneStates }
            }

        /** One row of the BL348 tree: [depth] 0 = root; [orphaned] = parent_id set but parent not in list. */
        public data class TreeRow(val session: Session, val depth: Int, val orphaned: Boolean)

        public companion object {
            private const val RECENT_WINDOW_MINUTES: Long = 5

            /**
             * PWA `renderSessionsAsTree`: a session whose `parent_id` matches
             * another visible session's full id nests under it (pre-order,
             * siblings keep list order); everything else is a root, flagged
             * orphaned when it names a parent that is not in the list.
             */
            public fun flattenTree(sessions: List<Session>): List<TreeRow> {
                val byFullId = sessions.associateBy { it.fullId }
                val children = mutableMapOf<String, MutableList<Session>>()
                val roots = mutableListOf<Session>()
                sessions.forEach { s ->
                    val parent = s.parentId
                    if (parent != null && parent != s.fullId && byFullId.containsKey(parent)) {
                        children.getOrPut(parent) { mutableListOf() }.add(s)
                    } else {
                        roots.add(s)
                    }
                }
                val out = mutableListOf<TreeRow>()
                val seen = HashSet<String>()

                fun visit(
                    s: Session,
                    depth: Int,
                ) {
                    if (!seen.add(s.fullId)) return // cycle guard
                    val orphaned = s.parentId != null && !byFullId.containsKey(s.parentId)
                    out.add(TreeRow(s, depth, orphaned))
                    children[s.fullId].orEmpty().forEach { visit(it, depth + 1) }
                }
                roots.forEach { visit(it, 0) }
                // Pure cycles (a↔b) have no root — append them flat so nothing disappears.
                sessions.filter { it.fullId !in seen }.forEach { out.add(TreeRow(it, 0, false)) }
                return out
            }
            public const val STATE_CHIP_ALL: String = "all"

            /** PWA `realStateChips` order. */
            public val STATE_CHIP_KEYS: List<String> =
                listOf(STATE_CHIP_ALL, "running", "waiting_input", "rate_limited", "complete", "failed", "killed")

            /** Chips that select done sessions — picking one turns History on (PWA). */
            public val HISTORICAL_STATE_CHIPS: Set<String> = setOf("complete", "failed", "killed")

            /** Domain state → PWA wire key used by the chips. */
            public fun stateChipKey(state: com.dmzs.datawatchclient.domain.SessionState): String =
                when (state) {
                    com.dmzs.datawatchclient.domain.SessionState.Running -> "running"
                    com.dmzs.datawatchclient.domain.SessionState.Waiting -> "waiting_input"
                    com.dmzs.datawatchclient.domain.SessionState.RateLimited -> "rate_limited"
                    com.dmzs.datawatchclient.domain.SessionState.Completed -> "complete"
                    com.dmzs.datawatchclient.domain.SessionState.Error -> "failed"
                    com.dmzs.datawatchclient.domain.SessionState.Killed -> "killed"
                    com.dmzs.datawatchclient.domain.SessionState.New -> "new"
                }

            /** PWA `sortSessionsByOrder`. */
            public fun sortByManualOrder(
                sessions: List<Session>,
                order: List<String>,
            ): List<Session> {
                val byId = sessions.associateBy { it.id }
                val inOrder = order.mapNotNull { byId[it] }.distinctBy { it.id }
                val seen = inOrder.mapTo(HashSet()) { it.id }
                val rest = sessions.filterNot { it.id in seen }.sortedByDescending { it.lastActivityAt }
                return inOrder + rest
            }
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

    private fun prefs() = android.preference.PreferenceManager.getDefaultSharedPreferences(ServiceLocator.context())

    // Parity D12a: PWA state chip, persisted like `cs_session_state_chip`.
    private val stateChipFlow =
        MutableStateFlow(
            prefs().getString(PREF_STATE_CHIP, UiState.STATE_CHIP_ALL)
                ?.takeIf { it in UiState.STATE_CHIP_KEYS } ?: UiState.STATE_CHIP_ALL,
        )

    /**
     * Manual (drag) ordering — persisted like the PWA's `cs_session_order`
     * (parity D42a).
     */
    private val _customOrder: MutableStateFlow<List<String>> =
        MutableStateFlow(
            prefs().getString(PREF_SESSION_ORDER, null)
                ?.split(',')
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    private val _reorderMode = MutableStateFlow(false)
    private val _allServersSessions = MutableStateFlow<List<Session>>(emptyList())
    private val _lastProbeEpochMs = MutableStateFlow<Long?>(null)
    private val _deleteSupported = MutableStateFlow(true)
    private val _backendByProfileId = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _whisperConfigured = MutableStateFlow(false)
    private val _treeView = MutableStateFlow(prefs().getString(PREF_TREE_VIEW, "0") == "1")
    private val _pendingSchedules = MutableStateFlow<List<com.dmzs.datawatchclient.domain.Schedule>>(emptyList())

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
        // Build the base state from the 16-flow combine (max allowed), then
        // layer in _stateFilter with a second combine so we don't exceed the
        // vararg limit.
        val baseFlow =
            combine(
                activeProfile,
                allProfiles,
                sessionsFlow,
                _refreshing,
                _banner,
                _filterText,
                _backendFilter,
                _showHistory,
                stateChipFlow,
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
                    stateChip = args[8] as String,
                    allServersMode = args[9] as Boolean,
                    activeReachable = args[10] as Boolean?,
                    lastProbeEpochMs = args[11] as Long?,
                    deleteSupported = args[12] as Boolean,
                    backendByProfileId = args[13] as Map<String, String>,
                    customOrder = args[14] as List<String>,
                    reorderMode = args[15] as Boolean,
                )
            }
        return combine(baseFlow, _whisperConfigured, _treeView, _pendingSchedules) { base, wc, tree, sched ->
            base.copy(whisperConfigured = wc, treeView = tree, pendingSchedules = sched)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Auto-refresh whenever the active profile identity changes (non-null).
        activeProfile
            .onEach { if (it != null) refresh() }
            .launchIn(viewModelScope)
        // Trigger immediate load when switching to all-servers mode (activeProfile emits null
        // in this mode, so the activeProfile.onEach guard above never fires).
        allServersMode
            .filter { it }
            .onEach { refreshAllServers() }
            .launchIn(viewModelScope)
        // Fetch whisper.backend config once per profile switch — hides mic when not configured.
        activeProfile
            .onEach { profile ->
                if (profile == null) {
                    _whisperConfigured.value = false
                } else {
                    ServiceLocator.transportFor(profile).fetchConfig().onSuccess { cfg ->
                        _whisperConfigured.value =
                            (cfg.raw["whisper.backend"] as? kotlinx.serialization.json.JsonPrimitive)
                                ?.content?.isNotBlank() == true
                    }
                }
            }
            .launchIn(viewModelScope)
        // Open a persistent WS connection per active profile to receive server-pushed
        // session-list updates. The server sends a "sessions" frame immediately on
        // connect and again on every session change; SessionsHub routes it here.
        activeProfile
            .flatMapLatest { profile ->
                if (profile == null) emptyFlow()
                else ServiceLocator.wsTransportFor(profile).globalStream()
            }
            .launchIn(viewModelScope)

        // Full-list WS push: replaces SQLite cache exactly as refresh() does.
        activeProfile
            .flatMapLatest { profile ->
                if (profile == null) emptyFlow()
                else SessionsHub.fullListFlow
                    .filter { it.serverProfileId == profile.id }
                    .onEach { update ->
                        ServiceLocator.sessionRepository.replaceAll(profile.id, update.sessions)
                        _refreshing.value = false
                        _lastProbeEpochMs.value = System.currentTimeMillis()
                        SessionStateWatcher.onSessionsUpdated(update.sessions, ServiceLocator.context())
                        ServiceLocator.refreshHomeWidgets()
                    }
            }
            .launchIn(viewModelScope)

        // Single-session diff (v8.37.0+): upsert without clearing the whole cache.
        activeProfile
            .flatMapLatest { profile ->
                if (profile == null) emptyFlow()
                else SessionsHub.singleSessionFlow
                    .filter { it.serverProfileId == profile.id }
                    .onEach { update ->
                        ServiceLocator.sessionRepository.upsert(update.session)
                    }
            }
            .launchIn(viewModelScope)

        // REST fallback poll — WS push handles live updates; this fires every 30 s
        // to recover from WS reconnect gaps and keep the reachability dot accurate.
        viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(AUTO_REFRESH_MS)
                if (!_refreshing.value) refresh()
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_MS: Long = 30_000L
        const val PREF_STATE_CHIP = "cs_session_state_chip"
        const val PREF_SESSION_ORDER = "cs_session_order"
        const val PREF_TREE_VIEW = "cs_session_tree_view"
    }

    /** BL348 — PWA `toggleSessionTreeView`, persisted as `cs_session_tree_view`. */
    public fun toggleTreeView() {
        val next = !_treeView.value
        _treeView.value = next
        prefs().edit().putString(PREF_TREE_VIEW, if (next) "1" else "0").apply()
    }

    /** PWA pending-schedules dropdown ✕ — cancel, then reload the badge. */
    public fun cancelSchedule(scheduleId: String) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val transport = ServiceLocator.transportFor(profile)
            transport.deleteSchedule(scheduleId).onFailure { e ->
                com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                    "Cancel failed: ${e.message ?: e::class.simpleName}",
                    com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                )
            }
            loadPendingSchedules(profile)
        }
    }

    private suspend fun loadPendingSchedules(profile: ServerProfile) {
        ServiceLocator.transportFor(profile).listSchedules(state = "pending")
            .onSuccess { list -> _pendingSchedules.value = list.filter { it.state == null || it.state == "pending" } }
            .onFailure { _pendingSchedules.value = emptyList() }
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

    /**
     * Parity D12a — PWA `setSessionStateChip`: persist the chip; picking a
     * historical state (complete / failed / killed) turns History on so
     * those sessions are actually visible.
     */
    public fun setStateChip(chip: String) {
        val key = chip.takeIf { it in UiState.STATE_CHIP_KEYS } ?: UiState.STATE_CHIP_ALL
        stateChipFlow.value = key
        prefs().edit().putString(PREF_STATE_CHIP, key).apply()
        if (key in UiState.HISTORICAL_STATE_CHIPS) _showHistory.value = true
    }

    private fun setCustomOrder(order: List<String>) {
        _customOrder.value = order
        prefs().edit().putString(PREF_SESSION_ORDER, order.joinToString(",")).apply()
    }

    public fun toggleShowHistory() {
        _showHistory.value = !_showHistory.value
    }

    public fun toggleReorderMode() {
        _reorderMode.value = !_reorderMode.value
        // Seed the manual order with the current visible order so the
        // first moves work.
        if (_reorderMode.value) seedCustomOrder()
    }

    /** Manual order = current visible order (PWA `sortSessionsByOrder(...).map(id)`). */
    private fun seedCustomOrder() {
        val visible = state.value.visibleSessions.map { it.id }
        val rest = _customOrder.value.filterNot { it in visible }
        setCustomOrder(visible + rest)
    }

    /** Move the session [sessionId] one slot toward the top of the custom ordering. */
    public fun moveUp(sessionId: String) {
        val list = _customOrder.value.toMutableList()
        val idx = list.indexOf(sessionId)
        if (idx <= 0) return
        list.removeAt(idx)
        list.add(idx - 1, sessionId)
        setCustomOrder(list)
    }

    /**
     * Move [sessionId] by [rowOffset] positions in the custom ordering.
     * Positive offsets move toward the bottom; negative toward the top.
     * No-op when the session is not yet in the custom order (fires
     * [toggleReorderMode] semantics to seed the list first). Used by
     * the long-press drag-drop gesture on SessionsScreen — a single
     * drag emits one call instead of looping through moveUp/moveDown.
     */
    public fun moveSessionByOffset(
        sessionId: String,
        rowOffset: Int,
    ) {
        if (rowOffset == 0) return
        // Seed from the current visible order so ids not yet in the
        // manual list can be moved — PWA moveSession does the same.
        seedCustomOrder()
        val list = _customOrder.value.toMutableList()
        val idx = list.indexOf(sessionId)
        if (idx < 0) return
        val newIdx = (idx + rowOffset).coerceIn(0, list.size - 1)
        if (newIdx == idx) return
        list.removeAt(idx)
        list.add(newIdx, sessionId)
        setCustomOrder(list)
    }

    /** Move the session [sessionId] one slot toward the bottom. */
    public fun moveDown(sessionId: String) {
        val list = _customOrder.value.toMutableList()
        val idx = list.indexOf(sessionId)
        if (idx < 0 || idx >= list.size - 1) return
        list.removeAt(idx)
        list.add(idx + 1, sessionId)
        setCustomOrder(list)
    }

    public fun toggleMute(
        sessionId: String,
        currentlyMuted: Boolean,
    ) {
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(sessionId, !currentlyMuted)
        }
    }

    /**
     * Sprint 23 (#116) — watched-session IDs for the active profile,
     * reactive. Empty set = no sessions watched (badge shows all alerts).
     */
    public val watchedIds: StateFlow<Set<String>> =
        activeProfile
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(emptySet())
                } else {
                    ServiceLocator.watchedSessionsStore.watchedFlow(profile.id)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Toggle the watched state for a session on the active profile. */
    public fun toggleWatch(sessionId: String) {
        val profile = activeProfile.value ?: return
        val current = ServiceLocator.watchedSessionsStore.isWatched(profile.id, sessionId)
        ServiceLocator.watchedSessionsStore.setWatched(profile.id, sessionId, !current)
    }

    /** Rename a session on the server; refresh the list on success. */
    public fun rename(
        sessionId: String,
        newName: String,
    ) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).renameSession(fullIdFor(sessionId), newName).fold(
                onSuccess = {
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        ServiceLocator.context().getString(com.dmzs.datawatchclient.R.string.session_renamed),
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Success,
                    )
                    refresh()
                },
                onFailure = { err ->
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        ServiceLocator.context().getString(com.dmzs.datawatchclient.R.string.session_rename_failed) +
                            ": ${err.message ?: err::class.simpleName}",
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                    )
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
        if (text.isEmpty()) return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).replyToSession(fullIdFor(sessionId), text).fold(
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

    /** Fetch the LLM-generated current-status summary for a running session. */
    public suspend fun fetchCurrentStatus(sessionId: String): com.dmzs.datawatchclient.transport.dto.CurrentStatusDto? {
        val profile = profileForSession(sessionId) ?: return null
        return ServiceLocator.transportFor(profile)
            .getSessionCurrentStatus(fullIdFor(sessionId))
            .getOrNull()
            ?.takeIf { it.currentStatus.isNotBlank() || it.noChange }
    }

    /** Trigger a manual re-summarize and return the result wrapped as a [CurrentStatusDto] for display. */
    public suspend fun resummmarizeSession(
        sessionId: String,
    ): com.dmzs.datawatchclient.transport.dto.CurrentStatusDto? {
        val profile = profileForSession(sessionId) ?: return null
        val result =
            ServiceLocator.transportFor(profile)
                .summarizeSession(fullIdFor(sessionId))
                .getOrNull() ?: return null
        // summarize returns a flat "summary" field — wrap it as a CurrentStatusDto for display
        return com.dmzs.datawatchclient.transport.dto.CurrentStatusDto(currentStatus = result.summary)
    }

    /** Fetch server-configured system quick-commands (datawatch#28). Empty list = use client fallback. */
    public suspend fun fetchSystemQuickCommands(sessionId: String): List<QuickCommandItem> {
        val profile = profileForSession(sessionId) ?: return emptyList()
        return ServiceLocator.transportFor(profile).fetchSystemQuickCommands().fold(
            onSuccess = { it },
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
                    _banner.value = "Stop failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /** Warm-resume a completed or failed session. Refresh on success. */
    public fun restart(sessionId: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).restartSession(fullIdFor(sessionId)).fold(
                onSuccess = { session ->
                    // Upsert immediately so the restarted session is visible before
                    // the background refresh completes (BL-T4-1).
                    ServiceLocator.sessionRepository.upsert(session)
                    refresh()
                },
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

    /** Bulk delete (used by multi-select mode). Calls deleteSession per-ID; server has no bulk endpoint. */
    public fun deleteMany(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) return
        viewModelScope.launch {
            var hadNotFound = false
            for (sessionId in sessionIds) {
                val profile = profileForSession(sessionId) ?: activeProfile.value ?: continue
                ServiceLocator.transportFor(profile).deleteSession(fullIdFor(sessionId)).fold(
                    onSuccess = {},
                    onFailure = { err ->
                        if (err is TransportError.NotFound) {
                            hadNotFound = true
                        } else {
                            _banner.value = "Delete failed — ${err.message ?: err::class.simpleName}"
                        }
                    },
                )
            }
            if (hadNotFound) {
                _deleteSupported.value = false
                _banner.value =
                    "This server doesn't support session delete. Contact dmz006/datawatch."
            }
            refresh()
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
    private fun fullIdsFor(sessionIds: List<String>): List<String> = sessionIds.map { fullIdFor(it) }

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
                    // Fire or cancel "waiting for input" notifications on state transitions.
                    SessionStateWatcher.onSessionsUpdated(sessions, ServiceLocator.context())
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
            // PWA loadGlobalScheduleBadge — best-effort, hidden on failure.
            loadPendingSchedules(profile)
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
