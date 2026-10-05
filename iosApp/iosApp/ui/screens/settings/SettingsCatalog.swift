import Foundation

// Settings parity (D31b): the PWA's six settings tabs become six grouped-list
// sections; each PWA card is a row that pushes a detail screen. Card order
// follows the PWA `_cardOrder` map + DOM order (app.js renderSettingsView).
// Config field schemas are ported 1:1 from PWA GENERAL/COMMS/LLM_CONFIG_FIELDS.

/// How a config field renders and how its value is typed on save
/// (`IosSettingsConfig.write(kind:)`).
enum SettingsFieldKind: String {
    case toggle, number, text, password, select, interface, llm, csv, lines, readonly
}

struct SettingsField: Identifiable {
    let key: String
    let label: String
    let kind: SettingsFieldKind
    var options: [String] = []
    var placeholder: String = ""

    var id: String { key }

    /// Wire kind for `IosSettingsConfig.write`.
    var writeKind: String {
        switch kind {
        case .toggle: return "toggle"
        case .number: return "number"
        case .csv: return "csv"
        case .lines: return "lines"
        default: return "text"
        }
    }

    /// PWA RESTART_FIELDS — saving these shows the inline "Restart now" link (D57b).
    var needsRestart: Bool { SettingsCatalog.restartKeys.contains(key) }

    static func toggle(_ key: String, _ label: String) -> SettingsField {
        SettingsField(key: key, label: label, kind: .toggle)
    }
    static func number(_ key: String, _ label: String, _ placeholder: String = "") -> SettingsField {
        SettingsField(key: key, label: label, kind: .number, placeholder: placeholder)
    }
    static func text(_ key: String, _ label: String, _ placeholder: String = "") -> SettingsField {
        SettingsField(key: key, label: label, kind: .text, placeholder: placeholder)
    }
    static func secret(_ key: String, _ label: String, _ placeholder: String = "") -> SettingsField {
        SettingsField(key: key, label: label, kind: .password, placeholder: placeholder)
    }
    static func select(_ key: String, _ label: String, _ options: [String]) -> SettingsField {
        SettingsField(key: key, label: label, kind: .select, options: options)
    }
}

/// Extra in-card action shown under a config form.
enum SettingsConfigExtra {
    case none
    case summarizerTest
    /// PWA loadAutomataSettingsPanel scan config (Autonomous Config card).
    case scanDefaults
}

/// Bespoke (non-schema) cards.
enum SettingsCustomCard {
    case notifications, security, docsSearch, rawConfig
    case servers, commBackends, push
    case alertRules, savedCommands, outputFilters
    case identity, automataTypes, pipelineManager, orchestratorGraphs, algorithmMode
    case about, apiLinks, mcpTools, mcpChannel, subsystemReload, encryption
    case exitHooks, workQueue
}

/// Add-entry form spec for list cards (keys map to `IosSettingsLists.create`).
struct SettingsAddField: Identifiable {
    let key: String
    let label: String
    var secure: Bool = false
    var placeholder: String = ""
    var multiline: Bool = false
    var id: String { key }
}

enum SettingsCardContent {
    case config([SettingsField], SettingsConfigExtra)
    /// `kind` for IosSettingsLists; `add` = add-form fields (empty = no add);
    /// `cardActions` = labels for IosSettingsLists.cardAction by index.
    case list(kind: String, add: [SettingsAddField], cardActions: [String])
    case custom(SettingsCustomCard)
}

struct SettingsCard: Identifiable {
    let id: String
    let title: String
    let icon: String
    let content: SettingsCardContent

    /// Central-manual anchor (PWA defsLink slug rule: non-alnum → "-").
    var docsAnchor: String {
        let lowered = title.lowercased()
        var out = ""
        var lastDash = false
        for ch in lowered {
            if ch.isLetter || ch.isNumber {
                out.append(ch)
                lastDash = false
            } else if !lastDash {
                out.append("-")
                lastDash = true
            }
        }
        return out.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }

