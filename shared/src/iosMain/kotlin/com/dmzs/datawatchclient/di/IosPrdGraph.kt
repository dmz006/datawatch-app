package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.util.PrdDagLayout
import com.dmzs.datawatchclient.util.PrdDagLayoutResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Automaton DAG card data (Android PrdDetailDialog "Graph" card / PrdDagCanvas,
 * #184; operator 2026-10-05, PWA side dmz006/datawatch#182). Fetches
 * GET /api/orchestrator/graphs/{prdId} and returns the shared layered layout.
 * onResult(null) on failure; a result with no nodes when the graph is empty —
 * Swift hides the card in both cases, like Android.
 */
public object IosPrdGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        prdId: String,
        onResult: (PrdDagLayoutResult?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).orchestratorGraph(prdId)
            val graph = r.getOrNull()
            if (graph == null) {
                onResult(null)
                return@launch
            }
            val layout: PrdDagLayoutResult = PrdDagLayout.layout(nodes = graph.nodes, edges = graph.edges)
            onResult(layout)
        }
    }
}
