package com.dmzs.datawatchclient.ui.configfields

import com.dmzs.datawatchclient.ui.configfields.ConfigField.InterfaceSelect
import com.dmzs.datawatchclient.ui.configfields.ConfigField.LlmSelect
import com.dmzs.datawatchclient.ui.configfields.ConfigField.NumberField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Select
import com.dmzs.datawatchclient.ui.configfields.ConfigField.TextField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Toggle

/**
 * Field schemas ported from PWA `internal/server/web/app.js`
 * (COMMS_CONFIG_FIELDS, LLM_CONFIG_FIELDS, GENERAL_CONFIG_FIELDS
 * at lines 3532–3716). When the parent adds a field, mirror it
 * here — mobile's Settings tabs render generically off these
 * schemas so new fields show up by adding one line below.
 */
public object ConfigFieldSchemas {
    // ---- General tab ----

    public val Datawatch: ConfigSection =
        ConfigSection(
            id = "gc_dw",
            title = "Datawatch",
            fields =
                listOf(
                    Select(
                        key = "session.log_level",
                        label = "Log level",
                        options = listOf("info", "debug", "warn", "error"),
                    ),
                    Toggle("server.auto_restart_on_config", "Auto-restart on config save"),
                    LlmSelect("session.llm_backend", "Default LLM backend"),
                ),
        )

    public val AutoUpdate: ConfigSection =
        ConfigSection(
            id = "gc_autoupdate",
            title = "Auto-Update",
            fields =
                listOf(
                    Toggle("update.enabled", "Enabled"),
                    Select(
                        key = "update.schedule",
                        label = "Schedule",
                        options = listOf("hourly", "daily", "weekly"),
                    ),
                    TextField("update.time_of_day", "Time of day (HH:MM)"),
                ),
        )

    public val Session: ConfigSection =
        ConfigSection(
            id = "gc_sess",
            title = "Session",
            fields =
                listOf(
                    NumberField("session.max_sessions", "Max concurrent sessions"),
                    NumberField("session.input_idle_timeout", "Input idle timeout (sec)"),
                    NumberField("session.tail_lines", "Tail lines"),
                    NumberField("session.alert_context_lines", "Alert context lines", "10"),
                    TextField("session.default_project_dir", "Default project dir"),
                    TextField("session.root_path", "File browser root path"),
                    NumberField("session.console_cols", "Default console width (cols)", "80"),
                    NumberField("session.console_rows", "Default console height (rows)", "24"),
                    NumberField("server.recent_session_minutes", "Recent session visibility (min)"),
                    Toggle("session.skip_permissions", "Claude skip permissions"),
                    Toggle("session.channel_enabled", "Claude channel mode"),
                    Toggle("session.auto_git_init", "Auto git init"),
                    Toggle("session.auto_git_commit", "Auto git commit"),
                    Toggle("session.kill_sessions_on_exit", "Kill sessions on exit"),
                    NumberField("session.mcp_max_retries", "MCP auto-retry limit"),
                    NumberField("session.schedule_settle_ms", "Scheduled command settle (ms)"),
                    TextField("session.default_effort", "Default effort — quick/normal/thorough"),
                    Toggle("server.suppress_active_toasts", "Suppress toasts for active session"),
                ),
        )

    public val Rtk: ConfigSection =
        ConfigSection(
            id = "gc_rtk",
            title = "RTK (Token Savings)",
            fields =
                listOf(
                    Toggle("rtk.enabled", "Enable RTK integration"),
                    TextField("rtk.binary", "RTK binary path", "rtk"),
                    Toggle("rtk.show_savings", "Show savings in stats"),
                    Toggle("rtk.auto_init", "Auto-init hooks if missing"),
                    NumberField("rtk.discover_interval", "Discover check interval (sec, 0=off)"),
                ),
        )

    public val Pipelines: ConfigSection =
        ConfigSection(
            id = "gc_pipeline",
            title = "Pipelines (Session Chaining)",
            fields =
                listOf(
                    NumberField("pipeline.max_parallel", "Max parallel tasks (0 = default 3)", "3"),
                    TextField("pipeline.default_backend", "Default backend (empty = session default)"),
                ),
        )

    public val Autonomous: ConfigSection =
        ConfigSection(
            id = "gc_autonomous",
            title = "Autonomous PRD decomposition",
            fields =
                listOf(
                    Toggle("autonomous.enabled", "Enable autonomous loop"),
                    NumberField("autonomous.poll_interval_seconds", "Poll interval (sec)", "30"),
                    NumberField("autonomous.max_parallel_tasks", "Max parallel tasks", "3"),
                    TextField("autonomous.decomposition_backend", "Decomposition backend (empty = inherit)"),
                    TextField("autonomous.verification_backend", "Verification backend (empty = inherit)"),
                    NumberField("autonomous.auto_fix_retries", "Auto-fix retries", "1"),
                    Toggle("autonomous.security_scan", "Run security scan before commit"),
                ),
        )

    public val Plugins: ConfigSection =
        ConfigSection(
            id = "gc_plugins",
            title = "Plugin framework",
            fields =
                listOf(
                    Toggle("plugins.enabled", "Enable subprocess plugin framework"),
                    TextField("plugins.dir", "Plugin discovery directory", "~/.datawatch/plugins"),
                    NumberField("plugins.timeout_ms", "Invocation timeout (ms)", "2000"),
                ),
        )