    static func config(_ id: String, _ title: String, _ icon: String, _ fields: [SettingsField],
                       extra: SettingsConfigExtra = .none) -> SettingsCard {
        SettingsCard(id: id, title: title, icon: icon, content: .config(fields, extra))
    }
    static func list(_ id: String, _ title: String, _ icon: String, kind: String,
                     add: [SettingsAddField] = [], cardActions: [String] = []) -> SettingsCard {
        SettingsCard(id: id, title: title, icon: icon, content: .list(kind: kind, add: add, cardActions: cardActions))
    }
    static func custom(_ id: String, _ title: String, _ icon: String, _ c: SettingsCustomCard) -> SettingsCard {
        SettingsCard(id: id, title: title, icon: icon, content: .custom(c))
    }
}

struct SettingsGroup: Identifiable {
    let id: String
    let title: String
    let cards: [SettingsCard]
}

enum SettingsCatalog {
    static let restartKeys: Set<String> = [
        "server.host", "server.port", "server.tls", "server.tls_auto_generate", "server.tls_cert", "server.tls_key",
        "mcp.enabled", "mcp.sse_enabled", "mcp.sse_host", "mcp.sse_port", "mcp.tls_enabled",
        "dns_channel.enabled", "dns_channel.listen", "dns_channel.domain",
    ]

    /// PWA tab order: General · Plugins · Comms · Compute · Automata · About.
    static let groups: [SettingsGroup] = [
        SettingsGroup(id: "general", title: "General", cards: general),
        SettingsGroup(id: "plugins", title: "Plugins", cards: plugins),
        SettingsGroup(id: "comms", title: "Comms", cards: comms),
        SettingsGroup(id: "compute", title: "Compute", cards: compute),
        SettingsGroup(id: "automata", title: "Automata", cards: automata),
        SettingsGroup(id: "about", title: "About", cards: about),
    ]

    // MARK: General

