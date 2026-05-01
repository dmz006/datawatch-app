package com.dmzs.datawatchclient.wear.complication

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

/** W-2 — SHORT_TEXT complication showing the pending-input session count. */
public class WaitingComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val waiting = readWaiting()
            listener.onComplicationData(
                when (request.complicationType) {
                    ComplicationType.SHORT_TEXT ->
                        ShortTextComplicationData.Builder(
                            PlainComplicationText.Builder(waiting.toString()).build(),
                            PlainComplicationText.Builder("$waiting waiting").build(),
                        )
                            .setTitle(PlainComplicationText.Builder("wait").build())
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
                    PlainComplicationText.Builder("3").build(),
                    PlainComplicationText.Builder("3 waiting").build(),
                )
                    .setTitle(PlainComplicationText.Builder("wait").build())
                    .build()
            else -> null
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun readWaiting(): Int =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.getInt("waiting", 0) ?: 0
            } finally {
                items.release()
            }
        }.getOrElse { 0 }

    private companion object {
        const val COUNTS_PATH = "/datawatch/counts"
    }
}
