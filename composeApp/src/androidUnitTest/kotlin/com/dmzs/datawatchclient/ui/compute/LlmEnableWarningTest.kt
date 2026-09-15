package com.dmzs.datawatchclient.ui.compute

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors the unsupported-kind routing logic in LlmRegistryCard.onToggle onFailure.
 * datawatch#46 — LLM enable fails for auto-created entries should be a warning, not error.
 */
class LlmEnableWarningTest {
    private fun isUnsupportedKindError(message: String): Boolean =
        message.contains("unsupported", ignoreCase = true) ||
            message.contains("auto-created", ignoreCase = true) ||
            message.contains("kind", ignoreCase = true)

    @Test
    fun `unsupported in message routes to warning`() =
        assertTrue(isUnsupportedKindError("llm kind 'aider' is unsupported for enable toggle"))

    @Test
    fun `auto-created in message routes to warning`() =
        assertTrue(isUnsupportedKindError("cannot enable auto-created LLM entry"))

    @Test
    fun `kind in message routes to warning`() =
        assertTrue(isUnsupportedKindError("invalid kind: shell"))

    @Test
    fun `network error does not route to warning`() =
        assertFalse(isUnsupportedKindError("connection refused"))

    @Test
    fun `auth error does not route to warning`() =
        assertFalse(isUnsupportedKindError("401 unauthorized"))

    @Test
    fun `empty message does not route to warning`() =
        assertFalse(isUnsupportedKindError(""))
}
