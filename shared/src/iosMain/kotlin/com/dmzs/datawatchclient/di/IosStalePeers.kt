package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * PWA `updatePeerStaleBadge` (Settings tab `#peerStaleBadge`) for the iOS
 * root view; Android `countStalePeers` in FederatedPeersCard.kt.
 *
 * A peer is stale when it never pushed or its last push is older than 60 s.
 * Result: the count, or -1 when the request failed (keep the previous badge).
 * Callback runs on a background thread.
 */
public object IosStalePeers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun count(
        profile: ServerProfile,
        onResult: (Int) -> Unit,
    ) {
        scope.launch {
            val result = IosServiceLocator.transportFor(profile).observerPeers()
            val now: Long = Clock.System.now().toEpochMilliseconds()
            val n: Int =
                result.fold(
                    onSuccess = { dto ->
                        dto.peers.count { peer ->
                            val ts: String? = peer.lastPushAt
                            if (ts.isNullOrBlank()) {
                                true
                            } else {
                                runCatching { now - Instant.parse(ts).toEpochMilliseconds() > 60_000L }
                                    .getOrDefault(false)
                            }
                        }
                    },
                    onFailure = { -1 },
                )
            onResult(n)
        }
    }
}
