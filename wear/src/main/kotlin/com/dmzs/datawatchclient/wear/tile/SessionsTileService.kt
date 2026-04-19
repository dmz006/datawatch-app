package com.dmzs.datawatchclient.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.FontWeightProp
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * BL4 — Wear Tile that surfaces session counts on a glanceable tile. Sprint 4
 * Phase 1 renders placeholder zeros; Phase 2 wires counts via the phone's
 * Wearable Data Layer so the watch doesn't need its own REST transport.
 *
 * ProtoLayout (androidx.wear.protolayout) is the required builder stack for
 * Tiles 1.2+; androidx.wear.tiles still owns the [TileService] lifecycle.
 */
public class SessionsTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = Futures.immediateFuture(buildTile())

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun buildTile(): TileBuilders.Tile {
        // Placeholder counts until the Data Layer pipe lands in Phase 2.
        val running = 0
        val waiting = 0
        val total = 0
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(
                    buildLayout(running, waiting, total),
                ),
            )
            .build()
    }

    private fun buildLayout(
        running: Int,
        waiting: Int,
        total: Int,
    ): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .addContent(countColumn("run", running, COLOR_FG))
            .addContent(spacer())
            .addContent(countColumn("wait", waiting, COLOR_ACCENT))
            .addContent(spacer())
            .addContent(countColumn("total", total, COLOR_FG))
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(titleText("datawatch"))
            .addContent(spacerV())
            .addContent(row)
            .build()
    }

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
                    .setSize(sp(11f))
                    .setColor(argb(COLOR_ACCENT))
                    .setWeight(FontWeightProp.Builder().setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
                    .build(),
            )
            .build()

    private fun numText(text: String, color: Int): LayoutElementBuilders.Text =
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

    private fun spacer(): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(10f)).build()

    private fun spacerV(): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build()

    public companion object {
        private const val RESOURCES_VERSION: String = "1"
        private const val COLOR_FG: Int = 0xFFFFFFFF.toInt()
        private const val COLOR_ACCENT: Int = 0xFFA855F7.toInt()
        private const val COLOR_MUTED: Int = 0xFF888888.toInt()
    }
}
