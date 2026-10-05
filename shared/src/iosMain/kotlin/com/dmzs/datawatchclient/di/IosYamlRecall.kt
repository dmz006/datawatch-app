package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * PWA parity (operator 2026-10-05): Docs Search "Export YAML"
 * (docsTrustExport), Discussion Scopes "Recall" (discussionViewEntries) and
 * the LLM form's "</> YAML" escape hatch (openFormEditPopup — the PWA's
 * "YAML" view edits the record as pretty-printed JSON). Callbacks fire off
 * the main thread; mutations report null on success or an error message.
 */
public object IosYamlRecall {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pretty: Json = Json { prettyPrint = true }

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    /** GET /api/docs/trust/export → the server-rendered `docs_search.trust` YAML. */
    public fun docsTrustExport(
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).docsTrustExportYaml().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Export failed.") },
            )
        }
    }

    /** GET /api/memory/discussion/{id} → entry contents (newest server order). */
    public fun discussionRecall(
        profile: ServerProfile,
        id: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).recallDiscussion(id = id, topK = 10).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Recall failed.") },
            )
        }
    }

    /** GET /api/llms/{name} pretty-printed for the raw editor. */
    public fun llmJson(
        profile: ServerProfile,
        name: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchLlmJson(name).fold(
                onSuccess = { o ->
                    // Literal secrets are masked; save/test restore them from the server copy.
                    val shown: JsonObject = com.dmzs.datawatchclient.transport.SecretMask.mask(o)
                    onSuccess(pretty.encodeToString(JsonObject.serializer(), shown))
                },
                onFailure = { onError(it.message ?: "Load failed.") },
            )
        }
    }

    /** Parse [text] and PUT /api/llms/{name}. Parse errors are reported like the PWA. */
    public fun saveLlmJson(
        profile: ServerProfile,
        name: String,
        text: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val obj: JsonObject? = parseObject(text)
            if (obj == null) {
                onDone("JSON parse error: expected a JSON object")
                return@launch
            }
            val server: JsonObject? = t(profile).fetchLlmJson(name).getOrNull()
            val restored: JsonObject =
                if (server != null) com.dmzs.datawatchclient.transport.SecretMask.restore(obj, server) else obj
            val r: Result<Unit> = t(profile).saveLlmJson(name, restored)
            onDone(r.exceptionOrNull()?.let { "Save failed: " + (it.message ?: "") })
        }
    }

    /** PWA formEditTestBtn: save the edited values, then POST /api/llms/{name}/test. */
    public fun testLlmJson(
        profile: ServerProfile,
        name: String,
        text: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val obj: JsonObject? = parseObject(text)
            if (obj == null) {
                onError("JSON parse error: expected a JSON object")
                return@launch
            }
            val server: JsonObject? = t(profile).fetchLlmJson(name).getOrNull()
            val restored: JsonObject =
                if (server != null) com.dmzs.datawatchclient.transport.SecretMask.restore(obj, server) else obj
            val tr: TransportClient = t(profile)
            val saved: Result<Unit> = tr.saveLlmJson(name, restored)
            val saveErr: Throwable? = saved.exceptionOrNull()
            if (saveErr != null) {
                onError((saveErr.message ?: "Test failed.").take(240))
                return@launch
            }
            tr.testLlmJson(name, null).fold(
                onSuccess = { o ->
                    val txt: String = (o["text"] as? JsonPrimitive)?.content ?: o.toString()
                    onSuccess(txt.take(200).ifEmpty { "ok" })
                },
                onFailure = { onError((it.message ?: "Test failed.").take(240)) },
            )
        }
    }

    private fun parseObject(text: String): JsonObject? {
        val el: JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        return el as? JsonObject
    }
}
