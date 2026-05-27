package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-level DTOs that track datawatch's openapi.yaml exactly. Kept flat and
 * permissive so server version skew within a minor family does not break the
 * client — unknown fields are ignored by default Json config.
 *
 * DTO → domain conversion lives in `transport/rest/Mappers.kt`.
 */

/**
 * Matches the `Session` schema in the parent datawatch openapi.yaml exactly.
 * Field names are RFC3339 / snake_case as the server emits them. Most fields
 * are nullable / defaulted because not every session populates them
 * (e.g., a brand-new session has no `last_prompt`).
 */
@Serializable
public data class SessionDto(
    val id: String,
    val state: String,
    @SerialName("full_id") val fullId: String? = null,
    val task: String? = null,
    /**
     * User-assigned display name (via rename). Parent PWA prefers this over
     * [task] for the row header. When null, the row falls back to [task].
     */
    val name: String? = null,
    @SerialName("tmux_session") val tmuxSession: String? = null,
    @SerialName("log_file") val logFile: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val hostname: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("pending_input") val pendingInput: String? = null,
    @SerialName("last_prompt") val lastPrompt: String? = null,
    /**
     * Multi-line prompt context surfaced by the server when a session is in
     * `waiting_input`. PWA renders the last 4 lines under the card so users
     * know what the LLM is actually asking before they tap through.
     * Overrides [lastPrompt] when present.
     */
    @SerialName("prompt_context") val promptContext: String? = null,
    /**
     * Most-recent LLM response snippet. When present, the PWA shows a
     * "View last response" icon on the row; mobile does the same.
     */
    @SerialName("last_response") val lastResponse: String? = null,
    /**
     * Active backend family name for this session. Populates the per-row
     * backend badge. v7.0.0-alpha.27 renamed the wire field from
     * `llm_backend` → `backend_family`; both keys accepted for migration.
     */
    @SerialName("backend_family") val backendFamily: String? = null,
    /** Legacy key pre-alpha.27 — server no longer emits this; kept for deserialization compat. */
    @SerialName("llm_backend") val llmBackend: String? = null,
    /**
     * Federation-only: source server name when the row came from a
     * proxied fan-out call. `"local"` for rows on the user's own server.
     */
    val server: String? = null,
    /**
     * Output rendering mode. `"terminal"` (default), `"chat"` (bubble
     * transcript), `"log"` (read-only). PWA branches on this at
     * app.js:1644 (`sess.output_mode === 'chat'`).
     */
    /**
     * v0.42.6 — Container Workers: when set, this session was spawned by
     * a worker agent. PWA renders a purple ⬡ chip on the row so the
     * provenance is visible at a glance. PWA v5.26.58.
     */
    @SerialName("agent_id") val agentId: String? = null,
    /** v7 LLM registry name for this session. Null on sessions started before v7 or via legacy backend. */
    @SerialName("llm_ref") val llmRef: String? = null,
    /** v7 Compute Node this session was dispatched to. Null on legacy sessions. */
    @SerialName("compute_node_ref") val computeNodeRef: String? = null,
    @SerialName("output_mode") val outputMode: String? = null,
    /**
     * Input mode. `"tmux"` (default), `"chat"`, or `"none"` for
     * read-only sessions. PWA reads at app.js:1685.
     */
    @SerialName("input_mode") val inputMode: String? = null,
    /** datawatch v8.8.3 — session was started with `--chrome` (CDP integration). */
    val chrome: Boolean? = null,
)

@Serializable
public data class StartSessionDto(
    val task: String,
    @SerialName("server") val serverHint: String? = null,
    @SerialName("profile") val profile: String? = null,
    /**
     * Optional server-side working directory. Populated from the v0.12
     * mobile file-picker. Older server builds ignore unknown fields.
     */
    @SerialName("cwd") val workingDir: String? = null,
    /**
     * User-assigned session name (distinct from the task prompt).
     * Matches PWA `submitNewSession` payload. Older servers ignore.
     */
    val name: String? = null,
    /** Backend override — PWA passes the picked /api/backends name. */
    val backend: String? = null,
    /**
     * Resume a previous session by full id (matches PWA
     * `resume_id`). Server warm-restarts the named session rather
     * than starting fresh.
     */
    @SerialName("resume_id") val resumeId: String? = null,
    @SerialName("auto_git_init") val autoGitInit: Boolean? = null,
    @SerialName("auto_git_commit") val autoGitCommit: Boolean? = null,
    /** claude-code per-session permission mode (v5.27.5+). */
    @SerialName("permission_mode") val permissionMode: String? = null,
    /** claude-code per-session model alias (v5.27.5+). */
    val model: String? = null,
    /** claude-code per-session effort level (v5.27.5+). */
    @SerialName("claude_effort") val claudeEffort: String? = null,
    /** v7 LLM registry name. When set, daemon uses LLM's kind/node instead of legacy `backend`. */
    val llm: String? = null,
    /** v7 Compute Node override. Must be in LLM's compute_nodes list when provided. */
    @SerialName("compute_node") val computeNodeOverride: String? = null,
    /**
     * datawatch v8.8.3 — opt-in Chrome DevTools Protocol integration for
     * claude-code sessions. `true` adds `--chrome` to the launch; omitted
     * or `false` leaves the operator default unchanged.
     */
    val chrome: Boolean? = null,
)

@Serializable
public data class StartSessionResponseDto(
    @SerialName("session_id") val sessionId: String? = null,
    val id: String? = null,
    val state: String? = null,
)

@Serializable
public data class ReplyDto(
    @SerialName("session_id") val sessionId: String,
    val text: String,
)

@Serializable
public data class ReplyResponseDto(
    val ok: Boolean,
)

@Serializable
public data class HealthDto(
    val ok: Boolean = true,
    val version: String? = null,
)

@Serializable
public data class BackendsDto(
    val llm: List<String> = emptyList(),
    val active: String? = null,
)

/**
 * Full `/api/stats` response. Mirrors the flat fields PWA's
 * `renderStatsData` reads (app.js:5769-5920) plus the structured
 * observer envelopes added in dmz006/datawatch v4.1.0 (BL171).
 *
 * Every field optional so older v3.x servers still parse; mobile
 * renders whatever subset comes back. v1 `cpu_pct` / `mem_pct` /
 * `disk_pct` / `sessions_*` aliases live alongside structured
 * `cpu` / `mem` / `disk[]` / `sessions` objects — both read.
 */
