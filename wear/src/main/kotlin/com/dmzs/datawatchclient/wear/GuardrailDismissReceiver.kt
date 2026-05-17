package com.dmzs.datawatchclient.wear

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BL303-W3 — Receives the "Dismiss" action from the guardrail block
 * notification and cancels it. No server call is made; dismiss is
 * purely a local notification cancellation (the block remains active
 * on the server side until the user approves via WearApproveScreen).
 */
public class GuardrailDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WearAlertListenerService.GUARDRAIL_DISMISS_ACTION) return
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(GUARDRAIL_NOTIFICATION_ID)
    }

    private companion object {
        private const val GUARDRAIL_NOTIFICATION_ID: Int = 4
    }
}
