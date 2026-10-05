package com.dmzs.datawatchclient.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-scoped channel for deep-link targets that should be handled by AppRoot's
 * navigation graph as soon as it composes. Emits the *session id* component of a
 * `datawatch://session/<id>` URI (`dwclient://` accepted as a one-release alias, D84b).
 * Alert links (`datawatch://alert/<id>`, iOS AppRouter parity) go through
 * [pendingAlertTarget] instead.
 *
 * Using a SharedFlow with replay = 1 so a deep link delivered before AppRoot
 * subscribes is still received once the collector starts.
 */
public object DeepLinks {
    public val pendingSessionTarget: MutableSharedFlow<String> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 4)

    /** Canonical deep-link scheme (parity D84b). */
    public const val SCHEME: String = "datawatch"

    /** Pre-D84b scheme, still accepted for one release. Remove after the next release. */
    public const val LEGACY_SCHEME: String = "dwclient"

    public fun isAppScheme(scheme: String?): Boolean = scheme == SCHEME || scheme == LEGACY_SCHEME

    public fun sessionUri(sessionId: String): String = "$SCHEME://session/$sessionId"

    /**
     * Session detail keys on the server's SHORT id (`808e`), but links and
     * restored state can carry the full id (`<host>-808e`, hosts may contain
     * '-'). Short ids never contain '-', so the part after the last '-' is it.
     */
    public fun shortSessionId(id: String): String = id.substringAfterLast('-')

    /**
     * Pending `datawatch://alert/<id>` target — the alert id, or "" for a bare
     * `datawatch://alert(s)` link (open the Alerts tab only). A StateFlow that
     * the Home shell clears with [consumeAlertTarget], so a recomposition never
     * re-delivers a link that was already handled.
     */
    public val pendingAlertTarget: MutableStateFlow<String?> = MutableStateFlow(null)

    public fun alertUri(alertId: String): String = "$SCHEME://alert/$alertId"

    /**
     * Alert target for a parsed app-scheme URI (host = first segment for a custom
     * scheme): the alert id, "" when there is no id, or null when [host] is not an
     * alert link. Mirrors iOS `AppRouter.parse` ("alert" / "alerts").
     */
    public fun alertTargetFor(
        host: String?,
        pathSegments: List<String>,
    ): String? {
        val h = host?.lowercase() ?: return null
        if (h != "alert" && h != "alerts") return null
        return pathSegments.firstOrNull()?.trim().orEmpty()
    }

    public fun consumeAlertTarget() {
        pendingAlertTarget.value = null
    }
}
