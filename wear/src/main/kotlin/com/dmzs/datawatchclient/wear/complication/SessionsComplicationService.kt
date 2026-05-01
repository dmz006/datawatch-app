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

/** W-9 — SHORT_TEXT complication showing total active session count. */
public class SessionsComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val (running, waiting) = readCounts()
            listener.onComplicationData(
                when (request.complicationType) {
                    ComplicationType.SHORT_TEXT ->
                        ShortTextComplicationData.Builder(
                            PlainComplicationText.Builder("$running/$waiting").build(),
                            PlainComplicationText.Builder("$running running, $waiting waiting").build(),
                        )
                            .setTitle(PlainComplicationText.Builder("sess").build())
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
                    PlainComplicationText.Builder("2/1").build(),
                    PlainComplicationText.Builder("2 running, 1 waiting").build(),
                )
                    .setTitle(PlainComplicationText.Builder("sess").build())
                    .build()
            else -> null
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun readCounts(): Pair<Int, Int> =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m -> m.getInt("running", 0) to m.getInt("waiting", 0) }
                    ?: (0 to 0)
            } finally {
                items.release()
            }
        }.getOrElse { 0 to 0 }

    private companion object {
        const val COUNTS_PATH = "/datawatch/counts"
    }
}
