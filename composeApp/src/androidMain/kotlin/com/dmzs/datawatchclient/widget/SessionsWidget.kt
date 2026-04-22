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
 * Home-screen widget that surfaces session counts (BL6, ADR-0042). Refreshes
 * on the system updatePeriodMillis (30 min — AppWidgetManager's own minimum)
 * and any time the app explicitly calls [update] after a successful refresh.
 *
 * Pulls live counts from [ServiceLocator.transportFor] on the active
 * profile; falls back to cached counts from SessionRepository if REST fails.
 * Tap anywhere on the widget launches MainActivity.
 */
public class SessionsWidget : AppWidgetProvider() {
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
                refresh(context)
                MonitorWidget.requestUpdate(context)
            }
        }
    }

    private fun renderLoadingState(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_sessions)
        views.setTextViewText(R.id.widget_profile, "loading…")
        attachTapIntent(context, views)
        manager.updateAppWidget(widgetId, views)
    }

    private suspend fun refresh(context: Context) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val profile = profiles.firstOrNull { it.enabled }
        val counts =
            if (profile == null) {
                Counts(0, 0, 0, null, "No servers")
            } else {
                val r = ServiceLocator.transportFor(profile).listSessions()
                r.fold(
                    onSuccess = { list ->
                        Counts(
                            running = list.count { it.state.name == "Running" },
                            waiting = list.count { it.state.name == "Waiting" },
                            total = list.size,
                            profileName = profile.displayName,
                            error = null,
                        )
                    },
                    onFailure = {
                        Counts(0, 0, 0, profile.displayName, "offline")
                    },
                )
            }
        updateAll(context, counts)
    }

    private fun updateAll(
        context: Context,
        counts: Counts,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SessionsWidget::class.java))
        val views = RemoteViews(context.packageName, R.layout.widget_sessions)
        views.setTextViewText(R.id.widget_running, counts.running.toString())
        views.setTextViewText(R.id.widget_waiting, counts.waiting.toString())
        views.setTextViewText(R.id.widget_total, counts.total.toString())
        views.setTextViewText(
            R.id.widget_profile,
            counts.error?.let { "$it · ${counts.profileName ?: ""}" }
                ?: counts.profileName.orEmpty(),
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
        views.setOnClickPendingIntent(R.id.widget_root, openPi)
        // Tap the server-name label → cycle active profile. Mirrors
        // [MonitorWidget] so a multi-server user can switch from
        // either widget.
        views.setOnClickPendingIntent(
            R.id.widget_profile,
            WidgetActions.cyclePendingIntent(context, SessionsWidget::class.java),
        )
    }

    private data class Counts(
        val running: Int,
        val waiting: Int,
        val total: Int,
        val profileName: String?,
        val error: String?,
    )

    public companion object {
        /** Force-refresh all active instances. Called from the app after a successful list pull. */
        public fun requestUpdate(context: Context) {
            val intent =
                Intent(context, SessionsWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        AppWidgetManager.getInstance(context).getAppWidgetIds(
                            ComponentName(context, SessionsWidget::class.java),
                        ),
                    )
                }
            context.sendBroadcast(intent)
        }
    }
}
