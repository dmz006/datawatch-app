package com.dmzs.datawatchclient.push

import com.dmzs.datawatchclient.di.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM payloads from datawatch servers (parent issue #1, v3.0.0).
 *
 * Payload contract (data-only, no notification body — we always render via
 * NotificationPoster so channel + RemoteInput behaviour is consistent across
 * FCM and ntfy):
 *
 *   {
 *     "session_id": "ssn-...",
 *     "type": "input_needed" | "rate_limited" | "completed" | "error" | "state_change",
 *     "title": "...",
 *     "body": "...",
 *     "profile_hint": "primary"
 *   }
 *
 * Unknown `type` values fall through to the COMPLETED channel rather than
 * being dropped — keeps server-side evolution forward-compatible.
 */
public class DatawatchFcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val sessionId = data["session_id"] ?: return
        val typeWire = data["type"].orEmpty()
        val event = NotificationPoster.Event(
            sessionId = sessionId,
            type = parseType(typeWire),
            title = data["title"] ?: "Datawatch",
            body = data["body"] ?: typeWire.replace('_', ' '),
            profileHint = data["profile_hint"],
        )
        NotificationPoster(applicationContext).post(event)
    }

    override fun onNewToken(token: String) {
        ServiceLocator.pushTokenStore.setFcmToken(token)
        // Re-register against every enabled profile. Coordinator handles dedupe.
        scope.launch {
            PushRegistrationCoordinator(applicationContext).registerAll()
        }
    }

    private fun parseType(wire: String): NotificationPoster.Event.Type = when (wire) {
        "input_needed" -> NotificationPoster.Event.Type.InputNeeded
        "rate_limited" -> NotificationPoster.Event.Type.RateLimited
        "completed" -> NotificationPoster.Event.Type.Completed
        "error" -> NotificationPoster.Event.Type.Error
        "state_change" -> NotificationPoster.Event.Type.StateChange
        else -> NotificationPoster.Event.Type.Completed
    }
}
