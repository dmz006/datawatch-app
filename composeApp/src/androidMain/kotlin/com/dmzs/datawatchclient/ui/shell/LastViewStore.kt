package com.dmzs.datawatchclient.ui.shell

import android.content.Context

/**
 * Parity D40a — restore the last view on cold start, mirroring the PWA's
 * `cs_active_view` / `cs_active_session` localStorage keys (app.js:1727).
 *
 * The last bottom-nav tab is written on every tab change; the open session id
 * is written when session detail opens and cleared only when the user leaves
 * it with Back, so a process kill while a session is open restores it.
 */
public object LastViewStore {
    private const val PREFS = "shell_last_view"
    private const val KEY_TAB = "active_view"
    private const val KEY_SESSION = "active_session"

    private val validTabs: Set<String> =
        setOf(
            Destinations.Tabs.Sessions,
            Destinations.Tabs.Autonomous,
            Destinations.Tabs.Alerts,
            Destinations.Tabs.Observer,
            Destinations.Tabs.Dashboard,
            Destinations.Tabs.Settings,
        )

    public fun lastTab(context: Context): String =
        sanitizeTab(prefs(context).getString(KEY_TAB, null))

    public fun setLastTab(
        context: Context,
        route: String?,
    ) {
        if (route == null || route !in validTabs) return
        prefs(context).edit().putString(KEY_TAB, route).apply()
    }

    public fun lastSession(context: Context): String? = prefs(context).getString(KEY_SESSION, null)?.takeIf { it.isNotBlank() }

    public fun setLastSession(
        context: Context,
        sessionId: String?,
    ) {
        val edit = prefs(context).edit()
        if (sessionId.isNullOrBlank()) edit.remove(KEY_SESSION) else edit.putString(KEY_SESSION, sessionId)
        edit.apply()
    }

    /** Unknown / missing routes fall back to Sessions (the PWA default view). */
    public fun sanitizeTab(route: String?): String = if (route != null && route in validTabs) route else Destinations.Tabs.Sessions

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
