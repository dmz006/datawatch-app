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
     * Active backend name for this session. Populates the per-row
     * backend badge; was previously fetched from `/api/info` which only
     * returns the server's current backend, not the session's.
     */
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
    @SerialName("output_mode") val outputMode: String? = null,
    /**
     * Input mode. `"tmux"` (default), `"chat"`, or `"none"` for
     * read-only sessions. PWA reads at app.js:1685.
     */
    @SerialName("input_mode") val inputMode: String? = null,
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
    val parent: String? = null,
    val depth: Int = 0,
    @SerialName("is_template") val isTemplate: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val spec: String? = null,
    @SerialName("permission_mode") val permissionMode: String? = null,
    val stories: List<PrdStoryDto> = emptyList(),
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
    val name: String,
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
)

@Serializable
public data class NewPrdResponseDto(
    val id: String = "",
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
