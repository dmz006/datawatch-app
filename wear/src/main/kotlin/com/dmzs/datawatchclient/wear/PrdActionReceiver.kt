package com.dmzs.datawatchclient.wear

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles inline Approve / Reject taps from PRD review notifications.
 * Sends the prdAction message to the phone via MessageClient and cancels
 * the notification. No activity launch needed — the action is fire-and-forget.
 */
public class PrdActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prdId = intent.getStringExtra("prd_id") ?: return
        val action = intent.getStringExtra("prd_action") ?: return
        val reason = if (action == "reject") "rejected from watch notification" else ""

        // Cancel the notification
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(5) // PRD_REVIEW_NOTIFICATION_ID

        // Send message to phone
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val body = if (reason.isNotEmpty()) "$prdId\n$action\n$reason"
                           else "$prdId\n$action"
                val payload = body.toByteArray(Charsets.UTF_8)
                val nodeClient = Wearable.getNodeClient(context)
                val messageClient = Wearable.getMessageClient(context)
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/datawatch/prdAction", payload).await()
                }
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resumeWith(Result.success(it)) }
            addOnFailureListener { cont.resumeWith(Result.failure(it)) }
            addOnCanceledListener { cont.cancel() }
        }
}
