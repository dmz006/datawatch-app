package com.dmzs.datawatchclient.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton that tracks session states across polling cycles and fires or
 * cancels [NotificationPoster.Event.Type.InputNeeded] notifications when a
 * session transitions into or out of the [SessionState.Waiting] state.
 *
 * The first call to [onSessionsUpdated] seeds the known-state map without
 * firing any notifications, so cold-start sessions that are already waiting
 * do not produce spurious alerts.
 *
 * Called from [com.dmzs.datawatchclient.ui.sessions.SessionsViewModel.refresh]
 * after each successful poll — no server changes required.
 */
public object SessionStateWatcher {
    // Maps sessionId → previous known state
    private val knownStates = ConcurrentHashMap<String, SessionState>()
    private var seeded = false // first call seeds without firing

    public fun onSessionsUpdated(sessions: List<Session>, context: Context) {
        if (!seeded) {
            sessions.forEach { knownStates[it.id] = it.state }
            seeded = true
            return
        }
        val seen = mutableSetOf<String>()
        sessions.forEach { session ->
            seen += session.id
            val prev = knownStates[session.id]
            val curr = session.state
            if (prev != null && prev != SessionState.Waiting && curr == SessionState.Waiting) {
                // Transitioned INTO Waiting — fire notification.
                // title = session name (becomes the MessagingStyle "sender" Auto reads aloud).
                // body  = the prompt the session is waiting on, in priority order.
                val name = session.name ?: session.taskSummary ?: session.id
                // promptContext overrides lastPrompt per server spec — it's the pre-processed
                // last ~4 lines of conversation rather than the raw LLM prompt string.
                val body = session.promptContext?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(200)
                    ?: session.lastPrompt?.takeIf { it.isNotBlank() }?.take(200)
                    ?: session.lastSummaryLong?.takeIf { it.isNotBlank() }?.take(200)
                    ?: session.lastResponse?.takeIf { it.isNotBlank() }?.take(200)
                    ?: "Waiting for your input"
                NotificationPoster(context).post(
                    NotificationPoster.Event(
                        sessionId = session.id,
                        type = NotificationPoster.Event.Type.InputNeeded,
                        title = name,
                        body = body,
                    )
                )
            } else if (prev == SessionState.Waiting && curr != SessionState.Waiting) {
                // Transitioned OUT OF Waiting — cancel the notification
                NotificationManagerCompat.from(context)
                    .cancel(NotificationPoster.notificationIdFor(session.id))
            }
            knownStates[session.id] = curr
        }
        // Clean up removed sessions
        (knownStates.keys - seen).forEach { knownStates.remove(it) }
    }
}
