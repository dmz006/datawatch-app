package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.SessionDto
import kotlinx.datetime.Instant

internal fun SessionDto.toDomain(serverProfileId: String): Session = Session(
    id = id,
    serverProfileId = serverProfileId,
    hostnamePrefix = hostnamePrefix,
    state = SessionState.fromWire(state),
    taskSummary = taskSummary,
    createdAt = Instant.fromEpochMilliseconds(createdTs),
    lastActivityAt = Instant.fromEpochMilliseconds(lastActivityTs),
)
