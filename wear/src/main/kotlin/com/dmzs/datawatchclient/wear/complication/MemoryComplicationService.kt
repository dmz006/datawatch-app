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

/** W-9 — SHORT_TEXT complication showing active server memory usage %. */
public class MemoryComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val pct = readMemPct()
            val label = "%.0f%%".format(pct)
            listener.onComplicationData(
                when (request.complicationType) {
                    ComplicationType.SHORT_TEXT ->
                        ShortTextComplicationData.Builder(
                            PlainComplicationText.Builder(label).build(),
                            PlainComplicationText.Builder("Mem $label").build(),
                        )
                            .setTitle(PlainComplicationText.Builder("mem").build())
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
                    PlainComplicationText.Builder("61%").build(),
                    PlainComplicationText.Builder("Mem 61%").build(),
                )
                    .setTitle(PlainComplicationText.Builder("mem").build())
                    .build()
            else -> null
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun readMemPct(): Double =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == STATS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        val used = m.getLong("memUsed", 0L)
                        val total = m.getLong("memTotal", 0L)
                        if (total > 0) (used.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0) else 0.0
                    } ?: 0.0
            } finally {
                items.release()
            }
        }.getOrElse { 0.0 }

    private companion object {
        const val STATS_PATH = "/datawatch/stats"
    }
}
