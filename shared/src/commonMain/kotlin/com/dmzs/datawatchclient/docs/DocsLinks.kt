package com.dmzs.datawatchclient.docs

/**
 * BL414 — the single table behind every "?" help link in the Android and iOS
 * apps.
 *
 * Each key is a card or screen id (the PWA settings-section key where one
 * exists, so `work_queue`, `gc_dw`, `stats`, …; `view_*` for screen headers;
 * `dash_*` for Dashboard cards). Each value is a docs target relative to the
 * server's `docs/` tree — `file.md` or `file.md#anchor` — opened as
 * `<baseUrl>/diagrams.html#docs/<target>` ([viewerUrl]).
 *
 * Anchors follow the server docs viewer's heading-id rule (diagrams.js):
 * lowercase, every run of non-letter/digit characters → `-`, leading and
 * trailing dashes trimmed, duplicates suffixed `-2`, `-3`, ….
 *
 * Target choice, in order:
 *  1. the card's own section in the central manual (`datawatch-definitions.md`)
 *     — what the web UI's `defsLink(title)` aims at; the web UI derives the
 *     anchor from the card title, which misses whenever the manual's heading
 *     differs (the cause of "every ? opens the generic page");
 *  2. otherwise the page the web UI names for that card (its `docsPath` /
 *     header `?` link) when the server actually ships it;
 *  3. otherwise the most specific shipped page/heading for the feature.
 */
public object DocsLinks {
    /** The central manual; the docs viewer's default page. */
    public const val DEFINITIONS: String = "datawatch-definitions.md"

    private const val D = "$DEFINITIONS#"

