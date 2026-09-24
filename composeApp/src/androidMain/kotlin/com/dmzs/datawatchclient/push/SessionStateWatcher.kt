package com.dmzs.datawatchclient.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton that tracks session states across polling cycles and fires or
 * cancels [NotificationPoster.Event.Type.InputNeeded] notifications.
 *
 * Duplicate-suppression rules (mirrors server-side detection.alert_settle /
 * detection.alert_repeat defaults):
 *
 *   1. Settle window — a session must stay in [SessionState.Waiting] for at
 *      least [SETTLE_MS] (default 45 s) before a poll-sourced notification is
 *      fired. This prevents running→waiting→running flaps (server gap-watcher,
 *      slow model loads) from producing spurious alerts.
 *
 *   2. Prompt dedup — within a waiting episode the watcher fires only once
 *      per distinct prompt text. A new prompt (user is asked a different
 *      question) re-fires; a repeated prompt is silenced.
 *
 *   3. Cold-start safety — the first [onSessionsUpdated] call seeds the
 *      known-state map without firing, so already-waiting sessions on app
 *      launch do not produce spurious notifications.
 *
 *   4. Reconnect safety — a session that briefly vanishes from poll results
 *      (network blip) keeps its WaitingEpisode entry. If it returns still in
 *      Waiting with the same prompt, it will not re-notify.
 *
 * The pure-logic core lives in [computeUpdates] and is tested directly
 * without an Android [Context]; [onSessionsUpdated] applies the decisions.
 */
public object SessionStateWatcher {
    internal data class WaitingEpisode(
        val firstSeenMs: Long,
        val notifiedPrompt: String?,
    )

    private val knownStates = ConcurrentHashMap<String, SessionState>()
    private val waitingEpisodes = ConcurrentHashMap<String, WaitingEpisode>()
    private var seeded = false

    // Default settle window — mirrors server detection.alert_settle (45 s).
    internal const val SETTLE_MS: Long = 45_000L

    /** Called from SessionsViewModel after each successful poll. */
    public fun onSessionsUpdated(sessions: List<Session>, context: Context) {
        val (toPost, toCancel) = computeUpdates(sessions)
        if (toPost.isNotEmpty() || toCancel.isNotEmpty()) {
            val poster = NotificationPoster(context)
            toPost.forEach { poster.post(it) }
            val nm = NotificationManagerCompat.from(context)
            toCancel.forEach { sessionId -> nm.cancel(NotificationPoster.notificationIdFor(sessionId)) }
        }
    }

    /**
     * Call when the user replies to a session so the next distinct
     * prompt can trigger a new notification.
     */
    public fun onReplied(sessionId: String) {
        waitingEpisodes.remove(sessionId)
    }

    /** Reset all tracking — call when the active server profile changes. */
    public fun reset() {
        knownStates.clear()
        waitingEpisodes.clear()
        seeded = false
    }

    /**
     * Pure-logic core — returns (events to post, sessionIds whose notifications
     * should be cancelled) and updates internal state.
     *
     * @param nowMs injectable clock for tests; defaults to [System.currentTimeMillis].
     */
    internal fun computeUpdates(
        sessions: List<Session>,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<List<NotificationPoster.Event>, List<String>> {
        if (!seeded) {
            sessions.forEach { session ->
                knownStates[session.id] = session.state
                if (session.state == SessionState.Waiting) {
                    // Mark cold-start waiting sessions as already notified so they don't
                    // fire on the next poll cycle.
                    waitingEpisodes[session.id] = WaitingEpisode(
                        firstSeenMs = nowMs - SETTLE_MS,
                        notifiedPrompt = promptFor(session),
                    )
                }
            }
            seeded = true
            return Pair(emptyList(), emptyList())
        }

        val toPost = mutableListOf<NotificationPoster.Event>()
        val toCancel = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        sessions.forEach { session ->
            seen += session.id
            val prev = knownStates[session.id]
            val curr = session.state
            knownStates[session.id] = curr

            if (curr == SessionState.Waiting) {
                // If an episode already exists (session was Waiting before, briefly
                // disappeared due to a network blip, and came back), keep it intact to
                // preserve notifiedPrompt and firstSeenMs — prevents re-notification.
                val episode = waitingEpisodes.getOrPut(session.id) {
                    WaitingEpisode(firstSeenMs = nowMs, notifiedPrompt = null)
                }
                val prompt = promptFor(session)
                if (nowMs - episode.firstSeenMs >= SETTLE_MS && episode.notifiedPrompt != prompt) {
                    val name = session.name ?: session.taskSummary ?: session.id
                    toPost += NotificationPoster.Event(
                        sessionId = session.id,
                        type = NotificationPoster.Event.Type.InputNeeded,
                        title = name,
                        body = prompt,
                    )
                    waitingEpisodes[session.id] = episode.copy(notifiedPrompt = prompt)
                }
            } else if (prev == SessionState.Waiting) {
                // Definitively left Waiting — cancel and clear episode.
                toCancel += session.id
                waitingEpisodes.remove(session.id)
            }
        }

        // Sessions that vanished from the poll list may be a transient network blip.
        // Keep their WaitingEpisodes intact so they don't re-notify on reconnect.
        // Only prune knownStates and episodes for sessions that were NOT in Waiting.
        (knownStates.keys - seen).forEach { id ->
            if (knownStates[id] != SessionState.Waiting) {
                waitingEpisodes.remove(id)
            }
            knownStates.remove(id)
        }

        return Pair(toPost, toCancel)
    }

    internal fun promptFor(session: Session): String =
        session.promptContext?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(200)
            ?: session.lastPrompt?.takeIf { it.isNotBlank() }?.take(200)
            ?: session.lastSummaryLong?.takeIf { it.isNotBlank() }?.take(200)
            ?: session.lastResponse?.takeIf { it.isNotBlank() }?.take(200)
            ?: "Waiting for your input"
}