    public val Orchestrator: ConfigSection =
        ConfigSection(
            id = "gc_orchestrator",
            title = "PRD-DAG orchestrator",
            fields =
                listOf(
                    Toggle("orchestrator.enabled", "Enable PRD-DAG orchestrator"),
                    TextField("orchestrator.guardrail_backend", "Guardrail LLM backend (empty = inherit)"),
                    NumberField("orchestrator.guardrail_timeout_ms", "Guardrail timeout (ms)", "120000"),
                    NumberField("orchestrator.max_parallel_prds", "Max parallel PRDs", "2"),
                ),
        )

    public val Whisper: ConfigSection =
        ConfigSection(
            id = "gc_whisper",
            title = "Voice Input (Whisper)",
            fields =
                listOf(
                    Toggle("whisper.enabled", "Enable voice transcription"),
                    Select(
                        key = "whisper.model",
                        label = "Whisper model",
                        options = listOf("tiny", "base", "small", "medium", "large"),
                    ),
                    TextField("whisper.language", "Language (ISO 639-1 code or 'auto')", "en"),
                    TextField("whisper.venv_path", "Python venv path", ".venv"),
                ),
        )

    // ---- LLM tab ----

    public val Memory: ConfigSection =
        ConfigSection(
            id = "lc_memory",
            title = "Episodic Memory",
            fields =
                listOf(
                    Toggle("memory.enabled", "Enable memory system"),
                    Select("memory.backend", "Storage backend", listOf("sqlite", "postgres")),
                    Select("memory.embedder", "Embedding provider", listOf("ollama", "openai")),
                    TextField("memory.embedder_model", "Embedding model", "nomic-embed-text"),
                    TextField("memory.embedder_host", "Embedder host"),
                    NumberField("memory.top_k", "Search results (top-K)"),
                    Toggle("memory.auto_save", "Auto-save session summaries"),
                    Toggle("memory.learnings_enabled", "Extract task learnings"),
                    Select("memory.storage_mode", "Storage mode", listOf("summary", "verbatim")),
                    Toggle("memory.entity_detection", "Auto entity detection"),
                    Toggle("memory.session_awareness", "Inject memory instructions into sessions"),
                    Toggle("memory.session_broadcast", "Broadcast session summaries"),
                    Toggle("memory.auto_hooks", "Auto-install Claude Code hooks"),
                    NumberField("memory.hook_save_interval", "Hook save interval (messages)"),
                    NumberField("memory.retention_days", "Retention days (0 = forever)"),
                    TextField("memory.db_path", "SQLite database path", "~/.datawatch/memory.db"),
                    TextField("memory.postgres_url", "PostgreSQL URL (enterprise)", "postgres://…"),
                ),
        )

    public val LlmRtk: ConfigSection =
        ConfigSection(
            id = "lc_rtk",
            title = "RTK (Token Savings)",
            fields =
                listOf(
                    Toggle("rtk.enabled", "Enable RTK integration"),
                    TextField("rtk.binary", "RTK binary path", "rtk"),
                    Toggle("rtk.show_savings", "Show token savings in stats"),
                    Toggle("rtk.auto_init", "Auto-init hooks if missing"),
                    Toggle("rtk.auto_update", "Auto-update RTK binary"),
                    NumberField("rtk.update_check_interval", "Update check interval (sec, 0=off)", "86400"),
                    NumberField("rtk.discover_interval", "Discover interval (sec, 0=off)", "0"),
                ),
        )

    // ---- Comms tab ----

    public val WebServer: ConfigSection =
        ConfigSection(
            id = "cc_websrv",
            title = "Web Server",
            fields =
                listOf(
                    Toggle("server.enabled", "Enabled"),
                    InterfaceSelect("server.host", "Bind interface"),
                    NumberField("server.port", "Port"),
                    Toggle("server.tls", "TLS enabled"),
                    NumberField("server.tls_port", "TLS port", "8443"),
                    Toggle("server.tls_auto_generate", "TLS auto-generate cert"),
                    TextField("server.tls_cert", "TLS cert path"),
                    TextField("server.tls_key", "TLS key path"),
                    NumberField("server.channel_port", "Channel port (0=random)"),
                ),
        )

    public val McpServer: ConfigSection =
        ConfigSection(
            id = "cc_mcpsrv",
            title = "MCP Server",
            fields =
                listOf(
                    Toggle("mcp.enabled", "Enabled (stdio)"),
                    Toggle("mcp.sse_enabled", "SSE enabled (HTTP)"),
                    InterfaceSelect("mcp.sse_host", "SSE bind interface"),
                    NumberField("mcp.sse_port", "SSE port"),
                    Toggle("mcp.tls_enabled", "TLS enabled"),
                    Toggle("mcp.tls_auto_generate", "TLS auto-generate cert"),
                    TextField("mcp.tls_cert", "TLS cert path"),
                    TextField("mcp.tls_key", "TLS key path"),
                ),
        )

    public val CommsAuth: ConfigSection =
        ConfigSection(
            id = "comms_auth",
            title = "Authentication",
            fields =
                listOf(
                    TextField("server.token", "Server bearer token", password = true),
                    TextField("mcp.token", "MCP SSE bearer token", password = true),
                ),
        )

    public val Proxy: ConfigSection =
        ConfigSection(
            id = "proxy",
            title = "Proxy Resilience",
            fields =
                listOf(
                    Toggle("proxy.enabled", "Enabled"),
                    NumberField("proxy.health_interval", "Health interval (sec)", "30"),
                    NumberField("proxy.request_timeout", "Request timeout (sec)", "10"),
                    NumberField("proxy.offline_queue_size", "Offline queue size", "100"),
                    NumberField("proxy.circuit_breaker_threshold", "Circuit-breaker threshold", "3"),
                    NumberField("proxy.circuit_breaker_reset", "Circuit-breaker reset (sec)", "30"),
                ),
        )
}
