package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.security.IosWidgetConfigStore
import com.dmzs.datawatchclient.surfaces.VoiceSendTarget
import com.dmzs.datawatchclient.surfaces.WidgetConfig
import com.dmzs.datawatchclient.surfaces.WidgetMonitorSnapshot
import com.dmzs.datawatchclient.surfaces.WidgetServer
import com.dmzs.datawatchclient.surfaces.WidgetSessionCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Session counts for the Sessions widget. [status] is one of the `IosSurfaces.STATUS_*` values. */
public data class IosWidgetSessions(
    val status: String,
    val serverName: String,
    val running: Int,
    val waiting: Int,
    val total: Int,
)

/** Host stats for the Monitor widget; [snapshot] is null unless [status] is `ok`. */
public data class IosWidgetMonitor(
    val status: String,
    val serverName: String,
    val snapshot: WidgetMonitorSnapshot?,
)

/** Where a Siri "send to session" goes: Android `VoiceCommandActivity` target. */
public data class IosSendTarget(
    val profile: ServerProfile,
    val session: Session,
    /** Android label: session name, else the last 8 characters of its id. */
    val label: String,
)

/**
 * iOS platform surfaces (BL403): Siri / App Intents in the app, and the
 * WidgetKit extension (Sessions + Monitor widgets). Every network call goes
 * through [IosServiceLocator.transportFor] — no Swift networking.
 *
 * The app publishes a [WidgetConfig] to the shared Keychain ([IosWidgetConfigStore])
 * whenever its servers or the active server change; the widget extension reads it,
 * picks the server like Android's widgets do, and fetches from that server itself.
 * Each callback fires exactly once (safe for Swift `CheckedContinuation`).
 */
public object IosSurfaces {
    public const val STATUS_OK: String = "ok"
    public const val STATUS_NO_SERVERS: String = "no_servers"
    public const val STATUS_OFFLINE: String = "offline"

    /** Device locked: the bearer token (when-unlocked Keychain item) can't be read yet. */
    public const val STATUS_LOCKED: String = "locked"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configStore: IosWidgetConfigStore by lazy { IosWidgetConfigStore() }

    // ── App side ─────────────────────────────────────────────────────────

    /**
     * Write the widget configuration from the app's current profiles and
     * [activeProfileId] (the app's `dw.active_profile_id`; may be the
     * "All servers" sentinel, which the widget treats like Android: first enabled).
     */
    public fun publishWidgetConfig(
        activeProfileId: String?,
        onDone: () -> Unit,
    ) {
        scope.launch {
            try {
                val profiles = IosServiceLocator.profileRepository.observeAll().first()
                // A proxied remote (#234) publishes its parent: widgets stay on real servers.
                val realId = com.dmzs.datawatchclient.transport.ProxiedServers.realIdOf(activeProfileId)
                configStore.put(WidgetConfig.from(profiles, realId).encode())
            } catch (_: Throwable) {
                // Best effort: the widget keeps its previous configuration.
            }
            onDone()
        }
    }

    /**
     * Active server id as last written by either side. A widget tap on the server
     * name (Android "tap to cycle") changes it; the app adopts it on foreground.
     */
    public fun widgetActiveProfileId(): String? = WidgetConfig.decode(configStore.get())?.activeId

    /**
     * Siri "send to session": the active server (Android `activeProfileFlow`: the
     * stored id when enabled, else the first enabled server), then the running or
     * waiting session picked by [VoiceSendTarget]. `onResult(null)` = no server or
     * no running session; `onError` = the server could not be reached.
     */
    public fun resolveSendTarget(
        activeProfileId: String?,
        sessionHint: String?,
        onResult: (IosSendTarget?) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            try {
                val enabled = IosServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
                val realId = com.dmzs.datawatchclient.transport.ProxiedServers.realIdOf(activeProfileId)
                val profile = enabled.firstOrNull { it.id == realId } ?: enabled.firstOrNull()
                if (profile == null) {
                    onResult(null)
                    return@launch
                }
                IosServiceLocator.transportFor(profile).listSessions().fold(
                    onSuccess = { sessions ->
                        val s = VoiceSendTarget.resolve(sessions, sessionHint)
                        onResult(s?.let { IosSendTarget(profile, it, it.name ?: it.id.takeLast(8)) })
                    },
                    onFailure = { onError(it.message ?: "Could not reach the server.") },
                )
            } catch (e: Throwable) {
                onError(e.message ?: "Could not reach the server.")
            }
        }
    }

    // ── Widget extension side ────────────────────────────────────────────

    /** Android `WidgetActions.cycleActiveServer`, then the app picks it up on foreground. */
    public fun cycleWidgetServer(onDone: () -> Unit) {
        scope.launch {
            try {
                WidgetConfig.decode(configStore.get())?.let { configStore.put(it.cycled().encode()) }
            } catch (_: Throwable) {
                // Nothing to cycle.
            }
            onDone()
        }
    }

    public fun loadWidgetSessions(onResult: (IosWidgetSessions) -> Unit) {
        scope.launch {
            val server = WidgetConfig.decode(configStore.get())?.pick()
            val result =
                when {
                    server == null -> IosWidgetSessions(STATUS_NO_SERVERS, "", 0, 0, 0)
                    tokenLocked(server) -> IosWidgetSessions(STATUS_LOCKED, server.displayName, 0, 0, 0)
                    else ->
                        try {
                            IosServiceLocator.transportFor(server.toProfile()).listSessions().fold(
                                onSuccess = {
                                    val c = WidgetSessionCounts.of(it)
                                    IosWidgetSessions(STATUS_OK, server.displayName, c.running, c.waiting, c.total)
                                },
                                onFailure = { IosWidgetSessions(STATUS_OFFLINE, server.displayName, 0, 0, 0) },
                            )
                        } catch (_: Throwable) {
                            IosWidgetSessions(STATUS_OFFLINE, server.displayName, 0, 0, 0)
                        }
                }
            onResult(result)
        }
    }

    public fun loadWidgetMonitor(onResult: (IosWidgetMonitor) -> Unit) {
        scope.launch {
            val server = WidgetConfig.decode(configStore.get())?.pick()
            val result =
                when {
                    server == null -> IosWidgetMonitor(STATUS_NO_SERVERS, "", null)
                    tokenLocked(server) -> IosWidgetMonitor(STATUS_LOCKED, server.displayName, null)
                    else ->
                        try {
                            IosServiceLocator.transportFor(server.toProfile()).stats().fold(
                                onSuccess = {
                                    IosWidgetMonitor(STATUS_OK, server.displayName, WidgetMonitorSnapshot.of(it))
                                },
                                onFailure = { IosWidgetMonitor(STATUS_OFFLINE, server.displayName, null) },
                            )
                        } catch (_: Throwable) {
                            IosWidgetMonitor(STATUS_OFFLINE, server.displayName, null)
                        }
                }
            onResult(result)
        }
    }

    private fun tokenLocked(server: WidgetServer): Boolean =
        server.bearerTokenRef.isNotBlank() && IosServiceLocator.tokenStore.get(server.bearerTokenRef) == null
}
