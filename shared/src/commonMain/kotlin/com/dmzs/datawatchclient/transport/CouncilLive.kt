package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.CouncilRunDto
import com.dmzs.datawatchclient.transport.dto.CouncilRunEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull

/** Phase of a council run as rendered by the live-run sheets. */
public object CouncilLivePhase {
    public const val CONNECTING: String = "connecting"
    public const val RUNNING: String = "running"
    public const val SYNTHESIZING: String = "synthesizing"
    public const val COMPLETED: String = "completed"
    public const val CANCELLED: String = "cancelled"
    public const val ERROR: String = "error"
}

/** Per-persona reply state inside one round. */
public object CouncilReplyStatus {
    public const val WAITING: String = "waiting"
    public const val RESPONDING: String = "responding"
    public const val DONE: String = "done"
    public const val ERROR: String = "error"
}

/** One persona's reply in a round ([status] is a [CouncilReplyStatus] value). */
public data class CouncilLiveReply(
    val persona: String,
    val status: String,
    val text: String,
)

/** One debate round. */
public data class CouncilLiveRound(
    val index: Int,
    val replies: List<CouncilLiveReply>,
    val completed: Boolean,
)

/**
 * Render model of one council run — built incrementally from SSE events
 * ([CouncilLiveReducer.reduce]) or in one go from the run detail
 * ([CouncilLiveReducer.fromRun]) for replaying past runs.
 */
public data class CouncilLiveState(
    val runId: String,
    val proposal: String = "",
    val mode: String = "",
    val personas: List<String> = emptyList(),
    val roundsTotal: Int = 0,
    val rounds: List<CouncilLiveRound> = emptyList(),
    val phase: String = CouncilLivePhase.CONNECTING,
    val consensus: String = "",
    val dissent: String = "",
    val error: String = "",
    val startedAt: String = "",
    val finishedAt: String = "",
) {
    val isTerminal: Boolean
        get() =
            phase == CouncilLivePhase.COMPLETED ||
                phase == CouncilLivePhase.CANCELLED ||
                phase == CouncilLivePhase.ERROR
}

/** Pure state reducer shared by Android + iOS. */
public object CouncilLiveReducer {
    /** Rounds per mode (server `council.Run`: quick = 1, debate = 3). */
    public fun roundsForMode(mode: String): Int = if (mode == "debate") 3 else 1

    public fun reduce(
        state: CouncilLiveState,
        ev: CouncilRunEvent,
    ): CouncilLiveState =
        when (ev.type) {
            CouncilRunEvent.TYPE_HELLO ->
                if (state.phase == CouncilLivePhase.CONNECTING) state.copy(phase = CouncilLivePhase.RUNNING) else state
            CouncilRunEvent.TYPE_RUN_STARTED ->
                state.copy(
                    phase = CouncilLivePhase.RUNNING,
                    mode = ev.mode.ifEmpty { state.mode },
                    personas = ev.personas.ifEmpty { state.personas },
                    roundsTotal = if (ev.roundsTotal > 0) ev.roundsTotal else state.roundsTotal,
                )
            CouncilRunEvent.TYPE_ROUND_STARTED -> ensureRound(state, ev.round).copy(phase = CouncilLivePhase.RUNNING)
            CouncilRunEvent.TYPE_PERSONA_RESPONDING ->
                upsertReply(state, ev.round, ev.persona, CouncilReplyStatus.RESPONDING, "")
            CouncilRunEvent.TYPE_PERSONA_RESPONSE ->
                upsertReply(state, ev.round, ev.persona, CouncilReplyStatus.DONE, ev.text)
            CouncilRunEvent.TYPE_PERSONA_ERROR ->
                upsertReply(state, ev.round, ev.persona, CouncilReplyStatus.ERROR, ev.error)
            CouncilRunEvent.TYPE_ROUND_COMPLETED -> {
                val s: CouncilLiveState = ensureRound(state, ev.round)
                s.copy(rounds = s.rounds.map { r -> if (r.index == ev.round) r.copy(completed = true) else r })
            }
            CouncilRunEvent.TYPE_SYNTHESIS_STARTED -> state.copy(phase = CouncilLivePhase.SYNTHESIZING)
            CouncilRunEvent.TYPE_RUN_COMPLETED ->
                state.copy(
                    phase = CouncilLivePhase.COMPLETED,
                    consensus = ev.consensus,
                    dissent = ev.dissent,
                    finishedAt = ev.finishedAt,
                    rounds = state.rounds.map { it.copy(completed = true) },
                )
            CouncilRunEvent.TYPE_RUN_CANCELLED ->
                state.copy(phase = CouncilLivePhase.CANCELLED, finishedAt = ev.finishedAt)
            else -> state
        }

