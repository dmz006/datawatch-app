package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.Alert
import com.dmzs.datawatchclient.domain.ConfigView
import com.dmzs.datawatchclient.domain.FileList
import com.dmzs.datawatchclient.domain.SavedCommand
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.domain.ServerInfo
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

    /**
     * POST /api/sessions/start. Returns new session id. [workingDir] is an
     * optional server-side directory path (v0.12 file-picker integration).
     */
    public suspend fun startSession(
        task: String,
        serverHint: String? = null,
        workingDir: String? = null,
        profileName: String? = null,
        name: String? = null,
        backend: String? = null,
        resumeId: String? = null,
        autoGitInit: Boolean? = null,
        autoGitCommit: Boolean? = null,
    ): Result<String>

    /** POST /api/sessions/reply. */
    public suspend fun replyToSession(
        sessionId: String,
        text: String,
    ): Result<Unit>

    /** POST /api/sessions/kill. Requires confirm dialog upstream (ADR-0019). */
    public suspend fun killSession(sessionId: String): Result<Unit>

    /** POST /api/sessions/state. Force a session into a given state. */
    public suspend fun overrideSessionState(
        sessionId: String,
        state: SessionState,
    ): Result<Unit>

    /** GET /api/stats. */
    public suspend fun stats(): Result<StatsDto>

    /**
     * GET /api/observer/stats — richer observer payload that carries
     * the eBPF status block + cluster nodes (datawatch v4.4.0+ /
     * v4.5.0). Used by the v0.36.0 cluster-nodes + eBPF cards.
     */
    public suspend fun observerStats(): Result<com.dmzs.datawatchclient.transport.dto.ObserverStatsDto>

    /**
     * GET /api/observer/peers — federated peers list (Shape B / C /
     * Agent). datawatch v4.4.0+; S13 added the "agent" shape for
     * F10 ephemeral workers. Issue #2 + #6.
     */
    public suspend fun observerPeers(): Result<com.dmzs.datawatchclient.transport.dto.ObserverPeersDto>

    /**
     * GET /api/plugins — subprocess + native plugin list. The native
     * array (datawatch v4.2.0) carries in-process subsystems
     * (datawatch-observer + future bridges). Issue #5.
     */
    public suspend fun listPlugins(): Result<com.dmzs.datawatchclient.transport.dto.PluginsDto>

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

    // ---- v0.11 session power-user parity (see docs/plans/2026-04-20-v0.11-session-power-user.md) ----

    /** POST /api/sessions/rename — set a human-readable name on a session. */
    public suspend fun renameSession(
        sessionId: String,
        name: String,
    ): Result<Unit>

    /**
     * POST /api/sessions/restart — warm-resume a completed/failed session.
     * Returns the updated [Session] (state flips back to Running).
     */
    public suspend fun restartSession(sessionId: String): Result<Session>

    /**
     * POST /api/sessions/delete — parent-confirmation gate. The parent v3.0.0
     * openapi.yaml does not expose this endpoint today. Mobile sends the
     * expected `{"id": "..."}` body; callers receive [TransportError.NotFound]
     * if the server doesn't support it yet and grey out the UI control.
     */
    public suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * POST /api/sessions/delete with `{"ids": [...]}` body for bulk. Same
     * parent-confirmation gate as [deleteSession]; falls back to parallel
     * single-id calls at the caller's discretion if the server supports only
     * the single-id variant.
     */
    public suspend fun deleteSessions(sessionIds: List<String>): Result<Unit>

    /**
     * GET /api/cert — parent-confirmation gate. PEM-encoded CA cert bytes for
     * servers that use a self-signed TLS chain. Mobile hands this off to the
     * OS "Install a certificate" flow — we do not silently trust-anchor on
     * unrooted Android.
     */
    public suspend fun fetchCert(): Result<ByteArray>

    /**
     * POST /api/backends/active — parent-confirmation gate. Sets the active
     * LLM backend for new sessions on this server. UI is greyed out if this
     * returns [TransportError.NotFound].
     */
    public suspend fun setActiveBackend(name: String): Result<Unit>

    /** GET /api/alerts — returns the alerts list plus the unread count. */
    public suspend fun listAlerts(): Result<AlertsView>

    /**
     * POST /api/alerts — mark an alert read/dismissed. Pass [sessionId]=null
     * and [all]=true to dismiss every alert at once.
     */
    public suspend fun markAlertRead(
        alertId: String? = null,
        all: Boolean = false,
    ): Result<Unit>

    /**
     * GET /api/info — hostname, daemon version, active backends, session
     * count, bound server host+port. Used by the About card and
     * connection-status affordances.
     */
    public suspend fun fetchInfo(): Result<ServerInfo>

    /**
     * GET /api/sessions/timeline?id=<sessionId> — pipe-delimited
     * timeline lines: `"<ts> | <event> | <detail>"`. Mobile's
     * session-detail Timeline sheet prefers this over the client-side
     * WS-event-filter derivation whenever the server responds.
     */
    public suspend fun fetchTimeline(sessionId: String): Result<List<String>>

    /**
     * GET /api/ollama/models or /api/openwebui/models — returns a flat
     * array of model-name strings. [backend] must be "ollama" or
     * "openwebui"; any other value returns [TransportError.NotFound]
     * so callers can grey out the picker without special-casing.
     */
    public suspend fun listModels(backend: String): Result<List<String>>

    /**
     * GET /api/profiles — map of profile-name → profile object, each
     * carrying a `backend` field plus per-backend configuration.
     * Populates the profile picker on the New Session form so users
     * can pick an F10 ephemeral-agent profile at session start.
     */
    public suspend fun listProfiles(): Result<Map<String, kotlinx.serialization.json.JsonObject>>

    /**
     * PUT /api/config — write the full config document. Mobile callers
     * must fetch first (via [fetchConfig]), modify the relevant block
     * in memory, and send the whole document back; the parent replaces
     * the file wholesale. Guarded per ADR-0019: mobile only offers
     * structured field edits, never raw YAML.
     */
    public suspend fun writeConfig(
        raw: kotlinx.serialization.json.JsonObject,
    ): Result<Unit>

    /**
     * GET /api/logs?lines=<n>&offset=<m> — paged daemon log tail.
     * PWA-observed response: `{ lines: [...], total: N }`. [level]
     * optionally restricts to `info` / `warn` / `error`.
     */
    public suspend fun fetchLogs(
        lines: Int = 50,
        offset: Int = 0,
        level: String? = null,
    ): Result<LogsView>

    /**
     * POST /api/restart — daemon re-exec. Caller is responsible for
     * the confirm dialog (destructive-ish — every active session
     * briefly loses its WS connection during the re-exec).
     */
    public suspend fun restartDaemon(): Result<Unit>

    /**
     * POST /api/update — daemon self-update. PWA-observed response:
     * `{status: "up_to_date" | "installing" | ..., version: "…"}`.
     * Not in parent openapi.yaml today (undocumented but shipped —
     * see `internal/server/web/app.js` `runUpdate`). Returns the
     * raw JSON so the UI can pick the status string and branch on
     * "up_to_date" vs "installing".
     */
    public suspend fun updateDaemon(): Result<kotlinx.serialization.json.JsonObject>

    /**
     * GET /api/interfaces — read-only list of network interfaces the
     * daemon sees. Shape is loose (flags / ip / mac / mtu) so we
     * surface the raw JsonObject and let the UI pick fields.
     */
    public suspend fun listInterfaces(): Result<List<kotlinx.serialization.json.JsonObject>>

    /**
     * GET /api/memory/stats — episodic memory counters. Shape (PWA-
     * observed): `{enabled, total_count, manual_count, session_count,
     * learning_count, chunk_count, db_size_bytes}`.
     */
    public suspend fun memoryStats(): Result<kotlinx.serialization.json.JsonObject>

    /**
     * GET /api/memory/list?n=&role=&since= — browse stored memories.
     * Shape: array of `{id, role, content, created_at, similarity?}`.
     */
    public suspend fun memoryList(
        limit: Int = 50,
        role: String? = null,
        sinceIso: String? = null,
    ): Result<List<kotlinx.serialization.json.JsonObject>>

    /** GET /api/memory/search?q=<query> — semantic search over memories. */
    public suspend fun memorySearch(
        query: String,
    ): Result<List<kotlinx.serialization.json.JsonObject>>

    /** POST /api/memory/delete {id} — delete a single memory by id. */
    public suspend fun memoryDelete(id: Long): Result<Unit>

    /**
     * GET /api/memory/export — dump of every memory as a single
     * JSON/CSV/SQL blob (parent-negotiated). Returns the raw bytes
     * so the UI can hand them off to a SAF `ACTION_CREATE_DOCUMENT`
     * writer.
     */
    public suspend fun memoryExport(): Result<ByteArray>

    /**
     * GET /api/channels — list configured messaging channels with
     * their enabled state. Shape is per-channel `{id, type,
     * enabled, ...}` so the UI lists them with a toggle.
     */
    public suspend fun listChannels(): Result<List<kotlinx.serialization.json.JsonObject>>

    /**
     * POST /api/channels — create a new channel. Shipped upstream
     * in [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18)
     * on 2026-04-21. Server returns the created object so callers
     * can refresh their list without a second GET.
     */
    public suspend fun createChannel(
        type: String,
        id: String,
        enabled: Boolean,
        config: kotlinx.serialization.json.JsonObject? = null,
    ): Result<kotlinx.serialization.json.JsonObject>

    /**
     * DELETE /api/channels/{id} — remove a channel. Paired with
     * [createChannel] so the Add form has a symmetric Remove.
     */
    public suspend fun deleteChannel(channelId: String): Result<Unit>

    /**
     * PATCH /api/channels/{id} — flip a channel's enabled state.
     */
    public suspend fun setChannelEnabled(
        channelId: String,
        enabled: Boolean,
    ): Result<Unit>

    /**
     * POST /api/channel/send — fire a test-roundtrip message
     * through a named channel. Used by the Comms → "Send test"
     * button; lets users confirm the messaging backend is wired
     * before relying on it for alerts.
     */
    public suspend fun sendChannelTest(
        channelId: String,
        text: String,
    ): Result<Unit>

    /**
     * GET /api/servers — list of remote datawatch server
     * connections (federation peers) this server knows about.
     * Mobile uses this to render a read-only "Federated peers"
     * list under Settings → Comms; adding a peer is done via the
     * PWA config UI today.
     */
    public suspend fun listRemoteServers(): Result<List<kotlinx.serialization.json.JsonObject>>

    /**
     * GET /api/servers/health — per-peer health snapshot. Shape
     * is loose; PWA renders colour-coded dots per remote server.
     */
    public suspend fun listRemoteServerHealth(): Result<List<kotlinx.serialization.json.JsonObject>>

    /**
     * POST /api/stats/kill-orphans — kill orphaned tmux sessions
     * (those not tracked by datawatch). Returns `{killed: N,
     * orphaned_tmux: [...]}` or similar. Destructive; caller owns
     * the confirm dialog.
     */
    public suspend fun killOrphans(): Result<kotlinx.serialization.json.JsonObject>

    /**
     * POST /api/memory/test — end-to-end smoke test of the
     * memory subsystem (embedder + store + query round-trip).
     * Returns a status object; PWA shows success/fail toast.
     */
    public suspend fun memoryTest(): Result<kotlinx.serialization.json.JsonObject>

    /**
     * GET /api/filters — output / detection filter rules. Shape
     * (PWA-observed): `[{id, pattern, action, value, enabled}, ...]`.
     * `action` is one of `send_input`, `alert`, `schedule`,
     * `detect_prompt` per PWA `loadFilters`.
     */
    public suspend fun listFilters(): Result<List<kotlinx.serialization.json.JsonObject>>

    /** POST /api/filters — create a new filter rule. */
    public suspend fun createFilter(
        pattern: String,
        action: String,
        value: String? = null,
        enabled: Boolean = true,
    ): Result<Unit>

    /**
     * PATCH /api/filters — toggle or edit an existing filter. Nulls
     * preserve the server-side value so callers can send a partial
     * update (e.g. just flip `enabled`).
     */
    public suspend fun updateFilter(
        id: String,
        pattern: String? = null,
        action: String? = null,
        value: String? = null,
        enabled: Boolean? = null,
    ): Result<Unit>

    /** DELETE /api/filters?id=<id>. */
    public suspend fun deleteFilter(id: String): Result<Unit>

    /**
     * GET /api/mcp/docs — MCP tool catalogue. Returns an object of
     * tool groups or a flat array depending on parent version;
     * mobile viewer renders whatever structured form comes back.
     */
    public suspend fun fetchMcpDocs(): Result<kotlinx.serialization.json.JsonElement>

    /**
     * GET /api/profiles/<kind>s — list project or cluster profiles.
     * [kind] is `"project"` or `"cluster"`. Returns the raw
     * `profiles` array from the `{profiles: [...]}` response so
     * callers can render fields as the schema evolves.
     */
    public suspend fun listKindProfiles(
        kind: String,
    ): Result<List<kotlinx.serialization.json.JsonObject>>

    /** DELETE /api/profiles/<kind>s/<name>. */
    public suspend fun deleteKindProfile(
        kind: String,
        name: String,
    ): Result<Unit>

    /**
     * POST /api/profiles/<kind>s/<name>/smoke — validation round-trip.
     * Returns a status object; PWA surfaces success/error as a toast.
     */
    public suspend fun smokeKindProfile(
        kind: String,
        name: String,
    ): Result<kotlinx.serialization.json.JsonObject>

    /**
     * PUT /api/profiles/<kind>s/<name> — create or update a profile.
     * [body] is the full profile object per PWA schema (image_pair,
     * git, memory, kubernetes, etc.). Mobile MVP ships a minimal
     * name + description + kind editor; full nested-field editing
     * stays on the PWA for now.
     */
    public suspend fun putKindProfile(
        kind: String,
        name: String,
        body: kotlinx.serialization.json.JsonObject,
    ): Result<Unit>

    /**
     * GET /api/output?id=<sessionId>&n=<lines> — last N lines of a session's
     * PTY output as plain text. Useful as a backlog pager for sessions that
     * predate the current WebSocket subscription. [lines] clamped server-side
     * to 1000; client passes through without extra clamping.
     */
    public suspend fun fetchOutput(
        sessionId: String,
        lines: Int = 500,
    ): Result<String>

    // ---- v0.12 schedules + files + saved commands + config (read) ----
    // (see docs/plans/2026-04-20-v0.12-schedules-files-config.md)

    /**
     * GET /api/schedules — list scheduled commands on this server. Filters
     * ([sessionId], [state]) correspond to the query params the PWA uses:
     *  - `sessionId` scopes to schedules tied to one session (populates the
     *    per-session "Scheduled" strip in session detail);
     *  - `state` filters by schedule state (e.g. `"pending"`).
     *
     * Both params are optional; passing none returns every schedule.
     */
    public suspend fun listSchedules(
        sessionId: String? = null,
        state: String? = null,
    ): Result<List<Schedule>>

    /**
     * POST /api/schedules — create a scheduled command. [sessionId] attaches
     * the schedule to a session so it shows up in that session's strip.
     */
    public suspend fun createSchedule(
        task: String,
        cron: String,
        enabled: Boolean = true,
        sessionId: String? = null,
    ): Result<Schedule>

    /** DELETE /api/schedules?id=<id> — cancel a scheduled command. */
    public suspend fun deleteSchedule(scheduleId: String): Result<Unit>

    /**
     * GET /api/files?path=<path> — directory listing for the file picker.
     * [path] null lists the server's default root (whatever the daemon
     * chooses to expose; typically the user's home).
     */
    public suspend fun browseFiles(path: String? = null): Result<FileList>

    /**
     * POST /api/files with `{path, action: "mkdir"}` — create a new
     * folder server-side from inside the file picker. Mirrors PWA
     * v5.26.46's "+ New folder" affordance (issue #14).
     */
    public suspend fun mkdir(path: String): Result<Unit>

    /** GET /api/commands — list saved command snippets. */
    public suspend fun listCommands(): Result<List<SavedCommand>>

    /** POST /api/commands — save or update a named command snippet. */
    public suspend fun saveCommand(
        name: String,
        command: String,
    ): Result<Unit>

    /** DELETE /api/commands?name=<name> — remove a saved command snippet. */
    public suspend fun deleteCommand(name: String): Result<Unit>

    /**
     * GET /api/config — masked daemon config. Sensitive fields arrive as
     * "***"; we render them verbatim. PUT is deliberately out of scope until
     * a structured form lands per ADR-0019.
     */
    public suspend fun fetchConfig(): Result<ConfigView>
}

/**
 * Combined view returned by [TransportClient.listAlerts] — the list plus the
 * server's authoritative unread count (which may differ from the list if
 * alerts were trimmed by pagination).
 */
public data class AlertsView(
    val alerts: List<Alert>,
    val unreadCount: Int,
)

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

/**
 * Paginated daemon-log tail — PWA-observed shape:
 * `{ lines: ["…"], total: <int> }`. [total] is the full log length so
 * the UI can render a "Showing N of M" footer and paginate.
 */
public data class LogsView(
    val lines: List<String>,
    val total: Int,
)

public enum class DeviceKind(public val wire: String) { Fcm("fcm"), Ntfy("ntfy") }

public enum class DevicePlatform(public val wire: String) { Android("android"), Ios("ios") }
