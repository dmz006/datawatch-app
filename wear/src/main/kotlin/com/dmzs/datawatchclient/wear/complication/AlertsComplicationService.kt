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

/** W-#114 — SHORT_TEXT complication: active alert count badge. */
public class AlertsComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val (total, needsInput, errors) = readAlerts()
            listener.onComplicationData(
                when (request.complicationType) {
                    ComplicationType.SHORT_TEXT ->
                        ShortTextComplicationData.Builder(
                            PlainComplicationText.Builder("!$total").build(),
                            PlainComplicationText.Builder("$total alerts, $needsInput input, $errors errors").build(),
                        )
                            .setTitle(PlainComplicationText.Builder("alrt").build())
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
                    PlainComplicationText.Builder("!2").build(),
                    PlainComplicationText.Builder("2 alerts, 1 input, 0 errors").build(),
                )
                    .setTitle(PlainComplicationText.Builder("alrt").build())
                    .build()
            else -> null
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun readAlerts(): Triple<Int, Int, Int> =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == ALERTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        Triple(
                            m.getInt("total", 0),
                            m.getInt("needsInput", 0),
                            m.getInt("errors", 0),
                        )
                    }
                    ?: Triple(0, 0, 0)
            } finally {
                items.release()
            }
        }.getOrElse { Triple(0, 0, 0) }

    private companion object {
        const val ALERTS_PATH = "/datawatch/alerts"
    }
}
