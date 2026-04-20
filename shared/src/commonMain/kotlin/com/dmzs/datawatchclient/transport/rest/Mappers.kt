package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.AlertDto
import com.dmzs.datawatchclient.transport.dto.ServerInfoDto
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

internal fun AlertDto.toDomain(serverProfileId: String): Alert = Alert(
    id = id,
    serverProfileId = serverProfileId,
    type = type,
    severity = severity.toAlertSeverity(),
    message = message,
    sessionId = sessionId,
    createdAt = createdAt.toInstantOrEpoch(),
    read = read,
)

internal fun ServerInfoDto.toDomain(): ServerInfo = ServerInfo(
    hostname = hostname,
    version = version,
    llmBackend = llmBackend,
    messagingBackend = messagingBackend,
    sessionCount = sessionCount,
    serverHost = server?.host,
    serverPort = server?.port,
)

private fun String?.toAlertSeverity(): AlertSeverity = when (this?.lowercase()) {
    "error", "critical", "fatal" -> AlertSeverity.Error
    "warn", "warning" -> AlertSeverity.Warning
    else -> AlertSeverity.Info
}

private fun String?.toInstantOrEpoch(): Instant =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Instant.DISTANT_PAST