    static let general: [SettingsCard] = [
        .config("gc_dw", "datawatch", "gearshape", [
            .select("session.log_level", "Log level", ["info", "debug", "warn", "error"]),
            .toggle("server.auto_restart_on_config", "Auto-restart on config save"),
            SettingsField(key: "session.backend_family", label: "Default LLM backend", kind: .llm),
        ]),
        .config("gc_autoupdate", "Auto-Update", "arrow.down.circle", [
            .toggle("update.enabled", "Enabled"),
            .select("update.schedule", "Schedule", ["hourly", "daily", "weekly"]),
            .text("update.time_of_day", "Time of day (HH:MM)"),
        ]),
        .config("gc_sess", "Sessions", "terminal", [
            .number("session.max_sessions", "Max concurrent sessions"),
            .number("session.reserved_interactive", "Slots reserved for interactive sessions", "1"),
            .number("session.capacity_wait_seconds", "Interactive session capacity wait (sec)", "8"),
            .number("session.input_idle_timeout", "Input idle timeout (sec)"),
            .number("session.tail_lines", "Tail lines"),
            .number("session.alert_context_lines", "Alert context lines", "10"),
            .text("session.default_project_dir", "Default project dir"),
            .text("session.root_path", "File browser root path"),
            .number("session.console_cols", "Default console width (cols)", "80"),
            .number("session.console_rows", "Default console height (rows)", "24"),
            .number("server.recent_session_minutes", "Recent session visibility (min)"),
            .toggle("session.auto_git_init", "Auto git init"),
            .toggle("session.auto_git_commit", "Auto git commit"),
            .toggle("session.kill_sessions_on_exit", "Kill sessions on exit"),
            .number("session.mcp_max_retries", "MCP auto-retry limit"),
            .number("session.schedule_settle_ms", "Scheduled command settle (ms)"),
            .toggle("server.suppress_active_toasts", "Suppress toasts for active session"),
        ]),
        .config("gc_summarizer", "Session AI Summarizer", "text.quote", [
            .toggle("session.summarizer.enabled", "Summarize last response"),
            SettingsField(key: "session.summarizer.llm_ref", label: "Summarizer LLM", kind: .llm),
            .text("session.summarizer.model", "Summarizer model"),
        ], extra: .summarizerTest),
        .config("gc_whisper", "Voice Input (Whisper)", "waveform", [
            .toggle("whisper.enabled", "Enable voice transcription"),
            .select("whisper.backend", "Backend — openai / ollama / openwebui reuse the endpoint + API key already configured for that LLM backend",
                    ["whisper", "openai", "openai_compat", "openwebui", "ollama"]),
            .text("whisper.model", "Model (tiny/base/small/medium/large; or remote model name)", "base"),
            SettingsField(key: "whisper.language", label: "Language — tracks the app language. Override via CLI/YAML if needed.", kind: .readonly),
            .text("whisper.venv_path", "Python venv path (local whisper only)", ".venv"),
        ]),
        .custom("gc_notifs", "Notifications", "bell", .notifications),
        .list("templates", "Session Templates", "doc.on.doc", kind: "session_templates", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "backend", label: "Backend"),
            SettingsAddField(key: "project_dir", label: "Project dir"),
            SettingsAddField(key: "effort", label: "Effort", placeholder: "quick / normal / thorough"),
            SettingsAddField(key: "description", label: "Description"),
        ]),
        .list("device_aliases", "Device Aliases", "iphone", kind: "device_aliases", add: [
            SettingsAddField(key: "alias", label: "Alias"),
            SettingsAddField(key: "server", label: "Server"),
        ]),
        .list("tooling", "Backend Artifact Lifecycle", "shippingbox", kind: "tooling"),
        .custom("docs_search", "Docs Search", "magnifyingglass", .docsSearch),
        .list("file_service", "File Service", "folder", kind: "file_service"),
        .list("discussion_scopes", "Discussion Scopes", "bubble.left.and.bubble.right", kind: "discussions", add: [
            SettingsAddField(key: "id", label: "Scope ID", placeholder: "e.g. design-review"),
        ]),
        // iOS additions (decisions D79a / platform security)
        .custom("security", "Security", "lock", .security),
        .custom("raw_config", "Raw Config", "curlybraces", .rawConfig),
    ]

    // MARK: Plugins

    static let plugins: [SettingsCard] = [
        .config("gc_plugins", "Plugin framework", "puzzlepiece", [
            .toggle("plugins.enabled", "Enable subprocess plugin framework"),
            .text("plugins.dir", "Plugin discovery directory", "~/.datawatch/plugins"),
            .number("plugins.timeout_ms", "Invocation timeout (ms)", "2000"),
        ]),
        .list("plugins_list", "Plugin Manager", "puzzlepiece.extension", kind: "plugins", cardActions: ["Reload plugins"]),
    ]

    // MARK: Comms

    static let comms: [SettingsCard] = [
        .config("comms_auth", "Authentication", "key", [
            .secret("server.token", "Server bearer token"),
            .secret("mcp.token", "MCP SSE bearer token"),
        ]),
        .custom("servers", "Servers", "server.rack", .servers),
        .list("remote_servers", "Remote Servers", "network", kind: "remote_servers", add: [
            SettingsAddField(key: "name", label: "Name", placeholder: "e.g. prod, pi"),
            SettingsAddField(key: "url", label: "URL", placeholder: "https://host:8443"),
            SettingsAddField(key: "token", label: "Token (optional)", secure: true),
        ]),
        .list("fedpeers", "Federation Peers", "person.3", kind: "fed_peers", add: [
            SettingsAddField(key: "name", label: "Name", placeholder: "peer-alpha"),
            SettingsAddField(key: "url", label: "URL", placeholder: "http://198.51.100.2:8080"),
            SettingsAddField(key: "token", label: "Token", secure: true, placeholder: "(optional bearer token)"),
            SettingsAddField(key: "capabilities", label: "Capabilities", placeholder: "federation-peer…"),
            SettingsAddField(key: "channel_identity", label: "Channel Identity", placeholder: "channel-id-or-pattern"),
        ]),
        .custom("backends", "Communication Configuration", "antenna.radiowaves.left.and.right", .commBackends),
        .config("cc_websrv", "Web Server", "globe", [
            .toggle("server.enabled", "Enabled"),
            SettingsField(key: "server.host", label: "Bind interface", kind: .interface),
            .number("server.port", "Port"),
            .toggle("server.tls", "TLS enabled"),
            .number("server.tls_port", "TLS port", "8443"),
            .toggle("server.tls_auto_generate", "TLS auto-generate cert"),
            .text("server.tls_cert", "TLS cert path"),
            .text("server.tls_key", "TLS key path"),
            .number("server.channel_port", "Channel port (0=random)"),
        ]),
        .config("cc_mcpsrv", "MCP Server", "cpu", [
            .toggle("mcp.enabled", "Enabled (stdio)"),
            .toggle("mcp.sse_enabled", "SSE enabled (HTTP)"),
            SettingsField(key: "mcp.sse_host", label: "SSE bind interface", kind: .interface),
            .number("mcp.sse_port", "SSE port"),
            .toggle("mcp.tls_enabled", "TLS enabled"),
            .toggle("mcp.tls_auto_generate", "TLS auto-generate cert"),
            .text("mcp.tls_cert", "TLS cert path"),
            .text("mcp.tls_key", "TLS key path"),
        ]),
        .config("proxy", "Proxy Resilience", "arrow.triangle.2.circlepath", [
            .toggle("proxy.enabled", "Enabled"),
            .number("proxy.request_timeout", "Request timeout (sec)", "10"),
            .number("proxy.health_interval", "Health interval (sec)", "30"),
            .number("proxy.circuit_breaker_threshold", "Circuit-breaker threshold", "3"),
            .number("proxy.circuit_breaker_reset", "Circuit-breaker reset (sec)", "30"),
            .number("proxy.offline_queue_size", "Offline queue size", "100"),
        ]),
        .list("routing_rules", "Routing Rules", "arrow.triangle.branch", kind: "routing_rules", add: [
            SettingsAddField(key: "pattern", label: "Pattern"),
            SettingsAddField(key: "backend", label: "Backend"),
            SettingsAddField(key: "description", label: "Description"),
        ]),
        .list("channel_routing", "Channel Routing", "arrow.left.arrow.right", kind: "channel_routing", add: [
            SettingsAddField(key: "channel_pattern", label: "Channel pattern"),
            SettingsAddField(key: "peer_name", label: "Peer name"),
            SettingsAddField(key: "automata_type", label: "Automata type"),
        ]),
        .custom("push_notifications", "Push Notifications", "app.badge", .push),
    ]

    // MARK: Compute

    static let compute: [SettingsCard] = [
        .list("compute_nodes", "Compute Nodes", "server.rack", kind: "compute_nodes"),
        .list("llms", "LLMs", "brain", kind: "llms"),
        .config("lc_memory", "Episodic Memory", "memorychip", [
            .toggle("memory.enabled", "Enable memory system"),
            .select("memory.backend", "Storage backend", ["sqlite", "postgres"]),
            .select("memory.embedder", "Embedding provider", ["ollama", "openai"]),
            .text("memory.embedder_model", "Embedding model", "nomic-embed-text"),
            .text("memory.embedder_host", "Compute Node"),
            .number("memory.top_k", "Search results (top-K)"),
            .toggle("memory.auto_save", "Auto-save session summaries"),
            .toggle("memory.learnings_enabled", "Extract task learnings"),
            .select("memory.storage_mode", "Storage mode", ["summary", "verbatim"]),
            .toggle("memory.entity_detection", "Auto entity detection"),
            .toggle("memory.session_awareness", "Inject memory instructions into sessions"),
            .toggle("memory.session_broadcast", "Broadcast session summaries to active sessions"),
            .toggle("memory.auto_hooks", "Auto-install Claude Code hooks per session"),
            .number("memory.hook_save_interval", "Hook save interval (messages)"),
            .number("memory.retention_days", "Retention days (0 = forever)"),
            .text("memory.db_path", "SQLite database path", "~/.datawatch/memory.db"),
            .secret("memory.postgres_url", "PostgreSQL URL (enterprise)", "postgres://user:pass@host/db"),
        ]),
        .list("costrates", "Cost Rates (USD / 1K tokens)", "dollarsign.circle", kind: "cost_rates", add: [
            SettingsAddField(key: "model", label: "Model"),
            SettingsAddField(key: "in_per_k", label: "Input USD / 1K", placeholder: "0.003"),
            SettingsAddField(key: "out_per_k", label: "Output USD / 1K", placeholder: "0.015"),
        ]),
        .config("lc_rtk", "RTK (Token Savings)", "gauge.with.dots.needle.33percent", [
            .toggle("rtk.enabled", "Enable RTK integration"),
            .text("rtk.binary", "RTK binary path", "rtk"),
            .toggle("rtk.show_savings", "Show token savings in stats"),
            .toggle("rtk.auto_init", "Auto-init hooks if missing"),
            .toggle("rtk.auto_update", "Auto-update RTK binary"),
            .number("rtk.update_check_interval", "Update check interval (sec, 0=off)", "86400"),
            .number("rtk.discover_interval", "Discover interval (sec, 0=off)", "0"),
        ]),
        .list("gc_clusterprofiles", "Cluster Profiles", "square.stack.3d.up", kind: "cluster_profiles"),
        .config("gc_agents", "Container Workers", "shippingbox.circle", [
            .text("agents.image_prefix", "Container image prefix", "datawatch/worker"),
            .text("agents.image_tag", "Container image tag", "latest"),
            .text("agents.docker_bin", "Docker binary path", "docker"),
            .text("agents.kubectl_bin", "kubectl binary path", "kubectl"),
            .text("agents.callback_url", "Worker callback URL (worker → daemon)"),
            .number("agents.bootstrap_token_ttl_seconds", "Bootstrap token TTL (sec)", "300"),
            .number("agents.worker_bootstrap_deadline_seconds", "Worker bootstrap deadline (sec)", "120"),
        ]),
        .config("tailscale_config", "Tailscale Configuration", "point.3.filled.connected.trianglepath.dotted", [
            .toggle("tailscale.enabled", "Sidecar enabled"),
            .text("tailscale.coordinator_url", "Coordinator URL (headscale)"),
            .secret("tailscale.auth_key", "Auth key (or ${secret:name})"),
            .secret("tailscale.api_key", "Admin API key (or ${secret:name})"),
            .text("tailscale.image", "Sidecar image"),
        ]),
        .list("tailscale_status", "Mesh Status", "dot.radiowaves.left.and.right", kind: "tailscale",
              cardActions: ["Generate ACL", "Generate & Push ACL"]),
        .config("lc_goose", "Goose (Block)", "bird", [
            .toggle("goose.enabled", "Enable Goose backend"),
            .text("goose.binary", "Goose binary path", "goose"),
            .text("goose.provider", "Provider (e.g. anthropic, openai, google)", "anthropic"),
            .text("goose.model", "Model (e.g. claude-sonnet-4-6)"),
            .secret("goose.api_key_ref", "API key (literal or ${secret:name})", "${secret:goose-api-key}"),
            .toggle("goose.channel_enabled", "MCP channel bridge (connect Goose to this daemon)"),
        ]),
        .config("lc_opencode", "OpenCode", "chevron.left.forwardslash.chevron.right", [
            .text("opencode.default_model", "Default model (e.g. opencode/big-pickle)", "opencode/big-pickle"),
        ]),
        .config("lc_web_search", "Web Search", "magnifyingglass.circle", [
            .toggle("web_search.enabled", "Enable web search injection (opencode + goose sessions)"),
            .toggle("web_search.cache_enabled", "Internal result cache (reduces paid-API usage)"),
            .number("web_search.cache_ttl_seconds", "Default cache TTL (seconds)", "900"),
        ]),
        .config("lc_vision", "Vision (Image Descriptions)", "eye", [
            .toggle("vision.enabled", "Enable vision backend (image attachment descriptions)"),
            .select("vision.backend", "Backend", ["ollama", "openai", "openai_compat"]),
            .text("vision.endpoint", "Endpoint URL", "http://localhost:11434"),
            .secret("vision.api_key", "API key (required for openai; literal or ${secret:name})", "${secret:openai-key}"),
            .text("vision.model", "Model (must be vision-capable)", "llava"),
            .text("vision.default_prompt", "Default prompt (overrides built-in)", "Describe this image concisely."),
            .number("vision.max_image_bytes", "Max image size bytes (0 = 10 MB)", "0"),
        ]),
        .list("websearch_providers", "Web Search Providers", "list.bullet.rectangle", kind: "web_search_providers", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "type", label: "Type", placeholder: "searxng / brave"),
            SettingsAddField(key: "url", label: "SearXNG URL", placeholder: "http://localhost:8888"),
            SettingsAddField(key: "engine", label: "Engine"),
            SettingsAddField(key: "api_key", label: "API key", secure: true, placeholder: "literal or ${secret:name}"),
            SettingsAddField(key: "num_results", label: "Default results", placeholder: "10"),
            SettingsAddField(key: "priority", label: "Priority", placeholder: "0"),
            SettingsAddField(key: "cache_ttl_seconds", label: "Cache TTL (seconds)", placeholder: "0"),
        ]),
        .list("secrets_store", "Secrets Store", "lock.shield", kind: "secrets", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "value", label: "Value", secure: true),
            SettingsAddField(key: "description", label: "Description"),
            SettingsAddField(key: "tags", label: "Tags (comma-separated)"),
        ]),
        .custom("alert_rules", "Alert Rules", "bell.badge", .alertRules),
        .custom("cmds", "Saved Commands", "text.badge.star", .savedCommands),
        .config("detection", "Detection Filters", "eye.trianglebadge.exclamationmark", [
            SettingsField(key: "detection.prompt_patterns", label: "Prompt Patterns — substrings that indicate waiting for input (one per line)", kind: .lines),
            SettingsField(key: "detection.completion_patterns", label: "Completion Patterns — session completed markers (one per line)", kind: .lines),
            SettingsField(key: "detection.rate_limit_patterns", label: "Rate Limit Patterns — rate limit hit markers (one per line)", kind: .lines),
            SettingsField(key: "detection.input_needed_patterns", label: "Input Needed — explicit input-needed protocol markers (one per line)", kind: .lines),
            .number("detection.prompt_debounce", "Prompt debounce (sec)", "3"),
            .number("detection.notify_cooldown", "Notify cooldown (sec)", "15"),
            .number("detection.alert_settle", "Alert settle (sec)", "45"),
            .number("detection.alert_repeat", "Alert repeat (sec)", "300"),
        ]),
        .custom("filters", "Output Filters", "line.3.horizontal.decrease.circle", .outputFilters),
        // PWA _cardOrder compute: exit_hooks 215, Work Queue follows in DOM order.
        .custom("exit_hooks", "Exit Hooks", "arrow.uturn.backward.circle", .exitHooks),
        .custom("work_queue", "Work Queue", "tray.full", .workQueue),
    ]

    // MARK: Automata

    static let automata: [SettingsCard] = [
        .custom("identity", "Identity", "person.text.rectangle", .identity),
        .custom("algorithm", "Algorithm Mode", "dial.medium", .algorithmMode),
        .list("evals", "Evals", "checkmark.seal", kind: "evals"),
        .list("council", "Council Mode", "person.3.sequence", kind: "council_personas", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "description", label: "Description"),
            SettingsAddField(key: "prompt", label: "System prompt", multiline: true),
        ]),
        .config("gc_autonomous", "Autonomous Automata planning", "wand.and.stars", [
            .toggle("autonomous.enabled", "Enable autonomous loop"),
            .number("autonomous.poll_interval_seconds", "Poll interval (sec)", "30"),
            .number("autonomous.max_parallel_tasks", "Max parallel tasks", "3"),
            .toggle("autonomous.capacity_enabled", "Capacity-aware admission (wait for free slots instead of failing)"),
            .number("autonomous.capacity_wait_timeout_seconds", "Capacity wait timeout (sec, 0=4h)", "14400"),
            .number("autonomous.capacity_gpu_util_pct", "Hold tasks while node GPU is above (% util, 0=off)", "0"),
            SettingsField(key: "autonomous.planning_backend", label: "Planning backend", kind: .llm),
            .text("autonomous.planning_model", "Planning model"),
            .number("autonomous.planning_timeout_seconds", "Planning timeout (sec, 0=effort default)", "0"),
            SettingsField(key: "autonomous.verification_backend", label: "Verification backend", kind: .llm),
            .text("autonomous.verification_model", "Verification model"),
            SettingsField(key: "autonomous.verification_backends", label: "Verification backends (load-balance, comma-separated)", kind: .csv),
            .number("autonomous.auto_fix_retries", "Auto-fix retries", "1"),
            .number("autonomous.verifier_diff_max_bytes", "Verifier diff max bytes", "0"),
            .toggle("autonomous.security_scan", "Run security scan before commit"),
            .number("autonomous.max_recursion_depth", "Max recursion depth (0 disables spawn-automaton)", "5"),
            .toggle("autonomous.auto_approve_children", "Auto-approve spawned child automata"),
            SettingsField(key: "autonomous.per_task_guardrails", label: "Per-task guardrails", kind: .csv, placeholder: "rules, security"),
            SettingsField(key: "autonomous.per_story_guardrails", label: "Per-story guardrails", kind: .csv, placeholder: "release-readiness"),
            .toggle("autonomous.per_story_approval", "Per-story approval gate (each story needs explicit approve)"),
            .toggle("autonomous.continue_on_story_failure", "Continue past a failed story instead of halting (default: halt)"),
            .toggle("autonomous.default_quality_gates.enabled", "Quality gates enabled (default for all automata)"),
            .text("autonomous.default_quality_gates.test_command", "Quality gate test command", "go test ./..."),
            .number("autonomous.default_quality_gates.timeout", "Quality gate timeout (seconds, 0=no limit)", "0"),
            .toggle("autonomous.default_quality_gates.block_on_regression", "Block task on test regression"),
            .toggle("autonomous.injection_guard", "Prompt injection guard (warn on suspicious automaton/task specs)"),
            .toggle("autonomous.block_on_injection", "Block automaton/task create when injection phrases detected"),
        ]),
        .config("gc_orchestrator", "Automata-DAG orchestrator", "point.3.connected.trianglepath.dotted", [
            .toggle("orchestrator.enabled", "Enable Automata-DAG orchestrator"),
            SettingsField(key: "orchestrator.guardrail_backend", label: "Guardrail backend", kind: .llm),
            .text("orchestrator.guardrail_model", "Guardrail model"),
            .number("orchestrator.guardrail_timeout_ms", "Guardrail timeout (ms)", "120000"),
            .number("orchestrator.max_parallel_prds", "Max parallel automata", "2"),
        ]),
        .config("gc_pipeline", "Pipelines (Session Chaining)", "link", [
            .number("pipeline.max_parallel", "Max parallel tasks (0 = default 3)", "3"),
            .text("pipeline.default_backend", "Default backend (empty = session default)"),
        ]),
        .list("automata_scan", "Guardrail Library", "shield.lefthalf.filled", kind: "guardrail_library"),
        .config("automata_autonomous", "Autonomous Config", "slider.horizontal.3", [
            .number("autonomous.poll_interval_seconds", "Poll interval (s)", "30"),
            .number("autonomous.max_parallel_tasks", "Max parallel tasks", "3"),
            .number("autonomous.auto_fix_retries", "Auto-fix retries", "0"),
        ], extra: .scanDefaults),
        .list("automata_guardrail_profiles", "Guardrail Profiles", "shield.checkered", kind: "guardrail_profiles", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "guardrails", label: "Guardrails (comma-separated)", placeholder: "rules, security"),
            SettingsAddField(key: "block_on", label: "Block on (comma-separated)"),
            SettingsAddField(key: "warn_on", label: "Warn on (comma-separated)"),
        ]),
        .list("automata_skills", "Skill Registries", "books.vertical", kind: "skill_registries", add: [
            SettingsAddField(key: "name", label: "Name"),
            SettingsAddField(key: "url", label: "Git URL"),
            SettingsAddField(key: "branch", label: "Branch", placeholder: "main"),
        ]),
        .custom("automata_type_registry", "Type Registry", "square.stack.3d.up", .automataTypes),
        .list("gc_projectprofiles", "Project Profiles", "folder.badge.gearshape", kind: "project_profiles"),
        .custom("pipelines", "Pipeline Manager", "arrow.triangle.branch", .pipelineManager),
        .custom("orchestrator_graphs", "Automata Orchestrator", "point.3.connected.trianglepath.dotted", .orchestratorGraphs),
    ]

    // MARK: About

    static let about: [SettingsCard] = [
        .custom("about", "About", "info.circle", .about),
        .custom("api", "API", "link", .apiLinks),
        .custom("mcp_tools", "MCP Tools", "wrench.and.screwdriver", .mcpTools),
        .custom("mcp_channel", "MCP Channel", "point.topleft.down.curvedto.point.bottomright.up", .mcpChannel),
        .custom("subsystem_reload", "Subsystem Reload", "arrow.clockwise", .subsystemReload),
        .custom("encryption", "Encryption Status", "lock.doc", .encryption),
    ]

    /// PWA Communication Configuration services (loadConfigStatus) + BACKEND_FIELDS.
    static let commServices: [String] = [
        "signal", "telegram", "discord", "slack", "matrix", "ntfy", "email", "twilio", "github_webhook", "webhook", "dns_channel",
    ]

    /// Credential keys that mark a backend "configured" (PWA isBackendConfigured).
    static func credentialKeys(_ svc: String) -> [String] {
        switch svc {
        case "telegram", "discord", "slack": return ["token"]
        case "matrix": return ["access_token"]
        case "ntfy": return ["topic"]
        case "email": return ["host", "username"]
        case "twilio": return ["account_sid", "from_number"]
        case "github_webhook": return ["secret"]
        case "webhook": return ["addr"]
        case "dns_channel": return ["domain", "secret"]
        default: return []
        }
    }

    static func backendFields(_ svc: String) -> [SettingsField] {
        let p = svc + "."
        var f: [SettingsField] = [.toggle(p + "enabled", "Enabled")]
        switch svc {
        case "telegram":
            f += [.secret(p + "token", "Bot Token"), .text(p + "chat_id", "Chat ID"), .toggle(p + "auto_manage_group", "Auto-manage group")]
        case "discord":
            f += [.secret(p + "token", "Bot Token"), .text(p + "channel_id", "Channel ID"), .toggle(p + "auto_manage_channel", "Auto-manage channel")]
        case "slack":
            f += [.secret(p + "token", "OAuth Bot Token"), .text(p + "channel_id", "Channel ID"), .toggle(p + "auto_manage_channel", "Auto-manage channel")]
        case "matrix":
            f += [.text(p + "homeserver", "Homeserver URL"), .text(p + "user_id", "User ID (@bot:host)"),
                  .secret(p + "access_token", "Access Token", "${secret:matrix-access-token}"),
                  .text(p + "room_id", "Room ID or Alias", "!roomid:matrix.org or #alias:matrix.org"),
                  .toggle(p + "auto_manage_room", "Auto-manage room"),
                  .text(p + "device_id", "Device ID (optional)"), .text(p + "device_name", "Device Name (optional)")]
        case "ntfy":
            f += [.text(p + "server_url", "Server URL", "https://ntfy.sh"), .text(p + "topic", "Topic"), .secret(p + "token", "Token (optional)")]
        case "email":
            f += [.text(p + "host", "SMTP Host"), .number(p + "port", "Port", "587"), .text(p + "username", "Username"),
                  .secret(p + "password", "Password"), .text(p + "from", "From Address"), .text(p + "to", "To Address")]
        case "twilio":
            f += [.text(p + "account_sid", "Account SID"), .secret(p + "auth_token", "Auth Token"), .text(p + "from_number", "From Number"),
                  .text(p + "to_number", "To Number"), .text(p + "webhook_addr", "Webhook Addr", "127.0.0.1:9003")]
        case "github_webhook":
            f += [.text(p + "addr", "Listen Address", "127.0.0.1:9001"), .secret(p + "secret", "Webhook Secret")]
        case "webhook":
            f += [.text(p + "addr", "Listen Address", "127.0.0.1:9002"), .secret(p + "token", "Token (optional)")]
        case "signal":
            f += [.text(p + "group_id", "Group ID (base64)"), .text(p + "config_dir", "signal-cli config dir"), .text(p + "device_name", "Device name")]
        case "dns_channel":
            f += [.text(p + "mode", "Mode", "server"), .text(p + "domain", "Domain", "ctl.example.com"), .text(p + "listen", "Listen (server)", ":53"),
                  .text(p + "upstream", "Upstream (client)", "8.8.8.8:53"), .secret(p + "secret", "Shared Secret"),
                  .number(p + "ttl", "TTL (seconds)", "0"), .number(p + "max_response_size", "Max Response Size", "512"),
                  .text(p + "poll_interval", "Poll Interval", "5s"), .number(p + "rate_limit", "Rate limit (per IP/min)", "30")]
        default:
            break
        }
        return f
    }
}
