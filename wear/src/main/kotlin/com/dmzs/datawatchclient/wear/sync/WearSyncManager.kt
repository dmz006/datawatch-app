package com.dmzs.datawatchclient.wear.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * S10-5/S10-6 — watch-side sync request manager. Sends a MessageClient
 * message to all connected phone nodes asking for a fresh dashboard push.
 *
 * The phone's [com.dmzs.datawatchclient.wear.WearSyncService] listens on
 * [SYNC_PATH] and responds by calling [fetchAndPublishDashboard], which
 * pushes updated DataItems to the watch. This replaces the removed 15 s
 * polling loop — the watch now requests data on app-open, refresh tap,
 * and tile interactions.
 */
public object WearSyncManager {
    public const val SYNC_PATH: String = "/datawatch/sync"

    public suspend fun requestDashboard(context: Context) {
        sendSyncRequest(context, "DASHBOARD")
    }

    public suspend fun requestSessionDetail(context: Context, sessionId: String) {
        sendSyncRequest(context, "SESSION_DETAIL:$sessionId")
    }

    private suspend fun sendSyncRequest(context: Context, payload: String) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val messageClient = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, SYNC_PATH, payload.toByteArray()).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("WearSyncManager", "Sync request failed: ${e.message}")
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        addOnCanceledListener { cont.cancel() }
    }