    /** Key → docs target. Every key used by either app must be listed here. */
    public val byKey: Map<String, String> =
        mapOf(
            // ── Screen headers ─────────────────────────────────────────
            "view_sessions" to D + "sessions-list",
            "view_session_detail" to D + "inside-a-session-terminal-area",
            // Web UI links howto/new-session.md, which the server doesn't ship.
            "view_new_session" to "howto/sessions-deep-dive.md#4b-happy-path-pwa",
            "view_automata" to D + "automata",
            "view_automaton_detail" to D + "automaton-detail",
            // Web UI links howto/automata-wizard.md, which the server doesn't ship.
            "view_automata_wizard" to D + "launch-automation-form",
            "view_observer" to D + "observer",
            "view_alerts" to "howto/alerts-and-notifications.md",
            "view_dashboard" to "howto/dashboard.md",
            "view_settings" to D + "settings",
            "view_settings_general" to D + "settings-general",
            "view_settings_plugins" to D + "settings-plugins",
            "view_settings_comms" to D + "settings-comms",
            "view_settings_compute" to D + "settings-compute",
            "view_settings_automata" to D + "settings-automate",
            "view_settings_about" to D + "settings-about",
            // ── Settings → General ─────────────────────────────────────
            "security" to D + "app-only-settings-android-ios",
            "raw_config" to "operations.md#4-configuration",
            "gc_dw" to "howto/setup-and-install.md",
            "gc_autoupdate" to D + "self-update-v8-9-21",
            "gc_sess" to D + "sessions",
            "gc_summarizer" to D + "session-ai-summarizer",
            "gc_whisper" to "howto/voice-input.md",
            "test_whisper" to "howto/voice-input.md",
            "gc_notifs" to D + "notifications",
            "docs_search" to "howto/docs-as-mcp.md",
            "templates" to "api/sessions-productivity.md#templates-api-templates-bl5",
            "device_aliases" to D + "settings-general",
            "tooling" to D + "settings-general",
            "file_service" to D + "file-service-v8-3-0",
            "discussion_scopes" to D + "discussion-scopes-v8-4-0",
            // ── Settings → Comms ───────────────────────────────────────
            "comms" to D + "settings-comms",
            "comms_auth" to D + "authentication",
            "servers" to D + "remote-servers",
            "remote_servers" to D + "remote-servers",
            "cc_websrv" to "operations.md#7-network-security",
            "cc_mcpsrv" to "mcp.md#remote-setup-http-sse",
            "proxy" to D + "proxy-resilience",
            "routing_rules" to D + "routing-rules",
            "backends" to D + "communication-configuration",
            "channel_routing" to D + "channel-routing-v8-3-0",
            "fedpeers" to "howto/federation-cbac.md#pwa-federation-peers-panel",
            "push_notifications" to D + "push-notifications-v8-2-0",
            "cert_install" to "pwa-setup.md#installing-the-ca-certificate-self-signed-only",
            // ── Settings → Compute ─────────────────────────────────────
            "llms" to D + "llm-registry",
            "llm_config" to D + "llm-configuration-legacy",
            "compute_nodes" to D + "compute-nodes",
            "costrates" to D + "cost-rates-usd-1k-tokens",
            "gc_clusterprofiles" to D + "cluster-profiles",
            "lc_memory" to "memory.md#configuration",
            "lc_goose" to "llm-backends.md#goose",
            "lc_opencode" to "llm-backends.md#opencode",
            "lc_websearch" to D + "web-search-multi-provider-registry",
            "lc_web_search" to D + "web-search-multi-provider-registry",
            "websearch_providers" to D + "web-search-multi-provider-registry",
            "lc_rtk" to "rtk-integration.md#configuration",
            "lc_vision" to D + "vision-system",
            "gc_agents" to D + "container-workers",
            "detection" to D + "detection-filters",
            "alert_rules" to "howto/alert-rules.md",
            "cmds" to "llm-backends.md#saved-commands",
            "filters" to "llm-backends.md#output-filters",
            "tailscale_config" to D + "tailscale-mesh-status-configuration",
            "tailscale_status" to D + "tailscale-mesh-status-configuration",
            "exit_hooks" to D + "session-exit-hooks",
            "work_queue" to D + "work-queue",
            "secrets_store" to "howto/secrets-manager.md",
            "secrets_status" to "howto/secrets-manager.md#vault-status",
            "observer_quicklink" to "howto/federated-observer.md",
            // ── Settings → Automata ────────────────────────────────────
            "identity" to "howto/identity-and-telos.md",
            "algorithm" to "howto/algorithm-mode.md",
            "evals" to "howto/evals.md",
            "council" to "howto/council-mode.md",
            "gc_projectprofiles" to D + "project-profiles",
            "pipelines" to "howto/pipeline-chaining.md",
            "obs_pipelines" to "howto/pipeline-chaining.md",
            "orchestrator_graphs" to "howto/automata-orchestrator.md",
            "automata_scan" to "howto/guardrail-library.md",
            "automata_autonomous" to "api/autonomous.md#configuration",
            "guardrail_library_list" to "howto/guardrail-library.md#built-in-guardrails",
            "automata_guardrail_profiles" to "howto/guardrail-library.md#managing-guardrail-profiles",
            "gc_autonomous" to "howto/autonomous-planning.md",
            "automata_skills" to "howto/skills-sync.md",
            "automata_type_registry" to D + "settings-automate",
            "gc_pipeline" to "howto/pipeline-chaining.md",
            // Web UI names howto/prd-dag-orchestrator.md, which the server doesn't ship.
            "gc_orchestrator" to "howto/automata-orchestrator.md",
            // ── Settings → Plugins ─────────────────────────────────────
            "gc_plugins" to "api/plugins.md",
            "plugins_list" to D + "plugin-manager",
            "community_plugins" to "api/plugins.md",
            // ── Settings → About ───────────────────────────────────────
            "about" to D + "settings-about",
            "language" to D + "app-only-settings-android-ios",
            "theme" to D + "app-only-settings-android-ios",
            "api" to D + "api",
            "mcp_channel" to D + "mcp-channel-bridge-diagnostics-v8-10-16",
            "mcp_tools" to D + "mcp-tools",
            "daemon_update" to D + "self-update-v8-9-21",
            "hot_reload" to "howto/daemon-operations.md",
            "subsystem_reload" to "howto/daemon-operations.md",
            "daemon_restart" to "howto/daemon-operations.md",
            "kill_orphans" to D + "orphaned-tmux-sessions",
            "encryption_status" to "encryption.md",
            "encryption" to "encryption.md",
            "network_interfaces" to "operations.md#interface-configuration-summary",
            // ── Observer ───────────────────────────────────────────────
            "stats" to "operations.md#system-statistics",
            "sysgrid" to "operations.md#system-statistics",
            // System Statistics sub-panels with their own docs (others → `stats`).
            "stats_certificates" to "howto/letsencrypt-acme.md",
            "stats_envelopes" to D + "process-envelopes",
            "stats_rtk" to "rtk-integration.md#where-to-see-rtk-stats",
            "stats_memory" to "memory.md#monitoring",
            "stats_ollama" to "memory.md#ollama-server-monitoring-bl71",
            "stats_web_search" to D + "web-search-multi-provider-registry",
            "ebpf_status" to D + "ebpf-per-process-net",
            "ebpf_network" to D + "ebpf-per-process-net",
            "obs_plugins" to D + "installed-plugins",
            "peer_resources" to D + "federated-peers",
            "cluster_nodes" to "api/observer.md#deployment-shapes",
            "channel_diag" to D + "mcp-channel-bridge-diagnostics-v8-10-16",
            "comm_backends" to D + "communication-configuration",
            "membrowser" to "memory.md",
            "memscopes" to "memory.md",
            "memmaint" to "memory.md#advanced-features",
            "schedules" to D + "recurring-named-schedules",
            "cooldown" to D + "global-cooldown",
            "analytics" to D + "session-analytics",
            "audit" to D + "audit-log",
            "kg" to D + "knowledge-graph",
            "daemonlog" to D + "daemon-log",
            "observer_peers" to D + "federated-peers",
            // ── Dashboard ──────────────────────────────────────────────
            "dashboard_cards" to "howto/dashboard.md",
            "dash_orbital" to D + "session-constellation",
            "dash_ekg" to D + "ekg-waveform",
        )

