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
import com.dmzs.datawatchclient.transport.dto.MatrixStatusDto
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
        permissionMode: String? = null,
        model: String? = null,
        claudeEffort: String? = null,
        /** v7 LLM registry name override. */
        llm: String? = null,
        /** v7 Compute Node override. */
        computeNode: String? = null,
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
     * GET /api/plugins/browse?registry=<name> — browse plugins from a registry.
     * Defaults to "community" (the pre-seeded community registry).
     */
    public suspend fun browsePlugins(registry: String = "community"): Result<com.dmzs.datawatchclient.transport.dto.CommunityPluginsBrowseDto>

    /**
     * POST /api/plugins/install — install a plugin from a registry.
     * body: `{"registry":"community","name":"<name>"}`
     */
    public suspend fun installPlugin(registry: String, name: String): Result<com.dmzs.datawatchclient.transport.dto.PluginInstallResponseDto>

    /**
     * GET /api/backends — list of registered LLM backends + which is active.
     * Only returns backends where `enabled != false` (mirrors PWA renderBackendSelect
     * filter). The `shell` non-LLM backend is also excluded.
     */
    public suspend fun listBackends(): Result<BackendsView>

    /**
     * GET /api/ollama/models — model names available on the connected Ollama instance.
     * Returns an empty list when the endpoint 404s (Ollama not configured).
     */
    public suspend fun listOllamaModels(): Result<List<String>>

    /**
     * GET /api/openwebui/models — model IDs available on the connected Open WebUI.
     * Returns an empty list when the endpoint 404s (Open WebUI not configured).
     */
    public suspend fun listOpenWebUiModels(): Result<List<String>>

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
     * GET /api/llm/claude/{models,efforts,permission_modes} — static
     * lists for the claude-code "Advanced options" block (v5.27.5+).
     * Returns [TransportError.NotFound] on daemons that predate v5.27.5
     * so callers can hide the block without special-casing.
     */
    public suspend fun listClaudeModels(): Result<List<String>>
    public suspend fun listClaudeEfforts(): Result<List<String>>
    public suspend fun listClaudePermissionModes(): Result<List<String>>

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
    public suspend fun writeConfig(raw: kotlinx.serialization.json.JsonObject): Result<Unit>

    /**
     * Convenience: PUT /api/config `{"whisper.language": code}`.
     * Only called when the user selects a concrete locale in Settings → About;
     * selecting "auto" must NOT call this (preserves existing server config).
     */
    public suspend fun setWhisperLanguage(code: String): Result<Unit> =
        writeConfig(
            kotlinx.serialization.json.buildJsonObject {
                put("whisper.language", kotlinx.serialization.json.JsonPrimitive(code))
            },
        )

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
     * GET /api/update/check — check-only endpoint shipped in datawatch
     * v5.27.4. Returns `{status: "up_to_date"|"update_available", version}`.
     * Callers must 404-fallback to [updateDaemon] (POST) for older daemons.
     */
    public suspend fun checkUpdate(): Result<kotlinx.serialization.json.JsonObject>

    /**
     * POST /api/reload?subsystem=<name> — hot-reload a subsystem without
     * restarting the daemon. [subsystem] is one of `config`, `filters`,
     * `memory`. Response: `{ok, subsystem, applied[], requires_restart[]}`.
     */
    public suspend fun reloadSubsystem(subsystem: String): Result<kotlinx.serialization.json.JsonObject>

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
    public suspend fun memorySearch(query: String): Result<List<kotlinx.serialization.json.JsonObject>>

    /** POST /api/memory/delete {id} — delete a single memory by id. */
    public suspend fun memoryDelete(id: Long): Result<Unit>

    // ---- v0.37.0 mempalace surfaces (datawatch v5.27.0 / #21) ----

    /**
     * POST /api/memory/pin `{id, pinned}` — toggle the always-surface
     * flag so the entry sticks in L1 retrieval regardless of recency
     * decay. Idempotent.
     */
    public suspend fun memoryPin(
        id: Long,
        pinned: Boolean,
    ): Result<Unit>

    /**
     * POST /api/memory/sweep_stale `{older_than_days, dry_run}` —
     * similarity-stale eviction. Returns the count of entries that
     * would be (or were) removed; the daemon runs dry by default.
     */
    public suspend fun memorySweepStale(
        olderThanDays: Int,
        dryRun: Boolean = true,
    ): Result<Int>

    /**
     * POST /api/memory/spellcheck `{text, extra_words}` — Levenshtein
     * suggestions; never rewrites the input. Returns a list of
     * `{word, suggestions[]}` pairs.
     */
    public suspend fun memorySpellcheck(
        text: String,
        extraWords: List<String> = emptyList(),
    ): Result<List<com.dmzs.datawatchclient.transport.dto.SpellcheckSuggestionDto>>

    /**
     * POST /api/memory/extract_facts `{text}` — heuristic SVO triple
     * extraction. Returns `[{subject, verb, object}]`.
     */
    public suspend fun memoryExtractFacts(
        text: String,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.SvoTripleDto>>

    /**
     * GET /api/memory/wakeup — agent-state hydration on session
     * start. Returns the wakeup body the daemon would hand a fresh
     * agent for the supplied (project_dir, agent_id, parent_*).
     */
    public suspend fun memoryWakeup(
        projectDir: String? = null,
        agentId: String? = null,
        parentAgentId: String? = null,
        parentName: String? = null,
    ): Result<String>

    /**
     * POST /api/memory/remember `{text, role?, tags?}` — manually store a
     * memory entry. Returns the stored memory object. Role defaults to
     * "manual" when omitted.
     */
    public suspend fun memoryRemember(
        text: String,
        role: String = "manual",
        tags: List<String> = emptyList(),
    ): Result<kotlinx.serialization.json.JsonObject>

    // ---- v0.38.0 autonomous PRD lifecycle (datawatch BL191) ----

    /** GET /api/autonomous/prds — list PRDs (issue #11–13). */
    public suspend fun listPrds(): Result<com.dmzs.datawatchclient.transport.dto.PrdListDto>

    /** POST /api/autonomous/prds — create a new PRD (issue #11). */
    public suspend fun createPrd(request: com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto): Result<String>

    /** POST /api/autonomous/prds/{id}/{action} — approve|reject|request_revision|cancel|instantiate. */
    public suspend fun prdAction(
        prdId: String,
        action: String,
        body: kotlinx.serialization.json.JsonObject? = null,
    ): Result<Unit>

    /**
     * POST /api/autonomous/prds/{id}/edit_story — story title +
     * description edit while parent PRD is in needs_review or
     * revisions_asked. Empty `new_description` preserves existing.
     * Issue #12.
     */
    public suspend fun editStory(
        prdId: String,
        storyId: String,
        newTitle: String? = null,
        newDescription: String? = null,
        actor: String? = null,
    ): Result<Unit>

    /**
     * POST /api/autonomous/prds/{id}/edit_files — story or task
     * files-list edit. Pass story_id OR task_id, never both.
     * Issue #19.
     */
    public suspend fun editFiles(
        prdId: String,
        storyId: String? = null,
        taskId: String? = null,
        files: List<String> = emptyList(),
        actor: String? = null,
    ): Result<Unit>

    /**
     * PATCH /api/autonomous/prds/{id} — update PRD title and/or spec
     * on any non-running PRD (PWA v5.19.0 openPRDEditModal).
     */
    public suspend fun patchPrd(
        prdId: String,
        title: String? = null,
        spec: String? = null,
        permissionMode: String? = null,
    ): Result<Unit>

    /**
     * DELETE /api/autonomous/prds/{id} — cancel (hard=false) or hard-delete
     * (hard=true, removes descendants). PWA: cancel = bare DELETE, hard-delete
     * = DELETE ?hard=true.
     */
    public suspend fun deletePrd(
        prdId: String,
        hard: Boolean = false,
    ): Result<Unit>

    // ---- v0.63.0 BL221 Phase 4: Type registry + Guided Mode + Skills ----

    /** GET /api/autonomous/types — list registered automata types. */
    public suspend fun listAutomataTypes(): Result<List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto>>

    /** POST /api/autonomous/types — register a new automata type. */
    public suspend fun registerAutomataType(
        req: com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto>

    /** DELETE /api/autonomous/types/{id} — delete a registered type. */
    public suspend fun deleteAutomataType(id: String): Result<Unit>

    /** POST /api/autonomous/prds/{id}/set_type. */
    public suspend fun setPrdType(prdId: String, type: String): Result<Unit> =
        prdAction(prdId, "set_type", kotlinx.serialization.json.buildJsonObject { put("type", kotlinx.serialization.json.JsonPrimitive(type)) })

    /** POST /api/autonomous/prds/{id}/set_guided_mode. */
    public suspend fun setPrdGuidedMode(prdId: String, guidedMode: Boolean): Result<Unit> =
        prdAction(prdId, "set_guided_mode", kotlinx.serialization.json.buildJsonObject { put("guided_mode", kotlinx.serialization.json.JsonPrimitive(guidedMode)) })

    /** POST /api/autonomous/prds/{id}/set_skills. */
    public suspend fun setPrdSkills(prdId: String, skills: List<String>): Result<Unit> =
        prdAction(
            prdId,
            "set_skills",
            kotlinx.serialization.json.buildJsonObject {
                put("skills", kotlinx.serialization.json.buildJsonArray { skills.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            },
        )

    // ---- v0.62.0 BL221 Phase 3: Security scan (datawatch v6.2.0) ----

    /** POST /api/autonomous/prds/{id}/scan — trigger a security scan. */
    public suspend fun triggerScan(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.ScanResultDto>

    /** GET /api/autonomous/prds/{id}/scan — fetch the latest scan result. */
    public suspend fun getScanResult(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.ScanResultDto>

    /** POST /api/autonomous/prds/{id}/fix_prd — create a child PRD to fix scan findings. */
    public suspend fun createFixPrd(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.PrdDto>

    /** POST /api/autonomous/prds/{id}/propose_rules — propose lint/security rules from findings. */
    public suspend fun proposeRules(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.RuleProposalDto>

    /** GET /api/autonomous/scan_config — fetch global scan configuration. */
    public suspend fun getScanConfig(): Result<com.dmzs.datawatchclient.transport.dto.ScanConfigDto>

    /** PUT /api/autonomous/scan_config — update global scan configuration. */
    public suspend fun updateScanConfig(config: com.dmzs.datawatchclient.transport.dto.ScanConfigDto): Result<Unit>

    // ---- v0.61.0 BL221 Phase 2: Template Store (datawatch v6.2.0) ----

    /** GET /api/autonomous/templates — list all templates. */
    public suspend fun listTemplates(): Result<com.dmzs.datawatchclient.transport.dto.TemplateListDto>

    /** POST /api/autonomous/templates — create a new template. */
    public suspend fun createTemplate(
        req: com.dmzs.datawatchclient.transport.dto.CreateTemplateRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.TemplateDto>

    /** GET /api/autonomous/templates/{id} — fetch a single template. */
    public suspend fun getTemplate(id: String): Result<com.dmzs.datawatchclient.transport.dto.TemplateDto>

    /** PUT /api/autonomous/templates/{id} — update a template. */
    public suspend fun updateTemplate(
        id: String,
        req: com.dmzs.datawatchclient.transport.dto.UpdateTemplateRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.TemplateDto>

    /** DELETE /api/autonomous/templates/{id} — delete a template. */
    public suspend fun deleteTemplate(id: String): Result<Unit>

    /** POST /api/autonomous/templates/{id}/instantiate — create a PRD from a template. */
    public suspend fun instantiateTemplate(
        id: String,
        req: com.dmzs.datawatchclient.transport.dto.InstantiateTemplateRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.PrdDto>

    /** POST /api/autonomous/prds/{id}/clone_to_template — save a PRD's spec as a template. */
    public suspend fun clonePrdToTemplate(
        prdId: String,
        req: com.dmzs.datawatchclient.transport.dto.ClonePrdToTemplateRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.TemplateDto>

    /**
     * GET /api/orchestrator/graphs/{id} — PRD-DAG graph with
     * per-node observer_summary (datawatch v4.7.0 / S13). Issue #7.
     */
    public suspend fun orchestratorGraph(
        id: String,
    ): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto>

    /**
     * POST /api/agents — start an F10 ephemeral-agent session.
     * Distinct from [startSession] (`POST /api/sessions/start`) in
     * that the worker LLM is carried by the project profile's
     * `image_pair` rather than a flat `backend` field. Issue #20
     * (PWA v5.26.63 unified-Profile dropdown routing).
     */
    public suspend fun startAgent(request: com.dmzs.datawatchclient.transport.dto.StartAgentRequestDto): Result<String>

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
     * GET /api/channel/info — MCP channel bridge status.
     * Returns an object with `kind` ("go"/"js"), `ready` bool,
     * `path`, and optional `stale` list of outdated .mcp.json paths.
     */
    public suspend fun fetchChannelInfo(): Result<kotlinx.serialization.json.JsonElement>

    /**
     * GET /api/profiles/<kind>s — list project or cluster profiles.
     * [kind] is `"project"` or `"cluster"`. Returns the raw
     * `profiles` array from the `{profiles: [...]}` response so
     * callers can render fields as the schema evolves.
     */
    public suspend fun listKindProfiles(kind: String): Result<List<kotlinx.serialization.json.JsonObject>>

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

    /**
     * GET /api/config → quick_commands array.
     *
     * datawatch#28 added a `quick_commands` key to /api/config — a list of
     * {label, value} objects the client renders in the system-commands section
     * of QuickCommandsSheet. Returns an empty list on 404 or when the key is
     * absent so older daemons fall back to the client hard-coded defaults.
     */
    public suspend fun fetchSystemQuickCommands(): Result<List<QuickCommandItem>>

    // ------ BL21: Signal device-linking (datawatch#31) ------

    /** GET /api/link/qr (SSE) — stream base64 QR frames while pairing is in progress. */
    public fun startSignalLinking(): Flow<com.dmzs.datawatchclient.transport.dto.LinkQrFrameDto>

    /** GET /api/link/status — current paired-device state. */
    public suspend fun getSignalLinkStatus(): Result<com.dmzs.datawatchclient.transport.dto.SignalLinkStatusDto>

    /** POST /api/link/cancel — abort an in-progress pairing session. */
    public suspend fun cancelSignalLink(): Result<Unit>

    /** DELETE /api/link/{deviceId} — unlink a previously paired device. */
    public suspend fun unlinkSignalDevice(deviceId: String): Result<Unit>

    // ---- v0.66.0 BL255: Skill Registries (datawatch v6.7.0) ----

    /** GET /api/skills/registries — list all skill registries. */
    public suspend fun listSkillRegistries(): Result<List<com.dmzs.datawatchclient.transport.dto.SkillRegistryDto>>

    /** POST /api/skills/registries — add a new registry. */
    public suspend fun createSkillRegistry(
        req: com.dmzs.datawatchclient.transport.dto.SkillRegistryRequestDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.SkillRegistryDto>

    /** PUT /api/skills/registries/{name} — update a registry. */
    public suspend fun updateSkillRegistry(
        name: String,
        req: com.dmzs.datawatchclient.transport.dto.SkillRegistryUpdateDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.SkillRegistryDto>

    /** DELETE /api/skills/registries/{name} — delete a registry. */
    public suspend fun deleteSkillRegistry(name: String): Result<Unit>

    /** POST /api/skills/registries/add-default — add the built-in PAI registry. */
    public suspend fun addDefaultSkillRegistry(): Result<com.dmzs.datawatchclient.transport.dto.SkillRegistryDto>

    /** POST /api/skills/registries/{name}/connect — fetch remote manifest. */
    public suspend fun connectSkillRegistry(name: String): Result<com.dmzs.datawatchclient.transport.dto.SkillRegistryDto>

    /** GET /api/skills/registries/{name}/available — list skills available before sync. */
    public suspend fun listAvailableSkills(name: String): Result<List<com.dmzs.datawatchclient.transport.dto.AvailableSkillDto>>

    /** POST /api/skills/registries/{name}/sync — sync selected skills. */
    public suspend fun syncSkills(
        name: String,
        req: com.dmzs.datawatchclient.transport.dto.SyncSkillsRequestDto,
    ): Result<Unit>

    /** POST /api/skills/registries/{name}/unsync — remove synced skills. */
    public suspend fun unsyncSkills(
        name: String,
        req: com.dmzs.datawatchclient.transport.dto.SyncSkillsRequestDto,
    ): Result<Unit>

    /** GET /api/skills — list all synced skills across registries. */
    public suspend fun listSyncedSkills(): Result<List<com.dmzs.datawatchclient.transport.dto.SkillDto>>

    // ---- v0.74.0 Compute Nodes (S5-1) ----

    /** GET /api/compute/nodes — list all registered compute nodes. */
    public suspend fun listComputeNodes(): Result<List<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto>>

    /** POST /api/compute/nodes — create a new compute node. */
    public suspend fun createComputeNode(
        dto: com.dmzs.datawatchclient.transport.dto.ComputeNodeDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto>

    /** PUT /api/compute/nodes/{name} — update an existing compute node. */
    public suspend fun updateComputeNode(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.ComputeNodeDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto>

    /** DELETE /api/compute/nodes/{name} — remove a compute node. */
    public suspend fun deleteComputeNode(name: String): Result<Unit>

    /**
     * GET /api/compute/nodes/{name}/models?kind={kind} — list models
     * available on the given compute node for the specified LLM kind.
     */
    public suspend fun getComputeNodeModels(name: String, kind: String): Result<List<String>>

    // ---- v0.74.0 LLM Registry (S5-2) ----

    /** GET /api/llms — list all registered LLM entries. */
    public suspend fun listLlms(): Result<List<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>>

    /** POST /api/llms — create a new LLM registry entry. */
    public suspend fun createLlm(
        dto: com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>

    /** PUT /api/llms/{name} — update an existing LLM registry entry. */
    public suspend fun updateLlm(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>

    /** DELETE /api/llms/{name} — remove an LLM registry entry. */
    public suspend fun deleteLlm(name: String): Result<Unit>

    /**
     * PATCH /api/llms/{name}/enabled — enable or disable an LLM and
     * optionally toggle pretest mode.
     */
    public suspend fun enableLlm(
        name: String,
        enabled: Boolean,
        pretest: Boolean = false,
    ): Result<Unit>

    // ---- v0.74.0 Migration (S5-3) ----

    /** GET /api/migration/status — check if v7 auto-migration ran. */
    public suspend fun getMigrationStatus(): Result<com.dmzs.datawatchclient.transport.dto.MigrationStatusDto>

    /** DELETE /api/migration/status — dismiss the migration notice. */
    public suspend fun dismissMigration(): Result<Unit>

    // ---- v0.84.0 Sprint 15 — migration + observer binding ----

    /** GET /api/migration/compute-kinds — list nodes needing kind migration. */
    public suspend fun getMigrationComputeKinds(): Result<com.dmzs.datawatchclient.transport.dto.MigrationComputeKindsDto>

    /** PUT /api/migration/compute-kinds/{name} — migrate a compute node to a new kind. */
    public suspend fun migrateComputeNodeKind(name: String, kind: String): Result<Unit>

    /** PATCH /api/compute/nodes/{name}/enabled — enable or disable a compute node. */
    public suspend fun toggleComputeNodeEnabled(name: String, enabled: Boolean): Result<Unit>

    /** GET /api/observer/peers/free — list observer peers not yet bound to any compute node. */
    public suspend fun getFreePeers(): Result<List<com.dmzs.datawatchclient.transport.dto.FreeObserverPeerDto>>

    /** POST /api/compute/nodes/{name}/observer-peer — bind an observer peer to a compute node. */
    public suspend fun attachObserverPeer(nodeName: String, peer: String): Result<Unit>

    /** DELETE /api/compute/nodes/{name}/observer-peer — remove the observer peer binding from a compute node. */
    public suspend fun detachObserverPeer(nodeName: String): Result<Unit>

    // ---- v0.75.0 Vault/Secrets + Docs Search (S6-3, S6-4 BL274) ----

    /** GET /api/secrets/status — active secrets/vault backend + reachability. */
    public suspend fun getSecretsStatus(): Result<com.dmzs.datawatchclient.transport.dto.SecretsStatusDto>

    /** GET /api/docs/search?q=…&limit=N — full-text documentation search. */
    public suspend fun docsSearch(
        q: String,
        limit: Int = 10,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.DocsSearchResultDto>>

    /** GET /api/docs/trust/pending — list sources awaiting trust approval. */
    public suspend fun docsPendingList(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsPendingSourceDto>>

    /** POST /api/docs/trust/accept — bulk-accept pending sources. */
    public suspend fun docsTrustAccept(paths: List<String>): Result<Unit>

    /** POST /api/docs/trust/dismiss — bulk-dismiss pending sources. */
    public suspend fun docsTrustDismiss(paths: List<String>): Result<Unit>

    /** GET /api/docs/trust — list currently trusted sources. */
    public suspend fun docsTrustedList(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsTrustedSourceDto>>

    /** DELETE /api/docs/trust/{path} — remove trust from a source. */
    public suspend fun docsTrustRemove(path: String): Result<Unit>

    /** GET /api/docs/howtos — list how-to guides with exec metadata. */
    public suspend fun docsListHowtos(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsHowtoDto>>

    /** POST /api/docs/trust/add — add a new docs source to the trust list. */
    public suspend fun docsTrustAdd(source: String): Result<Unit>

    // ---- v0.73.0 Sprint 4: Identity, Algorithm Mode, Evals ----

    /** GET /api/identity — fetch the server's identity profile. */
    public suspend fun getIdentity(): Result<com.dmzs.datawatchclient.transport.dto.IdentityDto>

    /** PUT /api/identity — update the server's identity profile. */
    public suspend fun setIdentity(dto: com.dmzs.datawatchclient.transport.dto.IdentityDto): Result<com.dmzs.datawatchclient.transport.dto.IdentityDto>

    /** GET /api/algorithm — list active algorithm-mode sessions. */
    public suspend fun algorithmList(): Result<List<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>>

    /** PATCH /api/algorithm/{sessionId} with action=advance. */
    public suspend fun algorithmAdvance(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** PATCH /api/algorithm/{sessionId} with action=abort. */
    public suspend fun algorithmAbort(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** POST /api/algorithm/{sessionId} — register session in Algorithm Mode from Observe. (BL258) */
    public suspend fun algorithmStart(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** GET /api/algorithm/{sessionId} — read one session's algorithm state. (BL258) */
    public suspend fun algorithmGet(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** PATCH /api/algorithm/{sessionId} with action=reset — discard state, restart from Observe. (BL258) */
    public suspend fun algorithmReset(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** PATCH /api/algorithm/{sessionId} with action=edit — replace most-recent phase output. (BL258) */
    public suspend fun algorithmEdit(sessionId: String, output: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** PATCH /api/algorithm/{sessionId} with action=measure — bridge Measure phase to Evals. (BL259) */
    public suspend fun algorithmMeasure(sessionId: String, suite: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>

    /** GET /api/evals — list eval suites. */
    public suspend fun evalsList(): Result<List<com.dmzs.datawatchclient.transport.dto.EvalSuiteDto>>

    /** POST /api/evals/{suiteId}/run — trigger an eval run. */
    public suspend fun evalsRun(suiteId: String): Result<com.dmzs.datawatchclient.transport.dto.EvalRunResultDto>

    // ---- v0.77.0 Council persona wizard (S8-1/2/3, #92) ----

    /** GET /api/council/personas — list all council personas. */
    public suspend fun councilListPersonas(): Result<List<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto>>

    /** GET /api/council/runs — list council runs. */
    public suspend fun councilListRuns(): Result<List<com.dmzs.datawatchclient.transport.dto.CouncilRunDto>>

    /** GET /api/council/config — fetch council configuration. */
    public suspend fun councilGetConfig(): Result<com.dmzs.datawatchclient.transport.dto.CouncilConfigDto>

    /** PUT /api/council/config — update council configuration. */
    public suspend fun councilUpdateConfig(config: com.dmzs.datawatchclient.transport.dto.CouncilConfigDto): Result<com.dmzs.datawatchclient.transport.dto.CouncilConfigDto>

    /** POST /api/council/run — start a council run. */
    public suspend fun councilStartRun(request: com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest): Result<com.dmzs.datawatchclient.transport.dto.CouncilRunDto>

    /** DELETE /api/council/runs/{id} — stop/cancel a council run. */
    public suspend fun councilStopRun(id: String): Result<Unit>

    /** POST /api/council/personas — create a new council persona. */
    public suspend fun createCouncilPersona(
        dto: com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto>

    /** PUT /api/council/personas/{name} — update an existing council persona. */
    public suspend fun updateCouncilPersona(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto>

    // Sprint 31 — alpha.39/40 Council persona built-in support + delete
    /** GET /api/council/personas/{name} — fetch a single persona (built-in or custom). */
    public suspend fun getCouncilPersona(name: String): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto>

    /** PUT /api/council/personas/{name} — create or overwrite a persona using the full DTO. */
    public suspend fun setCouncilPersona(persona: com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto): Result<Unit>

    /** DELETE /api/council/personas/{name} — delete a custom (non-builtin) persona. */
    public suspend fun deleteCouncilPersona(name: String): Result<Unit>

    // ---- v0.80.0 Sprint 11: Cost Rates, Routing Rules, Tailscale Mesh ----

    /** GET /api/cost/rates — per-backend token cost rates. */
    public suspend fun getCostRates(): Result<com.dmzs.datawatchclient.transport.dto.CostRatesDto>

    /** POST /api/cost/rates — save per-backend token cost rates. */
    public suspend fun saveCostRates(
        rates: Map<String, com.dmzs.datawatchclient.transport.dto.CostRateDto>,
    ): Result<Unit>

    /** GET /api/routing-rules — list LLM routing rules. */
    public suspend fun getRoutingRules(): Result<com.dmzs.datawatchclient.transport.dto.RoutingRulesDto>

    /** POST /api/routing-rules — replace full routing-rules list. */
    public suspend fun setRoutingRules(
        rules: List<com.dmzs.datawatchclient.transport.dto.RoutingRuleDto>,
    ): Result<com.dmzs.datawatchclient.transport.dto.RoutingRulesDto>

    /** POST /api/routing-rules/test — test which backend a task would route to. */
    public suspend fun testRouting(task: String): Result<com.dmzs.datawatchclient.transport.dto.RoutingTestResultDto>

    /** GET /api/tailscale/status — Tailscale mesh status. */
    public suspend fun getTailscaleStatus(): Result<com.dmzs.datawatchclient.transport.dto.TailscaleStatusDto>

    // ---- v0.81.0 Sprint 12: Pipelines + OrchestratorGraphs list ----

    /** GET /api/pipelines — list active pipelines. */
    public suspend fun getPipelines(): Result<List<com.dmzs.datawatchclient.transport.dto.PipelineListItemDto>>

    /** GET /api/orchestrator/graphs — list orchestrator graphs. */
    public suspend fun getOrchestratorGraphsList(): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphsListDto>

    /** POST /api/orchestrator/graphs — create a new orchestrator graph. */
    public suspend fun createOrchestratorGraph(title: String, directory: String = "", prdIds: List<String> = emptyList()): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphListItemDto>

    /** POST /api/orchestrator/graphs/{id}/run — run an orchestrator graph. */
    public suspend fun runOrchestratorGraph(id: String): Result<Unit>

    /** DELETE /api/orchestrator/graphs/{id} — delete an orchestrator graph. */
    public suspend fun deleteOrchestratorGraph(id: String): Result<Unit>

    // ---- v0.82.0 Sprint 13: General tab gaps ----

    /** GET /api/templates — list session templates. */
    public suspend fun getSessionTemplates(): Result<List<com.dmzs.datawatchclient.transport.dto.SessionTemplateDto>>

    /** POST /api/templates — create a new session template. */
    public suspend fun createSessionTemplate(
        template: com.dmzs.datawatchclient.transport.dto.SessionTemplateDto,
    ): Result<Unit>

    /** DELETE /api/templates/{name} — delete a session template. */
    public suspend fun deleteSessionTemplate(name: String): Result<Unit>

    /** GET /api/device-aliases — list device aliases. */
    public suspend fun getDeviceAliases(): Result<List<com.dmzs.datawatchclient.transport.dto.DeviceAliasDto>>

    /** POST /api/device-aliases — create a device alias. */
    public suspend fun createDeviceAlias(alias: String, server: String): Result<Unit>

    /** DELETE /api/device-aliases/{alias} — delete a device alias. */
    public suspend fun deleteDeviceAlias(alias: String): Result<Unit>

    /** GET /api/tooling/status — backend artifact lifecycle status. */
    public suspend fun getToolingStatus(): Result<com.dmzs.datawatchclient.transport.dto.ToolingStatusDto>

    /** POST /api/tooling/gitignore — add artifact dirs to .gitignore. */
    public suspend fun toolingGitignore(backend: String): Result<Unit>

    /** POST /api/tooling/cleanup — remove artifact dirs. */
    public suspend fun toolingCleanup(backend: String): Result<Unit>

    /** GET /api/secrets — list secrets (name/metadata, no values). */
    public suspend fun getSecrets(): Result<com.dmzs.datawatchclient.transport.dto.SecretsListDto>

    /** POST /api/secrets — add a secret. */
    public suspend fun addSecret(
        secret: com.dmzs.datawatchclient.transport.dto.AddSecretDto,
    ): Result<Unit>

    /** DELETE /api/secrets/{name} — delete a secret. */
    public suspend fun deleteSecret(name: String): Result<Unit>

    // ---- v0.88.0 Sprint 19: Observer by-node grouping + federation meta-peers ----

    /** GET /api/observer/peers/by-node — local peers grouped by bound ComputeNode (alpha.24 #231). */
    public suspend fun getObserverPeersByNode(): Result<com.dmzs.datawatchclient.transport.dto.ObserverPeersByNodeDto>

    /** GET /api/federation/meta-peers — cross-instance peers aggregated by ComputeNode (alpha.24 #231). */
    public suspend fun getFederationMetaPeers(): Result<com.dmzs.datawatchclient.transport.dto.MetaPeersDto>

    // ---- v0.89.0 Sprint 20: opencode multi-select models (alpha.28 #243) ----

    /**
     * PATCH /api/profiles/projects/{name}/agent-settings — update the AgentSettings
     * block for a project profile (BL251 / alpha.28 #243).
     */
    public suspend fun patchProjectAgentSettings(
        name: String,
        settings: com.dmzs.datawatchclient.transport.dto.AgentSettingsDto,
    ): Result<Unit>

    // Sprint 26 — alpha.34 session status board
    public suspend fun getSessionStatus(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto>

    // Sprint 27 — alpha.33 Ollama marketplace
    public suspend fun getInstalledOllamaModels(nodeId: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaInstalledModelsDto>
    public suspend fun getOllamaCatalog(): Result<com.dmzs.datawatchclient.transport.dto.OllamaCatalogDto>
    public suspend fun pullOllamaModel(nodeId: String, model: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto>
    public suspend fun getPullTask(taskId: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto>
    public suspend fun deleteOllamaModel(nodeId: String, model: String): Result<Unit>

    // Sprint 28 — alpha.35 UnifiedPush SSE
    public suspend fun registerPush(registration: com.dmzs.datawatchclient.transport.dto.PushRegistrationDto): Result<Unit>
    public fun subscribePushAlerts(): Flow<com.dmzs.datawatchclient.transport.dto.PushEventDto>

    // Sprint 30 — LLM multi-node + session management
    public suspend fun getLlmSessions(name: String, page: Int = 1, size: Int = 10): Result<com.dmzs.datawatchclient.transport.dto.LlmSessionsDto>
    public suspend fun reassignLlmSessions(fromName: String, toName: String, force: Boolean = false): Result<Unit>

    // Sprint 35 — observer envelopes per-session (G8)
    public suspend fun getSessionEnvelopes(sessionId: String): Result<List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>>

    // ---- Dashboard Cards (alpha.75, issue #132) ----

    /** GET /api/dashboard/cards — list dashboard cards. Returns bare array. */
    public suspend fun listDashboardCards(): Result<List<com.dmzs.datawatchclient.transport.dto.DashboardCardDto>>

    /** POST /api/dashboard/cards — append a new dashboard card. */
    public suspend fun addDashboardCard(card: com.dmzs.datawatchclient.transport.dto.DashboardCardDto): Result<com.dmzs.datawatchclient.transport.dto.DashboardCardDto>

    /** PUT /api/dashboard/cards/{id} — update or create a dashboard card. */
    public suspend fun updateDashboardCard(id: String, card: com.dmzs.datawatchclient.transport.dto.DashboardCardDto): Result<com.dmzs.datawatchclient.transport.dto.DashboardCardDto>

    /** DELETE /api/dashboard/cards/{id} — remove a dashboard card. */
    public suspend fun deleteDashboardCard(id: String): Result<Unit>

    // ---- Session Telemetry (BL303 S1, issue #128) ----

    /** GET /api/sessions/{id}/telemetry — live task tree + sprint ancestry + guardrail verdicts. */
    public suspend fun getSessionTelemetry(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto>

    // ---- Guardrail Library + Profiles (BL303 S2, issue #128) ----

    /** GET /api/autonomous/guardrails — browse available guardrail checks. */
    public suspend fun listGuardrailLibrary(): Result<List<com.dmzs.datawatchclient.transport.dto.GuardrailLibraryItemDto>>

    /** GET /api/autonomous/guardrail-profiles — list guardrail profiles. */
    public suspend fun listGuardrailProfiles(): Result<List<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto>>

    /** POST /api/autonomous/guardrail-profiles — create a guardrail profile. */
    public suspend fun createGuardrailProfile(profile: com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto): Result<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto>

    /** PUT /api/autonomous/guardrail-profiles/{id} — update a guardrail profile. */
    public suspend fun updateGuardrailProfile(id: String, profile: com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto): Result<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto>

    /** DELETE /api/autonomous/guardrail-profiles/{id} — delete a guardrail profile. */
    public suspend fun deleteGuardrailProfile(id: String): Result<Unit>

    /** POST /api/sessions/{id}/guardrail — run guardrail against session project dir. */
    public suspend fun runSessionGuardrail(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.GuardrailRunResultDto>

    // ---- Smoke Progress (BL303 S4, issue #128) ----

    /**
     * GET /api/smoke/progress — current smoke run progress. Returns null (204) when no run is active.
     */
    public suspend fun getSmokeProgress(): Result<com.dmzs.datawatchclient.transport.dto.SmokeProgressDto?>

    /** DELETE /api/smoke/progress — clear a completed smoke run. */
    public suspend fun clearSmokeProgress(): Result<Unit>

    // ---- Eval Runs history (alpha.68, issue #131) ----

    /** GET /api/evals — list completed eval runs (alpha.68+). Returns empty list on 404. */
    public suspend fun listEvalRuns(): Result<List<com.dmzs.datawatchclient.transport.dto.EvalRunHistoryDto>>

    // ---- T30: Channel Routing ----

    /** GET /api/channel/routing — list channel routing rules. */
    public suspend fun getChannelRouting(): Result<com.dmzs.datawatchclient.transport.dto.ChannelRoutingListDto>

    /** PUT /api/channel/routing — replace channel routing rules. */
    public suspend fun putChannelRouting(rules: List<com.dmzs.datawatchclient.transport.dto.ChannelRoutingRuleDto>): Result<com.dmzs.datawatchclient.transport.dto.ChannelRoutingListDto>

    // ---- T30: File Service ----

    /** GET /api/files/meta — file service metadata. */
    public suspend fun getFileServiceMeta(): Result<com.dmzs.datawatchclient.transport.dto.FileServiceMetaDto>

    // ---- T30: Discussion Scopes ----

    /** GET /api/memory/discussion — list discussion scope IDs. */
    public suspend fun listDiscussions(): Result<com.dmzs.datawatchclient.transport.dto.DiscussionListDto>

    /** POST /api/memory/discussion/{id} — write a message to a discussion WAL. */
    public suspend fun writeDiscussionMessage(id: String, content: String): Result<com.dmzs.datawatchclient.transport.dto.DiscussionWriteResponseDto>

    // ---- T30: Encryption Status ----

    /** GET /api/security/encryption/status — encryption status. */
    public suspend fun getEncryptionStatus(): Result<com.dmzs.datawatchclient.transport.dto.EncryptionStatusDto>

    // ---- S14b: Alert Rules ----
    public suspend fun listAlertRules(): Result<com.dmzs.datawatchclient.transport.dto.AlertRulesListDto>

    /** GET /api/alert-rules/firings — last 100 alert rule firings. */
    public suspend fun listAlertRuleFirings(): Result<com.dmzs.datawatchclient.transport.dto.AlertRuleFiringsDto>
    public suspend fun createAlertRule(rule: com.dmzs.datawatchclient.transport.dto.AlertRuleDto): Result<Unit>
    public suspend fun deleteAlertRule(name: String): Result<Unit>
    public suspend fun enableAlertRule(name: String): Result<Unit>
    public suspend fun disableAlertRule(name: String): Result<Unit>

    // ---- Observer cards: Cooldown, Analytics, Audit ----

    /** GET /api/cooldown — fetch current global cooldown status. */
    public suspend fun getCooldownStatus(): Result<com.dmzs.datawatchclient.transport.dto.CooldownStatusDto>

    /** POST /api/cooldown — set a global cooldown until the given epoch-ms. */
    public suspend fun setCooldown(untilUnixMs: Long, reason: String): Result<Unit>

    /** DELETE /api/cooldown — clear any active global cooldown. */
    public suspend fun clearCooldown(): Result<Unit>

    /** GET /api/analytics?range=<n>d — bucketed session analytics. */
    public suspend fun getAnalytics(rangeDays: Int = 7): Result<com.dmzs.datawatchclient.transport.dto.AnalyticsDto>

    /** GET /api/audit — audit log with optional actor/action filters. */
    public suspend fun getAuditLog(
        actor: String? = null,
        action: String? = null,
        limit: Int = 20,
    ): Result<com.dmzs.datawatchclient.transport.dto.AuditListDto>

    /** GET /api/matrix/status — Matrix backend connection state. */
    public suspend fun fetchMatrixStatus(): Result<MatrixStatusDto>

    /**
     * GET /api/opencode/models — live model list from the opencode binary.
     * Returns grouped models with human-friendly [providerLabel] headers
     * and a [defaultModel] id to pre-select.
     */
    public suspend fun fetchOpenCodeModels(): Result<com.dmzs.datawatchclient.transport.dto.OpenCodeModelsResponseDto>
}

/** A single system quick-command entry served by /api/config quick_commands. */
public data class QuickCommandItem(val label: String, val value: String)

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
