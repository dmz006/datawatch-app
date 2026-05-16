package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T18 test-debt: JSON round-trip for AgentSettingsDto —
 * PATCH /api/profiles/projects/{name}/agent-settings (BL251 / alpha.28 #243).
 */
class AgentSettingsDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `full agent settings round-trip preserves all fields`() {
        val original = AgentSettingsDto(
            claudeAuthKeySecret = "secret-ref-42",
            opencodeOllamaUrl = "http://ollama:11434",
            opencodeModel = "codellama",
            opencodeModels = listOf("codellama", "mistral"),
        )
        val encoded = json.encodeToString(AgentSettingsDto.serializer(), original)
        val decoded = json.decodeFromString(AgentSettingsDto.serializer(), encoded)

        assertEquals("secret-ref-42", decoded.claudeAuthKeySecret)
        assertEquals("http://ollama:11434", decoded.opencodeOllamaUrl)
        assertEquals("codellama", decoded.opencodeModel)
        assertEquals(listOf("codellama", "mistral"), decoded.opencodeModels)
    }

    @Test
    fun `defaults produce empty strings and empty list`() {
        val dto = AgentSettingsDto()
        assertEquals("", dto.claudeAuthKeySecret)
        assertEquals("", dto.opencodeOllamaUrl)
        assertEquals("", dto.opencodeModel)
        assertTrue(dto.opencodeModels.isEmpty())
    }

    @Test
    fun `deserialization from wire JSON uses snake_case keys`() {
        val src = """
        {
          "claude_auth_key_secret": "vault://my-key",
          "opencode_ollama_url": "http://gpu-box:11434",
          "opencode_model": "llama3",
          "opencode_models": ["llama3","phi3"]
        }
        """.trimIndent()
        val dto = json.decodeFromString(AgentSettingsDto.serializer(), src)
        assertEquals("vault://my-key", dto.claudeAuthKeySecret)
        assertEquals("http://gpu-box:11434", dto.opencodeOllamaUrl)
        assertEquals("llama3", dto.opencodeModel)
        assertEquals(listOf("llama3", "phi3"), dto.opencodeModels)
    }

    @Test
    fun `empty models list is preserved`() {
        val src = """
        {
          "claude_auth_key_secret": "",
          "opencode_ollama_url": "",
          "opencode_model": "",
          "opencode_models": []
        }
        """.trimIndent()
        val dto = json.decodeFromString(AgentSettingsDto.serializer(), src)
        assertTrue(dto.opencodeModels.isEmpty())
    }

    @Test
    fun `unknown extra fields are ignored`() {
        // Ensures forward-compat when server adds new fields.
        val src = """
        {
          "claude_auth_key_secret": "s",
          "opencode_ollama_url": "",
          "opencode_model": "",
          "opencode_models": [],
          "future_field": "ignored"
        }
        """.trimIndent()
        val dto = json.decodeFromString(AgentSettingsDto.serializer(), src)
        assertEquals("s", dto.claudeAuthKeySecret)
    }
}
