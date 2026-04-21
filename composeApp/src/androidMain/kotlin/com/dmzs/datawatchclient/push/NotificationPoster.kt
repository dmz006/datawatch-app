package com.dmzs.datawatchclient.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.dmzs.datawatchclient.MainActivity
import com.dmzs.datawatchclient.R

/**
 * Builds and posts notifications for incoming push events.
 *
 * Channel selection follows event semantics:
 * - input_needed → high-importance, attaches a RemoteInput "Reply" action
 * - rate_limited / completed / state_change → default-importance
 * - error → high-importance
 *
 * Tap intent always opens the app via the `dwclient://session/<id>` deep link
 * declared in AndroidManifest, so the Sessions tab can navigate directly to
 * SessionDetail.
 */
public class NotificationPoster(private val context: Context) {
    public data class Event(
        val sessionId: String,
        val type: Type,
        val title: String,
        val body: String,
        val profileHint: String? = null,
    ) {
        public enum class Type { InputNeeded, RateLimited, Completed, Error, StateChange }
    }

    public fun post(event: Event) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        // Active-session suppression — skip notifications for a
        // session the user is already looking at in the foreground.
        // Matches PWA behaviour where no bell rings for the visible
        // session. State-change notifications pass through since
        // the user may want a haptic for state transitions even
        // with the session visible.
        if (event.type == Event.Type.InputNeeded &&
            ForegroundSessionTracker.isForeground(event.sessionId)
        ) {
            android.util.Log.d(
                "NotificationPoster",
                "suppressed InputNeeded for foreground session ${event.sessionId}",
            )
            return
        }
        NotificationChannels.ensureRegistered(context)

        val (channel, importance) =
            when (event.type) {
                Event.Type.InputNeeded -> NotificationChannels.INPUT_NEEDED to NotificationCompat.PRIORITY_HIGH
                Event.Type.Error -> NotificationChannels.ERROR to NotificationCompat.PRIORITY_HIGH
                Event.Type.RateLimited -> NotificationChannels.RATE_LIMITED to NotificationCompat.PRIORITY_DEFAULT
                else -> NotificationChannels.COMPLETED to NotificationCompat.PRIORITY_DEFAULT
            }

        val builder =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_dw)
                .setContentTitle(event.title)
                .setContentText(event.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
                .setPriority(importance)
                .setAutoCancel(true)
                .setContentIntent(deepLinkIntent(event.sessionId))

        if (event.type == Event.Type.InputNeeded) {
            builder.addAction(buildReplyAction(event.sessionId))
        }

        try {
            nm.notify(notificationIdFor(event.sessionId), builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+; surface to logcat only.
            android.util.Log.w("NotificationPoster", "post denied: ${e.message}")
        }
    }

    private fun deepLinkIntent(sessionId: String): PendingIntent {
        val uri = Uri.parse("dwclient://session/$sessionId")
        val intent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // Ensure the app opens to MainActivity; the deep-link `data` URI carries the routing.
                setClass(context, MainActivity::class.java)
            }
        return PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildReplyAction(sessionId: String): NotificationCompat.Action {
        val remoteInput =
            RemoteInput.Builder(REPLY_REMOTE_INPUT_KEY)
                .setLabel("Reply")
                .build()

        val replyIntent =
            Intent(context, ReplyBroadcastReceiver::class.java).apply {
                action = ReplyBroadcastReceiver.ACTION_REPLY
                putExtra(ReplyBroadcastReceiver.EXTRA_SESSION_ID, sessionId)
            }
        val replyPi =
            PendingIntent.getBroadcast(
                context,
                sessionId.hashCode() xor REPLY_REQUEST_CODE_SALT,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_dw,
            "Reply",
            replyPi,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    public companion object {
        public const val REPLY_REMOTE_INPUT_KEY: String = "dw.reply.text"
        private const val ID_BASE: Int = 1_000_000

        // Salt added to PendingIntent requestCodes for reply broadcasts so they
        // don't collide with the deep-link requestCodes (which use the bare
        // sessionId hashCode).
        private const val REPLY_REQUEST_CODE_SALT: Int = 0x5250_4C59

        public fun notificationIdFor(sessionId: String): Int = ID_BASE + (sessionId.hashCode() and 0x0F_FFFF)
    }
}
