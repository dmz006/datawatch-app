package com.dmzs.datawatchclient.wear.complication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * W-10 — Cycles the active server when the server-switch complication is tapped.
 * Reads the published profile list from the DataLayer, finds the next enabled
 * profile after the current active one, and sends a [SET_ACTIVE_PATH] message
 * to all connected phone nodes.
 */
public class ServerSwitchReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                cycleServer(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun cycleServer(context: Context) {
        runCatching {
            val dataClient = Wearable.getDataClient(context)
            val items = Tasks.await(dataClient.dataItems)
            val currentId: String
            val ids: Array<String>
            try {
                currentId = items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.getString("serverId", "") ?: ""
                ids = items.firstOrNull { it.uri.path == PROFILES_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.getStringArray("ids") ?: emptyArray()
            } finally {
                items.release()
            }
            if (ids.isEmpty()) return
            val currentIdx = ids.indexOfFirst { it == currentId }
            val nextId = ids[(currentIdx + 1) % ids.size]
            val messageClient = Wearable.getMessageClient(context)
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            nodes.forEach { node ->
                Tasks.await(messageClient.sendMessage(node.id, SET_ACTIVE_PATH, nextId.toByteArray()))
            }
        }
    }

    private companion object {
        const val COUNTS_PATH = "/datawatch/counts"
        const val PROFILES_PATH = "/datawatch/profiles"
        const val SET_ACTIVE_PATH = "/datawatch/setActive"
    }
}