@Serializable
public data class StatsDto(
    // ---- v1 flat scalars (back-compat aliases the observer still emits) ----
    @SerialName("cpu_pct") val cpuPct: Double? = null,
    @SerialName("mem_pct") val memPct: Double? = null,
    @SerialName("disk_pct") val diskPct: Double? = null,
    @SerialName("gpu_pct") val gpuPct: Double? = null,
    @SerialName("sessions_total") val sessionsTotal: Int = 0,
    @SerialName("sessions_running") val sessionsRunning: Int = 0,
    @SerialName("sessions_waiting") val sessionsWaiting: Int = 0,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    // ---- PWA flat fields renderStatsData reads directly ----
    val timestamp: String? = null,
    @SerialName("cpu_load_avg_1") val cpuLoad1: Double? = null,
    @SerialName("cpu_cores") val cpuCores: Int? = null,
    @SerialName("mem_used") val memUsed: Long? = null,
    @SerialName("mem_total") val memTotal: Long? = null,
    @SerialName("disk_used") val diskUsed: Long? = null,
    @SerialName("disk_total") val diskTotal: Long? = null,
    @SerialName("swap_used") val swapUsed: Long = 0,
    @SerialName("swap_total") val swapTotal: Long = 0,
    @SerialName("gpu_name") val gpuName: String? = null,
    @SerialName("gpu_util_pct") val gpuUtilPct: Double? = null,
    @SerialName("gpu_temp") val gpuTemp: Double? = null,
    @SerialName("gpu_mem_used_mb") val gpuMemUsedMb: Long? = null,
    @SerialName("gpu_mem_total_mb") val gpuMemTotalMb: Long? = null,
    @SerialName("ebpf_active") val ebpfActive: Boolean = false,
    /**
     * `ebpf_enabled` — daemon was built with eBPF capture support. When
     * this is `true` but [ebpfActive] is `false`, the kernel probes
     * aren't loaded and the network/pid traces the Monitor tab relies
     * on are missing. The PWA renders an inline amber "Degraded" banner
     * in that case; we do the same. Null for older servers that
     * predate this field.
     */
    @SerialName("ebpf_enabled") val ebpfEnabled: Boolean? = null,
    /**
     * Human-readable status message (e.g. `"Degraded — run: datawatch
     * setup ebpf"`). Shown in the eBPF Degraded banner when non-blank.
     */
    @SerialName("ebpf_message") val ebpfMessage: String? = null,
    @SerialName("net_rx_bytes") val netRxBytes: Long = 0,
    @SerialName("net_tx_bytes") val netTxBytes: Long = 0,
    @SerialName("daemon_rss_bytes") val daemonRssBytes: Long = 0,
    val goroutines: Int = 0,
    @SerialName("open_fds") val openFds: Int = 0,
    @SerialName("bound_interfaces") val boundInterfaces: List<String> = emptyList(),
    @SerialName("web_port") val webPort: Int? = null,
    @SerialName("tls_port") val tlsPort: Int = 0,
    @SerialName("tls_enabled") val tlsEnabled: Boolean = false,
    @SerialName("mcp_sse_port") val mcpSsePort: Int? = null,
    @SerialName("mcp_sse_host") val mcpSseHost: String? = null,
    @SerialName("tmux_sessions") val tmuxSessions: Int = 0,
    @SerialName("orphaned_tmux") val orphanedTmux: List<String> = emptyList(),
    @SerialName("rtk_installed") val rtkInstalled: Boolean = false,
    @SerialName("rtk_version") val rtkVersion: String? = null,
    @SerialName("rtk_update_available") val rtkUpdateAvailable: Boolean = false,
    @SerialName("rtk_hooks_active") val rtkHooksActive: Boolean = false,
    @SerialName("rtk_total_saved") val rtkTotalSaved: Long = 0,
    @SerialName("rtk_avg_savings_pct") val rtkAvgSavingsPct: Double? = null,
    @SerialName("rtk_total_commands") val rtkTotalCommands: Int = 0,
    @SerialName("memory_enabled") val memoryEnabled: Boolean = false,
    @SerialName("memory_encrypted") val memoryEncrypted: Boolean = false,
    @SerialName("memory_key_fingerprint") val memoryKeyFingerprint: String? = null,
    @SerialName("memory_backend") val memoryBackend: String? = null,
    @SerialName("memory_embedder") val memoryEmbedder: String? = null,
    @SerialName("memory_total_count") val memoryTotalCount: Int = 0,
    @SerialName("memory_manual_count") val memoryManualCount: Int = 0,
    @SerialName("memory_session_count") val memorySessionCount: Int = 0,
    @SerialName("memory_learning_count") val memoryLearningCount: Int = 0,
    @SerialName("memory_db_size_bytes") val memoryDbSizeBytes: Long = 0,
    @SerialName("ollama_stats") val ollamaStats: OllamaStatsDto? = null,
    // ---- v4.1.0 observer envelopes (BL171) ----
    val envelopes: List<StatEnvelopeDto> = emptyList(),
    val backends: List<BackendStatusDto> = emptyList(),
    // B5 — per-core CPU utilisation strip (server emits when eBPF/proc available)
    @SerialName("cpu_cores_detail") val cpuCoresDetail: List<Double> = emptyList(),
)

@Serializable
public data class OllamaStatsDto(
    val available: Boolean = false,
    val host: String? = null,
    @SerialName("model_count") val modelCount: Int = 0,
    @SerialName("total_size_bytes") val totalSizeBytes: Long = 0,
    @SerialName("running_models") val runningModels: List<OllamaRunningModelDto> = emptyList(),
)

@Serializable
public data class OllamaRunningModelDto(
    val name: String = "",
    @SerialName("size_vram") val sizeVram: Long = 0,
)

/**
 * Observer envelope — BL171 v4.1.0. One per
 * session / backend / container / system group with rolled-up CPU,
 * RSS, optional GPU + network counters.
 */
@Serializable
public data class ContainerInfoDto(
    @SerialName("container_id") val containerId: String = "",
    val image: String = "",
    val runtime: String = "",
)

@Serializable
public data class StatEnvelopeDto(
    val id: String = "",
    val kind: String = "",
    val label: String = "",
    @SerialName("root_pid") val rootPid: Int = 0,
    val pids: List<Int> = emptyList(),
    @SerialName("cpu_pct") val cpuPct: Double = 0.0,
    @SerialName("rss_bytes") val rssBytes: Long = 0,
    val threads: Int = 0,
    @SerialName("fds") val fds: Int = 0,
    @SerialName("net_rx_bps") val netRxBps: Long = 0,
    @SerialName("net_tx_bps") val netTxBps: Long = 0,
    @SerialName("gpu_pct") val gpuPct: Double = 0.0,
    @SerialName("gpu_mem_bytes") val gpuMemBytes: Long = 0,
    @SerialName("container_id") val containerId: String? = null,
    val image: String? = null,
    @SerialName("last_activity_unix_ms") val lastActivityUnixMs: Long = 0,
    val container: ContainerInfoDto? = null,
)

// ============================================================
// Sprint 26 — Session status board (alpha.34)
// ============================================================

@Serializable
public data class SprintStatusDto(
    val name: String = "",
    val progress: String = "",
)

@Serializable
public data class TestStatusDto(
    val passing: Int = 0,
    val failing: Int = 0,
    val total: Int = 0,
)

@Serializable
public data class GitStatusDto(
    val branch: String = "",
    val uncommitted: Int = 0,
    val ahead: Int = 0,
)

@Serializable
public data class LastEventDto(
    val ts: Long? = null,
    val event: String? = null,
    val tool: String? = null,
)

@Serializable
public data class SessionStatusBoardDto(
    val state: String = "idle",
    @SerialName("last_event") val lastEvent: LastEventDto? = null,
    @SerialName("idle_since") val idleSince: Long? = null,
    @SerialName("hook_health") val hookHealth: String = "missing",
    val sprint: SprintStatusDto? = null,
    val tests: TestStatusDto? = null,
    val git: GitStatusDto? = null,
    @SerialName("current_focus") val currentFocus: String? = null,
)

