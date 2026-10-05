package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ws.AlertPush
import com.dmzs.datawatchclient.transport.ws.AlertsHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Live WS `alert` frames for the iOS alert dock (parity D51a; Android
 * `AppRoot.LiveAlertFeed`). Keeps one global `/ws` open for [profile] so the
 * server's broadcast `alert` frames reach [AlertsHub], and forwards the ones
 * for this profile. Callbacks run on a background thread; Swift keeps the
 * handle and cancels it when the app backgrounds or the profile goes away.
 *
 * Other open global sockets for the same profile also route `alert` frames
 * into the hub, so Swift de-duplicates by [AlertPush.id].
 */
public object IosAlertFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun subscribe(
        profile: ServerProfile,
        onAlert: (AlertPush) -> Unit,
    ): IosSubscription {
        val job =
            scope.launch {
                launch { IosServiceLocator.wsTransportFor(profile).globalStream().collect { } }
                launch {
                    AlertsHub.flow
                        .filter { push: AlertPush -> push.serverProfileId == profile.id }
                        .collect { push: AlertPush -> onAlert(push) }
                }
            }
        return IosSubscription(job)
    }
}
