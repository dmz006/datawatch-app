package com.dmzs.datawatchclient.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * BL27 — Wake-on-alert for waiting_input sessions.
 * Phone's WearSyncService sends [ALERT_PATH] when a session
 * transitions to waiting_input; this posts a high-priority
 * notification so the watch wakes and the user can reply.
 *
 * W-#107 — also handles council consensus ([COUNCIL_ALERT_PATH])
 * and error/killed sessions ([ERROR_ALERT_PATH]).
 */
public class WearAlertListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val title = runCatching { String(messageEvent.data, Charsets.UTF_8) }
            .getOrElse { "" }
            .ifBlank { "" }
        when (messageEvent.path) {
            ALERT_PATH -> postWaitingNotification(title)
            COUNCIL_ALERT_PATH -> postCouncilNotification(title)
            ERROR_ALERT_PATH -> postErrorNotification(title)
        }
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun postWaitingNotification(sessionTitle: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wear_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val wearExtender = NotificationCompat.WearableExtender()
            .setHintShowBackgroundOnly(true)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dw_eye)
            .setContentTitle(getString(R.string.wear_notification_waiting_title))
            .setContentText(sessionTitle.take(80))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .extend(wearExtender)
            .build()
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun postCouncilNotification(topic: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                COUNCIL_CHANNEL_ID,
                getString(R.string.wear_notification_council_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val wearExtender = NotificationCompat.WearableExtender()
            .setHintShowBackgroundOnly(true)
        val notif = NotificationCompat.Builder(this, COUNCIL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dw_eye)
            .setContentTitle(getString(R.string.wear_notification_council_title))
            .setContentText(topic.take(80))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .extend(wearExtender)
            .build()
        nm.notify(COUNCIL_NOTIFICATION_ID, notif)
    }

    private fun postErrorNotification(sessionTitle: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ERROR_CHANNEL_ID,
                getString(R.string.wear_notification_error_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val wearExtender = NotificationCompat.WearableExtender()
            .setHintShowBackgroundOnly(true)
        val notif = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dw_eye)
            .setContentTitle(getString(R.string.wear_notification_error_title))
            .setContentText(sessionTitle.take(80))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .extend(wearExtender)
            .build()
        nm.notify(ERROR_NOTIFICATION_ID, notif)
    }

    public companion object {
        public const val ALERT_PATH: String = "/datawatch/alert"
        public const val COUNCIL_ALERT_PATH: String = "/datawatch/council"
        public const val ERROR_ALERT_PATH: String = "/datawatch/error-alert"
        private const val CHANNEL_ID: String = "dw_alerts"
        private const val NOTIFICATION_ID: Int = 1
        private const val COUNCIL_CHANNEL_ID: String = "dw_council"
        private const val COUNCIL_NOTIFICATION_ID: Int = 2
        private const val ERROR_CHANNEL_ID: String = "dw_errors"
        private const val ERROR_NOTIFICATION_ID: Int = 3
    }
}