// ============================================================
// v0.97.0 — Ollama marketplace DTOs (alpha.33 / Sprint 27)
// ============================================================

@Serializable
public data class OllamaInstalledModelsDto(
    val models: List<String> = emptyList(),
)

@Serializable
public data class OllamaCatalogDto(
    val models: List<OllamaCatalogModelDto> = emptyList(),
)

@Serializable
public data class OllamaCatalogModelDto(
    val name: String,
    val description: String = "",
    val tags: List<OllamaTagDto> = emptyList(),
)

@Serializable
public data class OllamaTagDto(
    val tag: String,
    val size: String = "",
    @SerialName("min_ram_gb") val minRamGb: Float = 0f,
    @SerialName("min_vram_gb") val minVramGb: Float = 0f,
    val fits: Boolean = true,
)

@Serializable
public data class OllamaPullTaskDto(
    val id: String,
    val model: String = "",
    val progress: Int = 0,
    val status: String = "pending",
)

// ============================================================
// v0.98.0 — UnifiedPush SSE DTOs (alpha.35 / Sprint 28)
// ============================================================

@Serializable
public data class PushRegistrationDto(
    val endpoint: String,
    @SerialName("client_id") val clientId: String,
    val token: String = "",
)

@Serializable
public data class PushEventDto(
    val event: String = "message",
    val title: String = "",
    val message: String = "",
    val priority: Int = 3,
    val tags: List<String> = emptyList(),
    val click: String = "",
)

@Serializable
public data class BackendStatusDto(
    val name: String = "",
    val reachable: Boolean = false,
    @SerialName("last_ok_unix_ms") val lastOkUnixMs: Long = 0,
    @SerialName("latency_ms") val latencyMs: Int = 0,
    val error: String? = null,
)

// ============================================================
// v0.36.0 — federated monitoring DTOs (datawatch v4.4.0+ + S13)
// ============================================================

/**
 * GET /api/observer/peers — list of Shape B / C / Agent peers the
 * parent observer knows about. Issue #2 + #6 (S13 agents filter).
 */
@Serializable
public data class ObserverPeersDto(
    val peers: List<ObserverPeerDto> = emptyList(),
)

@Serializable
public data class ObserverPeerDto(
    val name: String = "",
    /** "standalone" | "cluster" | "agent" — drives the row badge. */
    val shape: String = "",
    /** "agent" when this peer was a F10 ephemeral worker (S13). */
    @SerialName("host_info") val hostInfo: ObserverPeerHostDto? = null,
    val version: String? = null,
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("last_push_at") val lastPushAt: String? = null,
    /** alpha.24 #231 — bound ComputeNode name; null if peer is free. */
    @SerialName("compute_node") val computeNode: String? = null,
)

@Serializable
public data class ObserverPeerHostDto(
    val hostname: String? = null,
    /** "agent" tag on F10 ephemeral peers — surfaced via the filter pill. */
    val shape: String? = null,
    val os: String? = null,
    val arch: String? = null,
)

/**
 * GET /api/observer/peers/by-node — local peers grouped by bound ComputeNode.
 * alpha.24 #231 — used by the "Group by ComputeNode" toggle on the peers card.
 */
@Serializable
public data class ObserverPeersByNodeDto(
    @SerialName("by_node") val byNode: Map<String, List<ObserverPeerDto>> = emptyMap(),
    val unbound: List<ObserverPeerDto> = emptyList(),
)

/**
 * GET /api/federation/meta-peers — cross-instance peers aggregated by CN.
 * alpha.24 #231 — server merges local + reachable federation primaries.
 */
@Serializable
public data class MetaObserverEntryDto(
    val primary: String = "",
    val peer: String = "",
    val shape: String = "",
    @SerialName("last_push_at") val lastPushAt: String? = null,
    val version: String? = null,
)

@Serializable
public data class MetaNodeBucketDto(
    val observers: List<MetaObserverEntryDto> = emptyList(),
    @SerialName("observer_count") val observerCount: Int = 0,
    @SerialName("primary_count") val primaryCount: Int = 0,
)

@Serializable
public data class MetaPeersDto(
    val self: String = "",
    @SerialName("by_node") val byNode: Map<String, MetaNodeBucketDto> = emptyMap(),
    val unbound: List<MetaObserverEntryDto> = emptyList(),
    @SerialName("primaries_walked") val primariesWalked: List<String> = emptyList(),
)

/**
 * GET /api/plugins — subprocess + native plugin listing.
 * Issue #5 (datawatch v4.2.0 / B41 native rows).
 */
@Serializable
public data class PluginsDto(
    val plugins: List<PluginDto> = emptyList(),
    val native: List<PluginDto> = emptyList(),
)

@Serializable
public data class PluginDto(
    val name: String = "",
    /** "subprocess" or "native" — controls the row badge. */
    val kind: String = "subprocess",
    val description: String? = null,
    val enabled: Boolean = true,
    val version: String? = null,
    val message: String? = null,
)

/**
 * GET /api/observer/stats — richer observer view of the daemon's
 * own host + cluster posture. Issue #3 (cluster.nodes) + #4
 * (host.ebpf richer block).
 *
 * Only the fields used by mobile cards are modeled here; the
 * full observer payload is much larger.
 */
@Serializable
public data class ObserverStatsDto(
    val host: ObserverHostDto? = null,
    val cluster: ObserverClusterDto? = null,
)

@Serializable
public data class ObserverHostDto(
    val ebpf: ObserverEbpfDto? = null,
)

@Serializable
public data class ObserverEbpfDto(
    val configured: Boolean = false,
    val capability: Boolean = false,
    @SerialName("kprobes_loaded") val kprobesLoaded: Boolean = false,
    val message: String? = null,
)

@Serializable
public data class ObserverClusterDto(
    val nodes: List<ObserverClusterNodeDto> = emptyList(),
)

@Serializable
public data class ObserverClusterNodeDto(
    val name: String = "",
    val ready: Boolean = true,
    val pressures: List<String> = emptyList(),
    @SerialName("pod_count") val podCount: Int = 0,
    @SerialName("cpu_pct") val cpuPct: Double = 0.0,
    @SerialName("mem_pct") val memPct: Double = 0.0,
)

// ============================================================
// v0.37.0 — mempalace surfaces (datawatch v5.27.0 / #21)
// ============================================================

@Serializable
public data class MemoryPinDto(
    val id: Long,
    val pinned: Boolean,
)

@Serializable
public data class MemorySweepStaleRequestDto(
    @SerialName("older_than_days") val olderThanDays: Int,
    @SerialName("dry_run") val dryRun: Boolean = true,
)

@Serializable
public data class MemorySweepStaleResponseDto(
    val count: Int = 0,
    @SerialName("dry_run") val dryRun: Boolean = true,
)

@Serializable
public data class MemorySpellcheckRequestDto(
    val text: String,
    @SerialName("extra_words") val extraWords: List<String> = emptyList(),
)

@Serializable
public data class SpellcheckSuggestionDto(
    val word: String = "",
    val suggestions: List<String> = emptyList(),
)

@Serializable
public data class MemorySpellcheckResponseDto(
    val suggestions: List<SpellcheckSuggestionDto> = emptyList(),
)

@Serializable
public data class MemoryExtractFactsRequestDto(
    val text: String,
)

