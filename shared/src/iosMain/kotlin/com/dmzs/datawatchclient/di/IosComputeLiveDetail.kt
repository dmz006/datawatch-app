package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ComputeNodeLiveDetail
import com.dmzs.datawatchclient.transport.ComputeNodeLiveDetailLoader
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Stops a compute-node live-detail poll (closing the sheet). */
public class IosComputeLiveDetailSubscription internal constructor() {
    internal var job: Job? = null

    public fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * PWA `computeShowDetail` (Settings › Compute › node 📡) for iOS: polls
 * `/api/compute/nodes/{name}/detail` every second and reports the
 * pretty-printed JSON, or the server's reason once (polling then stops, like
 * the PWA). Callbacks fire on a background thread — hop to main in Swift.
 */
public object IosComputeLiveDetail {
    /** Compute-node row action label (intercepted in Swift, never sent to IosSettingsLists.action). */
    public const val ACTION: String = "📡 Live monitoring detail"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    public fun watch(
        profile: ServerProfile,
        name: String,
        onJson: (String) -> Unit,
        onError: (String) -> Unit,
    ): IosComputeLiveDetailSubscription {
        val sub = IosComputeLiveDetailSubscription()
        sub.job =
            scope.launch {
                val tr: TransportClient = t(profile)
                while (isActive) {
                    val d: ComputeNodeLiveDetail = ComputeNodeLiveDetailLoader.load(tr, name)
                    val err: String? = d.error
                    if (err != null) {
                        onError(err)
                        break
                    }
                    onJson(d.json.orEmpty())
                    delay(ComputeNodeLiveDetailLoader.POLL_INTERVAL_MS)
                }
            }
        return sub
    }
}
