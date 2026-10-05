package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.CouncilLivePhase
import com.dmzs.datawatchclient.transport.CouncilLiveReducer
import com.dmzs.datawatchclient.transport.CouncilLiveState
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.CouncilRunDto
import com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest
import com.dmzs.datawatchclient.transport.watchCouncilRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One persona reply (status: waiting · responding · done · error). */
public data class IosCouncilReply(
    val persona: String,
    val status: String,
    val text: String,
)

/** One debate round. */
public data class IosCouncilRound(
    val index: Int,
    val completed: Boolean,
    val replies: List<IosCouncilReply>,
)

/**
 * Render model for the live / replay council sheet. [phase] is one of
 * connecting · running · synthesizing · completed · cancelled · error.
 */
public data class IosCouncilLiveRun(
    val runId: String,
    val shortId: String,
    val proposal: String,
    val mode: String,
    val personas: List<String>,
    val roundsTotal: Int,
    val rounds: List<IosCouncilRound>,
    val phase: String,
    val terminal: Boolean,
    val consensus: String,
    val dissent: String,
)

/** Recent-runs row (PWA: mode chip · N personas × M rounds · short id · detail). */
public data class IosCouncilPastRun(
    val id: String,
    val shortId: String,
    val proposal: String,
    val mode: String,
    val personaCount: Int,
    val roundCount: Int,
    val running: Boolean,
    val consensus: String,
    val startedAt: String,
)

/** Cancels an in-flight council watch (closing the sheet). */
public class IosCouncilSubscription internal constructor() {
    internal var job: Job? = null

    public fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Council live runs for iOS (PWA `councilRun` → `councilOpenLiveWatch`,
 * `councilViewRun`). Callbacks fire on a background thread — hop to main in
 * Swift.
 */
public object IosCouncilRuns {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    /** Persona names (PWA renders one pre-checked checkbox per persona). */
    public fun personas(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).councilListPersonas().fold(
                onSuccess = { list -> onSuccess(list.map { it.name }.filter { it.isNotEmpty() }) },
                onFailure = { e -> onError(e.message ?: "Failed to load personas.") },
            )
        }
    }

    /** Most recent persisted runs (newest first), capped at [limit]. */
    public fun recentRuns(
        profile: ServerProfile,
        limit: Int,
        onSuccess: (List<IosCouncilPastRun>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).councilListRuns().fold(
                onSuccess = { runs -> onSuccess(runs.take(limit).map { toPast(it) }) },
                onFailure = { e -> onError(e.message ?: "Failed to load runs.") },
            )
        }
    }

    /** POST /api/council/run (async) → the initial live state to open the sheet with. */
    public fun start(
        profile: ServerProfile,
        proposal: String,
        mode: String,
        personas: List<String>,
        onStarted: (IosCouncilLiveRun) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val req: StartCouncilRunRequest =
                StartCouncilRunRequest(proposal = proposal.trim(), mode = mode, personas = personas)
            val res: Result<CouncilRunDto> = t(profile).councilStartRun(req)
            val run: CouncilRunDto? = res.getOrNull()
            if (run == null || run.id.isEmpty()) {
                onError(res.exceptionOrNull()?.message ?: "Council start failed.")
                return@launch
            }
            val initial: CouncilLiveState =
                CouncilLiveState(
                    runId = run.id,
                    proposal = req.proposal,
                    mode = mode,
                    personas = personas,
                    roundsTotal = CouncilLiveReducer.roundsForMode(mode),
                )
            onStarted(toIos(initial))
        }
    }

    /**
     * Subscribes to the run's SSE stream; [onUpdate] receives every new state
     * until the run is terminal. Call [IosCouncilSubscription.cancel] when the
     * sheet closes.
     */
    public fun watch(
        profile: ServerProfile,
        run: IosCouncilLiveRun,
        onUpdate: (IosCouncilLiveRun) -> Unit,
    ): IosCouncilSubscription {
        val handle = IosCouncilSubscription()
        val initial: CouncilLiveState =
            CouncilLiveState(
                runId = run.runId,
                proposal = run.proposal,
                mode = run.mode,
                personas = run.personas,
                roundsTotal = run.roundsTotal,
            )
        handle.job =
            scope.launch {
                try {
                    t(profile).watchCouncilRun(initial).collect { s: CouncilLiveState -> onUpdate(toIos(s)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    onUpdate(toIos(initial.copy(phase = CouncilLivePhase.ERROR, error = e.message ?: "error")))
                }
            }
        return handle
    }

    /** GET /api/council/runs/{id} — replay a past run. */
    public fun open(
        profile: ServerProfile,
        runId: String,
        onSuccess: (IosCouncilLiveRun) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).councilGetRun(runId).fold(
                onSuccess = { r -> onSuccess(toIos(CouncilLiveReducer.fromRun(r))) },
                onFailure = { e -> onError(e.message ?: "Failed to load run.") },
            )
        }
    }

    /** POST /api/council/runs/{id}/cancel. */
    public fun cancel(
        profile: ServerProfile,
        runId: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val err: Throwable? = t(profile).councilStopRun(runId).exceptionOrNull()
            onDone(if (err == null) null else (err.message ?: "Cancel failed."))
        }
    }

    private fun toPast(r: CouncilRunDto): IosCouncilPastRun =
        IosCouncilPastRun(
            id = r.id,
            shortId = r.id.take(8),
            proposal = r.proposal,
            mode = r.mode,
            personaCount = r.personas.size,
            roundCount = r.rounds.size,
            running = !r.isFinished,
            consensus = r.consensus.orEmpty(),
            startedAt = r.startedAt.orEmpty(),
        )

    private fun toIos(s: CouncilLiveState): IosCouncilLiveRun =
        IosCouncilLiveRun(
            runId = s.runId,
            shortId = s.runId.take(8),
            proposal = s.proposal,
            mode = s.mode,
            personas = s.personas,
            roundsTotal = s.roundsTotal,
            rounds =
                s.rounds.map { r ->
                    IosCouncilRound(
                        index = r.index,
                        completed = r.completed,
                        replies = r.replies.map { p -> IosCouncilReply(persona = p.persona, status = p.status, text = p.text) },
                    )
                },
            phase = s.phase,
            terminal = s.isTerminal,
            consensus = s.consensus,
            dissent = s.dissent,
        )
}