    /** Full state from a persisted run (`GET /api/council/runs/{id}`). */
    public fun fromRun(run: CouncilRunDto): CouncilLiveState {
        val rounds: List<CouncilLiveRound> =
            run.rounds.sortedBy { it.index }.map { r ->
                val ordered: List<String> =
                    run.personas.filter { r.responses.containsKey(it) } +
                        r.responses.keys.filter { it !in run.personas }.sorted()
                CouncilLiveRound(
                    index = r.index,
                    replies =
                        ordered.map { name ->
                            val text: String = r.responses[name].orEmpty()
                            CouncilLiveReply(
                                persona = name,
                                status = if (isErrorText(name, text)) CouncilReplyStatus.ERROR else CouncilReplyStatus.DONE,
                                text = text,
                            )
                        },
                    completed = true,
                )
            }
        val phase: String =
            when {
                run.cancelled -> CouncilLivePhase.CANCELLED
                run.isFinished -> CouncilLivePhase.COMPLETED
                else -> CouncilLivePhase.RUNNING
            }
        return CouncilLiveState(
            runId = run.id,
            proposal = run.proposal,
            mode = run.mode,
            personas = run.personas,
            roundsTotal = maxOf(rounds.size, roundsForMode(run.mode)),
            rounds = rounds,
            phase = phase,
            consensus = run.consensus.orEmpty(),
            dissent = run.dissent.orEmpty(),
            startedAt = run.startedAt.orEmpty(),
            finishedAt = if (run.isFinished) run.finishedAt.orEmpty() else "",
        )
    }

    /** Server marks failed replies as `[<persona>] error: …` (council.go runRoundWithEvents). */
    private fun isErrorText(
        persona: String,
        text: String,
    ): Boolean = text.startsWith("[$persona] error:")

    private fun ensureRound(
        state: CouncilLiveState,
        index: Int,
    ): CouncilLiveState {
        if (index <= 0 || state.rounds.any { it.index == index }) return state
        val seeded: List<CouncilLiveReply> =
            state.personas.map { CouncilLiveReply(persona = it, status = CouncilReplyStatus.WAITING, text = "") }
        val rounds: List<CouncilLiveRound> =
            (state.rounds + CouncilLiveRound(index = index, replies = seeded, completed = false)).sortedBy { it.index }
        return state.copy(rounds = rounds)
    }

    private fun upsertReply(
        state: CouncilLiveState,
        round: Int,
        persona: String,
        status: String,
        text: String,
    ): CouncilLiveState {
        if (persona.isEmpty()) return state
        val s: CouncilLiveState = ensureRound(state, if (round > 0) round else 1)
        val idx: Int = if (round > 0) round else 1
        val rounds: List<CouncilLiveRound> =
            s.rounds.map { r ->
                if (r.index != idx) {
                    r
                } else {
                    val reply = CouncilLiveReply(persona = persona, status = status, text = text)
                    val replies: List<CouncilLiveReply> =
                        if (r.replies.any { it.persona == persona }) {
                            r.replies.map { if (it.persona == persona) reply else it }
                        } else {
                            r.replies + reply
                        }
                    r.copy(replies = replies)
                }
            }
        val phase: String = if (s.phase == CouncilLivePhase.CONNECTING) CouncilLivePhase.RUNNING else s.phase
        return s.copy(rounds = rounds, phase = phase)
    }
}

/**
 * Watches one council run: subscribes to the SSE stream, reduces events into
 * [CouncilLiveState], and reconciles with the persisted run detail —
 *
 *  - on `hello`, if the run already finished before we subscribed (the hub
 *    has no replay), the detail is shown and the watch ends;
 *  - on a terminal event, the detail fills in replies missed before connect;
 *  - if the stream drops before a terminal event, the detail is polled until
 *    the run is persisted (runs are only written on completion).
 */
public fun TransportClient.watchCouncilRun(
    initial: CouncilLiveState,
    pollIntervalMs: Long = 5_000L,
    maxPolls: Int = 720,
): Flow<CouncilLiveState> =
    channelFlow {
        val runId: String = initial.runId
        var state: CouncilLiveState = initial
        send(state)

        suspend fun finishFromDetail(): Boolean {
            val run: CouncilRunDto = councilGetRun(runId).getOrNull() ?: return false
            if (!run.isFinished) return false
            val detail: CouncilLiveState = CouncilLiveReducer.fromRun(run)
            state = detail.copy(proposal = detail.proposal.ifEmpty { state.proposal })
            send(state)
            return true
        }

        var finished = false
        try {
            councilRunEvents(runId).firstOrNull { ev ->
                state = CouncilLiveReducer.reduce(state, ev)
                send(state)
                when {
                    ev.type == CouncilRunEvent.TYPE_HELLO -> {
                        finished = finishFromDetail()
                        finished
                    }
                    ev.isTerminal -> {
                        // The run is persisted just after run_completed is
                        // emitted — retry briefly so the detail can fill in
                        // replies published before we connected.
                        var ok: Boolean = finishFromDetail()
                        var tries = 0
                        while (!ok && tries < 3) {
                            delay(1_000L)
                            tries += 1
                            ok = finishFromDetail()
                        }
                        finished = ok || state.isTerminal
                        true
                    }
                    else -> false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Stream failed — fall through to polling the run detail.
        }
        var polls = 0
        while (!finished && !state.isTerminal && polls < maxPolls) {
            delay(pollIntervalMs)
            polls += 1
            finished = finishFromDetail()
        }
        if (!finished && !state.isTerminal) {
            state = state.copy(phase = CouncilLivePhase.ERROR, error = "Lost connection to the council run.")
            send(state)
        }
    }
