package com.dmzs.datawatchclient.ui.configfields

import com.dmzs.datawatchclient.ui.configfields.ConfigField.NumberField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.TextField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Toggle

/**
 * Per-backend field schemas for the LLM config dialog. Mirrors the
 * dot-path keys the parent daemon's `applyConfigPatch` recognises
 * under `backends.<name>.*`. When the parent adds a field, mirror
 * it here — the dialog renders generically off these schemas so new
 * fields appear by adding a single line below.
 *
 * Fallback: a backend the mobile app doesn't know a schema for
 * still shows the legacy 3-field set (model / base_url / api_key)
 * via [DefaultLlmFields] so novel backends aren't locked out.
 */
public object LlmBackendSchemas {
    /** Common-to-all-backends "is this backend enabled?" toggle. */
    private fun enabled(name: String): Toggle =
        Toggle("backends.$name.enabled", "Enabled")

    /** Common-to-most "password-style" API-key input. Empty preserves. */
    private fun apiKey(name: String, label: String = "API key"): TextField =
        TextField("backends.$name.api_key", "$label (leave blank to keep)", password = true)

    /** Fallback used when the backend name has no registered schema. */
    public val DefaultLlmFields: (String) -> List<ConfigField> = { name ->
        listOf(
            enabled(name),
            TextField("backends.$name.model", "Model"),
            TextField("backends.$name.base_url", "Base URL"),
            apiKey(name),
        )
    }

    /**
     * Return the schema for [backendName], synthesising dotted keys
     * per the parent server's naming convention. Backends not listed
     * here get [DefaultLlmFields] as a best-effort fallback.
     */
    public fun sectionFor(backendName: String): ConfigSection {
        val fields =
            when (backendName.lowercase()) {
                "ollama" ->
                    listOf(
                        enabled(backendName),
                        TextField("backends.ollama.host", "Host", placeholder = "http://localhost:11434"),
                        TextField("backends.ollama.model", "Model"),
                        NumberField("backends.ollama.timeout_seconds", "Timeout (sec)", placeholder = "120"),
                        NumberField("backends.ollama.context_window", "Context window (tokens)", placeholder = "8192"),
                    )
                "openai" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.openai.model", "Model", placeholder = "gpt-4o"),
                        TextField("backends.openai.base_url", "Base URL (optional)", placeholder = "https://api.openai.com/v1"),
                        NumberField("backends.openai.max_tokens", "Max output tokens"),
                        TextField("backends.openai.temperature", "Temperature (0.0-2.0)", placeholder = "0.7"),
                        TextField("backends.openai.system_prompt", "System prompt (optional)"),
                    )
                "anthropic", "claude" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.anthropic.model", "Model", placeholder = "claude-opus-4-7"),
                        NumberField("backends.anthropic.max_tokens", "Max output tokens", placeholder = "8192"),
                        TextField("backends.anthropic.temperature", "Temperature (0.0-1.0)", placeholder = "0.7"),
                    )
                "groq" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.groq.model", "Model", placeholder = "llama-3.3-70b-versatile"),
                        TextField("backends.groq.base_url", "Base URL (optional)"),
                        NumberField("backends.groq.max_tokens", "Max output tokens"),
                    )
                "openrouter" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.openrouter.model", "Model", placeholder = "anthropic/claude-3.5-sonnet"),
                        TextField("backends.openrouter.base_url", "Base URL (optional)"),
                        TextField("backends.openrouter.site_url", "Referer / site URL (optional)"),
                        TextField("backends.openrouter.app_name", "App name (optional)", placeholder = "datawatch"),
                    )
                "gemini", "google" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.gemini.model", "Model", placeholder = "gemini-2.0-flash-exp"),
                        NumberField("backends.gemini.max_tokens", "Max output tokens"),
                    )
                "xai", "grok" ->
                    listOf(
                        enabled(backendName),
                        apiKey(backendName),
                        TextField("backends.xai.model", "Model", placeholder = "grok-2-latest"),
                        TextField("backends.xai.base_url", "Base URL (optional)"),
                    )
                "openwebui" ->
                    listOf(
                        enabled(backendName),
                        TextField("backends.openwebui.base_url", "Base URL", placeholder = "http://localhost:3000"),
                        apiKey(backendName),
                        TextField("backends.openwebui.model", "Model"),
                    )
                "opencode" ->
                    listOf(
                        enabled(backendName),
                        TextField("backends.opencode.base_url", "Base URL"),
                        apiKey(backendName),
                        TextField("backends.opencode.model", "Model"),
                    )
                else -> DefaultLlmFields(backendName)
            }
        return ConfigSection(
            id = "lc_backend_$backendName",
            title = "$backendName configuration",
            fields = fields,
        )
    }

    /**
     * Known LLM backend names. Used by the "Messaging Backends"-style
     * sweep that surfaces backends configured in `/api/config` even
     * when they don't appear in `/api/backends` (e.g. keys set to
     * blank values or a backend the parent hasn't registered yet).
     */
    public val KnownBackends: List<String> =
        listOf(
            "ollama", "openai", "anthropic", "groq", "openrouter",
            "gemini", "xai", "openwebui", "opencode",
        )
}
