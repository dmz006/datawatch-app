package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.profiles.ProfileEditor
import com.dmzs.datawatchclient.profiles.ProfileField
import com.dmzs.datawatchclient.profiles.ProfileForm
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Editor state for a project / cluster profile: the masked working document (JSON) + form values. */
public data class IosProfileDraft(
    val docJson: String,
    val values: Map<String, String>,
)

/** Result of switching YAML → form; [error] is empty on success (then [docJson]/[values] are the parsed state). */
public data class IosProfileYamlResult(
    val docJson: String,
    val values: Map<String, String>,
    val error: String,
)

/**
 * Project / cluster profile editor (PWA `renderProfileEditor`): the shared
 * [ProfileForm] fields plus the Home-Assistant-style "YAML view" toggle via
 * [ProfileEditor]. [kind] is the Settings list kind (`project_profiles` /
 * `cluster_profiles`). The working document never holds literal secrets
 * (masked); [save] restores them from the server copy. Callbacks fire off the
 * main thread; [save] reports null on success or an error message.
 */
public object IosProfileEditor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json: Json = Json { encodeDefaults = true }

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun kindOf(listKind: String): String = if (listKind == "cluster_profiles" || listKind == "cluster") "cluster" else "project"

    private fun encode(o: JsonObject): String = json.encodeToString(JsonObject.serializer(), o)

    private fun decode(s: String): JsonObject = json.parseToJsonElement(s) as? JsonObject ?: JsonObject(emptyMap())

    /** PWA form fields in order (type: text / select / bool / number / list). */
    public fun fields(kind: String): List<ProfileField> = ProfileForm.fields(kindOf(kind))

    /** Select options without the "" entry (the view adds "(none)" when [hasEmptyOption]). */
    public fun options(field: ProfileField): List<String> = field.options.filter { it.isNotEmpty() }

    public fun hasEmptyOption(field: ProfileField): Boolean = field.options.contains("")

    /** Starting state: the stored profile [name] (masked), or the PWA template when [name] is empty. */
    public fun load(
        profile: ServerProfile,
        kind: String,
        name: String,
        onSuccess: (IosProfileDraft) -> Unit,
        onError: (String) -> Unit,
    ) {
        val k: String = kindOf(kind)
        if (name.isEmpty()) {
            val doc: JsonObject = ProfileEditor.workingDoc(k, null)
            onSuccess(IosProfileDraft(docJson = encode(doc), values = ProfileForm.valuesFrom(k, doc)))
            return
        }
        scope.launch {
            runCatching {
                val stored: JsonObject =
                    t(profile).listKindProfiles(k).getOrThrow().firstOrNull { nameOf(it) == name }
                        ?: throw IllegalStateException("Profile \"$name\" not found.")
                val doc: JsonObject = ProfileEditor.workingDoc(k, stored)
                IosProfileDraft(docJson = encode(doc), values = ProfileForm.valuesFrom(k, doc))
            }.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load.") },
            )
        }
    }

    /** Form → YAML: the working document with [values] applied, serialized. */
    public fun toYaml(
        kind: String,
        docJson: String,
        values: Map<String, String>,
    ): String = ProfileEditor.toYaml(kindOf(kind), decode(docJson), values)

    /** YAML → form. On a parse error the caller keeps its text and shows [IosProfileYamlResult.error]. */
    public fun fromYaml(
        kind: String,
        yaml: String,
    ): IosProfileYamlResult {
        val k: String = kindOf(kind)
        return try {
            val doc: JsonObject = ProfileEditor.parseYaml(straighten(yaml))
            IosProfileYamlResult(docJson = encode(doc), values = ProfileForm.valuesFrom(k, doc), error = "")
        } catch (e: IllegalArgumentException) {
            IosProfileYamlResult(docJson = "", values = emptyMap(), error = e.message ?: "YAML parse error")
        }
    }

    /**
     * Validates and saves: POST for a new profile ([originalName] null), PUT for an
     * edit. In YAML view [yamlText] is the source; otherwise [docJson] + [values].
     */
    public fun save(
        profile: ServerProfile,
        kind: String,
        originalName: String?,
        docJson: String,
        values: Map<String, String>,
        yamlMode: Boolean,
        yamlText: String,
        onDone: (String?) -> Unit,
    ) {
        val k: String = kindOf(kind)
        scope.launch {
            val r: Result<Unit> =
                runCatching {
                    val tr: TransportClient = t(profile)
                    val stored: JsonObject? =
                        if (originalName == null) {
                            null
                        } else {
                            tr.listKindProfiles(k).getOrThrow().firstOrNull { nameOf(it) == originalName }
                        }
                    val body: JsonObject =
                        ProfileEditor.buildBody(
                            kind = k,
                            doc = decode(docJson),
                            values = values,
                            yamlMode = yamlMode,
                            yamlText = straighten(yamlText),
                            originalName = originalName,
                            stored = stored,
                        )
                    if (originalName == null) {
                        tr.createKindProfile(k, body).getOrThrow()
                    } else {
                        tr.putKindProfile(k, originalName, body).getOrThrow()
                    }
                }
            onDone(r.exceptionOrNull()?.let { it.message ?: "Save failed." })
        }
    }

    /** iOS text views may auto-insert curly quotes; YAML needs straight ones. */
    private fun straighten(s: String): String =
        s.replace('\u201C', '"').replace('\u201D', '"').replace('\u2018', '\'').replace('\u2019', '\'')

    private fun nameOf(o: JsonObject): String = (o["name"] as? JsonPrimitive)?.content.orEmpty()
}
