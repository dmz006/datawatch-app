package com.dmzs.datawatchclient.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-scoped channel for deep-link targets that should be handled by AppRoot's
 * navigation graph as soon as it composes. Emits the *session id* component of a
 * `datawatch://session/<id>` URI (`dwclient://` accepted as a one-release alias, D84b).
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
}
