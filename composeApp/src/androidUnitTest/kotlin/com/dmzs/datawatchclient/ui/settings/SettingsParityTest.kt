package com.dmzs.datawatchclient.ui.settings

import com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas
import com.dmzs.datawatchclient.ui.plugins.pluginToggleAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings parity helpers (07 › Plugin Manager, restart link D57b, config keys). */
class SettingsParityTest {
    @Test
    fun `plugin toggle offers the opposite action`() {
        assertEquals("disable", pluginToggleAction(true))
        assertEquals("enable", pluginToggleAction(false))
    }

    @Test
    fun `restart hint shows only after a save with auto-restart off`() {
        assertFalse(restartHintVisible(saved = false, autoRestart = false, message = null))
        assertFalse(restartHintVisible(saved = true, autoRestart = true, message = null))
        assertTrue(restartHintVisible(saved = true, autoRestart = false, message = null))
        assertTrue(restartHintVisible(saved = false, autoRestart = null, message = "Restarting…"))
    }

    @Test
    fun `config schemas carry the PWA keys`() {
        val s = ConfigFieldSchemas
        val keys =
            listOf(s.Datawatch, s.Whisper, s.WebSearch, s.Vision, s.Orchestrator, s.Autonomous)
                .flatMap { it.fields }
                .map { it.key }
        listOf(
            "session.backend_family",
            "whisper.backend",
            "web_search.cache_enabled",
            "web_search.cache_ttl_seconds",
            "vision.model",
            "orchestrator.guardrail_model",
            "autonomous.planning_backend",
            "autonomous.capacity_enabled",
        ).forEach { assertTrue(it in keys, it) }
    }
}
