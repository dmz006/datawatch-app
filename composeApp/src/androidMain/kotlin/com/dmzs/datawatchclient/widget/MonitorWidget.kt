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
        appWidgetIds.forEach { renderLoadingState(context, appWidgetManager, it) }
        scope.launch { refresh(context) }
    }

    override fun onReceive(context: Context, intent: Intent) {
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
                        Snapshot(
                            profileName = profile.displayName,
                            cpuPct = cpuPct?.toInt(),
                            cpuText = cpuText,
                            memPct = memPct?.toInt(),
                            memText = memText,
                            sessionsText =
                                "${s.sessionsTotal} total · ${s.sessionsRunning} run · ${s.sessionsWaiting} wait",
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
        views.setTextViewText(
            R.id.widget_monitor_sessions,
            snap.sessionsText ?: "—",
        )
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
            val intent =
                Intent(context, MonitorWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        AppWidgetManager.getInstance(context).getAppWidgetIds(
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