@Serializable
public data class SvoTripleDto(
    val subject: String = "",
    val verb: String = "",
    @SerialName("object") val obj: String = "",
)

@Serializable
public data class MemoryExtractFactsResponseDto(
    val triples: List<SvoTripleDto> = emptyList(),
)

// ============================================================
// v0.38.0 — autonomous PRD lifecycle DTOs (datawatch BL191/v5.x)
// ============================================================

/**
 * GET /api/autonomous/prds — list PRDs the parent observer knows about.
 * Mirrors the PWA `loadPRDs()` payload shape.
 */
@Serializable
public data class PrdListDto(
    val prds: List<PrdDto> = emptyList(),
)

@Serializable
public data class PrdDto(
    val id: String = "",
    val name: String = "",
    val title: String? = null,
    /** decomposing | needs_review | revisions_asked | approved | running | complete | rejected | cancelled */
    val status: String = "",
    @SerialName("project_dir") val projectDir: String? = null,
    @SerialName("project_profile") val projectProfile: String? = null,
    @SerialName("cluster_profile") val clusterProfile: String? = null,
    val backend: String? = null,
    val effort: String? = null,
    val model: String? = null,
    @SerialName("parent_prd_id") val parentPrdId: String? = null,
    val depth: Int = 0,
    @SerialName("is_template") val isTemplate: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val spec: String? = null,
    @SerialName("permission_mode") val permissionMode: String? = null,
    val stories: List<PrdStoryDto> = emptyList(),
    /** PRD type (v0.63.0 BL221 Phase 4): software|research|operational|personal|custom. */
    val type: String? = null,
    @SerialName("guided_mode") val guidedMode: Boolean = false,
    val skills: List<String> = emptyList(),
    val decisions: List<DecisionDto>? = null,
)

@Serializable
public data class DecisionDto(
    val at: String? = null,
    val kind: String? = null,
    val actor: String? = null,
    val note: String? = null,
)

@Serializable
public data class PrdStoryDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    /** pending | awaiting_approval | in_progress | complete | rejected */
    val status: String = "",
    val files: List<String> = emptyList(),
    @SerialName("files_touched") val filesTouched: List<String> = emptyList(),
    @SerialName("execution_profile") val executionProfile: String? = null,
)

/**
 * POST /api/autonomous/prds — create a new PRD. Mirrors PWA New PRD
 * modal form fields after the v5.26.30 unified-Profile dropdown
 * collapse: when `project_profile` is set, daemon ignores the
 * `project_dir` / `backend` / `effort` / `model` fields.
 */
@Serializable
public data class NewPrdRequestDto(
    val name: String = "",
    val title: String? = null,
    @SerialName("project_dir") val projectDir: String? = null,
    @SerialName("project_profile") val projectProfile: String? = null,
    @SerialName("cluster_profile") val clusterProfile: String? = null,
    val backend: String? = null,
    val effort: String? = null,
    val model: String? = null,
    @SerialName("decomposition_profile") val decompositionProfile: String? = null,
    /** claude-code per-PRD permission mode (v5.27.5+). Most-specific-wins: task > PRD > session default. */
    @SerialName("permission_mode") val permissionMode: String? = null,
    /** v0.63.0: PRD type, guided mode, skills. */
    val type: String? = null,
    @SerialName("guided_mode") val guidedMode: Boolean? = null,
    val skills: List<String>? = null,
    val spec: String? = null,
)

@Serializable
public data class NewPrdResponseDto(
    val id: String = "",
)

// ============================================================
// v0.63.0 — BL221 Phase 4: Type registry (datawatch v6.2.0)
// ============================================================

@Serializable
public data class AutomataTypeDto(
    val id: String = "",
    val label: String = "",
    val description: String? = null,
    val color: String? = null,
)

@Serializable
public data class AutomataTypeRequestDto(
    val id: String,
    val label: String,
    val description: String? = null,
    val color: String? = null,
)

// ============================================================
// v0.62.0 — BL221 Phase 3: Security scan (datawatch v6.2.0)
// ============================================================

@Serializable
public data class ScanFindingDto(
    val scanner: String = "",
    val file: String = "",
    val line: Int? = null,
    val severity: String = "info",
    @SerialName("rule_id") val ruleId: String? = null,
    val message: String = "",
    val fixable: Boolean = false,
)

@Serializable
public data class ScanResultDto(
    val at: String = "",
    @SerialName("prd_id") val prdId: String = "",
    val pass: Boolean = false,
    val verdict: String = "pass",
    val notes: String? = null,
    val findings: List<ScanFindingDto> = emptyList(),
)

@Serializable
public data class ScanConfigDto(
    val enabled: Boolean = false,
    @SerialName("sast_enabled") val sast: Boolean = false,
    @SerialName("secrets_enabled") val secrets: Boolean = false,
    @SerialName("deps_enabled") val deps: Boolean = false,
    @SerialName("fail_on_severity") val failOnSeverity: String = "error",
    val grader: Boolean = false,
    @SerialName("fix_loop") val fixLoop: Boolean = false,
    @SerialName("max_retries") val maxRetries: Int = 3,
)

@Serializable
public data class RuleProposalDto(
    val text: String = "",
    val diff: String? = null,
)

// ============================================================
// v0.61.0 — BL221 Phase 2: Template Store (datawatch v6.2.0)
// ============================================================

