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
 * W-8 — Wear Tile showing up to 3 waiting-input sessions by name.
 * Reads session list from the `/datawatch/sessions` DataItem the
 * phone's WearSyncService publishes every 15 s; filters for
 * `state == waiting_input`. Tap → open companion activity.
 */
public class WaitingTileService : TileService() {

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
        val sessions = readWaitingSessions()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(buildLayout(sessions)),
            )
            .build()
    }

    private fun readWaitingSessions(): List<Pair<String, String>> =
        runCatching {
            val client = Wearable.getDataClient(this)
            val items = Tasks.await(client.dataItems)
            try {
                items.firstOrNull { it.uri.path == SESSIONS_PATH }
                    ?.let { DataMapItem.fromDataItem(it).dataMap }
                    ?.let { m ->
                        val ids = m.getStringArray("ids") ?: emptyArray()
                        val titles = m.getStringArray("titles") ?: emptyArray()
                        val states = m.getStringArray("states") ?: emptyArray()
                        ids.indices
                            .filter { i -> states.getOrNull(i).orEmpty().equals("waiting", ignoreCase = true) }
                            .take(MAX_ROWS)
                            .map { i -> ids[i] to titles.getOrNull(i).orEmpty() }
                    } ?: emptyList()
            } finally {
                items.release()
            }
        }.getOrElse { emptyList() }

    private fun buildLayout(sessions: List<Pair<String, String>>): LayoutElementBuilders.LayoutElement {
        val col = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(openAppModifiers())
            .addContent(titleText("datawatch"))
            .addContent(spacerV())
            .addContent(subText("waiting"))

        if (sessions.isEmpty()) {
            col.addContent(spacerV())
                .addContent(subText("none"))
        } else {
            sessions.forEach { (_, title) ->
                col.addContent(spacerV(3f))
                    .addContent(sessionRow(title))
            }
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
                                    .setClassName("com.dmzs.datawatchclient.wear.WearMainActivity")
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
                    .setWeight(FontWeightProp.Builder().setValue(LayoutElementBuilders.FONT_WEIGHT_BOLD).build())
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

    private fun sessionRow(title: String): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(TypeBuilders.StringProp.Builder("● ${title.take(20)}").build())
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(COLOR_WARNING))
                    .build(),
            )
            .build()

    private fun spacerV(heightDp: Float = 6f): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val SESSIONS_PATH = "/datawatch/sessions"
        const val FRESHNESS_MS = 30_000L
        const val MAX_ROWS = 3
        const val COLOR_ACCENT = 0xFF00E5A0.toInt()
        const val COLOR_WARNING = 0xFFFFB020.toInt()
        const val COLOR_MUTED = 0xFF9AA7B3.toInt()
    }
}
