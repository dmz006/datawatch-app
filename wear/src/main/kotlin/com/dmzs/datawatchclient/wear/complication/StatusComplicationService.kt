package com.dmzs.datawatchclient.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
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
 * BL303-W6 — Mission status complication: "3R 1B" (running + blocked) as
 * SHORT_TEXT, and overall session progress as RANGED_VALUE.
 *
 * SHORT_TEXT: "{running}R {blocked}B" — e.g. "3R 1B"
 * RANGED_VALUE: avg progress across all running sessions (0.0–1.0 → 0–100)
 *
 * Health dot tint follows the same green/amber/red logic as the Watch tile
 * and the Monitor page.
 */
public class StatusComplicationService : ComplicationDataSourceService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        scope.launch {
            val snap = readSnapshot()
            listener.onComplicationData(buildData(request.complicationType, snap))
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildData(type, StatusSnapshot(running = 3, waiting = 1, error = 0, progress = 0.45f))

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildData(type: ComplicationType, snap: StatusSnapshot): ComplicationData? {
        val shortLabel = buildString {
            append("${snap.running}R")
            val blocked = snap.waiting + snap.error
            if (blocked > 0) append(" ${blocked}B")
        }
        val contentDesc = "${snap.running} running, ${snap.waiting} waiting"
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(shortLabel).build(),
                    PlainComplicationText.Builder(contentDesc).build(),
                )
                    .setTitle(PlainComplicationText.Builder("sess").build())
                    .build()

            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = (snap.progress * 100f).coerceIn(0f, 100f),
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder(contentDesc).build(),
                )
                    .setText(PlainComplicationText.Builder(shortLabel).build())
                    .setTitle(PlainComplicationText.Builder("prog").build())
                    .build()

            else -> null
        }
    }

    private fun readSnapshot(): StatusSnapshot =
        runCatching {
            val client = Wearable.getDataClient(this)
            val items = Tasks.await(client.dataItems)
            try {
                val countsMap = items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                val telemetryMap = items.firstOrNull { it.uri.path == TELEMETRY_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                StatusSnapshot(
                    running = countsMap?.getInt("running", 0) ?: 0,
                    waiting = countsMap?.getInt("waiting", 0) ?: 0,
                    error = countsMap?.getInt("error", 0) ?: 0,
                    progress = telemetryMap?.getFloat("progress", 0f) ?: 0f,
                )
            } finally {
                items.release()
            }
        }.getOrElse { StatusSnapshot() }

    internal data class StatusSnapshot(
        val running: Int = 0,
        val waiting: Int = 0,
        val error: Int = 0,
        val progress: Float = 0f,
    )

    private companion object {
        const val COUNTS_PATH = "/datawatch/counts"
        const val TELEMETRY_PATH = "/datawatch/telemetry"
    }
}
