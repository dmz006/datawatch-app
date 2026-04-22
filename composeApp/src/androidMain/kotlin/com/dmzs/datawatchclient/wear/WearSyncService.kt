package com.dmzs.datawatchclient.wear

import android.content.Context
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public fun start() {
        scope.launch {
            // Re-key the Session flow on active-profile changes so the
            // watch always reflects the server the phone is currently
            // looking at. If the user switches profiles on the phone,
            // the watch follows within the DataLayer's next tick.
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
                .collectLatest { snap -> publish(snap) }
        }
    }

    private data class Snapshot(
        val serverId: String,
        val serverName: String,
        val running: Int,
        val waiting: Int,
        val total: Int,
    )

    private fun publish(snap: Snapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(COUNTS_PATH).apply {
                    dataMap.putString("serverId", snap.serverId)
                    dataMap.putString("serverName", snap.serverName)
                    dataMap.putInt("running", snap.running)
                    dataMap.putInt("waiting", snap.waiting)
                    dataMap.putInt("total", snap.total)
                    // Force Wearable to treat the item as changed even
                    // when the counts are identical — otherwise the
                    // onDataChanged listener wouldn't fire on a
                    // same-state republish.
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
    }
}
