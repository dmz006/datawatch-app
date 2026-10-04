package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.dmzs.datawatchclient.transport.ws.WsOutbound

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

    /**
     * `sendkey <fullId>: <Key>` over the session's open /ws (PWA keys strip / quick inputs).
     * [key] is a tmux key name: Escape, Up, Down, Left, Right, Enter, C-c, C-b.
     * Returns false when no socket is subscribed to the session.
     */
    public fun sendKey(session: Session, key: String): Boolean =
        WsOutbound.sendCommand(session.id, "sendkey ${session.fullId}: $key")

    /** tmux scroll commands (PWA): tmux-copy-mode | tmux-page-up | tmux-page-down. */
    public fun tmuxCommand(session: Session, command: String): Boolean =
        WsOutbound.sendCommand(session.id, "$command ${session.fullId}")

    /**
     * Schedule input for this session (PWA showScheduleInputPopup → POST /api/schedules).
     * [runAt]: natural language ("in 30m", "at 14:00", "tomorrow at 9am") or blank for
     * on-next-input; [cronExpr]: optional 5-field cron, overrides [runAt] for recurrence.
     */
    public fun scheduleInput(
        profile: ServerProfile,
        session: Session,
        command: String,
        runAt: String,
        cronExpr: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).createSchedule(
                task = command.trim(),
                cron = cronExpr.trim(),
                sessionId = session.fullId,
                runAt = runAt.trim(),
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Couldn't schedule the command.") },
            )
        }
    }

    /** `send_input` over the session's open /ws (used by chat-mode sessions, which mount no terminal). */
    public fun sendText(session: Session, text: String): Boolean = WsOutbound.sendInput(session.id, text)

    /** Chat role as a plain string ("user" | "assistant" | "system") — avoids Kotlin enum interop in Swift. */
    public fun chatRole(event: com.dmzs.datawatchclient.domain.SessionEvent.ChatMessage): String =
        event.role.name.lowercase()
}
