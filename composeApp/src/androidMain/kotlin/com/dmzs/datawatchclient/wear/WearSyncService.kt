package com.dmzs.datawatchclient.wear

import android.content.Context
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes the active server's session counts + name to the paired
 * Wear device via the Wearable Data Layer. The watch subscribes to
 * the `/datawatch/counts` DataItem path in its own ViewModel.
 *
 * Path + key contract:
 *
 * ```
 * /datawatch/counts
 *   serverId: String         (empty = none paired)
 *   serverName: String
 *   running: Int
 *   waiting: Int
 *   total: Int
 *   ts: Long                 (force DataItem to be considered changed)
 * ```
 *
 * Started from [com.dmzs.datawatchclient.DatawatchApp.onCreate]; runs
 * the length of the process. Uses the phone-side SessionRepository so
 * the watch never needs to hold a bearer token or call the server.
 *
 * Filed after v0.33.11 on-device test surfaced the Wear app's "Pair
 * phone in Settings" stub — the Phase 1 placeholder in the Wear
 * ViewModel never had an actual DataLayer producer on the phone side.
 */
public class WearSyncService(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val messageListener =
        MessageClient.OnMessageReceivedListener { ev: MessageEvent ->
            when (ev.path) {
                SET_ACTIVE_PATH -> {
                    val id = runCatching { String(ev.data, Charsets.UTF_8) }.getOrNull()
                    if (!id.isNullOrEmpty()) {
                        ServiceLocator.activeServerStore.set(id)
                    }
                }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public fun start() {
        // Listen for the watch's "switch active server" message so a
        // server picked on the wrist updates the phone's shared store.
        runCatching {
            Wearable.getMessageClient(context).addListener(messageListener)
        }
        // Active server's session counts.
        scope.launch {
            ServiceLocator.activeServerStore.observe()
                .flatMapLatest { activeId ->
                    combine(
                        ServiceLocator.profileRepository.observeAll(),
                        if (activeId == null || activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            ServiceLocator.sessionRepository.observeForProfile(activeId)
                        },
                    ) { profiles, sessions ->
                        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                        Snapshot(
                            serverId = profile?.id.orEmpty(),
                            serverName = profile?.displayName.orEmpty(),
                            running = sessions.count { it.state == SessionState.Running },
                            waiting = sessions.count { it.state == SessionState.Waiting },
                            total = sessions.size,
                        )
                    }
                }
                .collectLatest { snap -> publishCounts(snap) }
        }
        // Enabled profile list — watch's server-picker page reads this.
        scope.launch {
            combine(
                ServiceLocator.profileRepository.observeAll(),
                ServiceLocator.activeServerStore.observe(),
            ) { profiles, activeId ->
                ProfilesSnapshot(
                    activeId = activeId.orEmpty(),
                    ids = profiles.filter { it.enabled }.map { it.id },
                    names = profiles.filter { it.enabled }.map { it.displayName },
                )
            }.collectLatest { snap -> publishProfiles(snap) }
        }
        // Light stats snapshot — polled every 15 s on the phone, cached
        // in the DataLayer so the watch reads the last-known values
        // without opening its own HTTP client.
        scope.launch {
            while (isActive) {
                runCatching {
                    val activeId = ServiceLocator.activeServerStore.get()
                    if (!activeId.isNullOrEmpty() && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS) {
                        val profile =
                            ServiceLocator.profileRepository.observeAll().first()
                                .firstOrNull { it.id == activeId && it.enabled }
                        if (profile != null) {
                            ServiceLocator.transportFor(profile).stats().onSuccess { s ->
                                publishStats(
                                    StatsSnapshot(
                                        cpuLoad1 = s.cpuLoad1 ?: s.cpuPct ?: 0.0,
                                        cpuCores = s.cpuCores ?: 0,
                                        memUsed = s.memUsed ?: 0L,
                                        memTotal = s.memTotal ?: 0L,
                                        diskUsed = s.diskUsed ?: 0L,
                                        diskTotal = s.diskTotal ?: 0L,
                                        uptimeSeconds = s.uptimeSeconds,
                                        sessionsTotal = s.sessionsTotal,
                                        sessionsRunning = s.sessionsRunning,
                                        sessionsWaiting = s.sessionsWaiting,
                                    ),
                                )
                            }
                        }
                    }
                }
                delay(STATS_POLL_MS)
            }
        }
    }

    public fun stop() {
        runCatching {
            Wearable.getMessageClient(context).removeListener(messageListener)
        }
    }

    private data class Snapshot(
        val serverId: String,
        val serverName: String,
        val running: Int,
        val waiting: Int,
        val total: Int,
    )

    private data class ProfilesSnapshot(
        val activeId: String,
        val ids: List<String>,
        val names: List<String>,
    )

    private data class StatsSnapshot(
        val cpuLoad1: Double,
        val cpuCores: Int,
        val memUsed: Long,
        val memTotal: Long,
        val diskUsed: Long,
        val diskTotal: Long,
        val uptimeSeconds: Long,
        val sessionsTotal: Int,
        val sessionsRunning: Int,
        val sessionsWaiting: Int,
    )

    private fun publishCounts(snap: Snapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(COUNTS_PATH).apply {
                    dataMap.putString("serverId", snap.serverId)
                    dataMap.putString("serverName", snap.serverName)
                    dataMap.putInt("running", snap.running)
                    dataMap.putInt("waiting", snap.waiting)
                    dataMap.putInt("total", snap.total)
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    private fun publishProfiles(snap: ProfilesSnapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(PROFILES_PATH).apply {
                    dataMap.putString("activeId", snap.activeId)
                    dataMap.putStringArray("ids", snap.ids.toTypedArray())
                    dataMap.putStringArray("names", snap.names.toTypedArray())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    private fun publishStats(snap: StatsSnapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(STATS_PATH).apply {
                    dataMap.putDouble("cpuLoad1", snap.cpuLoad1)
                    dataMap.putInt("cpuCores", snap.cpuCores)
                    dataMap.putLong("memUsed", snap.memUsed)
                    dataMap.putLong("memTotal", snap.memTotal)
                    dataMap.putLong("diskUsed", snap.diskUsed)
                    dataMap.putLong("diskTotal", snap.diskTotal)
                    dataMap.putLong("uptimeSeconds", snap.uptimeSeconds)
                    dataMap.putInt("sessionsTotal", snap.sessionsTotal)
                    dataMap.putInt("sessionsRunning", snap.sessionsRunning)
                    dataMap.putInt("sessionsWaiting", snap.sessionsWaiting)
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
        public const val PROFILES_PATH: String = "/datawatch/profiles"
        public const val STATS_PATH: String = "/datawatch/stats"
        public const val SET_ACTIVE_PATH: String = "/datawatch/setActive"
        public const val STATS_POLL_MS: Long = 15_000L
    }
}
