package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LlmSaveBodyTest {
    private fun obj(vararg pairs: Pair<String, Any>): JsonObject =
        JsonObject(
            pairs.associate { (k, v) ->
                k to
                    when (v) {
                        is Boolean -> JsonPrimitive(v)
                        is Number -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
            },
        )

    @Test
    fun redactedLiteralKeyIsOmittedSoTheServerKeepsIt() {
        // datawatch v8.63.1 GET shape for a literal key (GH#179).
        val get = obj("name" to "claude", "api_key_ref" to "", "api_key_ref_present" to true, "api_key_ref_prefix" to "sk-a")
        val sent = LlmSaveBody.strip(get)
        assertFalse("api_key_ref" in sent)
        assertFalse("api_key_ref_present" in sent)
        assertFalse("api_key_ref_prefix" in sent)
        assertEquals("claude", (sent["name"] as JsonPrimitive).content)
    }

    @Test
    fun newKeyTypedOverARedactedOneIsSent() {
        val edited = obj("name" to "claude", "api_key_ref" to "sk-new", "api_key_ref_present" to true)
        assertEquals("sk-new", (LlmSaveBody.strip(edited)["api_key_ref"] as JsonPrimitive).content)
    }

    @Test
    fun blankKeyWithoutPresentFlagIsSentAsIs() {
        // Older servers / no key configured: blank means "no key", unchanged behaviour.
        val body = obj("name" to "ollama", "api_key_ref" to "")
        assertTrue("api_key_ref" in LlmSaveBody.strip(body))
    }

    @Test
    fun secretReferenceIsKept() {
        val body = obj("name" to "owui", "api_key_ref" to "\${secret:owui}", "api_key_ref_present" to true)
        assertEquals("\${secret:owui}", (LlmSaveBody.strip(body)["api_key_ref"] as JsonPrimitive).content)
    }

    @Test
    fun dtoRoundTripFromRedactedListEntryKeepsKey() {
        // Android "refresh models" PUTs the list entry back unchanged.
        val listed =
            RestTransport.DefaultJson.decodeFromString(
                LlmRegistryEntryDto.serializer(),
                """{"name":"claude","kind":"anthropic","api_key_ref":"","api_key_ref_present":true,"api_key_ref_prefix":"sk-a"}""",
            )
        assertEquals(true, listed.apiKeyRefPresent)
        val sent = LlmSaveBody.of(listed)
        assertFalse("api_key_ref" in sent)
        assertFalse("api_key_ref_present" in sent)
    }

    @Test
    fun autoCreatedStillDropped() {
        val dto = LlmRegistryEntryDto(name = "x", kind = "ollama")
        val sent = LlmSaveBody.of(dto.copy())
        assertFalse("auto_created" in sent)
        assertFalse("api_key_ref_present" in RestTransport.DefaultJson.encodeToJsonElement(LlmRegistryEntryDto.serializer(), dto).jsonObject)
    }
}