@Serializable
public data class TemplateDto(
    val id: String = "",
    val title: String = "",
    val spec: String = "",
    val type: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class TemplateListDto(
    val templates: List<TemplateDto> = emptyList(),
)

@Serializable
public data class CreateTemplateRequestDto(
    val title: String,
    val spec: String,
    val type: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
public data class UpdateTemplateRequestDto(
    val title: String? = null,
    val spec: String? = null,
    val type: String? = null,
    val tags: List<String>? = null,
    val description: String? = null,
)

@Serializable
public data class InstantiateTemplateRequestDto(
    @SerialName("project_dir") val projectDir: String? = null,
    @SerialName("project_profile") val projectProfile: String? = null,
    val vars: Map<String, String> = emptyMap(),
)

@Serializable
public data class ClonePrdToTemplateRequestDto(
    val description: String? = null,
    val actor: String? = null,
)

// ============================================================
// v0.39.0 — orchestrator PRD-DAG graph (datawatch v4.7.0 / S13)
// ============================================================

@Serializable
public data class OrchestratorGraphDto(
    val id: String = "",
    val name: String? = null,
    val nodes: List<OrchestratorNodeDto> = emptyList(),
    val edges: List<OrchestratorEdgeDto> = emptyList(),
)

@Serializable
public data class OrchestratorNodeDto(
    val id: String = "",
    val name: String? = null,
    /** "running" / "complete" / "needs_review" / etc. */
    val status: String = "",
    val kind: String? = null,
    /**
     * S13 (v4.7.0) — present when the node is a Shape A peer that
     * pushed a recent envelope. Omitted on agents that haven't
     * registered or whose last push expired; the UI hides the
     * badge in that case.
     */
    @SerialName("observer_summary") val observerSummary: ObserverSummaryDto? = null,
)

@Serializable
public data class ObserverSummaryDto(
    @SerialName("cpu_pct") val cpuPct: Double? = null,
    @SerialName("rss_mb") val rssMb: Long? = null,
    @SerialName("envelope_count") val envelopeCount: Int? = null,
    @SerialName("last_push_at") val lastPushAt: String? = null,
)

@Serializable
public data class OrchestratorEdgeDto(
    val from: String = "",
    val to: String = "",
    val kind: String? = null,
)

// ============================================================
// v0.39.1 — F10 ephemeral-agent session start (#20 / PWA v5.26.63)
// ============================================================

@Serializable
public data class StartAgentRequestDto(
    val task: String,
    @SerialName("project_profile") val projectProfile: String,
    @SerialName("cluster_profile") val clusterProfile: String? = null,
    val branch: String? = null,
    val name: String? = null,
)

@Serializable
public data class StartAgentResponseDto(
    @SerialName("session_id") val sessionId: String? = null,
    val id: String? = null,
)

// ============================================================
// v0.64.0 — BL21 Signal device-linking (datawatch#31)
// ============================================================

@Serializable
public data class SignalLinkStartDto(
    @SerialName("session_token") val sessionToken: String = "",
)

@Serializable
public data class LinkQrFrameDto(
    @SerialName("image_base64") val imageBase64: String,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
public data class SignalLinkStatusDto(
    val linked: Boolean = false,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    val status: String = "unlinked",
)

// ============================================================
// v0.66.0 — BL255: Skill Registries (datawatch v6.7.0)
// ============================================================

@Serializable
public data class SkillRegistryDto(
    val name: String = "",
    val url: String = "",
    val branch: String = "main",
    val status: String = "disconnected",
    @SerialName("last_synced") val lastSynced: String? = null,
    val builtin: Boolean = false,
    val enabled: Boolean = true,
    @SerialName("synced_count") val syncedCount: Int = 0,
)

@Serializable
public data class SkillRegistryRequestDto(
    val name: String,
    val url: String,
    val branch: String = "main",
    val enabled: Boolean = true,
)

@Serializable
public data class SkillRegistryUpdateDto(
    val url: String? = null,
    val branch: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
public data class SkillDto(
    val name: String = "",
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val registry: String = "",
    val version: String? = null,
)

@Serializable
public data class AvailableSkillDto(
    val name: String = "",
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val synced: Boolean = false,
)

@Serializable
public data class SyncSkillsRequestDto(
    val skills: List<String>,
)

// ============================================================
// v0.74.0 — Compute Nodes + LLM Registry + Migration (S5)
// ============================================================

/**
 * GET/POST/PUT /api/compute/nodes — a registered compute worker
 * (e.g. ollama instance, opencode server, shell runner, etc.).
 * openwebui is NOT a ComputeNode kind — it is an LLM kind that
 * references an ollama ComputeNode.
 */
@Serializable
public data class DeclaredCapacityDto(
    val gpus: Int = 1,
    @SerialName("gpu_mem_gb") val gpuMemGb: Int = 0,
    @SerialName("max_concurrent_models") val maxConcurrentModels: Int = 10,
)

@Serializable
public data class ComputeNodeDto(
    val name: String,
    val kind: String,
    val address: String,
    @SerialName("declared_capacity") val declaredCapacity: DeclaredCapacityDto? = null,
    val tags: List<String> = emptyList(),
    @SerialName("auto_created") val autoCreated: Boolean = false,
    @SerialName("hardware_spec") val hardwareSpec: ComputeHardwareSpec? = null,
    /** v7: operator-enabled flag; false = explicitly disabled by operator. */
    val enabled: Boolean = true,
    /** v7: daemon-applied tags (migration markers, shape labels). Hidden from UI; use `tags` only. */
    @SerialName("auto_tags") val autoTags: List<String> = emptyList(),
    /** v7: name of the bound observer peer (explicit or name-match). */
    @SerialName("observer_peer") val observerPeer: String? = null,
    /** v7: reason string when daemon auto-disables the node. */
    @SerialName("disabled_reason") val disabledReason: String? = null,
)

@Serializable
public data class ComputeHardwareSpec(
    val os: String? = null,
    val arch: String? = null,
    @SerialName("gpu_vendor") val gpuVendor: String? = null,
    @SerialName("gpu_model") val gpuModel: String? = null,
    @SerialName("gpu_count") val gpuCount: Int = 0,
    @SerialName("memory_gb") val memoryGb: Int = 0,
    @SerialName("cpu_cores") val cpuCores: Int = 0,
)

// v0.84.0 — Sprint 15: migration + free-observer
@Serializable
public data class MigrationNodeItemDto(
    val name: String = "",
    @SerialName("current_kind") val currentKind: String = "",
    val address: String = "",
)

@Serializable
public data class MigrationComputeKindsDto(
    val nodes: List<MigrationNodeItemDto> = emptyList(),
    @SerialName("supported_kinds") val supportedKinds: List<String> = listOf("ollama", "openai-compat"),
    val count: Int = 0,
)

@Serializable
public data class MigrateKindRequestDto(
    val kind: String,
)

@Serializable
public data class AttachObserverRequestDto(
    val peer: String,
)

@Serializable
public data class FreeObserverPeerDto(
    val name: String = "",
    val shape: String = "",
)

/** Sprint 30 — per-node model assignment in the LLM registry. */
@Serializable
public data class LlmModelPairDto(
    @SerialName("compute_node") val computeNode: String = "",
    val model: String = "",
)

/**
 * GET/POST/PUT /api/llms — a registered LLM entry. kind=openwebui
 * is valid here (references an ollama ComputeNode under the hood).
 * alpha.41: session-backend and claude-code-specific fields added.
 */
@Serializable
public data class LlmRegistryEntryDto(
    val name: String,
    val kind: String,
    @SerialName("compute_node") val computeNode: String = "",
    /** v7 multi-node list for the picker cascade. The `compute_node` field above is the primary; this list may contain alternates. */
    @SerialName("compute_nodes") val computeNodes: List<String> = emptyList(),
    val model: String = "",
    /** Sprint 30 — per-node model pairs; supersedes computeNode+model when non-empty. */
    val models: List<LlmModelPairDto> = emptyList(),
    val enabled: Boolean = true,
    @SerialName("pretest_enabled") val pretestEnabled: Boolean = false,
    /** Sprint 30 — when true, models list is managed by the server and is display-only. */
    @SerialName("auto_add_models") val autoAddModels: Boolean = false,
    // alpha.41 core fields
    @SerialName("api_key_ref") val apiKeyRef: String? = null,
    val timeout: Int? = null,
    val tags: List<String>? = null,
    // alpha.41 session-backend section (visible for session-backend kinds)
    val binary: String? = null,
    @SerialName("console_cols") val consoleCols: Int? = null,
    @SerialName("console_rows") val consoleRows: Int? = null,
    @SerialName("output_mode") val outputMode: String? = null,
    @SerialName("input_mode") val inputMode: String? = null,
    @SerialName("auto_git_init") val autoGitInit: Boolean? = null,
    @SerialName("auto_git_commit") val autoGitCommit: Boolean? = null,
    // alpha.41 claude-code-specific section (visible when kind == "claude-code")
    @SerialName("skip_permissions") val skipPermissions: Boolean? = null,
    @SerialName("channel_enabled") val channelEnabled: Boolean? = null,
    @SerialName("auto_accept_disclaimer") val autoAcceptDisclaimer: Boolean? = null,
    @SerialName("permission_mode") val permissionMode: String? = null,
    @SerialName("default_effort") val defaultEffort: String? = null,
    @SerialName("fallback_chain") val fallbackChain: List<String>? = null,
)

/** Sprint 30 — GET /api/llms/{name}/sessions response. */
@Serializable
public data class LlmSessionsDto(
    val sessions: List<LlmSessionRefDto>,
    val total: Int,
    val page: Int,
    val size: Int,
)

/** Sprint 30 — one session reference in the LLM sessions list. */
@Serializable
public data class LlmSessionRefDto(
    val id: String,
    val task: String,
    val state: String,
    @SerialName("llm_ref") val llmRef: String,
    @SerialName("created_at") val createdAt: String,
)

/** Sprint 30 — POST /api/llms/{name}/reassign body. */
@Serializable
public data class LlmReassignDto(
    @SerialName("new_llm") val newLlm: String,
    val force: Boolean = false,
)

/** PATCH /api/llms/{name}/enabled body. */
@Serializable
public data class LlmToggleRequest(
    val enabled: Boolean,
    val pretest: Boolean = false,
)

/** GET /api/compute/nodes response envelope. */
@Serializable
internal data class ComputeNodesResponseDto(
    val nodes: List<ComputeNodeDto> = emptyList(),
)

/** GET /api/llms response envelope. */
@Serializable
internal data class LlmsResponseDto(
    val llms: List<LlmRegistryEntryDto> = emptyList(),
)

/** GET /api/skills response envelope. */
@Serializable
internal data class SkillsResponseDto(
    val skills: List<SkillDto>? = null,
)

/** GET /api/skills/registries response envelope. */
@Serializable
internal data class SkillRegistriesResponseDto(
    val registries: List<SkillRegistryDto> = emptyList(),
)

/** GET /api/council/personas response envelope. */
@Serializable
internal data class CouncilPersonasResponseDto(
    val personas: List<CouncilPersonaDto> = emptyList(),
)

/** GET /api/council/runs response envelope. */
@Serializable
internal data class CouncilRunsResponseDto(
    val runs: List<CouncilRunDto> = emptyList(),
)

/** GET /api/evals/suites response envelope. */
@Serializable
internal data class EvalSuitesResponseDto(
    val suites: List<EvalSuiteDto> = emptyList(),
)

/** GET /api/algorithm response envelope. */
@Serializable
internal data class AlgorithmListResponseDto(
    val phases: List<String> = emptyList(),
    val sessions: List<AlgorithmStateDto> = emptyList(),
)

/** GET /api/migration/status — v7 first-launch auto-migration report. */
@Serializable
public data class MigrationStatusDto(
    @SerialName("at_unix") val atUnix: Long = 0L,
    val howto: String = "",
    val migrated: List<String> = emptyList(),
    val notice: String = "",
    val show: Boolean = false,
    val version: String = "",
)

// ── v0.75.0 BL274 Docs Search + Vault/Secrets DTOs ──────────────────────────

/** GET /api/secrets/status — active secrets backend + reachability (v0.75.0 S6-3). */
@Serializable
public data class SecretsStatusDto(
    @SerialName("active_backend") val activeBackend: String = "local",
    val reachable: Boolean = true,
    val address: String? = null,
    val mount: String? = null,
    @SerialName("last_success") val lastSuccess: String? = null,
    @SerialName("last_error") val lastError: String? = null,
)

/** GET /api/docs/search — a single search result with BM25/vector index kind badge (v0.75.0 S6-4, #84, #85). */
@Serializable
public data class DocsSearchResultDto(
    val path: String,
    val title: String,
    val excerpt: String,
    @SerialName("index_kind") val indexKind: String = "bm25",
    val score: Float = 0f,
)

/** GET /api/docs/trust/pending — a source pending user trust approval (v0.75.0 S6-4). */
@Serializable
public data class DocsPendingSourceDto(
    val path: String,
    val reason: String? = null,
)

/** GET /api/docs/trust — a trusted source entry (v0.75.0 S6-4). */
@Serializable
public data class DocsTrustedSourceDto(val path: String)

/** POST /api/docs/trust/accept or /dismiss body (v0.75.0 S6-4). */
@Serializable
public data class DocsTrustBulkRequest(val paths: List<String>)

/** GET /api/docs/howtos — single how-to entry. */
@Serializable
public data class DocsHowtoDto(
    val path: String,
    val title: String,
    val source: String = "core",
    @SerialName("has_exec_steps") val hasExecSteps: Boolean = false,
    @SerialName("exec_provenance") val execProvenance: String = "llm_translatable",
    val topics: List<String> = emptyList(),
)

/** GET /api/docs/howtos response wrapper. */
@Serializable
public data class DocsHowtosResponse(
    val howtos: List<DocsHowtoDto> = emptyList(),
)

/** POST /api/docs/trust/add request body. */
@Serializable
public data class DocsTrustAddRequest(
    val source: String,
    @SerialName("granted_by") val grantedBy: String = "operator",
    val note: String? = null,
)

// ── v0.73.0 Identity + Algorithm + Evals (S4-1/2/3, #53/#54/#55) ────────────

@Serializable
public data class IdentityDto(
    val role: String = "",
    @SerialName("north_star_goals") val northStarGoals: List<String> = emptyList(),
    @SerialName("current_projects") val currentProjects: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    @SerialName("current_focus") val currentFocus: String = "",
    @SerialName("context_notes") val contextNotes: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
public data class AlgorithmStateDto(
    @SerialName("session_id") val sessionId: String,
    val current: String,
    val history: List<AlgorithmPhaseDto> = emptyList(),
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val aborted: Boolean = false,
)

@Serializable
public data class AlgorithmPhaseDto(
    val phase: String,
    val output: String,
    val timestamp: String,
)

@Serializable
public data class EvalSuiteDto(
    val id: String = "",
    val name: String,
    val description: String = "",
    @SerialName("case_count") val cases: Int = 0,
    @SerialName("last_run") val lastRun: String? = null,
    @SerialName("last_score") val lastScore: Double? = null,
    @SerialName("pass_threshold") val passThreshold: Double = 0.7,
) {
    /** Server may omit `id`; fall back to `name` which is always unique per suite. */
    val effectiveId: String get() = id.ifEmpty { name }
}

@Serializable
public data class EvalRunResultDto(
    @SerialName("suite_id") val suiteId: String,
    val score: Double,
    val passed: Int,
    val failed: Int,
    val details: List<EvalCaseResult> = emptyList(),
    @SerialName("run_at") val runAt: String,
)

@Serializable
public data class EvalCaseResult(
    val name: String,
    val passed: Boolean,
    val output: String = "",
)

// ── v0.77.0 Council persona wizard (S8-1/2/3, #92) ──────────────────────────

/** GET /api/council/personas — a single council persona entry. */
@Serializable
public data class CouncilPersonaDto(
    val name: String = "",
    val description: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    @SerialName("assist_backend") val assistBackend: String? = null,
    /** Sprint 31 — true for the 4 platform built-in personas (cannot be deleted). */
    @SerialName("is_builtin") val isBuiltin: Boolean = false,
)

/** Sprint 31 — GET /api/council/personas response wrapper. */
@Serializable
public data class CouncilPersonasDto(
    val personas: List<CouncilPersonaDto> = emptyList(),
)

/** BL295-296 (alpha.41): per-persona answer from InferenceFn. */
@Serializable
public data class PersonaAnswerDto(
    val persona: String = "",
    val role: String = "",
    val answer: String = "",
)

/** GET /api/council/runs — a single council run entry. */
@Serializable
public data class CouncilRunDto(
    val id: String,
    val proposal: String,
    val personas: List<String> = emptyList(),
    val mode: String = "debate",
    val status: String = "pending",
    val round: Int = 0,
    val consensus: String? = null,
    val dissent: String? = null,
    /** alpha.41 BL295-296 — per-persona answers from InferenceFn. */
    val answers: List<PersonaAnswerDto> = emptyList(),
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

/** GET/PUT /api/council/config — council configuration. */
@Serializable
public data class CouncilConfigDto(
    @SerialName("comm_firehose") val commFirehose: Boolean = false,
    @SerialName("spawn_real_sessions") val spawnRealSessions: Boolean = false,
    @SerialName("llm_ref") val llmRef: String? = null,
    @SerialName("max_parallel") val maxParallel: Int? = null,
    @SerialName("draft_retention_days") val draftRetentionDays: Int? = null,
)

/** POST /api/council/run — start a council run. */
@Serializable
public data class StartCouncilRunRequest(
    val proposal: String,
    val mode: String,
    val personas: List<String> = emptyList(),
    @SerialName("spawn_real_sessions") val spawnRealSessions: Boolean = false,
)

/** POST/PUT /api/council/personas — create or update a council persona. */
@Serializable
public data class CouncilPersonaCreateDto(
    val name: String,
    val prompt: String,
    val description: String = "",
    @SerialName("assist_backend") val assistBackend: String? = null,
)

// ============================================================
// v0.80.0 — Cost Rates, Routing Rules, Tailscale Mesh (Sprint 11)
// ============================================================

/** GET/POST /api/cost/rates — per-backend token cost rates. */
@Serializable
public data class CostRatesDto(val rates: Map<String, CostRateDto> = emptyMap())

@Serializable
public data class CostRateDto(
    @SerialName("in_per_k") val inPerK: Double? = null,
    @SerialName("out_per_k") val outPerK: Double? = null,
)

/** GET/POST /api/routing-rules — LLM routing rules. */
@Serializable
public data class RoutingRulesDto(val rules: List<RoutingRuleDto> = emptyList())

@Serializable
public data class RoutingRuleDto(
    val pattern: String,
    val backend: String,
    val description: String = "",
)

@Serializable
public data class RoutingTestRequestDto(val task: String)

@Serializable
public data class RoutingTestResultDto(val matched: Boolean, val backend: String = "")

/** GET /api/tailscale/status — Tailscale mesh status. */
@Serializable
public data class TailscaleStatusDto(
    val enabled: Boolean = false,
    val backend: String = "",
    @SerialName("coordinator_url") val coordinatorUrl: String = "",
    val nodes: List<TailscaleNodeDto> = emptyList(),
    val error: String = "",
)

@Serializable
public data class TailscaleNodeDto(
    val name: String,
    val ip: String = "",
    val online: Boolean = false,
    val tags: List<String> = emptyList(),
)

// v0.81.0 — Sprint 12: Pipelines + OrchestratorGraphs list
@Serializable
public data class PipelineTaskDto(
    val id: String = "",
    val state: String = "",
)

@Serializable
public data class PipelineListItemDto(
    val id: String = "",
    val name: String = "",
    val state: String = "",
    val tasks: List<PipelineTaskDto> = emptyList(),
)

@Serializable
public data class OrchestratorGraphListItemDto(
    val id: String = "",
    val title: String = "",
    val status: String = "pending",
    @SerialName("prd_ids") val prdIds: List<String> = emptyList(),
)

@Serializable
public data class OrchestratorGraphsListDto(
    val graphs: List<OrchestratorGraphListItemDto> = emptyList(),
)

@Serializable
public data class CreateOrchestratorGraphRequestDto(
    val title: String,
    val directory: String = "",
    @SerialName("prd_ids") val prdIds: List<String> = emptyList(),
)

// v0.82.0 — Sprint 13: General tab — Templates / Device Aliases / Tooling / Secrets
@Serializable
public data class SessionTemplateDto(
    val name: String = "",
    val backend: String = "",
    @SerialName("project_dir") val projectDir: String = "",
    val effort: String = "",
    val description: String = "",
)

@Serializable
public data class DeviceAliasDto(
    val alias: String = "",
    val server: String = "",
)

@Serializable
public data class ToolingBackendDto(
    val backend: String = "",
    val present: List<String> = emptyList(),
    val ignored: Boolean = false,
)

@Serializable
public data class ToolingStatusDto(
    val backends: List<ToolingBackendDto> = emptyList(),
)

@Serializable
public data class SecretDto(
    val name: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
)

@Serializable
public data class SecretsListDto(
    val secrets: List<SecretDto> = emptyList(),
)

@Serializable
public data class AddSecretDto(
    val name: String,
    val value: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val scopes: List<String> = emptyList(),
)

/**
 * PATCH /api/profiles/projects/{name}/agent-settings — backend-specific
 * settings injected into agent containers at spawn (BL251).
 * alpha.28 (#243) adds [opencodeModels] multi-select pool alongside
 * the existing [opencodeModel] single-model default.
 */
@Serializable
public data class AgentSettingsDto(
    @SerialName("claude_auth_key_secret") val claudeAuthKeySecret: String = "",
    @SerialName("opencode_ollama_url") val opencodeOllamaUrl: String = "",
    @SerialName("opencode_model") val opencodeModel: String = "",
    /** alpha.28 #243 — multi-model pool; first entry is default when opencodeModel is empty. */
    @SerialName("opencode_models") val opencodeModels: List<String> = emptyList(),
)

// ---- Dashboard Cards (alpha.75, issue #132) ----
@Serializable
public data class DashboardCardDto(
    val id: String = "",
    val cs: Int = 12,
    val rs: Int? = null,
)

// ---- Session Telemetry (alpha.75 BL303 S1, issue #128) ----
@Serializable
public data class TelemetrySprintDto(
    val name: String = "",
    val id: String = "",
    val automata: String = "",
    @SerialName("automata_id") val automataId: String = "",
    val task: String = "",
    @SerialName("task_id") val taskId: String = "",
)

@Serializable
public data class TelemetryTaskDto(
    val id: String = "",
    val title: String = "",
    val status: String = "pending",
    @SerialName("duration_ms") val durationMs: Long = 0,
)

@Serializable
public data class TelemetryTestsDto(
    val pass: Int = 0,
    val fail: Int = 0,
    val skip: Int = 0,
    val total: Int = 0,
)

@Serializable
public data class GuardrailVerdictDto(
    val guardrail: String = "",
    val outcome: String = "pass",
    val summary: String = "",
)

@Serializable
public data class SessionTelemetryDto(
    @SerialName("current_task") val currentTask: String = "",
    val tool: String = "",
    val file: String = "",
    val sprint: TelemetrySprintDto? = null,
    val tasks: List<TelemetryTaskDto> = emptyList(),
    val tests: TelemetryTestsDto = TelemetryTestsDto(),
    val progress: Float = 0f,
    @SerialName("guardrail_verdicts") val guardrailVerdicts: List<GuardrailVerdictDto> = emptyList(),
    @SerialName("parent_session_id") val parentSessionId: String = "",
)

// ---- Guardrail Library + Profiles (alpha.75 BL303 S2, issue #128) ----
@Serializable
public data class GuardrailLibraryItemDto(
    val name: String = "",
    val description: String = "",
    val kind: String = "",
)

@Serializable
public data class GuardrailProfileDto(
    val id: String = "",
    val name: String = "",
    val guardrails: List<String> = emptyList(),
    @SerialName("block_on") val blockOn: List<String> = emptyList(),
    @SerialName("warn_on") val warnOn: List<String> = emptyList(),
)

@Serializable
public data class GuardrailRunResultDto(
    val verdicts: List<GuardrailVerdictDto> = emptyList(),
)

// ---- Eval Runs history (alpha.68, issue #131) ----
/** One entry in the GET /api/evals response (completed eval run). */
@Serializable
public data class EvalRunHistoryDto(
    val id: String = "",
    val name: String = "",
    val status: String = "pass",
    val score: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
internal data class EvalRunsResponseDto(
    val runs: List<EvalRunHistoryDto> = emptyList(),
)

// ---- Smoke Progress (alpha.57 BL303 S4, issue #128) ----
@Serializable
public data class SmokeProgressDto(
    @SerialName("run_id") val runId: String = "",
    val status: String = "running",
    val progress: Float = 0f,
    val passed: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
    @SerialName("started_at") val startedAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
)

// ---- T30: Channel Routing ----
@Serializable
public data class ChannelRoutingRuleDto(
    @SerialName("channel_pattern") val channelPattern: String = "",
    @SerialName("peer_name") val peerName: String = "",
    @SerialName("automata_type") val automataType: String = "",
)

@Serializable
public data class ChannelRoutingListDto(
    val rules: List<ChannelRoutingRuleDto> = emptyList(),
)

// ---- T30: File Service ----
@Serializable
public data class FileServiceMetaDto(
    val discussions: List<String> = emptyList(),
    val peers: List<String> = emptyList(),
    val root: String = "",
)

// ---- T30: Discussion Scopes ----
@Serializable
public data class DiscussionListDto(
    val count: Int = 0,
    val discussions: List<String> = emptyList(),
)

@Serializable
public data class DiscussionWriteRequestDto(
    val content: String,
)

@Serializable
public data class DiscussionWriteResponseDto(
    @SerialName("discussion_id") val discussionId: String = "",
    @SerialName("memory_id") val memoryId: Int = 0,
    val ok: Boolean = false,
)

// ---- S14b: Alert Rules ----
@Serializable
public data class AlertConditionDto(
    val metric: String = "",
    val operator: String = ">",
    val threshold: Double = 0.0,
)

@Serializable
public data class AlertActionDto(
    val kind: String = "alert",
    @SerialName("scale_target") val scaleTarget: String? = null,
    @SerialName("scale_amount") val scaleAmount: Int = 1,
)

@Serializable
public data class AlertRuleDto(
    val name: String = "",
    val description: String? = null,
    val condition: AlertConditionDto = AlertConditionDto(),
    @SerialName("source_filter") val sourceFilter: String? = null,
    @SerialName("window_seconds") val windowSeconds: Int = 60,
    val action: AlertActionDto = AlertActionDto(),
    val enabled: Boolean = true,
    @SerialName("cooldown_seconds") val cooldownSeconds: Int = 300,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
public data class AlertRulesListDto(
    val rules: List<AlertRuleDto> = emptyList(),
)

// ---- T30: Encryption Status ----
@Serializable
public data class EncryptedFileStatusDto(
    val path: String = "",
    val encrypted: Boolean = false,
    val exists: Boolean = true,
)

@Serializable
public data class EncryptionStatusDto(
    @SerialName("secure_mode") val secureMode: Boolean = false,
    val files: List<EncryptedFileStatusDto> = emptyList(),
)

// ---- Observer cards: Cooldown, Analytics, Audit ----

@Serializable
public data class CooldownStatusDto(
    val active: Boolean = false,
    @SerialName("until_unix_ms") val untilUnixMs: Long? = null,
    val reason: String? = null,
)

@Serializable
public data class AnalyticsBucketDto(
    val date: String = "",
    @SerialName("session_count") val sessionCount: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val killed: Int = 0,
)

@Serializable
public data class AnalyticsDto(
    @SerialName("success_rate") val successRate: Double? = null,
    val buckets: List<AnalyticsBucketDto> = emptyList(),
)

@Serializable
public data class AuditEntryDto(
    val ts: String? = null,
    val action: String = "",
    val actor: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val details: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
public data class AuditListDto(
    val entries: List<AuditEntryDto> = emptyList(),
)

@Serializable
public data class MatrixStatusDto(
    val connected: Boolean = false,
    val homeserver: String = "",
    @SerialName("room_id") val roomId: String = "",
    val error: String? = null,
)

@Serializable
public data class OpenCodeModelDto(
    val id: String,
    val label: String = "",
    val provider: String = "",
    @SerialName("provider_label") val providerLabel: String = "",
    val kind: String = "",
    val default: Boolean = false,
)

@Serializable
public data class OpenCodeModelsResponseDto(
    val models: List<OpenCodeModelDto> = emptyList(),
    @SerialName("default_model") val defaultModel: String = "",
)

@Serializable
public data class AlertRuleFiringDto(
    @SerialName("rule_name") val ruleName: String = "",
    @SerialName("fired_at") val firedAt: String = "",
    val value: Double = 0.0,
    val threshold: Double = 0.0,
    val pod: String? = null,
)

@Serializable
public data class AlertRuleFiringsDto(
    val firings: List<AlertRuleFiringDto> = emptyList(),
)

@Serializable
public data class CommunityPluginManifestDto(
    val name: String = "",
    val version: String = "",
    val description: String = "",
)

@Serializable
public data class CommunityPluginDto(
    val name: String = "",
    val path: String = "",
    val manifest: CommunityPluginManifestDto = CommunityPluginManifestDto(),
)

@Serializable
public data class CommunityPluginsBrowseDto(
    val registry: String = "",
    val plugins: List<CommunityPluginDto> = emptyList(),
)

@Serializable
public data class PluginInstallResponseDto(
    val status: String = "",
    val installed: String = "",
    @SerialName("from_registry") val fromRegistry: String = "",
)

@Serializable
public data class TailscaleAuthKeyDto(
    val key: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    val error: String? = null,
)

@Serializable
public data class TailscaleAclDto(
    val policy: String = "",
    @SerialName("generated_policy") val generatedPolicy: String? = null,
    val error: String? = null,
)

@Serializable
public data class RemoteServerDto(
    val name: String = "",
    val url: String = "",
    val token: String? = null,
    val enabled: Boolean = true,
    val federated: Boolean = false,
    val capabilities: List<String> = emptyList(),
)

@Serializable
public data class WebPushRegistrationDto(
    val id: String = "",
    val endpoint: String = "",
)

@Serializable
public data class WebPushRegistrationsDto(
    val registrations: List<WebPushRegistrationDto> = emptyList(),
)
