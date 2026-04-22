package com.dmzs.datawatchclient.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ActionBuilders
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
 * Wear Tile — Sessions at a glance. Reads the `/datawatch/counts`
 * DataItem published by the phone's `WearSyncService`, so the tile
 * renders whatever the phone's active server reports. No REST on
 * the watch; no bearer token.
 *
 * ProtoLayout (androidx.wear.protolayout) is the required builder stack for
 * Tiles 1.2+; androidx.wear.tiles still owns the [TileService] lifecycle.
 */
public class SessionsTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(buildTile())

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildTile(): TileBuilders.Tile {
        val snap = readLatestCounts()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // 30 s freshness hint — Wear's tile framework throttles
            // actual polling; this just tells it we'd like to be
            // re-requested as soon as the phone publishes new counts.
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    buildLayout(snap),
                ),
            )
            .build()
    }

    private fun readLatestCounts(): CountsSnapshot {
        return runCatching {
            val client = Wearable.getDataClient(this)
            val items = Tasks.await(client.dataItems)
            try {
                items.firstOrNull { it.uri.path == COUNTS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        CountsSnapshot(
                            serverName = m.getString("serverName", ""),
                            running = m.getInt("running", 0),
                            waiting = m.getInt("waiting", 0),
                            total = m.getInt("total", 0),
                            hasData = true,
                        )
                    }
                    ?: CountsSnapshot()
            } finally {
                items.release()
            }
        }.getOrElse { CountsSnapshot() }
    }

    private fun buildLayout(snap: CountsSnapshot): LayoutElementBuilders.LayoutElement {
        val row =
            LayoutElementBuilders.Row.Builder()
                .addContent(countColumn("run", snap.running, COLOR_ACCENT))
                .addContent(spacer())
                .addContent(countColumn("wait", snap.waiting, COLOR_WARNING))
                .addContent(spacer())
                .addContent(countColumn("total", snap.total, COLOR_FG))
                .build()

        val col =
            LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                // Tap anywhere on the tile → open the Wear companion,
                // which owns the multi-server picker page. Tiles can't
                // host their own picker UI, so we hand off to the app.
                .setModifiers(openAppModifiers())
                .addContent(titleText("datawatch"))
                .addContent(spacerV())
                .addContent(row)
        if (snap.hasData && snap.serverName.isNotBlank()) {
            col.addContent(spacerV())
            col.addContent(subText(snap.serverName))
        } else if (!snap.hasData) {
            col.addContent(spacerV())
            col.addContent(subText("open phone app"))
        }
        return col.build()
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

    private fun countColumn(
        label: String,
        value: Int,
        valueColor: Int,
    ): LayoutElementBuilders.Column =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(numText(value.toString(), valueColor))
            .addContent(labelText(label))
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

    private fun numText(
        text: String,
        color: Int,
    ): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(24f))
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun labelText(text: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(9f))
                    .setColor(argb(COLOR_MUTED))
                    .build(),
            )
            .build()

    private fun subText(text: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder(text).build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(10f))
                    .setColor(argb(COLOR_MUTED))
                    .build(),
            )
            .build()

    private fun spacer(): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(10f)).build()

    private fun spacerV(): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build()

    private data class CountsSnapshot(
        val serverName: String = "",
        val running: Int = 0,
        val waiting: Int = 0,
        val total: Int = 0,
        val hasData: Boolean = false,
    )

    public companion object {
        private const val RESOURCES_VERSION: String = "1"
        private const val COUNTS_PATH: String = "/datawatch/counts"
        private const val FRESHNESS_MS: Long = 30_000L

        // Datawatch palette: teal accent (running), amber (waiting),
        // light foreground, muted gray labels. Matches the companion
        // Wear activity's colour tokens so the tile and the app feel
        // like one surface.
        private const val COLOR_FG: Int = 0xFFE7EDF3.toInt()
        private const val COLOR_ACCENT: Int = 0xFF00E5A0.toInt()
        private const val COLOR_WARNING: Int = 0xFFFFB020.toInt()
        private const val COLOR_MUTED: Int = 0xFF9AA7B3.toInt()
    }
}
