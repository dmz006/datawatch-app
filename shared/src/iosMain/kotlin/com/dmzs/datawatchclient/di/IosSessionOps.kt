package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Session-detail operations for Swift (parity B6): state override, delete with a
 * memory strategy, and the server timeline. Ids follow Android's
 * SessionDetailViewModel — mutations use the full id, the timeline uses the short id.
 */
public object IosSessionOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** POST /api/sessions/state. [wireState]: running | waiting_input | complete | killed | failed (PWA options). */
    public fun overrideState(
        profile: ServerProfile,
        session: Session,
        wireState: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val state =
            when (wireState) {
                "running" -> SessionState.Running
                "waiting_input" -> SessionState.Waiting
                "complete" -> SessionState.Completed
                "killed" -> SessionState.Killed
                "failed" -> SessionState.Error
                else -> {
                    onError("Unknown state: $wireState")
                    return
                }
            }
        scope.launch {
            IosServiceLocator.transportFor(profile).overrideSessionState(session.fullId, state).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Couldn't change the state.") },
            )
        }
    }

    /**
     * Delete with a memory strategy: "keep" | "purge" | "archive". For "archive",
     * [roleFilter] is an optional comma-separated role-prefix list and [archiveScope]
     * is "project-shared" or "global-shared".
     */
    public fun delete(
        profile: ServerProfile,
        session: Session,
        strategy: String,
        roleFilter: String,
        archiveScope: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val archive = strategy == "archive"
            IosServiceLocator.transportFor(profile).deleteSession(
                sessionId = session.fullId,
                memoryStrategy = strategy.takeIf { it != "keep" },
                archiveRoleFilter =
                    if (archive) roleFilter.split(',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList(),
                archiveToScope = archiveScope.takeIf { archive && it.isNotBlank() },
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Delete failed.") },
            )
        }
    }

    /** GET /api/sessions/timeline — pipe-delimited lines "<ts> | <event> | <detail>". */
    public fun timeline(
        profile: ServerProfile,
        session: Session,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchTimeline(session.id).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load timeline.") },
            )
        }
    }
}
