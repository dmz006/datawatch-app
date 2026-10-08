package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.FedConnPhase
import com.dmzs.datawatchclient.transport.FedConnStatus
import com.dmzs.datawatchclient.transport.FederatedConnectionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Swift-friendly snapshot of [FedConnStatus]. [phase] is one of
 * [PHASE_CONNECTING], [PHASE_LOADING], [PHASE_ERROR].
 */
public class IosFedConnState(
    public val profileId: String,
    public val serverName: String,
    public val parentId: String,
    public val parentName: String,
    public val phase: String,
    public val reason: String,
    public val authFailed: Boolean,
) {
    public companion object {
        public const val PHASE_CONNECTING: String = "connecting"
        public const val PHASE_LOADING: String = "loading"
        public const val PHASE_ERROR: String = "error"
    }
}

/**
 * #236.2 / #235 — iOS bridge for the shared [FederatedConnectionMonitor]
 * (same one-shot authenticated probe and status phases as Android and the
 * PWA). `ServerProfileStore` feeds the active profile on every change
 * ([onActiveProfile], nil for "All servers") and observes [watch]; the
 * Sessions view model calls [reportData] whenever a real session list for
 * the shown server arrives. All arguments are explicit (Swift doesn't see
 * Kotlin defaults).
 */
public object IosFedConn {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val monitor: FederatedConnectionMonitor by lazy {
        FederatedConnectionMonitor(
            scope = scope,
            probe = { profile -> IosServiceLocator.transportFor(profile).listSessions() },
            onSessions = { profile, sessions ->
                IosServiceLocator.sessionRepository.replaceAll(profile.id, sessions)
            },
        )
    }

    public fun onActiveProfile(profile: ServerProfile?) {
        monitor.onActiveProfile(profile)
    }

    public fun reportData(profileId: String) {
        monitor.reportData(profileId)
    }

    /** Delivers every status change (null = nothing to show) on a background thread. */
    public fun watch(onStatus: (IosFedConnState?) -> Unit): IosSubscription =
        IosSubscription(scope.launch { monitor.status.collect { onStatus(it?.toIos()) } })

    private fun FedConnStatus.toIos(): IosFedConnState =
        IosFedConnState(
            profileId = profileId,
            serverName = serverName,
            parentId = parentId,
            parentName = parentName,
            phase =
                when (phase) {
                    FedConnPhase.CONNECTING -> IosFedConnState.PHASE_CONNECTING
                    FedConnPhase.LOADING_SESSIONS -> IosFedConnState.PHASE_LOADING
                    FedConnPhase.ERROR -> IosFedConnState.PHASE_ERROR
                },
            reason = reason.orEmpty(),
            authFailed = authFailed,
        )
}
