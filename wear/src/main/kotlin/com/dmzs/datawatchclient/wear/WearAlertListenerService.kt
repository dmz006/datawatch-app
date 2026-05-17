package com.dmzs.datawatchclient.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
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
        val payload = runCatching { String(messageEvent.data, Charsets.UTF_8) }
            .getOrElse { "" }
            .ifBlank { "" }
        when (messageEvent.path) {
            ALERT_PATH -> postWaitingNotification(payload)
            COUNCIL_ALERT_PATH -> postCouncilNotification(payload)
            ERROR_ALERT_PATH -> postErrorNotification(payload)
            GUARDRAIL_BLOCK_PATH -> {
                // Payload: "sessionId\nblockSummary"
                val parts = payload.split("\n", limit = 2)
                val sessionId = parts.getOrNull(0).orEmpty()
                val summary = parts.getOrNull(1).orEmpty()
                postGuardrailBlockNotification(sessionId, summary)
            }
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

    /**
     * BL303-W3 — triple-buzz haptic (long+pause+long+pause+long) to signal
     * a guardrail block. Distinct from standard Wear notification vibration.
     * Pattern: delay 0, buzz 400ms, pause 200ms, buzz 400ms, pause 200ms, buzz 400ms.
     */
    private fun tripleBuzz() {
        runCatching {
            val vib = getSystemService(Vibrator::class.java) ?: return
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400)
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun postGuardrailBlockNotification(sessionId: String, summary: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                GUARDRAIL_CHANNEL_ID,
                getString(R.string.wear_notification_guardrail_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        // Approve action → opens WearApproveScreen on the watch
        val approveIntent = PendingIntent.getActivity(
            this,
            GUARDRAIL_APPROVE_REQ,
            Intent(this, WearApproveScreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WearApproveScreen.EXTRA_SESSION_ID, sessionId)
                putExtra(WearApproveScreen.EXTRA_BLOCK_SUMMARY, summary)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Dismiss action → cancels the notification
        val dismissIntent = PendingIntent.getBroadcast(
            this,
            GUARDRAIL_DISMISS_REQ,
            Intent(GUARDRAIL_DISMISS_ACTION).apply { setPackage(packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val wearExtender = NotificationCompat.WearableExtender()
            .setHintShowBackgroundOnly(false)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    getString(R.string.wear_notification_guardrail_action_approve),
                    approveIntent,
                ).build(),
            )

        // Triple-buzz haptic pattern for block (0=delay, then 400+200+400+200+400ms)
        val vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)

        val notif = NotificationCompat.Builder(this, GUARDRAIL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dw_eye)
            .setContentTitle(getString(R.string.wear_notification_guardrail_title))
            .setContentText(summary.take(80))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(approveIntent)
            .setVibrate(vibrationPattern)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.wear_notification_guardrail_action_dismiss),
                dismissIntent,
            )
            .extend(wearExtender)
            .build()

        tripleBuzz()
        nm.notify(GUARDRAIL_NOTIFICATION_ID, notif)
    }

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
        // BL303-W3: guardrail block notification path
        public const val GUARDRAIL_BLOCK_PATH: String = "/datawatch/guardrailBlock"
        public const val GUARDRAIL_DISMISS_ACTION: String =
            "com.dmzs.datawatchclient.wear.ACTION_GUARDRAIL_DISMISS"
        private const val CHANNEL_ID: String = "dw_alerts"
        private const val NOTIFICATION_ID: Int = 1
        private const val COUNCIL_CHANNEL_ID: String = "dw_council"
        private const val COUNCIL_NOTIFICATION_ID: Int = 2
        private const val ERROR_CHANNEL_ID: String = "dw_errors"
        private const val ERROR_NOTIFICATION_ID: Int = 3
        private const val GUARDRAIL_CHANNEL_ID: String = "dw_guardrail"
        private const val GUARDRAIL_NOTIFICATION_ID: Int = 4
        private const val GUARDRAIL_APPROVE_REQ: Int = 40
        private const val GUARDRAIL_DISMISS_REQ: Int = 41
    }
}
