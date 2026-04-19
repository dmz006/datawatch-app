package com.dmzs.datawatchclient.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.dmzs.datawatchclient.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles inline replies posted from an "Input needed" notification's RemoteInput.
 * Extracts the typed text, picks the active server profile (Sprint 2: first
 * enabled), and POSTs to /api/sessions/reply. Dismisses the notification on
 * success; updates with an error string on failure.
 */
public class ReplyBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationPoster.REPLY_REMOTE_INPUT_KEY)
            ?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        scope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val active = profiles.firstOrNull { it.enabled } ?: return@launch
            val transport = ServiceLocator.transportFor(active)
            transport.replyToSession(sessionId, text).fold(
                onSuccess = {
                    NotificationManagerCompat.from(context)
                        .cancel(NotificationPoster.notificationIdFor(sessionId))
                },
                onFailure = {
                    android.util.Log.w(
                        "ReplyBroadcastReceiver",
                        "reply failed for $sessionId: ${it.message}",
                    )
                },
            )
        }
    }

    public companion object {
        public const val ACTION_REPLY: String = "com.dmzs.datawatchclient.action.REPLY"
        public const val EXTRA_SESSION_ID: String = "session_id"
    }
}
