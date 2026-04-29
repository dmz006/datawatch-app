package com.dmzs.datawatchclient.ui.configfields

import com.dmzs.datawatchclient.ui.configfields.ConfigField.NumberField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Select
import com.dmzs.datawatchclient.ui.configfields.ConfigField.TextField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Toggle

/**
 * Per-backend field schemas for the LLM config dialog.
 *
 * **Path convention** (verified 2026-04-24 against live /api/config
 * on localhost:8443 + parent app.js:4262-4282 `LLM_FIELDS` +
 * app.js:4286-4288 `LLM_CFG_SECTION`):
 *
 * Each registered backend has its own top-level config section. The
 * section name is **not** `backends.<name>` — that prefix doesn't
 * exist in the server config. Prior Android schemas used the wrong
 * path and every write/toggle was silently dropped.
 *
 *  - `claude-code` → fields live under **`session.*`** (e.g.
 *    `session.claude_code_bin`, `session.claude_enabled`).
 *  - `aider` / `goose` / `gemini` / `ollama` / `opencode` /
 *    `openwebui` → each has its own section named the same.
 *  - `opencode-acp` → **`opencode_acp.*`** (note underscore).
 *  - `opencode-prompt` → **`opencode_prompt.*`**.
 *  - `shell` → **`shell_backend.*`**.
 *  - `auto_git_init` / `auto_git_commit` → **`session.*`** (shared
 *    across all backends).
 *
 * Backends not listed on the server's `available_backends` (GET
 * `/api/info`) are **not** schematized here. `anthropic`, `openai`,
 * `groq`, `openrouter`, `xai`, etc. were removed 2026-04-24 —
 * parent daemon doesn't ship adapters for them, so exposing config
 * fields for them was dead UX.
 */
public object LlmBackendSchemas {
    /**
     * Map backend display name → config section name (section.* is
     * the dotted prefix for every field on this backend).
     */
    private fun section(backendName: String): String =
        when (backendName.lowercase()) {
            "claude-code", "claude_code", "claudecode" -> "session"
            "opencode-acp", "opencode_acp" -> "opencode_acp"
            "opencode-prompt", "opencode_prompt" -> "opencode_prompt"
            "shell" -> "shell_backend"
            else -> backendName.lowercase()
        }

    /**
     * Per-backend "enabled" config key. Surface for the LLM card's
     * toggle, and for the NewSession picker's enable filter. Most
     * backends use `<section>.enabled`; claude-code is the exception
     * (`session.claude_enabled` — historical, the `session` section
     * predates the other adapters).
     */
    public fun enabledKey(backendName: String): String =
        when (backendName.lowercase()) {
            "claude-code", "claude_code", "claudecode" -> "session.claude_enabled"
            else -> "${section(backendName)}.enabled"
        }

    private fun consoleSizeFields(backendName: String): List<ConfigField> {
        val s = section(backendName)
        return listOf(
            NumberField("$s.console_cols", "Console width (cols)", placeholder = "80"),
            NumberField("$s.console_rows", "Console height (rows)", placeholder = "24"),
            Select(
                "$s.output_mode",
                "Output mode",
                options = listOf("terminal", "log", "chat"),
            ),
            Select(
                "$s.input_mode",
                "Input mode",
                options = listOf("tmux", "none"),
            ),
        )
    }

    /** Shared "Auto git init/commit" under session.*. */
    private val GitFields: List<ConfigField> =
        listOf(
            Toggle("session.auto_git_init", "Auto git init"),
            Toggle("session.auto_git_commit", "Auto git commit"),
        )

    /** Fallback when we get a backend name we don't know. Keeps
     * the user able to flip enable + set a model, nothing fancy. */
    public val DefaultLlmFields: (String) -> List<ConfigField> = { name ->
        val s = section(name)
        listOf(
            Toggle(enabledKey(name), "Enabled"),
            TextField("$s.binary", "Binary path"),
            TextField("$s.model", "Model"),
        ) + consoleSizeFields(name) + GitFields
    }

    public fun sectionFor(backendName: String): ConfigSection {
        val name = backendName.lowercase()
        val s = section(backendName)
        val fields: List<ConfigField> =
            when (name) {
                "ollama" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.host", "Host URL", placeholder = "http://localhost:11434"),
                        TextField("$s.model", "Model"),
                    ) + consoleSizeFields(backendName) + GitFields

                "openwebui" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.url", "Server URL", placeholder = "http://localhost:3000"),
                        TextField("$s.api_key", "API key (leave blank to keep)", password = true),
                        TextField("$s.model", "Model"),
                    ) + consoleSizeFields(backendName) + GitFields

                "claude-code", "claude_code", "claudecode" ->
                    listOf(
                        Toggle("session.claude_enabled", "Enabled"),
                        TextField(
                            "session.claude_code_bin",
                            "Claude binary",
                            placeholder = "claude",
                        ),
                        Toggle("session.skip_permissions", "Skip permissions"),
                        Toggle("session.claude_auto_accept_disclaimer", "Auto-accept disclaimer"),
                        Toggle("session.channel_enabled", "Channel mode"),
                        TextField(
                            "session.fallback_chain",
                            "Fallback chain (comma-separated profiles)",
                            placeholder = "claude-personal,gemini-backup",
                        ),
                    ) + consoleSizeFields(backendName) + GitFields

                "aider" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "aider"),
                    ) + consoleSizeFields(backendName) + GitFields

                "goose" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "goose"),
                    ) + consoleSizeFields(backendName) + GitFields

                "gemini" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "gemini"),
                    ) + consoleSizeFields(backendName) + GitFields

                "opencode" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "opencode"),
                    ) + consoleSizeFields(backendName) + GitFields

                "opencode-acp", "opencode_acp" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "opencode"),
                        NumberField(
                            "$s.acp_startup_timeout",
                            "ACP startup timeout (sec)",
                            placeholder = "30",
                        ),
                        NumberField(
                            "$s.acp_health_interval",
                            "ACP health interval (sec)",
                            placeholder = "5",
                        ),
                        NumberField(
                            "$s.acp_message_timeout",
                            "ACP message timeout (sec)",
                            placeholder = "120",
                        ),
                    ) + consoleSizeFields(backendName) + GitFields

                "opencode-prompt", "opencode_prompt" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField("$s.binary", "Binary path", placeholder = "opencode"),
                    ) + consoleSizeFields(backendName) + GitFields

                "shell" ->
                    listOf(
                        Toggle("$s.enabled", "Enabled"),
                        TextField(
                            "$s.script_path",
                            "Script path (empty = interactive shell)",
                        ),
                    ) + consoleSizeFields(backendName) + GitFields

                else -> DefaultLlmFields(backendName)
            }
        return ConfigSection(
            id = "lc_backend_${name.replace('-', '_')}",
            title = "$backendName configuration",
            fields = fields,
        )
    }

    /**
     * Adapters the parent daemon registers (verified via
     * `GET /api/info` `available_backends` on 2026-04-24 against
     * dmz006/datawatch v4.x). This list controls which rows the
     * LLM card surfaces when `/api/backends` is stale or a user
     * has configured a backend that isn't yet in the daemon's
     * active registry.
     */
    public val KnownBackends: List<String> =
        listOf(
            "claude-code",
            "ollama",
            "openwebui",
            "aider",
            "goose",
            "gemini",
            "opencode",
            "opencode-acp",
            "opencode-prompt",
            "shell",
        )
}
