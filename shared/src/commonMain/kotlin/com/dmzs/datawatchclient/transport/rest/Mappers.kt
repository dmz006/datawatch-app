package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionDto
import kotlinx.datetime.Instant

/**
 * SessionDto → domain mapper. The wire format uses RFC3339 string timestamps
 * (`created_at`, `updated_at`); we parse them via [Instant.parse]. If the
 * server omits a timestamp (rare but legal for new sessions), we fall back
 * to [Instant.DISTANT_PAST] so the UI doesn't crash on null.
 */
internal fun SessionDto.toDomain(serverProfileId: String): Session = Session(
    id = id,
    serverProfileId = serverProfileId,
    hostnamePrefix = hostname,
    state = SessionState.fromWire(state),
    taskSummary = task,
    createdAt = createdAt.toInstantOrEpoch(),
    lastActivityAt = updatedAt.toInstantOrEpoch(),
)

private fun String?.toInstantOrEpoch(): Instant =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Instant.DISTANT_PAST
