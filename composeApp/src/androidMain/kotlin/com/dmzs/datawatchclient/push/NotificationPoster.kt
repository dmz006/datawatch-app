package com.dmzs.datawatchclient.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
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
                .setPriority(importance)
                .setAutoCancel(true)
                .setContentIntent(deepLinkIntent(event.sessionId))

        if (event.type == Event.Type.InputNeeded) {
            // MessagingStyle tells Android Auto to treat this as a car message:
            // the head unit reads it aloud via TTS and offers native voice reply
            // through the RemoteInput action below — no app screen required.
            val sender = Person.Builder()
                .setName(event.title)
                .setImportant(true)
                .build()
            builder.setStyle(
                NotificationCompat.MessagingStyle(sender)
                    .setConversationTitle(event.title)
                    .addMessage(event.body, System.currentTimeMillis(), sender)
            )
            builder.addAction(buildQuickReplyAction(event.sessionId, "Yes", "yes"))
            builder.addAction(buildQuickReplyAction(event.sessionId, "No", "no"))
            builder.addAction(buildReplyAction(event.sessionId))
            builder.extend(buildCarAppExtender(event))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
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

    private fun buildQuickReplyAction(sessionId: String, label: String, text: String): NotificationCompat.Action {
        val intent = Intent(context, ReplyBroadcastReceiver::class.java).apply {
            action = ReplyBroadcastReceiver.ACTION_QUICK_REPLY
            putExtra(ReplyBroadcastReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(ReplyBroadcastReceiver.EXTRA_REPLY_TEXT, text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (sessionId + label).hashCode() xor QUICK_REPLY_REQUEST_CODE_SALT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_dw, label, pi).build()
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

    /**
     * Builds a [androidx.car.app.notification.CarAppExtender] for [Event.Type.InputNeeded]
     * notifications. Sets high importance so the car head unit surfaces the alert
     * immediately. Content intent carries the session ID + title so tapping the alert
     * in the car navigates directly to [AutoSessionDetailScreen] via [onNewIntent].
     *
     * IMPORTANT: The intent must target the CarAppService (DatawatchMessagingService) via
     * getService(), NOT MainActivity via getActivity(). CarAppExtender.contentIntent in
     * the head unit is delivered to the service's onNewIntent() — an Activity intent silently
     * does nothing in Android Auto (activities cannot run on the head unit).
     */
    private fun buildCarAppExtender(
        event: Event,
    ): androidx.car.app.notification.CarAppExtender {
        val tapIntent =
            android.content.Intent().apply {
                // Reference DatawatchMessagingService by class name to avoid an import cycle
                // between :composeApp and :auto. The class name is stable — it's part of the
                // public AndroidManifest component declaration.
                setClassName(
                    context.packageName,
                    "com.dmzs.datawatchclient.auto.messaging.DatawatchMessagingService",
                )
                action = android.content.Intent.ACTION_VIEW
                putExtra(EXTRA_CAR_SESSION_ID, event.sessionId)
                putExtra(EXTRA_CAR_SESSION_TITLE, event.title)
            }
        val tapPi =
            android.app.PendingIntent.getService(
                context,
                event.sessionId.hashCode() xor CAR_TAP_REQUEST_CODE_SALT,
                tapIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        return androidx.car.app.notification.CarAppExtender.Builder()
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setImportance(androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH)
            .setContentIntent(tapPi)
            .build()
    }

    public companion object {
        public const val REPLY_REMOTE_INPUT_KEY: String = "dw.reply.text"

        /** Intent extras read by the car app's [onNewIntent] to navigate to a session. */
        public const val EXTRA_CAR_SESSION_ID: String = "dw.car.session_id"
        public const val EXTRA_CAR_SESSION_TITLE: String = "dw.car.session_title"

        private const val ID_BASE: Int = 1_000_000

        // Salt added to PendingIntent requestCodes so they don't collide with each other.
        private const val REPLY_REQUEST_CODE_SALT: Int = 0x5250_4C59
        private const val QUICK_REPLY_REQUEST_CODE_SALT: Int = 0x5155_4352
        private const val CAR_TAP_REQUEST_CODE_SALT: Int = 0x4341_5220

        public fun notificationIdFor(sessionId: String): Int = ID_BASE + (sessionId.hashCode() and 0x0F_FFFF)
    }
}
