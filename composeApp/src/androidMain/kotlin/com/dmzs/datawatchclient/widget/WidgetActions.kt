package com.dmzs.datawatchclient.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dmzs.datawatchclient.di.ServiceLocator
import kotlinx.coroutines.flow.first

/**
 * Shared plumbing for the home-screen widgets' "tap server name to
 * cycle active profile" affordance. Both [SessionsWidget] and
 * [MonitorWidget] attach a PendingIntent to their profile label so
 * the user can switch fleets without opening the app.
 *
 * The cycle is intentionally server-side: we mutate the same
 * `ActiveServerStore` the phone's Sessions + Monitor tabs and
 * Wear/Auto surfaces all read from — one source of truth.
 */
public object WidgetActions {
    public const val ACTION_CYCLE_SERVER: String =
        "com.dmzs.datawatchclient.widget.CYCLE_SERVER"

    /**
     * Advance `ActiveServerStore` to the next enabled profile in
     * insertion order. Wraps around at the end. No-op when only one
     * profile is configured — a widget user with a single server has
     * nothing to cycle to.
     */
    public suspend fun cycleActiveServer(): String? {
        val enabled =
            ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        if (enabled.size <= 1) return enabled.firstOrNull()?.id
        val currentId = ServiceLocator.activeServerStore.get()
        val currentIdx = enabled.indexOfFirst { it.id == currentId }
        val next = enabled[(currentIdx + 1).mod(enabled.size)]
        ServiceLocator.activeServerStore.set(next.id)
        return next.id
    }

    /**
     * Build a broadcast PendingIntent that targets the given receiver
     * class with [ACTION_CYCLE_SERVER]. Widget code attaches this to
     * the profile-name text view; the receiver handles it via
     * [onCycleReceived].
     */
    public fun cyclePendingIntent(
        context: Context,
        receiverClass: Class<*>,
    ): PendingIntent {
        val intent =
            Intent(context, receiverClass).apply {
                action = ACTION_CYCLE_SERVER
            }
        return PendingIntent.getBroadcast(
            context,
            // Distinct request code per receiver class so
            // Sessions and Monitor widgets don't collapse into the
            // same PendingIntent slot.
            receiverClass.name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
