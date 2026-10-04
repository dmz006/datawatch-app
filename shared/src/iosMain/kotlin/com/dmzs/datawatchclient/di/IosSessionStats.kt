package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One Stats-tab sample: the session's process envelope and its compute node. */
public data class IosSessionStatsSnapshot(
    val envelope: StatEnvelopeDto?,
    val computeNodeRef: String?,
    val computeNode: ComputeNodeDetailDto?,
)

/**
 * Session Stats sub-tab data (parity B9; PWA envelopes poll, Android
 * SessionStatsViewModel). Envelope match: session-kind by id first, then the
 * backend envelope (`backend:<family>` / `backend:<family>-docker`). The compute
 * node comes from the session, else the first node of its LLM registry entry.
 */
public object IosSessionStats {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodeRefCache = HashMap<String, String?>()

    public fun load(
        profile: ServerProfile,
        session: Session,
        onResult: (IosSessionStatsSnapshot) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val cacheKey = "${profile.id}/${session.id}"
            val nodeRef =
                if (nodeRefCache.containsKey(cacheKey)) {
                    nodeRefCache[cacheKey]
                } else {
                    val ref =
                        session.computeNodeRef?.takeIf { it.isNotBlank() }
                            ?: session.backend?.let { bf ->
                                t.listLlms().getOrNull()?.firstOrNull { it.name == bf }?.computeNodes?.firstOrNull()
                            }
                    nodeRefCache[cacheKey] = ref
                    ref
                }
            val snapshot =
                coroutineScope {
                    val detail = async { nodeRef?.let { t.getComputeNodeDetail(it).getOrNull() } }
                    val envelopes = async { t.getAllEnvelopes().getOrNull().orEmpty() }
                    val list = envelopes.await()
                    val bf = session.backend
                    val env =
                        list.firstOrNull {
                            it.kind == "session" && (it.sessionId == session.id || it.id == "session:${session.id}")
                        } ?: bf?.let { b ->
                            list.firstOrNull { it.kind == "backend" && (it.id == "backend:$b" || it.id == "backend:$b-docker") }
                        }
                    IosSessionStatsSnapshot(envelope = env, computeNodeRef = nodeRef, computeNode = detail.await())
                }
            onResult(snapshot)
        }
    }
}
