package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.PrdComputeResolution
import com.dmzs.datawatchclient.transport.PrdComputeResolver
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryResourceRow
import com.dmzs.datawatchclient.transport.dto.PrdStoryResources
import com.dmzs.datawatchclient.transport.sse.DecomposeLiveState
import com.dmzs.datawatchclient.transport.sse.DecomposeStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Live planning stream for the iOS Automata detail (PWA _startDecomposeStream).
 * [onState] receives the accumulated [DecomposeLiveState] after every event;
 * [onEnd] fires once when the stream completes (terminal event, no job, or
 * retries exhausted) so Swift can refresh the automaton.
 */
public object IosDecomposeStream {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun watch(
        profile: ServerProfile,
        prdId: String,
        onState: (DecomposeLiveState) -> Unit,
        onEnd: () -> Unit,
    ): IosSubscription {
        val job =
            scope.launch {
                var state = DecomposeLiveState()
                try {
                    IosServiceLocator.transportFor(profile).decomposeEvents(prdId).collect { ev: DecomposeStreamEvent ->
                        state = state.apply(ev)
                        onState(state)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // stream failure — fall through to onEnd so the view refreshes
                }
                onEnd()
            }
        return IosSubscription(job)
    }
}

/** Automata detail resource snapshot (Android progress + compute-node cards). */
public data class IosPrdResourceSnapshot(
    val stories: List<PrdStoryResourceRow>,
    val nodeRef: String?,
    val node: ComputeNodeDetailDto?,
)

/**
 * Per-story progress with worker CPU/RSS (observer envelopes) and the compute
 * node the automaton's task sessions run on — same lookups as Android
 * AutonomousViewModel.startPrdProgressPolling. Swift polls every 5 s.
 */
public object IosPrdResources {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val terminalStates: Set<String> = setOf("killed", "complete", "completed", "failed", "cancelled", "stopped", "error")

    public fun load(
        profile: ServerProfile,
        prd: PrdDto,
        onResult: (IosPrdResourceSnapshot) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val envelopes = t.getAllEnvelopes().getOrNull().orEmpty()
            val sessions = t.listSessions().getOrNull().orEmpty()
            val taskSessionIds: Set<String> =
                prd.stories.flatMap { s -> s.tasks.mapNotNull { it.sessionId?.takeIf { id -> id.isNotBlank() } } }.toSet()
            fun linked(fullId: String, shortId: String): Boolean =
                fullId in taskSessionIds || shortId in taskSessionIds ||
                    taskSessionIds.any { tid -> fullId.endsWith(tid) || tid.endsWith(shortId) }
            val active =
                sessions.filter { s ->
                    linked(s.fullId, s.id) && s.state.name.lowercase() !in terminalStates
                }
            var ref: String? = active.firstOrNull { it.computeNodeRef != null }?.computeNodeRef
            if (ref == null && taskSessionIds.isNotEmpty()) {
                ref = sessions.firstOrNull { s -> linked(s.fullId, s.id) && s.computeNodeRef != null }?.computeNodeRef
            }
            // Session ref > backend LLM's node > local stats (PWA order) — planning
            // has no task sessions yet but still shows the planner's node.
            val taskBackend: String? =
                prd.stories.flatMap { st -> st.tasks.map { tk -> Pair(tk, st) } }
                    .firstOrNull { (tk, _) ->
                        val sid: String? = tk.sessionId
                        sid != null && active.any { s -> s.fullId == sid || s.id == sid || s.fullId.endsWith(sid) }
                    }?.let { (tk, st) -> tk.backend ?: st.backend }
            val resolved: PrdComputeResolution =
                PrdComputeResolver.resolve(transport = t, prd = prd, sessionRef = ref, taskBackend = taskBackend)
            onResult(
                IosPrdResourceSnapshot(
                    stories = PrdStoryResources.rows(prd, envelopes),
                    nodeRef = resolved.ref,
                    node = resolved.detail,
                ),
            )
        }
    }
}
