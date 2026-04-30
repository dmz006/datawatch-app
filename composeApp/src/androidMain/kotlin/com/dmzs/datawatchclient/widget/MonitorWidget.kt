package com.dmzs.datawatchclient.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dmzs.datawatchclient.MainActivity
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget that surfaces live host stats from the active
 * datawatch server — CPU load (load1 / cores), memory used / total,
 * and session counts. Companion to [SessionsWidget] which only shows
 * sessions; this surface is the "Monitor at a glance" card that the
 * PWA Monitor tab collapses into one tile.
 *
 * Refreshes on the AppWidgetManager cadence (30 min minimum) and
 * whenever the app calls [requestUpdate] after a successful stats
 * pull. Tap anywhere launches MainActivity so Settings → Monitor can
 * take over with the full card stack.
 */
public class MonitorWidget : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Do NOT render a loading state here. On every refresh tick
        // (AppWidgetManager cadence or app-triggered `requestUpdate`)
        // the widget should keep its last-known content and only
        // re-render once the async refresh produces new numbers.
        // Clearing to "loading…" caused the visible flash reported
        // on 2026-04-22. The initial layout state (dashes in
        // `widget_monitor.xml`) still shows on first placement, which
        // is acceptable — it's a one-time non-flashing state.
        scope.launch { refresh(context) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == WidgetActions.ACTION_CYCLE_SERVER) {
            scope.launch {
                WidgetActions.cycleActiveServer()
                // Refresh both widget types so the whole home-screen
                // stays in sync after a single tap.
                refresh(context)
                SessionsWidget.requestUpdate(context)
            }
        }
    }

    private fun renderLoadingState(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_monitor)
        views.setTextViewText(R.id.widget_monitor_profile, "loading…")
        attachTapIntent(context, views)
        manager.updateAppWidget(widgetId, views)
    }

    private suspend fun refresh(context: Context) {
        // Honour the same active-profile selection the Monitor tab
        // uses so widget + in-app view always agree on "which server".
        val activeId = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val profile =
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        val snap =
            if (profile == null) {
                Snapshot(profileName = null, error = "No servers")
            } else {
                val transport = ServiceLocator.transportFor(profile)
                transport.stats().fold(
                    onSuccess = { s ->
                        val load1 = s.cpuLoad1
                        val cores = s.cpuCores
                        val cpuPctFlat = s.cpuPct
                        val cpuPct =
                            when {
                                load1 != null && cores != null && cores > 0 ->
                                    ((load1 / cores) * 100.0).coerceIn(0.0, 100.0)
                                cpuPctFlat != null -> cpuPctFlat.coerceIn(0.0, 100.0)
                                else -> null
                            }
                        val cpuText =
                            when {
                                load1 != null && cores != null -> "%.2f".format(load1)
                                cpuPctFlat != null -> "%.1f%%".format(cpuPctFlat)
                                else -> "—"
                            }
                        val memUsed = s.memUsed
                        val memTotal = s.memTotal
                        val memPctFlat = s.memPct
                        val memPct =
                            when {
                                memUsed != null && memTotal != null && memTotal > 0 ->
                                    (memUsed.toDouble() / memTotal.toDouble() * 100.0).coerceIn(0.0, 100.0)
                                memPctFlat != null -> memPctFlat.coerceIn(0.0, 100.0)
                                else -> null
                            }
                        val memText =
                            when {
                                memUsed != null && memTotal != null ->
                                    "${fmt(memUsed)} / ${fmt(memTotal)}"
                                memPctFlat != null -> "%.1f%%".format(memPctFlat)
                                else -> "—"
                            }
                        // Disk
                        val diskUsed = s.diskUsed
                        val diskTotal = s.diskTotal
                        val diskPctFlat = s.diskPct
                        val diskPct =
                            when {
                                diskUsed != null && diskTotal != null && diskTotal > 0 ->
                                    (diskUsed.toDouble() / diskTotal.toDouble() * 100.0).coerceIn(0.0, 100.0)
                                diskPctFlat != null -> diskPctFlat.coerceIn(0.0, 100.0)
                                else -> null
                            }
                        val diskText =
                            when {
                                diskUsed != null && diskTotal != null ->
                                    "${fmt(diskUsed)} / ${fmt(diskTotal)}"
                                diskPctFlat != null -> "%.0f%%".format(diskPctFlat)
                                else -> "—"
                            }
                        // GPU / VRAM — only when the host actually has one.
                        val vramTotal = s.gpuMemTotalMb
                        val gpuPctVal = s.gpuUtilPct ?: s.gpuPct
                        val hasGpu = vramTotal != null && vramTotal > 0 || s.gpuName != null
                        val gpuText =
                            if (hasGpu) {
                                val utilStr = gpuPctVal?.let { "%.0f%%".format(it) }
                                val vramStr =
                                    if (vramTotal != null && vramTotal > 0) {
                                        val used = s.gpuMemUsedMb ?: 0L
                                        "$used/${vramTotal}M"
                                    } else {
                                        null
                                    }
                                listOfNotNull(utilStr, vramStr).joinToString(" · ").ifBlank { "—" }
                            } else {
                                ""
                            }
                        // Use VRAM bar when total known, else util%.
                        val gpuBar =
                            if (vramTotal != null && vramTotal > 0) {
                                val used = s.gpuMemUsedMb ?: 0L
                                (used.toDouble() / vramTotal.toDouble() * 100.0).coerceIn(0.0, 100.0).toInt()
                            } else {
                                gpuPctVal?.coerceIn(0.0, 100.0)?.toInt()
                            }
                        val gpuTemp = s.gpuTemp
                        val gpuTextExt =
                            if (hasGpu) {
                                val parts = mutableListOf<String>()
                                gpuPctVal?.let { parts += "%.0f%%".format(it) }
                                gpuTemp?.let { parts += "${it.toInt()}°C" }
                                if (vramTotal != null && vramTotal > 0) {
                                    val used = s.gpuMemUsedMb ?: 0L
                                    parts += "$used/${vramTotal}M"
                                }
                                parts.joinToString(" · ").ifBlank { "—" }
                            } else {
                                ""
                            }
                        // Swap — only show the row when the host has swap
                        // configured. swap_total is in bytes per PWA.
                        val hasSwap = s.swapTotal > 0
                        val swapPct =
                            if (hasSwap) {
                                (s.swapUsed.toDouble() / s.swapTotal.toDouble() * 100.0)
                                    .coerceIn(0.0, 100.0).toInt()
                            } else {
                                null
                            }
                        val swapText =
                            if (hasSwap) "${fmt(s.swapUsed)} / ${fmt(s.swapTotal)}" else "—"
                        // Network — `/api/stats` reports cumulative rx/tx
                        // counters (bytes since daemon start). Label
                        // reflects whether eBPF is providing the numbers
                        // or a system fallback, matching the PWA badge.
                        val netLabel = if (s.ebpfActive) "Net (eBPF)" else "Net"
                        val netRxStr = "↓ ${fmt(s.netRxBytes)}"
                        val netTxStr = "↑ ${fmt(s.netTxBytes)}"
                        // Daemon footer — RSS + goroutine count + open FDs.
                        val daemonParts = mutableListOf<String>()
                        if (s.daemonRssBytes > 0) daemonParts += "${fmt(s.daemonRssBytes)} RSS"
                        if (s.goroutines > 0) daemonParts += "${s.goroutines}g"
                        if (s.openFds > 0) daemonParts += "${s.openFds}fd"
                        val daemonStr = daemonParts.joinToString(" · ")
                        // Uptime stringy shortform so it fits the footer.
                        val uptimeStr =
                            if (s.uptimeSeconds > 0) formatUptimeShort(s.uptimeSeconds) else ""
                        Snapshot(
                            profileName = profile.displayName,
                            cpuPct = cpuPct?.toInt(),
                            cpuText = cpuText,
                            memPct = memPct?.toInt(),
                            memText = memText,
                            diskPct = diskPct?.toInt(),
                            diskText = diskText,
                            hasSwap = hasSwap,
                            swapPct = swapPct,
                            swapText = swapText,
                            gpuPct = gpuBar,
                            gpuText = gpuTextExt,
                            hasGpu = hasGpu,
                            netLabel = netLabel,
                            netRxText = netRxStr,
                            netTxText = netTxStr,
                            daemonText = daemonStr,
                            uptimeText = uptimeStr,
                            sessionsText =
                                "${s.sessionsTotal} · ${s.sessionsRunning}r · ${s.sessionsWaiting}w",
                        )
                    },
                    onFailure = {
                        Snapshot(profileName = profile.displayName, error = "offline")
                    },
                )
            }
        updateAll(context, snap)
    }

    private fun updateAll(
        context: Context,
        snap: Snapshot,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MonitorWidget::class.java))
        val views = RemoteViews(context.packageName, R.layout.widget_monitor)
        views.setTextViewText(
            R.id.widget_monitor_profile,
            snap.error?.let { "$it · ${snap.profileName ?: ""}" } ?: snap.profileName.orEmpty(),
        )
        views.setTextViewText(R.id.widget_monitor_cpu_value, snap.cpuText)
        views.setProgressBar(R.id.widget_monitor_cpu_bar, 100, snap.cpuPct ?: 0, false)
        views.setTextViewText(R.id.widget_monitor_mem_value, snap.memText)
        views.setProgressBar(R.id.widget_monitor_mem_bar, 100, snap.memPct ?: 0, false)
        views.setTextViewText(R.id.widget_monitor_disk_value, snap.diskText)
        views.setProgressBar(R.id.widget_monitor_disk_bar, 100, snap.diskPct ?: 0, false)
        // Swap row visibility + values
        val swapVisible = if (snap.hasSwap) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_monitor_swap_row, swapVisible)
        views.setViewVisibility(R.id.widget_monitor_swap_bar, swapVisible)
        views.setTextViewText(R.id.widget_monitor_swap_value, snap.swapText)
        views.setProgressBar(R.id.widget_monitor_swap_bar, 100, snap.swapPct ?: 0, false)
        // GPU row visibility + values
        val gpuVisible = if (snap.hasGpu) android.view.View.VISIBLE else android.view.View.GONE
        views.setViewVisibility(R.id.widget_monitor_gpu_row, gpuVisible)
        views.setViewVisibility(R.id.widget_monitor_gpu_bar, gpuVisible)
        views.setTextViewText(R.id.widget_monitor_gpu_value, snap.gpuText)
        views.setProgressBar(R.id.widget_monitor_gpu_bar, 100, snap.gpuPct ?: 0, false)
        // Network row — label reflects eBPF vs system fallback source.
        views.setTextViewText(R.id.widget_monitor_net_label, snap.netLabel)
        views.setTextViewText(R.id.widget_monitor_net_rx, snap.netRxText)
        views.setTextViewText(R.id.widget_monitor_net_tx, snap.netTxText)
        // Daemon row (RSS · goroutines · fds).
        views.setTextViewText(R.id.widget_monitor_daemon_value, snap.daemonText)
        views.setTextViewText(
            R.id.widget_monitor_sessions,
            snap.sessionsText ?: "—",
        )
        views.setTextViewText(R.id.widget_monitor_uptime, snap.uptimeText)
        attachTapIntent(context, views)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun attachTapIntent(
        context: Context,
        views: RemoteViews,
    ) {
        val openApp =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val openPi =
            PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_monitor_root, openPi)
        // Tapping the server-name label cycles to the next enabled
        // profile so users with multi-server setups can switch from
        // the home screen without opening the app.
        views.setOnClickPendingIntent(
            R.id.widget_monitor_profile,
            WidgetActions.cyclePendingIntent(context, MonitorWidget::class.java),
        )
    }

    private data class Snapshot(
        val profileName: String?,
        val cpuPct: Int? = null,
        val cpuText: String = "—",
        val memPct: Int? = null,
        val memText: String = "—",
        val diskPct: Int? = null,
        val diskText: String = "—",
        val hasSwap: Boolean = false,
        val swapPct: Int? = null,
        val swapText: String = "—",
        val gpuPct: Int? = null,
        val gpuText: String = "",
        val hasGpu: Boolean = false,
        val netRxText: String = "",
        val netTxText: String = "",
        val netLabel: String = "Net",
        val daemonText: String = "",
        val uptimeText: String = "",
        val sessionsText: String? = null,
        val error: String? = null,
    )

    public companion object {
        /**
         * Force-refresh all Monitor widget instances. Called from the
         * app after the Monitor tab's `/api/stats` poll lands so the
         * glanceable view matches without waiting for the 30 min
         * AppWidgetManager cadence.
         */
        public fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val intent =
                Intent(context, MonitorWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        manager.getAppWidgetIds(
                            ComponentName(context, MonitorWidget::class.java),
                        ),
                    )
                }
            context.sendBroadcast(intent)
        }
    }
}

private fun fmt(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

private fun formatUptimeShort(seconds: Long): String {
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3600
    val m = (seconds % 3600) / 60
    return when {
        d > 0 -> "${d}d${h}h"
        h > 0 -> "${h}h${m}m"
        else -> "${m}m"
    }
}
