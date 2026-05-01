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
 */
public class WearAlertListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != ALERT_PATH) return
        val title = runCatching { String(messageEvent.data, Charsets.UTF_8) }
            .getOrElse { "session" }
            .ifBlank { "session" }
        postNotification(title)
    }

    private fun postNotification(sessionTitle: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wear_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        nm.createNotificationChannel(channel)
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dw_eye)
            .setContentTitle(getString(R.string.wear_notification_waiting_title))
            .setContentText(sessionTitle.take(80))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notif)
    }

    public companion object {
        public const val ALERT_PATH: String = "/datawatch/alert"
        private const val CHANNEL_ID: String = "dw_alerts"
        private const val NOTIFICATION_ID: Int = 1
    }
}
