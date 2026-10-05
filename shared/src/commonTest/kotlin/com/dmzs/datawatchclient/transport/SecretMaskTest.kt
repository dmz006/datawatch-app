package com.dmzs.datawatchclient.transport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SecretMaskTest {
    private val original =
        JsonObject(
            mapOf(
                "name" to JsonPrimitive("owui"),
                "api_key_ref" to JsonPrimitive("sk-literal-value"),
                "model" to JsonPrimitive("m"),
            ),
        )

    @Test
    fun `literal key is masked and restored on save`() {
        val shown = SecretMask.mask(original)
        assertEquals(SecretMask.PLACEHOLDER, (shown["api_key_ref"] as JsonPrimitive).content)
        val edited = JsonObject(shown + ("model" to JsonPrimitive("m2")))
        val saved = SecretMask.restore(edited, original)
        assertEquals("sk-literal-value", (saved["api_key_ref"] as JsonPrimitive).content)
        assertEquals("m2", (saved["model"] as JsonPrimitive).content)
    }

    @Test
    fun `secret references are shown and a replaced key is kept`() {
        val ref = JsonObject(original + ("api_key_ref" to JsonPrimitive("\${secret:owui}")))
        assertEquals("\${secret:owui}", (SecretMask.mask(ref)["api_key_ref"] as JsonPrimitive).content)
        val edited = JsonObject(SecretMask.mask(original) + ("api_key_ref" to JsonPrimitive("new-key")))
        assertEquals("new-key", (SecretMask.restore(edited, original)["api_key_ref"] as JsonPrimitive).content)
    }
}
