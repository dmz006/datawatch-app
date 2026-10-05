package com.dmzs.datawatchclient.profiles

import com.dmzs.datawatchclient.transport.SecretMask
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deep [SecretMask] for project / cluster profile documents: any literal value under
 * a secret-looking key (at any depth — e.g. `env.ANTHROPIC_API_KEY`) is shown as
 * [SecretMask.PLACEHOLDER] in the form and the YAML view, and put back from the
 * server's copy on save. `${secret:name}` references and secret *names*
 * (`claude_auth_key_secret`, `image_pull_secret`) stay visible.
 */
public object ProfileSecrets {
    private val secretish =
        Regex(
            "(^|_)(token|password|passwd|pass|pwd|api_?key|apikey|secret_?key|private_?key|access_?key|credentials?|bearer)($|_)",
            RegexOption.IGNORE_CASE,
        )

    public fun isSecretKey(key: String): Boolean = SecretMask.isLiteralSecret(key.lowercase(), "x") || secretish.containsMatchIn(key)

    public fun mask(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject ->
                JsonObject(
                    element.mapValues { (k, v) ->
                        if (v is JsonPrimitive && v.isString && isSecretKey(k) && v.content.isNotEmpty() &&
                            !SecretMask.isReference(v.content)
                        ) {
                            JsonPrimitive(SecretMask.PLACEHOLDER)
                        } else {
                            mask(v)
                        }
                    },
                )
            is JsonArray -> JsonArray(element.map { mask(it) })
            else -> element
        }

    public fun maskObject(obj: JsonObject): JsonObject = mask(obj) as JsonObject

    /**
     * Replaces every remaining [SecretMask.PLACEHOLDER] in [edited] with the value at the
     * same path in [original]. A placeholder with no stored value (e.g. the key was
     * renamed) is an error rather than being saved literally.
     */
    public fun restore(
        edited: JsonElement,
        original: JsonElement?,
        path: String = "",
    ): JsonElement =
        when {
            edited is JsonPrimitive && edited.isString && edited.content == SecretMask.PLACEHOLDER -> {
                val stored = original as? JsonPrimitive
                if (stored == null || !stored.isString || stored.content == SecretMask.PLACEHOLDER) {
                    throw IllegalArgumentException(
                        "\"${path.ifEmpty { "value" }}\" still holds the masked placeholder but has no stored value — enter the real value",
                    )
                }
                stored
            }
            edited is JsonObject ->
                JsonObject(
                    edited.mapValues { (k, v) ->
                        restore(v, (original as? JsonObject)?.get(k), if (path.isEmpty()) k else "$path.$k")
                    },
                )
            edited is JsonArray ->
                JsonArray(edited.mapIndexed { i, v -> restore(v, (original as? JsonArray)?.getOrNull(i), "$path[$i]") })
            else -> edited
        }

    public fun restoreObject(
        edited: JsonObject,
        original: JsonObject?,
    ): JsonObject = restore(edited, original ?: JsonObject(emptyMap())) as JsonObject
}
