package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.StatsDto
import kotlinx.coroutines.flow.Flow

/**
 * Transport contract between the mobile app and a single datawatch server profile.
 * Per AGENT.md, this interface is load-bearing — breaking changes are breaking
 * releases; additions are a minor version bump.
 *
 * All operations are suspend functions returning [Result] so call sites can handle
 * [TransportError] without try/catch ceremony.
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
     * GET /api/backends — list of registered LLM backends + which is active.
     * Mobile uses this to populate the Channels / LLM backends tab.
     */
    public suspend fun listBackends(): Result<BackendsView>

    /**
     * POST /api/voice/transcribe — parent issue #2 (Whisper-backed).
     *
     * Uploads an audio blob (opus/ogg/webm, 16 kHz mono preferred) and returns
     * the transcript. If [sessionId] is non-null and [autoExec] is false, the
     * server does NOT auto-reply — the caller places the transcript in the
     * composer and the user taps Send, matching the PWA voice-button UX.
     */
    public suspend fun transcribeAudio(
        audio: ByteArray,
        audioMime: String,
        sessionId: String? = null,
        autoExec: Boolean = false,
    ): Result<VoiceTranscript>

    /**
     * POST /api/devices/register — parent issue #1.
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
     * GET /api/federation/sessions — parent issue #3.
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

public data class VoiceTranscript(
    val transcript: String,
    val confidence: Double,
    val action: String?,
    val sessionId: String?,
    val latencyMs: Long,
)

public data class BackendsView(
    val llm: List<String>,
    val active: String?,
)

public data class FederationView(
    val primary: List<Session>,
    val proxied: Map<String, List<Session>>,
    val errors: Map<String, String>,
)

public enum class DeviceKind(public val wire: String) { Fcm("fcm"), Ntfy("ntfy") }

public enum class DevicePlatform(public val wire: String) { Android("android"), Ios("ios") }
