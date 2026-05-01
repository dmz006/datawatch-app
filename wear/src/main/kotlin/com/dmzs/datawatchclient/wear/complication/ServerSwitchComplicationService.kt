package com.dmzs.datawatchclient.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * W-10 — SHORT_TEXT complication showing the active server name.
 * Tap cycles to the next enabled server via [ServerSwitchReceiver].
 */
public class ServerSwitchComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val name = readServerName()
            val abbrev = name.take(6).ifEmpty { "—" }
            listener.onComplicationData(
                when (request.complicationType) {
                    ComplicationType.SHORT_TEXT ->
                        ShortTextComplicationData.Builder(
                            PlainComplicationText.Builder(abbrev).build(),
                            PlainComplicationText.Builder("Active: $name").build(),
                        )
                            .setTitle(PlainComplicationText.Builder("srvr").build())
                            .setTapAction(buildTapAction())
                            .build()
                    else -> null
                },
            )
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder("home").build(),
                    PlainComplicationText.Builder("Active: home").build(),
                )
                    .setTitle(PlainComplicationText.Builder("srvr").build())
                    .build()
            else -> null
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildTapAction(): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ServerSwitchReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun readServerName(): String =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.getString("serverName", "") ?: ""
            } finally {
                items.release()
            }
        }.getOrElse { "" }

    private companion object {
        const val COUNTS_PATH = "/datawatch/counts"
    }
}
