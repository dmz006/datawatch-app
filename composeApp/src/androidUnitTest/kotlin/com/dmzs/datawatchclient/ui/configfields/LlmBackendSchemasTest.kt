package com.dmzs.datawatchclient.ui.configfields

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the v0.35.0 (G45) LLM config-path rewrite.
 * Server stores each backend's config under a top-level section named
 * the same as the backend (ollama.*, openwebui.*, shell_backend.*,
 * opencode_acp.*, session.* for claude-code). The prior
 * `backends.<name>.*` prefix was a silent no-op; this test locks in
 * the correct mappings so we don't regress.
 */
class LlmBackendSchemasTest {
    @Test
    fun `enabledKey for ollama-family points at section enabled`() {
        assertEquals("ollama.enabled", LlmBackendSchemas.enabledKey("ollama"))
        assertEquals("openwebui.enabled", LlmBackendSchemas.enabledKey("openwebui"))
        assertEquals("aider.enabled", LlmBackendSchemas.enabledKey("aider"))
        assertEquals("goose.enabled", LlmBackendSchemas.enabledKey("goose"))
        assertEquals("gemini.enabled", LlmBackendSchemas.enabledKey("gemini"))
        assertEquals("opencode.enabled", LlmBackendSchemas.enabledKey("opencode"))
    }

    @Test
    fun `enabledKey for claude-code uses session claude_enabled`() {
        // Historical — claude-code's config lives under session.*
        // because it predates the other adapters.
        assertEquals("session.claude_enabled", LlmBackendSchemas.enabledKey("claude-code"))
        assertEquals("session.claude_enabled", LlmBackendSchemas.enabledKey("claude_code"))
        assertEquals("session.claude_enabled", LlmBackendSchemas.enabledKey("CLAUDE-CODE"))
    }

    @Test
    fun `enabledKey for shell uses shell_backend section`() {
        assertEquals("shell_backend.enabled", LlmBackendSchemas.enabledKey("shell"))
    }

    @Test
    fun `enabledKey for opencode variants uses underscore sections`() {
        assertEquals("opencode_acp.enabled", LlmBackendSchemas.enabledKey("opencode-acp"))
        assertEquals("opencode_acp.enabled", LlmBackendSchemas.enabledKey("opencode_acp"))
        assertEquals("opencode_prompt.enabled", LlmBackendSchemas.enabledKey("opencode-prompt"))
        assertEquals("opencode_prompt.enabled", LlmBackendSchemas.enabledKey("opencode_prompt"))
    }

    @Test
    fun `sectionFor claude-code uses session-prefixed keys`() {
        val fields = LlmBackendSchemas.sectionFor("claude-code").fields
        val keys = fields.map { it.key }
        assertTrue("session.claude_enabled" in keys, "expected enabled toggle under session.*")
        assertTrue(
            "session.claude_code_bin" in keys,
            "expected binary field at session.claude_code_bin",
        )
        assertTrue("session.skip_permissions" in keys)
        assertTrue("session.channel_enabled" in keys)
        assertTrue("session.fallback_chain" in keys)
        // Console + git should be under session.* too
        assertTrue("session.console_cols" in keys)
        assertTrue("session.console_rows" in keys)
        assertTrue("session.output_mode" in keys)
        assertTrue("session.input_mode" in keys)
        // Auto-git lives under session.* (shared)
        assertTrue("session.auto_git_init" in keys)
        assertTrue("session.auto_git_commit" in keys)
    }

    @Test
    fun `sectionFor ollama uses flat ollama keys`() {
        val fields = LlmBackendSchemas.sectionFor("ollama").fields
        val keys = fields.map { it.key }
        assertTrue("ollama.enabled" in keys)
        assertTrue("ollama.host" in keys)
        assertTrue("ollama.model" in keys)
        assertTrue("ollama.console_cols" in keys)
        assertTrue("ollama.output_mode" in keys)
        // Must NOT emit the old backends.<name> prefix anywhere.
        val bad = keys.filter { it.startsWith("backends.") }
        assertTrue(bad.isEmpty(), "found legacy backends.* keys: $bad")
    }

    @Test
    fun `sectionFor openwebui emits url and api_key not base_url`() {
        val fields = LlmBackendSchemas.sectionFor("openwebui").fields
        val keys = fields.map { it.key }.toSet()
        assertTrue("openwebui.url" in keys)
        assertTrue("openwebui.api_key" in keys)
        assertTrue("openwebui.model" in keys)
        // base_url was a pre-0.35.0 artifact we don't want back
        assertTrue("openwebui.base_url" !in keys)
    }

    @Test
    fun `KnownBackends matches server-registered adapter list`() {
        // This must stay in sync with parent /api/info
        // `available_backends` — covered by the 2026-04-24 server walk.
        val expected =
            setOf(
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
        assertEquals(expected, LlmBackendSchemas.KnownBackends.toSet())
    }

    @Test
    fun `dropped dead backends return DefaultLlmFields fallback`() {
        // anthropic/openai/groq/openrouter/xai were dropped — server
        // doesn't support them. sectionFor should still return
        // *something* (DefaultLlmFields) so the UI doesn't crash on
        // a user-configured rogue backend, but it must NOT emit the
        // old backends.<name>.* paths.
        val fields = LlmBackendSchemas.sectionFor("anthropic").fields
        val keys = fields.map { it.key }
        val bad = keys.filter { it.startsWith("backends.") }
        assertTrue(bad.isEmpty(), "dead backends must not regress to backends.* paths: $bad")
    }

    @Test
    fun `opencode-acp schema carries ACP timeout fields`() {
        val fields = LlmBackendSchemas.sectionFor("opencode-acp").fields
        val keys = fields.map { it.key }.toSet()
        assertTrue("opencode_acp.binary" in keys)
        assertTrue("opencode_acp.acp_startup_timeout" in keys)
        assertTrue("opencode_acp.acp_health_interval" in keys)
        assertTrue("opencode_acp.acp_message_timeout" in keys)
    }

    @Test
    fun `shell schema carries script_path and shell_backend section`() {
        val fields = LlmBackendSchemas.sectionFor("shell").fields
        val keys = fields.map { it.key }.toSet()
        assertTrue("shell_backend.enabled" in keys)
        assertTrue("shell_backend.script_path" in keys)
    }
}
