package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.dashboard.IosDashBoard
import com.dmzs.datawatchclient.dashboard.IosDashCard
import com.dmzs.datawatchclient.dashboard.IosDashCatalog
import com.dmzs.datawatchclient.dashboard.IosDashMemStats
import com.dmzs.datawatchclient.dashboard.IosDashParsers
import com.dmzs.datawatchclient.dashboard.IosDashSmokeDetail
import com.dmzs.datawatchclient.dashboard.IosDashSmokeRun
import com.dmzs.datawatchclient.dashboard.IosDashWebSearch
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AnalyticsBucketDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.ws.HookHub
import com.dmzs.datawatchclient.transport.ws.SessionsHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * iOS Dashboard parity (B36) — callback bridge for the PWA card grid. All
 * shaping lives in commonMain `com.dmzs.datawatchclient.dashboard`
 * (IosDashCatalog / IosDashParsers / IosDashEngine / IosDashConstellation);
 * this object only runs transport calls off the main thread. Callbacks fire
 * on a background thread — Swift hops to the main actor.
 */
public object IosDashboard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    /** GET /api/dashboard/layout → cards (PWA default layout on any failure). */
    public fun loadLayout(
        profile: ServerProfile,
        onResult: (List<IosDashCard>) -> Unit,
    ) {
        scope.launch {
            val obj = t(profile).fetchDashboardLayoutJson().getOrNull()
            onResult(IosDashCatalog.parseLayout(obj))
        }
    }

    /** PUT /api/dashboard/layout (PWA `_dashSaveLayout` on "Done"). [onDone] gets an error message or null. */
    public fun saveLayout(
        profile: ServerProfile,
        cards: List<IosDashCard>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r: Result<Unit> = t(profile).putDashboardLayout(IosDashCatalog.layoutBody(cards))
            onDone(r.exceptionOrNull()?.let { it.message ?: "Save failed." })
        }
    }

    /** GET /api/autonomous/prds (engine filters running/blocked/planning). */
    public fun loadPrds(
        profile: ServerProfile,
        onResult: (List<PrdDto>) -> Unit,
    ) {
        scope.launch {
            t(profile).listPrds().onSuccess { onResult(it.prds) }
        }
    }

    /** GET /api/cost → total USD (only delivered on success). */
    public fun loadCost(
        profile: ServerProfile,
        onResult: (Double) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchCostSummaryJson().onSuccess { onResult(IosDashParsers.costTotal(it)) }
        }
    }

    /** GET /api/analytics?range=30d buckets for the heatmap. */
    public fun loadHeatmap(
        profile: ServerProfile,
        onResult: (List<AnalyticsBucketDto>) -> Unit,
    ) {
        scope.launch {
            t(profile).getAnalytics(30).onSuccess { onResult(it.buckets) }
        }
    }

    /** GET /api/smoke/progress (array of run envelopes). */
    public fun loadSmokeRuns(
        profile: ServerProfile,
        onResult: (List<IosDashSmokeRun>) -> Unit,
    ) {
        scope.launch {
            t(profile).listSmokeRunsJson().onSuccess { onResult(IosDashParsers.smokeRuns(it)) }
        }
    }

    /**
     * GET /api/smoke/progress/{id}. [onResult] gets null when the run is gone
     * (404) so the engine drops its selection; transient errors are ignored.
     */
    public fun loadSmokeDetail(
        profile: ServerProfile,
        id: String,
        onResult: (IosDashSmokeDetail?) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchSmokeRunJson(id).onSuccess { obj ->
                onResult(obj?.let { IosDashParsers.smokeDetail(it) })
            }
        }
    }

    /** DELETE one run ([id]) or all runs ([id] empty). */
    public fun deleteSmokeRun(
        profile: ServerProfile,
        id: String,
        onDone: () -> Unit,
    ) {
        scope.launch {
            t(profile).deleteSmokeRun(id.takeIf { it.isNotBlank() })
            onDone()
        }
    }

    /** GET /api/memory/stats → Memory Scopes tile; null = unavailable. */
    public fun loadMemoryStats(
        profile: ServerProfile,
        onResult: (IosDashMemStats?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).memoryStats()
            onResult(r.getOrNull()?.let { IosDashParsers.memStats(it) })
        }
    }

    /** GET /api/websearch/stats?days=14 → Search Usage tile; null = unavailable. */
    public fun loadWebSearch(
        profile: ServerProfile,
        onResult: (IosDashWebSearch?) -> Unit,
    ) {
        scope.launch {
            val r = t(profile).fetchWebSearchStatsV2(14)
            onResult(r.getOrNull()?.let { IosDashParsers.webSearch(it) })
        }
    }

    /** GET /api/sessions/{id}/status → board (seeds node health/verdicts before WS frames arrive). */
    public fun loadBoard(
        profile: ServerProfile,
        sessionId: String,
        onResult: (IosDashBoard) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchSessionStatusJson(sessionId).onSuccess { obj ->
                onResult(IosDashParsers.board(sessionId = sessionId, obj = obj))
            }
        }
    }

    /**
     * One global `/ws` for [profile] (no session subscription) delivering the
     * broadcast `sessions` list, single-session `session_state` diffs and
     * `hook_update` boards — the PWA dashboard's live inputs ("live · ws").
     * Cancel the handle when the Dashboard disappears.
     */
    public fun subscribeLive(
        profile: ServerProfile,
        onSessions: (List<Session>) -> Unit,
        onSession: (Session) -> Unit,
        onBoard: (IosDashBoard) -> Unit,
    ): IosSubscription {
        val job =
            scope.launch {
                launch { IosServiceLocator.wsTransportFor(profile).globalStream().collect { } }
                launch {
                    SessionsHub.fullListFlow
                        .filter { it.serverProfileId == profile.id }
                        .collect { onSessions(it.sessions) }
                }
                launch {
                    SessionsHub.singleSessionFlow
                        .filter { it.serverProfileId == profile.id }
                        .collect { onSession(it.session) }
                }
                launch {
                    HookHub.flow
                        .filter { it.serverProfileId == profile.id }
                        .collect { u -> onBoard(IosDashParsers.board(sessionId = u.sessionId, obj = u.board)) }
                }
            }
        return IosSubscription(job)
    }
}
