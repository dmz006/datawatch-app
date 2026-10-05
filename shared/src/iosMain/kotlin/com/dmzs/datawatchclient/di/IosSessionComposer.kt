package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One pending schedule for the session-detail strip. [runAtMs] 0 = cron / unknown. */
public data class IosPendingSchedule(
    val id: String,
    val command: String,
    val runAtMs: Long,
    val cron: String,
)

/**
 * Session-detail composer gaps (parity 03): pending-schedules strip
 * (PWA `loadSessionSchedules`, `/api/schedules?session_id&state=pending`,
 * per-item ✕ cancel) and send-via-channel `▶ ch` (PWA `sendChannelMessage`,
 * POST /api/channel/send). Callbacks run on a background thread.
 */
public object IosSessionComposer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun pendingSchedules(
        profile: ServerProfile,
        sessionId: String,
        onResult: (List<IosPendingSchedule>) -> Unit,
    ) {
        scope.launch {
            val list = IosServiceLocator.transportFor(profile)
                .listSchedules(sessionId = sessionId, state = "pending")
                .getOrNull()
                .orEmpty()
            val rows: List<IosPendingSchedule> =
                list.map { sc ->
                    IosPendingSchedule(
                        id = sc.id,
                        command = sc.task,
                        runAtMs = sc.runAt?.toEpochMilliseconds() ?: 0L,
                        cron = sc.cron.orEmpty(),
                    )
                }
            onResult(rows)
        }
    }

    /** onDone(null) on success, else an error message. */
    public fun cancelSchedule(
        profile: ServerProfile,
        scheduleId: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).deleteSchedule(scheduleId)
            onDone(r.exceptionOrNull()?.let { "Cancel failed: " + (it.message ?: "error") })
        }
    }

    /**
     * D43a fresh-fetch response viewer: onResult(response, null) on success
     * (response "" when none was captured), else onResult(null, message).
     */
    public fun fetchResponse(
        profile: ServerProfile,
        sessionId: String,
        onResult: (String?, String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchSessionResponse(sessionId).fold(
                onSuccess = { text: String -> onResult(text, null) },
                onFailure = { e: Throwable -> onResult(null, e.message ?: "Failed to load response.") },
            )
        }
    }

    /** onDone(null) on success, else an error message. */
    public fun sendChannel(
        profile: ServerProfile,
        sessionId: String,
        text: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r = IosServiceLocator.transportFor(profile).sendChannelMessage(sessionId, text)
            onDone(r.exceptionOrNull()?.let { "Channel send failed: " + (it.message ?: "error") })
        }
    }
}
