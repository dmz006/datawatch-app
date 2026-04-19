package com.dmzs.datawatchclient.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-scoped channel for deep-link targets that should be handled by AppRoot's
 * navigation graph as soon as it composes. Emits the *session id* component of a
 * `dwclient://session/<id>` URI.
 *
 * Using a SharedFlow with replay = 1 so a deep link delivered before AppRoot
 * subscribes is still received once the collector starts.
 */
public object DeepLinks {
    public val pendingSessionTarget: MutableSharedFlow<String> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 4)
}
