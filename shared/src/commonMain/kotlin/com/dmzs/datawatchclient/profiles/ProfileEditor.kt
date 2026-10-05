package com.dmzs.datawatchclient.profiles

import com.dmzs.datawatchclient.yaml.ProfileYaml
import com.dmzs.datawatchclient.yaml.YamlParseException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Home-Assistant-style "one editor, two views" logic shared by the Android and iOS
 * profile editors. The working document is always the *masked* profile
 * ([ProfileSecrets.mask]); form values overlay it via [ProfileForm.apply], the YAML
 * view is [ProfileYaml] of it, and [buildBody] restores masked secrets from the
 * stored copy right before saving.
 */
public object ProfileEditor {
    /** Masked working document for an existing profile, or the PWA template for a new one. */
    public fun workingDoc(
        kind: String,
        stored: JsonObject?,
    ): JsonObject = stored?.let { ProfileSecrets.maskObject(it) } ?: ProfileForm.template(kind)

    /** Form → YAML: the document with the form's values applied, as YAML text. */
    public fun toYaml(
        kind: String,
        doc: JsonObject,
        values: Map<String, String>,
    ): String = ProfileYaml.stringify(ProfileForm.apply(kind, doc, values))

    /**
     * YAML → document. Throws [IllegalArgumentException] with a user-facing message
     * (line-numbered for syntax errors) when [text] isn't a YAML/JSON mapping.
     */
    public fun parseYaml(text: String): JsonObject {
        val el =
            try {
                ProfileYaml.parse(text)
            } catch (e: YamlParseException) {
                throw IllegalArgumentException("YAML parse error — ${e.message}", e)
            }
        return when (el) {
            is JsonObject -> el
            is JsonNull -> throw IllegalArgumentException("YAML body is empty")
            else -> throw IllegalArgumentException("YAML parse error — the profile must be a mapping of key: value lines")
        }
    }

    /** Returns null when [text] parses, otherwise the inline error message. */
    public fun yamlError(text: String): String? =
        try {
            parseYaml(text)
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "YAML parse error"
        }

    /**
     * The JSON body to POST/PUT. [doc] + [values] in form view, or the parsed
     * [yamlText] in YAML view; validated like the server; masked secrets restored
     * from [stored]. An edit keeps [originalName] (the server forces the URL name).
     * Throws [IllegalArgumentException] with every problem joined by newlines.
     */
    public fun buildBody(
        kind: String,
        doc: JsonObject,
        values: Map<String, String>,
        yamlMode: Boolean,
        yamlText: String,
        originalName: String?,
        stored: JsonObject?,
    ): JsonObject {
        var body = if (yamlMode) parseYaml(yamlText) else ProfileForm.apply(kind, doc, values)
        if (originalName != null) body = JsonObject(body + ("name" to JsonPrimitive(originalName)))
        val errs = ProfileForm.validate(kind, body)
        if (errs.isNotEmpty()) throw IllegalArgumentException(errs.joinToString("\n"))
        return ProfileSecrets.restoreObject(body, stored)
    }
}