    /**
     * Messaging channel type (`ChannelBackendSchemas` / `/api/channels` kind)
     * → its section of messaging-backends.md.
     */
    public val channelTypes: Map<String, String> =
        mapOf(
            "signal" to "messaging-backends.md#signal",
            "telegram" to "messaging-backends.md#telegram",
            "matrix" to "messaging-backends.md#matrix",
            "discord" to "messaging-backends.md#discord",
            "slack" to "messaging-backends.md#slack",
            "twilio" to "messaging-backends.md#twilio-sms",
            "ntfy" to "messaging-backends.md#ntfy",
            "email" to "messaging-backends.md#email-smtp",
            "github_webhook" to "messaging-backends.md#github-webhook",
            "webhook" to "messaging-backends.md#generic-webhook",
            "dns" to "messaging-backends.md#dns-channel-covert",
            "dns_channel" to "messaging-backends.md#dns-channel-covert",
        )

    /** LLM backend (lowercase, `-` → `_`) → its section of llm-backends.md. */
    public val llmBackends: Map<String, String> =
        mapOf(
            "claude_code" to "llm-backends.md#claude-code-default",
            "claudecode" to "llm-backends.md#claude-code-default",
            "aider" to "llm-backends.md#aider",
            "goose" to "llm-backends.md#goose",
            "gemini" to "llm-backends.md#gemini-cli",
            "opencode" to "llm-backends.md#opencode",
            "opencode_acp" to "llm-backends.md#opencode-acp-mode",
            "opencode_prompt" to "llm-backends.md#opencode",
            "ollama" to "llm-backends.md#ollama-local-models",
            "openwebui" to "llm-backends.md#openwebui",
            "shell" to "llm-backends.md#shell-custom-script",
        )

    /**
     * Docs target for [key], or null when the key is unknown (callers then
     * show no "?" rather than a link to the top of the manual).
     */
    public fun forKey(key: String): String? =
        byKey[key] ?: when {
            // Observer → System Statistics sub-panels (`stats_<panel>`).
            key.startsWith("stats_") -> byKey["stats"]
            // Settings → Compute → per-backend config dialog (`lc_backend_<name>`).
            key.startsWith("lc_backend_") -> forLlmBackend(key.removePrefix("lc_backend_"))
            // Settings → Comms → global per-type channel config (`cc_global_<type>`).
            key.startsWith("cc_global_") -> forChannelType(key.removePrefix("cc_global_"))
            // Dashboard cards without their own manual section.
            key.startsWith("dash_") -> byKey["view_dashboard"]
            else -> null
        }

    /** Messaging-backend docs for a channel [type]; Communication Configuration when unknown. */
    public fun forChannelType(type: String): String =
        channelTypes[type.lowercase()] ?: (D + "communication-configuration")

    /** LLM-backend docs for a backend [name]; the LLM Registry section when unknown. */
    public fun forLlmBackend(name: String): String =
        llmBackends[name.lowercase().replace('-', '_')] ?: (D + "llm-registry")

    /** Full docs-viewer URL for [target] on the server at [baseUrl]. */
    public fun viewerUrl(
        baseUrl: String,
        target: String,
    ): String = baseUrl.trimEnd('/') + "/diagrams.html#docs/" + target

    /**
     * The server docs viewer's heading-id slug (diagrams.js): lowercase, every
     * run of characters that are not letters or digits → `-`, ends trimmed.
     */
    public fun slug(heading: String): String {
        val sb = StringBuilder()
        var lastDash = false
        for (ch in heading.lowercase()) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch)
                lastDash = false
            } else if (!lastDash) {
                sb.append('-')
                lastDash = true
            }
        }
        return sb.toString().trim('-')
    }
}
