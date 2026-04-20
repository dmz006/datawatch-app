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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

    public enum class Filter(public val label: String) {
        All("All"), Running("Running"), Waiting("Waiting"),
        Completed("Completed"), Error("Error")
    }

    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val allProfiles: List<ServerProfile> = emptyList(),
        val allServersMode: Boolean = false,
        val sessions: List<Session> = emptyList(),
        val filter: Filter = Filter.All,
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
        public val visibleSessions: List<Session>
            get() = when (filter) {
                Filter.All -> sessions
                Filter.Running -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Running }
                Filter.Waiting -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Waiting }
                Filter.Completed -> sessions.filter {
                    it.state == com.dmzs.datawatchclient.domain.SessionState.Completed ||
                        it.state == com.dmzs.datawatchclient.domain.SessionState.Killed
                }
                Filter.Error -> sessions.filter { it.state == com.dmzs.datawatchclient.domain.SessionState.Error }
            }
    }

    private val allProfiles: StateFlow<List<ServerProfile>> = ServiceLocator.profileRepository
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = emptyList())

    private val activeId: StateFlow<String?> = ServiceLocator.activeServerStore.observe()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val activeProfile: StateFlow<ServerProfile?> = combine(
        allProfiles, activeId,
    ) { profiles, storedId ->
        val enabled = profiles.filter { it.enabled }
        if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine null
        storedId?.let { id -> enabled.firstOrNull { it.id == id } }
            ?: enabled.firstOrNull()
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val allServersMode: StateFlow<Boolean> = activeId
        .map { it == ActiveServerStore.SENTINEL_ALL_SERVERS }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val _refreshing = MutableStateFlow(false)
    private val _banner = MutableStateFlow<String?>(null)
    private val _filter = MutableStateFlow(Filter.All)
    private val _allServersSessions = MutableStateFlow<List<Session>>(emptyList())
    private val _lastProbeEpochMs = MutableStateFlow<Long?>(null)
    private val _deleteSupported = MutableStateFlow(true)

    /**
     * Per-active-profile reachability. Flattens into `null` when the active
     * profile is null (no server selected) or when the user is in all-servers
     * mode — the TopAppBar indicator hides in those cases rather than
     * misrepresenting any one server's state.
     */
    private val activeReachable: StateFlow<Boolean?> = activeProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf<Boolean?>(null)
            else ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    public val state: StateFlow<UiState> = combineLatestState()

    private fun combineLatestState(): StateFlow<UiState> {
        val perProfileSessionsFlow = activeProfile.flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList())
            else ServiceLocator.sessionRepository.observeForProfile(profile.id)
        }
        // Choose source by mode — the all-servers list is fed by refresh()
        // since there's no per-profile cache merge layer.
        val sessionsFlow = combine(
            allServersMode, perProfileSessionsFlow, _allServersSessions,
        ) { all, single, federated ->
            if (all) federated else single
        }
        return combine(
            activeProfile,
            allProfiles,
            sessionsFlow,
            _refreshing,
            _banner,
            _filter,
            allServersMode,
            activeReachable,
            _lastProbeEpochMs,
            _deleteSupported,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            UiState(
                activeProfile = args[0] as ServerProfile?,
                allProfiles = args[1] as List<ServerProfile>,
                sessions = args[2] as List<Session>,
                refreshing = args[3] as Boolean,
                banner = args[4] as String?,
                filter = args[5] as Filter,
                allServersMode = args[6] as Boolean,
                activeReachable = args[7] as Boolean?,
                lastProbeEpochMs = args[8] as Long?,
                deleteSupported = args[9] as Boolean,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    }

    init {
        // Auto-refresh whenever the active profile identity changes (non-null).
        activeProfile
            .onEach { if (it != null) refresh() }
            .launchIn(viewModelScope)
    }

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }

    public fun selectAllServers() {
        ServiceLocator.activeServerStore.set(ActiveServerStore.SENTINEL_ALL_SERVERS)
    }

    public fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    public fun toggleMute(sessionId: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(sessionId, !currentlyMuted)
        }
    }

    /** Rename a session on the server; refresh the list on success. */
    public fun rename(sessionId: String, newName: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).renameSession(sessionId, newName).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _banner.value = "Rename failed — ${err.message ?: err::class.simpleName}"
                },
            )
        }
    }

    /** Warm-resume a completed or failed session. Refresh on success. */
    public fun restart(sessionId: String) {
        val profile = profileForSession(sessionId) ?: return
        viewModelScope.launch {
            ServiceLocator.transportFor(profile).restartSession(sessionId).fold(
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
            ServiceLocator.transportFor(profile).deleteSession(sessionId).fold(
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
            ServiceLocator.transportFor(profile).deleteSessions(sessionIds).fold(
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
                },
                onFailure = { err ->
                    _refreshing.value = false
                    _banner.value = "Disconnected — showing cached data. " +
                        "(${err.message ?: err::class.simpleName})"
                },
            )
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
            val profiles = ServiceLocator.profileRepository.observeAll()
                .first().filter { it.enabled }
            val errors = mutableListOf<String>()
            val merged = linkedMapOf<String, Session>()
            kotlinx.coroutines.coroutineScope {
                profiles.map { p ->
                    async {
                        ServiceLocator.transportFor(p).federationSessions().fold(
                            onSuccess = { view ->
                                val combined = view.primary +
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
            _banner.value = if (errors.isEmpty()) null
                else "Some servers unreachable: " + errors.take(3).joinToString("; ")
        }
    }
}
