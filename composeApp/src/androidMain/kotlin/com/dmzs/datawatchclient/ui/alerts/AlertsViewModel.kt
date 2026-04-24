package com.dmzs.datawatchclient.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    public enum class Tab { Active, Inactive }

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
        }
    }

    public data class UiState(
        val active: List<AlertGroup> = emptyList(),
        val inactive: List<AlertGroup> = emptyList(),
        val selectedTab: Tab = Tab.Active,
        val expandedSessionIds: Set<String> = emptySet(),
        val refreshing: Boolean = false,
        val banner: String? = null,
    ) {
        /** Bottom-nav badge — **active-only** count matches the PWA label `Active (N)`. */
        public val count: Int
            get() = active.sumOf { it.alerts.size }

        public val visibleGroups: List<AlertGroup>
            get() =
                when (selectedTab) {
                    Tab.Active -> active
                    Tab.Inactive -> inactive
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

    // Active profile — alerts are per-server, so we re-fetch whenever
    // the user flips the active server.
    private val activeProfileFlow =
        ServiceLocator.profileRepository.observeAll().flatMapLatest { profiles ->
            ServiceLocator.activeServerStore.observe().map { id ->
                profiles.firstOrNull { it.id == id && it.enabled }
                    ?: profiles.firstOrNull { it.enabled }
            }
        }

    init {
        // Polling loop. Cancelled automatically on VM clear.
        viewModelScope.launch {
            activeProfileFlow.collect { profile ->
                _alerts.value = emptyList()
                if (profile == null) return@collect
                while (true) {
                    _refreshing.value = true
                    ServiceLocator.transportFor(profile).listAlerts().fold(
                        onSuccess = {
                            _alerts.value = it.alerts
                            _banner.value = null
                        },
                        onFailure = { err ->
                            _banner.value =
                                "Alerts fetch failed — ${err.message ?: err::class.simpleName}"
                        },
                    )
                    _refreshing.value = false
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Observes the active profile's session list so we can classify
     * each alert group as Active vs Inactive. When the session is
     * missing from the list (deleted, orphaned, cross-server), we
     * treat the group as Inactive — mirrors PWA app.js:5558-5566.
     */
    private val sessionsFlow =
        activeProfileFlow.flatMapLatest { profile ->
            if (profile == null) {
                flowOf(emptyList())
            } else {
                ServiceLocator.sessionRepository.observeForProfile(profile.id)
            }
        }

    public val state: StateFlow<UiState> =
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
                grouped.map { (sid, alerts) ->
                    val session =
                        sessionByFullId[sid] ?: sessionByShortId[sid]
                    AlertGroup(
                        sessionId = sid,
                        session = session,
                        alerts = alerts.sortedByDescending { it.createdAt },
                    )
                }

            val (active, inactive) = groups.partition { it.isActive }
            UiState(
                active = active.sortedByDescending { it.alerts.maxOfOrNull { a -> a.createdAt } },
                inactive = inactive.sortedByDescending { it.alerts.maxOfOrNull { a -> a.createdAt } },
                selectedTab = tab,
                expandedSessionIds = expanded,
                refreshing = refreshing,
                banner = _banner.value,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    public fun selectTab(tab: Tab) {
        _selectedTab.value = tab
    }

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
            _alerts.value = _alerts.value.map {
                if (it.id == alertId) it.copy(read = true) else it
            }
        }
    }

    public fun dismissBanner() {
        _banner.value = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
