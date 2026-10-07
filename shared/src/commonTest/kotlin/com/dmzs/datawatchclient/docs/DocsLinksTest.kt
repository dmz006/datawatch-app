package com.dmzs.datawatchclient.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BL414 — locks the "?" help-link table. The server docs tree isn't available
 * in CI, so these check completeness and well-formedness; the target files and
 * headings were verified against the server docs when the table was built.
 */
class DocsLinksTest {
    private val wellFormed = Regex("""^[a-z0-9][a-z0-9/_.-]*\.md(#[\p{L}\p{N}]+(-[\p{L}\p{N}]+)*)?$""")

    private fun allTargets(): List<String> = DocsLinks.byKey.values + DocsLinks.channelTypes.values + DocsLinks.llmBackends.values

    @Test
    fun everyTargetIsAWellFormedDocsPath() {
        allTargets().forEach { t -> assertTrue(wellFormed.matches(t), "malformed docs target: $t") }
    }

    @Test
    fun noTargetIsTheBareManual() {
        // The bug: every "?" landed on the top of the definitions page.
        allTargets().forEach { t -> assertNotEquals(DocsLinks.DEFINITIONS, t, "bare manual target") }
        DocsLinks.byKey
            .filterValues { it.startsWith(DocsLinks.DEFINITIONS) }
            .forEach { (k, v) -> assertTrue('#' in v, "$k must anchor into the manual") }
    }

    @Test
    fun anchorsAreInViewerSlugForm() {
        allTargets().filter { '#' in it }.forEach { t ->
            val anchor = t.substringAfter('#')
            assertEquals(DocsLinks.slug(anchor), anchor, "anchor not in slug form: $t")
        }
    }

    @Test
    fun slugMatchesTheDocsViewerRule() {
        assertEquals("cost-rates-usd-1k-tokens", DocsLinks.slug("Cost Rates (USD / 1K tokens)"))
        assertEquals("settings-general", DocsLinks.slug("Settings — General"))
        assertEquals("how-it-s-built", DocsLinks.slug("How it's built"))
        assertEquals("self-update-v8-9-21", DocsLinks.slug("Self-update (v8.9.21)"))
        assertEquals("4b-happy-path-pwa", DocsLinks.slug("4b. Happy path — PWA"))
    }

    @Test
    fun everyScreenHeaderHasATarget() {
        listOf(
            "view_sessions", "view_session_detail", "view_new_session", "view_automata",
            "view_automaton_detail", "view_automata_wizard", "view_observer", "view_alerts",
            "view_dashboard", "view_settings", "view_settings_general", "view_settings_plugins",
            "view_settings_comms", "view_settings_compute", "view_settings_automata", "view_settings_about",
        ).forEach { assertNotNull(DocsLinks.forKey(it), "missing screen key $it") }
    }

    @Test
    fun webUiScreenLinksAreKept() {
        // Web UI header map (app.js headerHelpLink) + per-screen "?" links that the server ships.
        assertEquals("datawatch-definitions.md#sessions-list", DocsLinks.forKey("view_sessions"))
        assertEquals("datawatch-definitions.md#inside-a-session-terminal-area", DocsLinks.forKey("view_session_detail"))
        assertEquals("datawatch-definitions.md#automata", DocsLinks.forKey("view_automata"))
        assertEquals("datawatch-definitions.md#observer", DocsLinks.forKey("view_observer"))
        assertEquals("howto/alerts-and-notifications.md", DocsLinks.forKey("view_alerts"))
        assertEquals("howto/dashboard.md", DocsLinks.forKey("view_dashboard"))
    }

    @Test
    fun iosSettingsAndObserverKeysResolve() {
        // iosApp SettingsCatalog card ids + ObserverView ObsSection keys.
        listOf(
            "gc_dw", "gc_autoupdate", "gc_sess", "gc_summarizer", "gc_whisper", "gc_notifs", "templates",
            "device_aliases", "tooling", "docs_search", "file_service", "discussion_scopes", "security",
            "raw_config", "gc_plugins", "plugins_list", "comms_auth", "servers", "remote_servers", "fedpeers",
            "backends", "cc_websrv", "cc_mcpsrv", "proxy", "routing_rules", "channel_routing",
            "push_notifications", "compute_nodes", "llms", "lc_memory", "costrates", "lc_rtk",
            "gc_clusterprofiles", "gc_agents", "tailscale_config", "tailscale_status", "lc_goose",
            "lc_opencode", "lc_web_search", "lc_vision", "websearch_providers", "secrets_store",
            "observer_quicklink", "alert_rules", "cmds", "detection", "filters", "exit_hooks", "work_queue",
            "identity", "algorithm", "evals", "council", "gc_autonomous", "gc_orchestrator", "gc_pipeline",
            "automata_scan", "automata_autonomous", "automata_guardrail_profiles", "automata_skills",
            "automata_type_registry", "gc_projectprofiles", "pipelines", "orchestrator_graphs", "about", "api",
            "mcp_tools", "mcp_channel", "subsystem_reload", "encryption",
            "stats", "membrowser", "memmaint", "schedules", "cooldown", "analytics", "audit", "kg",
            "daemonlog", "observer_peers",
        ).forEach { assertNotNull(DocsLinks.forKey(it), "missing iOS key $it") }
    }

    @Test
    fun dynamicKeysResolve() {
        assertEquals(DocsLinks.forKey("stats"), DocsLinks.forKey("stats_gpu"))
        assertEquals("llm-backends.md#claude-code-default", DocsLinks.forKey("lc_backend_claude_code"))
        assertEquals("llm-backends.md#opencode-acp-mode", DocsLinks.forLlmBackend("opencode-acp"))
        assertEquals("datawatch-definitions.md#llm-registry", DocsLinks.forLlmBackend("mystery"))
        assertEquals("messaging-backends.md#signal", DocsLinks.forKey("cc_global_signal"))
        assertEquals("messaging-backends.md#twilio-sms", DocsLinks.forChannelType("Twilio"))
        assertEquals("datawatch-definitions.md#communication-configuration", DocsLinks.forChannelType("pigeon"))
        assertEquals("datawatch-definitions.md#ekg-waveform", DocsLinks.forKey("dash_ekg"))
        assertEquals("howto/dashboard.md", DocsLinks.forKey("dash_gantt"))
        assertNull(DocsLinks.forKey("no_such_card"))
    }

    @Test
    fun viewerUrlJoinsBaseAndTarget() {
        assertEquals(
            "https://dw.example/diagrams.html#docs/howto/evals.md",
            DocsLinks.viewerUrl("https://dw.example/", "howto/evals.md"),
        )
        assertEquals(
            "https://dw.example:8443/diagrams.html#docs/datawatch-definitions.md#work-queue",
            DocsLinks.viewerUrl("https://dw.example:8443", DocsLinks.forKey("work_queue")!!),
        )
    }
}
