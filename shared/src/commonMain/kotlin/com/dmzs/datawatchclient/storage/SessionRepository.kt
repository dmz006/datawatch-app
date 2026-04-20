package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Cached session state per server profile. The server remains source of truth
 * (ADR-0008); this repository exists so the UI can render instantly from cache
 * while a fresh [com.dmzs.datawatchclient.transport.TransportClient.listSessions]
 * call resolves.
 */
public class SessionRepository(
    internal val db: DatawatchDb,
    private val ioDispatcher: CoroutineDispatcher,
) {
    public fun observeForProfile(profileId: String): Flow<List<Session>> =
        db.sessionQueries.selectSessionsForProfile(profileId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    public suspend fun replaceAll(
        profileId: String,
        sessions: List<Session>,
    ) {
        db.transaction {
            db.sessionQueries.deleteSessionsForProfile(profileId)
            sessions.forEach { upsertInternal(it) }
        }
    }

    public suspend fun upsert(session: Session) {
        upsertInternal(session)
    }

    public suspend fun setMuted(
        sessionId: String,
        muted: Boolean,
    ) {
        db.sessionQueries.setSessionMuted(if (muted) 1L else 0L, sessionId)
    }

    private fun upsertInternal(session: Session) {
        db.sessionQueries.upsertSession(
            id = session.id,
            server_profile_id = session.serverProfileId,
            hostname_prefix = session.hostnamePrefix,
            state = session.state.name,
            task_summary = session.taskSummary,
            created_ts = session.createdAt.toEpochMilliseconds(),
            last_activity_ts = session.lastActivityAt.toEpochMilliseconds(),
            muted = if (session.muted) 1L else 0L,
        )
    }

    private fun com.dmzs.datawatchclient.db.Session.toDomain(): Session =
        Session(
            id = id,
            serverProfileId = server_profile_id,
            hostnamePrefix = hostname_prefix,
            state = SessionState.fromWire(state),
            taskSummary = task_summary,
            createdAt = Instant.fromEpochMilliseconds(created_ts),
            lastActivityAt = Instant.fromEpochMilliseconds(last_activity_ts),
            muted = muted != 0L,
        )
}
