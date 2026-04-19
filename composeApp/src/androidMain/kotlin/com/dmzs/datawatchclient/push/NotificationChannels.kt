package com.dmzs.datawatchclient.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * Per-event-type notification channels per Android user-control guidance: separate
 * channels for input-needed (high-importance, override Do-Not-Disturb-eligible),
 * rate-limited / completed (default), and error (high). User can disable any
 * channel from system settings without losing the others.
 */
public object NotificationChannels {
    public const val INPUT_NEEDED: String = "dw.input_needed"
    public const val COMPLETED: String = "dw.completed"
    public const val RATE_LIMITED: String = "dw.rate_limited"
    public const val ERROR: String = "dw.error"
    public const val FOREGROUND: String = "dw.foreground"

    public fun ensureRegistered(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(INPUT_NEEDED, "Input needed", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A datawatch session is waiting for your reply."
                    enableVibration(true)
                },
                NotificationChannel(COMPLETED, "Completed", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A session finished or changed state."
                },
                NotificationChannel(RATE_LIMITED, "Rate limited", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Upstream LLM is rate-limited; the session will resume."
                },
                NotificationChannel(ERROR, "Errors", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "A session encountered an error."
                },
                NotificationChannel(FOREGROUND, "Background services", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent notifications for ntfy fallback subscriptions."
                },
            ),
        )
    }
}
