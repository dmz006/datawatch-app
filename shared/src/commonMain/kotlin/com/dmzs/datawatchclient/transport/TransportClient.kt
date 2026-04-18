package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.flow.Flow

/**
 * Stable surface the rest of the app calls to reach a datawatch server.
 * Per AGENT.md, this interface is load-bearing — changes are breaking.
 *
 * Implementations: [RestTransport], [WebSocketTransport], [McpSseTransport],
 * [DnsTxtTransport]. The relay/Intent-handoff path (ADR-0004) is intentionally not
 * part of this interface — it's a `BackendRelay`, not a transport.
 */
public interface TransportClient {
    public val profile: ServerProfile

    /** True if a ping round-trip succeeded recently. Cached, cheap to read. */
    public val isReachable: Flow<Boolean>

    /** Active round-trip ping against `/api/health`. */
    public suspend fun ping(): Boolean
}
