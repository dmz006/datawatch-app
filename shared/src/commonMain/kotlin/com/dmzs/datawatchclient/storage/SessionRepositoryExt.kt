package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Observe a single session by id across all profiles — the session detail
 * screen doesn't know the owning profile until it reaches the ViewModel.
 */
public fun SessionRepository.observeForProfileAny(
    sessionId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Flow<Session?> =
    db.sessionQueries.selectSessionById(sessionId)
        .asFlow()
        .mapToOneOrNull(dispatcher)
        .map { row ->
            row?.let {
                Session(
                    id = it.id,
                    serverProfileId = it.server_profile_id,
                    hostnamePrefix = it.hostname_prefix,
                    state = SessionState.fromWire(it.state),
                    taskSummary = it.task_summary,
                    createdAt = Instant.fromEpochMilliseconds(it.created_ts),
                    lastActivityAt = Instant.fromEpochMilliseconds(it.last_activity_ts),
                    muted = it.muted != 0L,
                    lastPrompt = it.last_prompt,
                )
            }
        }
