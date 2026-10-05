package com.dmzs.datawatchclient.transport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keeps literal secrets out of raw editors (LLM "</> YAML" view): a literal value in
 * a secret field is shown as [PLACEHOLDER] and restored from the server's copy on
 * save. `${secret:name}` references are shown as-is (they're not secrets).
 */
public object SecretMask {
    public const val PLACEHOLDER: String = "•••••••• (unchanged)"

    private val SECRET_KEYS: Set<String> =
        setOf("api_key_ref", "api_key", "token", "bearer_token", "password", "secret")

    public fun isReference(value: String): Boolean = value.trim().startsWith("\${secret:")

    /** True when [value] in field [key] is a literal secret that must not be shown. */
    public fun isLiteralSecret(
        key: String,
        value: String,
    ): Boolean = key in SECRET_KEYS && value.isNotEmpty() && !isReference(value)

    public fun mask(obj: JsonObject): JsonObject =
        JsonObject(
            obj.mapValues { (k, v) ->
                if (v is JsonPrimitive && v.isString && isLiteralSecret(k, v.content)) JsonPrimitive(PLACEHOLDER) else v
            },
        )

    /** Puts the original literal back wherever the edited copy still holds [PLACEHOLDER]. */
    public fun restore(
        edited: JsonObject,
        original: JsonObject,
    ): JsonObject =
        JsonObject(
            edited.mapValues { (k, v) ->
                if (v is JsonPrimitive && v.isString && v.content == PLACEHOLDER) original[k] ?: JsonPrimitive("") else v
            },
        )
}
