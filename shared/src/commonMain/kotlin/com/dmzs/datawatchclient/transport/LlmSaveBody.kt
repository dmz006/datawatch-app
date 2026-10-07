package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Builds the POST/PUT `/api/llms` body. `LlmRegistryEntryDto` is read from
 * `GET /api/llms` and sent back on save, so server-owned read-only fields
 * (`auto_created` — PWA "auto" badge; `api_key_ref_present` /
 * `api_key_ref_prefix`) are dropped here and never written back.
 *
 * Servers ≥ v8.63.1 (datawatch GH#179) redact a literal API key on read: blank
 * `api_key_ref` plus `api_key_ref_present: true`. Echoing that blank back would
 * clear the stored key, so it is omitted and the server's PUT merge keeps it.
 */
public object LlmSaveBody {
    /** Server-owned keys the client must never send. */
    public val READ_ONLY_KEYS: Set<String> = setOf("auto_created", "api_key_ref_present", "api_key_ref_prefix")

    public fun of(dto: LlmRegistryEntryDto): JsonObject =
        strip(RestTransport.DefaultJson.encodeToJsonElement(LlmRegistryEntryDto.serializer(), dto).jsonObject)

    public fun strip(body: JsonObject): JsonObject {
        val redactedKey: Boolean =
            (body["api_key_ref_present"] as? JsonPrimitive)?.booleanOrNull == true &&
                (body["api_key_ref"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.isNullOrBlank()
        val drop: Set<String> = if (redactedKey) READ_ONLY_KEYS + "api_key_ref" else READ_ONLY_KEYS
        return if (drop.none { it in body }) body else JsonObject(body.filterKeys { it !in drop })
    }
}
