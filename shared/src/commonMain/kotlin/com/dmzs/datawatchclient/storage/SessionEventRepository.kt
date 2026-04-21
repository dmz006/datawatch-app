package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.Prompt
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Persisted ring buffer of [SessionEvent]s per session. The live WebSocket
 * stream writes into this repo; the detail UI observes it. Capped at
 * [RETAIN_PER_SESSION] events per session; oldest are pruned on insert.
 */
public class SessionEventRepository(
    private val db: DatawatchDb,
    private val ioDispatcher: CoroutineDispatcher,
) {
    public companion object {
        public const val RETAIN_PER_SESSION: Long = 5_000L
    }

    /**
     * In-memory latest [SessionEvent.PaneCapture] per session id. Pane captures
     * are not persisted to SQLite (whole-screen snapshots, not event-log rows),
     * but they **must** reach the live terminal view — so we keep the newest
     * capture here and merge it into [observe]'s flow. Previously the insert
     * path silently dropped captures and TerminalView fell back to the legacy
     * `raw_output` renderer, which parent `dmz006/datawatch` killed off in
     * commit `0393e262` (garbles TUIs).
     */
    private val liveCaptures: MutableMap<String, MutableStateFlow<SessionEvent.PaneCapture?>> =
        mutableMapOf()

    private fun captureFlowFor(sessionId: String): MutableStateFlow<SessionEvent.PaneCapture?> =
        liveCaptures.getOrPut(sessionId) { MutableStateFlow(null) }

    public fun observe(
        sessionId: String,
        limit: Long = RETAIN_PER_SESSION,
    ): Flow<List<SessionEvent>> {
        val dbFlow =
            db.eventQueries.selectEventsForSession(sessionId, limit)
                .asFlow()
                .mapToList(ioDispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        val liveFlow = captureFlowFor(sessionId)
        return combine(dbFlow, liveFlow) { dbEvents, liveCapture ->
            if (liveCapture == null) dbEvents else dbEvents + liveCapture
        }
    }

    // Encrypted SQLCipher inserts are 5–50 ms each on a mobile SoC; running
    // them on the caller's dispatcher is what blocked main and triggered the
    // "Input dispatching timed out" ANR we saw on B1 bursts. Force IO.
    public suspend fun insert(event: SessionEvent): Unit =
        withContext(ioDispatcher) {
            when (event) {
                is SessionEvent.Output ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "output",
                        body = event.body,
                        stream = event.stream.name.lowercase(),
                        from_state = null,
                        to_state = null,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = null,
                        exit_code = null,
                        message = null,
                    )
                is SessionEvent.StateChange ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "state",
                        body = null,
                        stream = null,
                        from_state = event.from.name,
                        to_state = event.to.name,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = null,
                        exit_code = null,
                        message = null,
                    )
                is SessionEvent.PromptDetected ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "prompt",
                        body = null,
                        stream = null,
                        from_state = null,
                        to_state = null,
                        prompt_text = event.prompt.text,
                        prompt_kind = event.prompt.kind.name,
                        retry_after = null,
                        exit_code = null,
                        message = null,
                    )
                is SessionEvent.RateLimited ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "rate",
                        body = null,
                        stream = null,
                        from_state = null,
                        to_state = null,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = event.retryAfter?.toEpochMilliseconds(),
                        exit_code = null,
                        message = null,
                    )
                is SessionEvent.Completed ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "done",
                        body = null,
                        stream = null,
                        from_state = null,
                        to_state = null,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = null,
                        exit_code = event.exitCode?.toLong(),
                        message = null,
                    )
                is SessionEvent.Error ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = "error",
                        body = null,
                        stream = null,
                        from_state = null,
                        to_state = null,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = null,
                        exit_code = null,
                        message = event.message,
                    )
                is SessionEvent.Unknown ->
                    db.eventQueries.insertEvent(
                        session_id = event.sessionId,
                        ts = event.ts.toEpochMilliseconds(),
                        type = event.type,
                        body = null,
                        stream = null,
                        from_state = null,
                        to_state = null,
                        prompt_text = null,
                        prompt_kind = null,
                        retry_after = null,
                        exit_code = null,
                        message = null,
                    )
                is SessionEvent.PaneCapture ->
                    // Pane captures are live-display-only — whole-screen snapshots,
                    // not event-log rows. Keep the newest in the in-memory live bus
                    // so [observe] merges it into the emitted list; TerminalView
                    // keys off `hasPaneCapture` to pick the pane-capture render path
                    // instead of the legacy raw_output fallback (parent killed that
                    // path in `0393e262` as broken for TUIs).
                    captureFlowFor(event.sessionId).value = event
            }
            // Prune oldest after each insert so the ring buffer stays bounded.
            db.eventQueries.pruneOldEvents(event.sessionId, event.sessionId, RETAIN_PER_SESSION)
        }

    public suspend fun deleteForSession(sessionId: String): Unit =
        withContext(ioDispatcher) {
            db.eventQueries.deleteEventsForSession(sessionId)
        }

    private fun com.dmzs.datawatchclient.db.Session_event.toDomain(): SessionEvent =
        when (type) {
            "output" ->
                SessionEvent.Output(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    body = body ?: "",
                    stream =
                        when (stream?.lowercase()) {
                            "stderr" -> SessionEvent.Output.Stream.Stderr
                            "system" -> SessionEvent.Output.Stream.System
                            else -> SessionEvent.Output.Stream.Stdout
                        },
                )
            "state" ->
                SessionEvent.StateChange(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    from = from_state?.let { SessionState.valueOf(it) } ?: SessionState.New,
                    to = to_state?.let { SessionState.valueOf(it) } ?: SessionState.Error,
                )
            "prompt" ->
                SessionEvent.PromptDetected(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    prompt =
                        Prompt(
                            sessionId = session_id,
                            text = prompt_text ?: "",
                            detectedAt = Instant.fromEpochMilliseconds(ts),
                            kind =
                                prompt_kind?.let {
                                    runCatching { Prompt.Kind.valueOf(it) }.getOrNull()
                                } ?: Prompt.Kind.FreeForm,
                        ),
                )
            "rate" ->
                SessionEvent.RateLimited(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    retryAfter = retry_after?.let { Instant.fromEpochMilliseconds(it) },
                )
            "done" ->
                SessionEvent.Completed(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    exitCode = exit_code?.toInt(),
                )
            "error" ->
                SessionEvent.Error(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    message = message ?: "error",
                )
            else ->
                SessionEvent.Unknown(
                    sessionId = session_id,
                    ts = Instant.fromEpochMilliseconds(ts),
                    type = type,
                )
        }
}
