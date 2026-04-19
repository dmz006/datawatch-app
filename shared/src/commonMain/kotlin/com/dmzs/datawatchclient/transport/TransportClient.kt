package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.flow.Flow

/**
 * Sprint-1 expanded contract. This is the primary surface the rest of the app talks
 * to. Per AGENT.md, this interface is load-bearing — changes are breaking; additions
 * are a minor version bump.
 *
 * All operations are suspend functions returning [Result] so call sites can handle
 * [TransportError] without try/catch ceremony. A streaming WebSocket surface arrives
 * in Sprint 2 (`/ws?session=`) and MCP SSE in Sprint 3 — those are deliberately not
 * added here to keep Sprint 1 focused.
 */
public interface TransportClient {
    public val profile: ServerProfile

    /** Cached reachability, updated by [ping] and transport-error observations. */
    public val isReachable: Flow<Boolean>

    /** GET /api/health. */
    public suspend fun ping(): Result<Unit>

    /** GET /api/sessions. */
    public suspend fun listSessions(): Result<List<Session>>

    /** POST /api/sessions/start. Returns new session id. */
    public suspend fun startSession(task: String, serverHint: String? = null): Result<String>

    /** POST /api/sessions/reply. */
    public suspend fun replyToSession(sessionId: String, text: String): Result<Unit>

    /** POST /api/sessions/kill. Requires confirm dialog upstream (ADR-0019). */
    public suspend fun killSession(sessionId: String): Result<Unit>

    /** POST /api/sessions/state. Force a session into a given state. */
    public suspend fun overrideSessionState(sessionId: String, state: SessionState): Result<Unit>

    /** GET /api/stats. */
    public suspend fun stats(): Result<StatsDto>

    /**
     * POST /api/devices/register — closes parent issue #1.
     *
     * Registers a push token (FCM or ntfy) with this datawatch server so it can
     * deliver wake notifications. Returns the server-assigned `device_id` which
     * the caller persists for later un-registration.
     */
    public suspend fun registerDevice(
        deviceToken: String,
        kind: DeviceKind,
        appVersion: String,
        platform: DevicePlatform,
        profileHint: String,
    ): Result<String>

    /** DELETE /api/devices/{id} — un-register a previously-registered push token. */
    public suspend fun unregisterDevice(deviceId: String): Result<Unit>

    /**
     * GET /api/federation/sessions — closes parent issue #3.
     *
     * Returns this server's primary sessions plus a parallel fan-out to every
     * remote it federates with. The mobile client uses this for the
     * "all servers" view; it still calls per-profile transports for
     * single-server views to keep behaviour predictable.
     */
    public suspend fun federationSessions(
        sinceEpochMs: Long? = null,
        states: List<SessionState> = emptyList(),
        includeProxied: Boolean = true,
    ): Result<FederationView>
}

public data class FederationView(
    val primary: List<Session>,
    val proxied: Map<String, List<Session>>,
    val errors: Map<String, String>,
)

public enum class DeviceKind(public val wire: String) { Fcm("fcm"), Ntfy("ntfy") }

public enum class DevicePlatform(public val wire: String) { Android("android"), Ios("ios") }
