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
import com.dmzs.datawatchclient.wear.sync.WearSyncManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * W-#114 — Wear Tile: active alert breakdown (total / needs-input / errors).
 * Reads the `/datawatch/alerts` DataItem published by the phone's
 * `WearSyncService`. Health dot: red on errors, amber on needs-input, green
 * otherwise. Tap → WearMainActivity.
 */
public class AlertsTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        scope.launch { WearSyncManager.requestDashboard(applicationContext) }
        return Futures.immediateFuture(buildTile())
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildTile(): TileBuilders.Tile {
        val snap = readAlerts()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(buildLayout(snap)),
            )
            .build()
    }

    private fun readAlerts(): AlertsSnapshot =
        runCatching {
            val client = Wearable.getDataClient(this)
            val items = Tasks.await(client.dataItems)
            try {
                items.firstOrNull { it.uri.path == ALERTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        AlertsSnapshot(
                            total = m.getInt("total", 0),
                            needsInput = m.getInt("needsInput", 0),
                            errors = m.getInt("errors", 0),
                            syncTs = m.getLong("ts", 0),
                            hasData = true,
                        )
                    }
                    ?: AlertsSnapshot()
            } finally {
                items.release()
            }
        }.getOrElse { AlertsSnapshot() }

    private fun buildLayout(snap: AlertsSnapshot): LayoutElementBuilders.LayoutElement {
        val col =
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setModifiers(openAppModifiers(packageName))
                .addContent(titleText("Alerts"))
                .addContent(spacerV(2f))
                .addContent(healthDot(snap))
                .addContent(spacerV(4f))
        if (!snap.hasData) {
            col.addContent(subText("open phone app"))
            return col.build()
        }
        col.addContent(
            statRow("Total", snap.total.toString(), if (snap.total > 0) COLOR_WARNING else COLOR_FG),
        )
        col.addContent(spacerV(3f))
        col.addContent(
            statRow("Input", snap.needsInput.toString(), if (snap.needsInput > 0) COLOR_WARNING else COLOR_MUTED),
        )
        col.addContent(spacerV(3f))
        col.addContent(
            statRow("Err", snap.errors.toString(), if (snap.errors > 0) COLOR_ERROR else COLOR_MUTED),
        )
        if (snap.syncTs > 0) {
            val minutesAgo = (System.currentTimeMillis() - snap.syncTs) / 60_000
            col.addContent(spacerV(2f))
            col.addContent(subText("sync ${minutesAgo}m ago"))
        }
        return col.build()
    }

    private data class AlertsSnapshot(
        val total: Int = 0,
        val needsInput: Int = 0,
        val errors: Int = 0,
        val syncTs: Long = 0,
        val hasData: Boolean = false,
    )

    public companion object {
        private const val RESOURCES_VERSION: String = "1"
        private const val ALERTS_PATH: String = "/datawatch/alerts"
        private const val FRESHNESS_MS: Long = 30_000L

        private const val COLOR_FG: Int = 0xFFE7EDF3.toInt()
        private const val COLOR_ACCENT: Int = 0xFF00E5A0.toInt()
        private const val COLOR_WARNING: Int = 0xFFFFB020.toInt()
        private const val COLOR_ERROR: Int = 0xFFFF5555.toInt()
        private const val COLOR_MUTED: Int = 0xFF9AA7B3.toInt()

        private const val COLOR_HEALTH_GOOD: Int = 0xFF10B981.toInt()
        private const val COLOR_HEALTH_WAITING: Int = 0xFFF59E0B.toInt()
        private const val COLOR_HEALTH_ERROR: Int = 0xFFEF4444.toInt()

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
                                    FontWeightProp.Builder()
                                        .setValue(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                                        .build(),
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
                        .setWeight(
                            FontWeightProp.Builder().setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD).build(),
                        )
                        .build(),
                )
                .build()

        private fun labelText(text: String): LayoutElementBuilders.Text =
            LayoutElementBuilders.Text.Builder()
                .setText(TypeBuilders.StringProp.Builder(text).build())
                .setFontStyle(FontStyle.Builder().setSize(sp(10f)).setColor(argb(COLOR_MUTED)).build())
                .build()

        private fun subText(text: String): LayoutElementBuilders.Text =
            LayoutElementBuilders.Text.Builder()
                .setText(TypeBuilders.StringProp.Builder(text).build())
                .setFontStyle(FontStyle.Builder().setSize(sp(9f)).setColor(argb(COLOR_MUTED)).build())
                .build()

        private fun spacerV(h: Float): LayoutElementBuilders.Spacer =
            LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

        private fun hSpacer(w: Float): LayoutElementBuilders.Spacer =
            LayoutElementBuilders.Spacer.Builder().setWidth(dp(w)).build()

        private fun openAppModifiers(pkgName: String): ModifiersBuilders.Modifiers =
            ModifiersBuilders.Modifiers.Builder()
                .setClickable(
                    ModifiersBuilders.Clickable.Builder()
                        .setId("open_app")
                        .setOnClick(
                            ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(
                                    ActionBuilders.AndroidActivity.Builder()
                                        .setPackageName(pkgName)
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

        private fun healthDot(snap: AlertsSnapshot): LayoutElementBuilders.Box {
            val color =
                when {
                    snap.errors > 0 -> COLOR_HEALTH_ERROR
                    snap.needsInput > 0 -> COLOR_HEALTH_WAITING
                    else -> COLOR_HEALTH_GOOD
                }
            return LayoutElementBuilders.Box.Builder()
                .setWidth(dp(10f))
                .setHeight(dp(10f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(color))
                                .setCorner(
                                    ModifiersBuilders.Corner.Builder().setRadius(dp(5f)).build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build()
        }
    }
}
