package com.dmzs.datawatchclient.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
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
 * Health ring complication — shows RANGED_VALUE with value = max(cpuPct, memPct),
 * range 0..100, giving the worst of CPU/mem at a glance.
 * SHORT_TEXT = "{cpu}c", SHORT_TITLE = "{mem}m".
 */
public class HealthRingComplicationService : ComplicationDataSourceService() {

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
        buildData(type, HealthSnapshot(cpuPct = 42f, memPct = 61f))

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildData(type: ComplicationType, snap: HealthSnapshot): ComplicationData? {
        val worstPct = maxOf(snap.cpuPct, snap.memPct).coerceIn(0f, 100f)
        val cpuText = "${snap.cpuPct.toInt()}c"
        val memText = "${snap.memPct.toInt()}m"
        val contentDesc = "datawatch Health: CPU ${snap.cpuPct.toInt()}% MEM ${snap.memPct.toInt()}%"
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = worstPct,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder(contentDesc).build(),
                )
                    .setText(PlainComplicationText.Builder(cpuText).build())
                    .setTitle(PlainComplicationText.Builder(memText).build())
                    .build()

            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(cpuText).build(),
                    PlainComplicationText.Builder(contentDesc).build(),
                )
                    .setTitle(PlainComplicationText.Builder(memText).build())
                    .build()

            else -> null
        }
    }

    private fun readSnapshot(): HealthSnapshot =
        runCatching {
            val items = Tasks.await(Wearable.getDataClient(this).dataItems)
            try {
                items.firstOrNull { it.uri.path == STATS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        val load = m.getDouble("cpuLoad1", 0.0)
                        val cores = m.getInt("cpuCores", 0)
                        val cpuPct = if (cores > 0) {
                            (load / cores * 100.0).coerceIn(0.0, 100.0).toFloat()
                        } else {
                            load.coerceIn(0.0, 100.0).toFloat()
                        }
                        val memUsed = m.getLong("memUsed", 0L)
                        val memTotal = m.getLong("memTotal", 0L)
                        val memPct = if (memTotal > 0) {
                            (memUsed.toDouble() / memTotal.toDouble() * 100.0)
                                .coerceIn(0.0, 100.0).toFloat()
                        } else {
                            0f
                        }
                        HealthSnapshot(cpuPct = cpuPct, memPct = memPct)
                    } ?: HealthSnapshot()
            } finally {
                items.release()
            }
        }.getOrElse { HealthSnapshot() }

    internal data class HealthSnapshot(
        val cpuPct: Float = 0f,
        val memPct: Float = 0f,
    )

    private companion object {
        const val STATS_PATH = "/datawatch/stats"
    }
}
