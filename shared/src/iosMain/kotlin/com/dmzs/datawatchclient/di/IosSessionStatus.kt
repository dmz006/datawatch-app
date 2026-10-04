package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Status-tab snapshot: the hook status board plus optional telemetry. */
public data class IosSessionStatusSnapshot(
    /** Null when the server has no `/status` board for this session (older daemon). */
    val board: SessionStatusBoardDto?,
    val telemetry: SessionTelemetryDto?,
    val error: String?,
)

/**
 * Session Status sub-tab data (parity B8; PWA `/api/sessions/{id}/status` 5 s poll,
 * Android SessionStatusViewModel). Board and telemetry are fetched in parallel.
 */
public object IosSessionStatus {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        session: Session,
        onResult: (IosSessionStatusSnapshot) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val snapshot =
                coroutineScope {
                    val board = async { t.getSessionStatus(session.id) }
                    val telemetry = async { t.getSessionTelemetry(session.id).getOrNull() }
                    val b = board.await()
                    IosSessionStatusSnapshot(
                        board = b.getOrNull(),
                        telemetry = telemetry.await(),
                        error = b.exceptionOrNull()?.message,
                    )
                }
            onResult(snapshot)
        }
    }
}
