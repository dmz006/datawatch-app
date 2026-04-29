package com.dmzs.datawatchclient.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.FontWeightProp
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Wear Tile — Monitor at a glance. Reads the `/datawatch/stats`
 * DataItem published by the phone's `WearSyncService`. Shows CPU
 * load relative to cores, memory used as a pct of total, and the
 * session total. Uptime goes as a sub-line so the user can tell at a
 * glance whether the daemon restarted.
 */
public class MonitorTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(buildTile())

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildTile(): TileBuilders.Tile {
        val snap = readLatestStats()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    buildLayout(snap),
                ),
            )
            .build()
    }

    private fun readLatestStats(): StatsSnapshot {
        return runCatching {
            val client = Wearable.getDataClient(this)
            val items = Tasks.await(client.dataItems)
            try {
                items.firstOrNull { it.uri.path == STATS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        StatsSnapshot(
                            cpuLoad1 = m.getDouble("cpuLoad1", 0.0),
                            cpuCores = m.getInt("cpuCores", 0),
                            memUsed = m.getLong("memUsed", 0),
                            memTotal = m.getLong("memTotal", 0),
                            sessionsTotal = m.getInt("sessionsTotal", 0),
                            sessionsRunning = m.getInt("sessionsRunning", 0),
                            sessionsWaiting = m.getInt("sessionsWaiting", 0),
                            uptimeSeconds = m.getLong("uptimeSeconds", 0),
                            hasData = true,
                        )
                    }
                    ?: StatsSnapshot()
            } finally {
                items.release()
            }
        }.getOrElse { StatsSnapshot() }
    }

    private fun buildLayout(snap: StatsSnapshot): LayoutElementBuilders.LayoutElement {
        val col =
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                // Tap → open Wear companion so the user can reach the
                // Server picker + Sessions pages. Tiles themselves are
                // non-interactive beyond a single click target.
                .setModifiers(openAppModifiers())
                .addContent(titleText("Monitor"))
                .addContent(spacerV(4f))
        if (!snap.hasData) {
            col.addContent(subText("open phone app"))
            return col.build()
        }
        val cpuRatio =
            if (snap.cpuCores > 0) (snap.cpuLoad1 / snap.cpuCores).coerceIn(0.0, 1.5) else null
        val cpuText =
            if (snap.cpuCores > 0) "%.2f / %d".format(snap.cpuLoad1, snap.cpuCores) else "—"
        col.addContent(statRow("CPU", cpuText, cpuColor(cpuRatio)))
        val memPct =
            if (snap.memTotal > 0) (snap.memUsed.toDouble() / snap.memTotal.toDouble()) else null
        val memText =
            if (snap.memTotal > 0) {
                "${(memPct!! * 100).toInt()}%"
            } else {
                "—"
            }
        col.addContent(spacerV(3f))
        col.addContent(statRow("Mem", memText, pctColor(memPct)))
        col.addContent(spacerV(3f))
        col.addContent(
            statRow(
                "Sess",
                "${snap.sessionsTotal} · ${snap.sessionsWaiting}w",
                if (snap.sessionsWaiting > 0) COLOR_WARNING else COLOR_FG,
            ),
        )
        if (snap.uptimeSeconds > 0) {
            col.addContent(spacerV(3f))
            col.addContent(subText("up ${formatUptime(snap.uptimeSeconds)}"))
        }
        return col.build()
    }

    private fun statRow(
        label: String,
        value: String,
        valueColor: Int,
    ): LayoutElementBuilders.Row =
        LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(labelText(label))
            .addContent(hSpacer(6f))
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(TypeBuilders.StringProp.Builder(value).build())
                    .setFontStyle(
                        FontStyle.Builder()
                            .setSize(sp(14f))
                            .setColor(argb(valueColor))
                            .setWeight(
                                FontWeightProp.Builder().setValue(LayoutElementBuilders.FONT_WEIGHT_MEDIUM).build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun titleText(text: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(12f))
                    .setColor(argb(COLOR_ACCENT))
                    .setWeight(FontWeightProp.Builder().setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                    .build(),
            )
            .build()

    private fun labelText(text: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(10f))
                    .setColor(argb(COLOR_MUTED))
                    .build(),
            )
            .build()

    private fun subText(text: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(9f))
                    .setColor(argb(COLOR_MUTED))
                    .build(),
            )
            .build()

    private fun spacerV(h: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    private fun hSpacer(w: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(w)).build()

    private fun cpuColor(ratio: Double?): Int =
        when {
            ratio == null -> COLOR_FG
            ratio >= 0.9 -> COLOR_ERROR
            ratio >= 0.7 -> COLOR_WARNING
            else -> COLOR_ACCENT
        }

    private fun pctColor(pct: Double?): Int =
        when {
            pct == null -> COLOR_FG
            pct >= 0.9 -> COLOR_ERROR
            pct >= 0.7 -> COLOR_WARNING
            else -> COLOR_ACCENT
        }

    private fun openAppModifiers(): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open_app")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .setClassName(
                                        "com.dmzs.datawatchclient.wear.WearMainActivity",
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun formatUptime(seconds: Long): String {
        val d = seconds / 86_400
        val h = (seconds % 86_400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d${h}h"
            h > 0 -> "${h}h${m}m"
            else -> "${m}m"
        }
    }

    private data class StatsSnapshot(
        val cpuLoad1: Double = 0.0,
        val cpuCores: Int = 0,
        val memUsed: Long = 0,
        val memTotal: Long = 0,
        val sessionsTotal: Int = 0,
        val sessionsRunning: Int = 0,
        val sessionsWaiting: Int = 0,
        val uptimeSeconds: Long = 0,
        val hasData: Boolean = false,
    )

    public companion object {
        private const val RESOURCES_VERSION: String = "1"
        private const val STATS_PATH: String = "/datawatch/stats"
        private const val FRESHNESS_MS: Long = 30_000L

        private const val COLOR_FG: Int = 0xFFE7EDF3.toInt()
        private const val COLOR_ACCENT: Int = 0xFF00E5A0.toInt()
        private const val COLOR_WARNING: Int = 0xFFFFB020.toInt()
        private const val COLOR_ERROR: Int = 0xFFFF5555.toInt()
        private const val COLOR_MUTED: Int = 0xFF9AA7B3.toInt()
    }
}
