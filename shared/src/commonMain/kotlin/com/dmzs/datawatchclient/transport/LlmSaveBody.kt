package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Builds the POST/PUT `/api/llms` body. `LlmRegistryEntryDto` is read from
 * `GET /api/llms` and sent back on save, so server-owned read-only fields
 * (`auto_created` — PWA "auto" badge) are dropped here and never written back.
 */
public object LlmSaveBody {
    /** Server-owned keys the client must never send. */
    public val READ_ONLY_KEYS: Set<String> = setOf("auto_created")

    public fun of(dto: LlmRegistryEntryDto): JsonObject =
        strip(RestTransport.DefaultJson.encodeToJsonElement(LlmRegistryEntryDto.serializer(), dto).jsonObject)

    public fun strip(body: JsonObject): JsonObject =
        if (READ_ONLY_KEYS.none { it in body }) body else JsonObject(body.filterKeys { it !in READ_ONLY_KEYS })
}
