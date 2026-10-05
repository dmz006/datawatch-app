package com.dmzs.datawatchclient.ui.sessions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PWA #backendWarn rule (08 › Backend setup hint). */
class BackendSetupHintTest {
    @Test
    fun `warns only when the server listed backends and the kind is missing`() {
        assertTrue(backendNeedsSetup("ollama", listOf("claude-code")))
        assertFalse(backendNeedsSetup("Ollama", listOf("ollama")))
        assertFalse(backendNeedsSetup("ollama", emptyList()))
        assertFalse(backendNeedsSetup("", listOf("ollama")))
    }
}
