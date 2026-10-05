package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.ChannelRoutingRuleDto
import com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto
import com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto
import com.dmzs.datawatchclient.transport.dto.SessionTemplateDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryUpdateDto
import com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest
import com.dmzs.datawatchclient.transport.dto.WebSearchProviderDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One BL356 exit hook (`GET /api/exit-hooks`). */
public data class IosExitHook(
    val id: String,
    val name: String,
    val action: String,
    val notifySession: String,
    val cooldownSeconds: Int,
    val enabled: Boolean,
    val lastFiredAt: String,
)

/** One BL357 work-queue item (`GET /api/queue`). */
public data class IosQueueItem(
    val id: String,
    val role: String,
    val state: String,
    val claimedBy: String,
    val payload: String,
)

/** One Ollama marketplace catalog entry; [tags] are "tag · size" labels, [tagNames] the raw tags. */
public data class IosOllamaModel(
    val name: String,
    val description: String,
    val tags: List<String>,
    val tagNames: List<String>,
)

/** A pull in flight / finished (`GET /api/marketplace/ollama/tasks/{id}`). */
public data class IosPullTask(
    val id: String,
    val model: String,
    val progress: Int,
    val status: String,
)

/** Council run summary row. */
public data class IosCouncilRun(
    val id: String,
    val proposal: String,
    val mode: String,
    val status: String,
    val personas: List<String>,
    val consensus: String,
    val startedAt: String,
)

/** Compute-node kind migration state (deprecated kinds → ollama / openai-compat). */
public data class IosKindMigration(
    val notice: String,
    val show: Boolean,
    val nodes: List<String>,
    val nodeKinds: List<String>,
    val supportedKinds: List<String>,
)

/**
 * Settings depth parity (iOS item 16): create/edit forms for list cards,
 * Exit Hooks + Work Queue, federation peer Test, Tailscale auth key, Ollama
 * marketplace pulls, council runs, discussion scopes and compute kind
 * migration. Mutations report null on success or an error message.
 * Callbacks fire off the main thread.
 *
 * Secret-bearing fields (`token`, `api_key`) are never returned by
 * [formValues]; a blank value on save leaves the stored secret untouched.
 */
public object IosSettingsCrud {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
    private val sensitive = Regex("token|secret|password|api_key|apikey|auth", RegexOption.IGNORE_CASE)
    private const val MASK: String = "•••"

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun msg(
        e: Throwable?,
        fallback: String,
    ): String? = e?.let { it.message ?: fallback }

    /** List kinds whose rows open an edit form here (create also routes here). */
    public fun canEdit(kind: String): Boolean =
        kind in
            setOf(
                "session_templates", "fed_peers", "web_search_providers", "guardrail_profiles",
                "channel_routing", "council_personas", "skill_registries",
            )

    /** Kinds edited as a raw JSON document (PWA form ↔ YAML escape hatch). */
    public fun isJsonEdited(kind: String): Boolean = kind == "cluster_profiles" || kind == "project_profiles"

    /** Kinds whose create goes through [save] (vs. the legacy IosSettingsLists.create). */
    public fun handlesCreate(kind: String): Boolean =
        kind == "discussions" || (canEdit(kind) && kind != "session_templates" && kind != "skill_registries")

    // ---- Generic list-card forms ----

    /** Current field values for an edit form (secrets omitted). */
    public fun formValues(
        profile: ServerProfile,
        kind: String,
        id: String,
        onSuccess: (Map<String, String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { values(t(profile), kind, id) }.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load.") },
            )
        }
    }

    private suspend fun values(
        tr: TransportClient,
        kind: String,
        id: String,
    ): Map<String, String> =
        when (kind) {
            "session_templates" -> {
                val s = tr.getSessionTemplates().getOrThrow().first { it.name == id }
                mapOf("name" to s.name, "backend" to s.backend, "project_dir" to s.projectDir, "effort" to s.effort, "description" to s.description)
            }
            "fed_peers" -> {
                val o = tr.listFederationPeers().getOrThrow().first { it.s("name") == id }
                mapOf(
                    "name" to o.s("name"),
                    "url" to o.s("url"),
                    "capabilities" to o.list("capabilities").joinToString(", "),
                    "channel_identity" to o.list("channel_identity").joinToString(", ").ifEmpty { o.s("channel_identity") },
                )
            }
            "web_search_providers" -> {
                val w = tr.listWebSearchProviders().getOrThrow().first { it.name == id }
                mapOf(
                    "name" to w.name, "type" to w.type, "url" to w.url, "engine" to w.engine,
                    "priority" to w.priority.toString(), "num_results" to w.numResults.toString(),
                    "cache_ttl_seconds" to w.cacheTtlSeconds.toString(),
                )
            }
            "guardrail_profiles" -> {
                val g = tr.listGuardrailProfiles().getOrThrow().first { it.id == id || it.name == id }
                mapOf(
                    "name" to g.name.ifEmpty { g.id },
                    "guardrails" to g.guardrails.joinToString(", "),
                    "block_on" to g.blockOn.joinToString(", "),
                    "warn_on" to g.warnOn.joinToString(", "),
                )
            }
            "channel_routing" -> {
                val idx = id.toIntOrNull() ?: -1
                val r = tr.getChannelRouting().getOrThrow().rules[idx]
                mapOf("channel_pattern" to r.channelPattern, "peer_name" to r.peerName, "automata_type" to r.automataType)
            }
            "council_personas" -> {
                val p = tr.getCouncilPersona(id).getOrThrow()
                mapOf("name" to p.name, "description" to p.description, "prompt" to p.prompt)
            }
            "skill_registries" -> {
                val r = tr.listSkillRegistries().getOrThrow().first { it.name == id }
                mapOf("name" to r.name, "url" to r.url, "branch" to r.branch)
            }
            else -> throw UnsupportedOperationException("Not editable: $kind")
        }

    /**
     * Create ([originalId] null) or update an entry from string fields.
     * Keys per kind: session_templates(name, backend, project_dir, effort, description) ·
     * fed_peers(name, url, token, capabilities, channel_identity) ·
     * web_search_providers(name, type, url, engine, api_key, priority, num_results, cache_ttl_seconds) ·
     * guardrail_profiles(name, guardrails, block_on, warn_on) ·
     * channel_routing(channel_pattern, peer_name, automata_type) ·
     * council_personas(name, description, prompt) · skill_registries(url, branch) · discussions(id).
     */
    public fun save(
        profile: ServerProfile,
        kind: String,
        originalId: String?,
        values: Map<String, String>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val r: Result<Unit> = runCatching { saveImpl(t(profile), kind, originalId, values) }
            onDone(msg(r.exceptionOrNull(), "Save failed."))
        }
    }

    private suspend fun saveImpl(
        tr: TransportClient,
        kind: String,
        originalId: String?,
        values: Map<String, String>,
    ) {
        fun v(k: String): String = values[k]?.trim().orEmpty()
        fun csv(k: String): List<String> = v(k).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        fun csvJson(k: String): JsonArray = JsonArray(csv(k).map { JsonPrimitive(it) })
        val isNew: Boolean = originalId == null
        when (kind) {
            "session_templates" ->
                tr.createSessionTemplate(
                    SessionTemplateDto(
                        name = originalId ?: v("name"),
                        backend = v("backend"),
                        projectDir = v("project_dir"),
                        effort = v("effort"),
                        description = v("description"),
                    ),
                ).getOrThrow()
            "fed_peers" -> {
                val fields = LinkedHashMap<String, JsonElement>()
                if (isNew) {
                    require(v("name").isNotEmpty()) { "Name is required" }
                    fields["name"] = JsonPrimitive(v("name"))
                    fields["enabled"] = JsonPrimitive(true)
                }
                require(v("url").isNotEmpty()) { "URL is required" }
                fields["url"] = JsonPrimitive(v("url"))
                if (v("token").isNotEmpty()) fields["token"] = JsonPrimitive(v("token"))
                if (csv("capabilities").isNotEmpty() || !isNew) fields["capabilities"] = csvJson("capabilities")
                if (csv("channel_identity").isNotEmpty() || !isNew) fields["channel_identity"] = csvJson("channel_identity")
                val body = JsonObject(fields)
                if (originalId == null) tr.addFederationPeer(body).getOrThrow() else tr.updateFederationPeer(originalId, body).getOrThrow()
            }
            "web_search_providers" -> {
                val type: String = v("type").ifEmpty { "searxng" }
                if (originalId == null) {
                    tr.createWebSearchProvider(
                        WebSearchProviderDto(
                            name = v("name"),
                            type = type,
                            enabled = true,
                            priority = v("priority").toIntOrNull() ?: 0,
                            url = v("url"),
                            engine = v("engine"),
                            apiKey = values["api_key"].orEmpty(),
                            numResults = v("num_results").toIntOrNull() ?: 10,
                            cacheTtlSeconds = v("cache_ttl_seconds").toIntOrNull() ?: 0,
                        ),
                    ).getOrThrow()
                } else {
                    val fields = LinkedHashMap<String, JsonElement>()
                    fields["type"] = JsonPrimitive(type)
                    fields["url"] = JsonPrimitive(v("url"))
                    fields["engine"] = JsonPrimitive(v("engine"))
                    v("priority").toIntOrNull()?.let { fields["priority"] = JsonPrimitive(it) }
                    v("num_results").toIntOrNull()?.let { fields["num_results"] = JsonPrimitive(it) }
                    v("cache_ttl_seconds").toIntOrNull()?.let { fields["cache_ttl_seconds"] = JsonPrimitive(it) }
                    val key: String = values["api_key"].orEmpty()
                    if (key.isNotEmpty()) fields["api_key"] = JsonPrimitive(key)
                    tr.patchWebSearchProviderJson(originalId, JsonObject(fields)).getOrThrow()
                }
            }
            "guardrail_profiles" -> {
                val dto =
                    GuardrailProfileDto(
                        id = originalId ?: "",
                        name = v("name"),
                        guardrails = csv("guardrails"),
                        blockOn = csv("block_on"),
                        warnOn = csv("warn_on"),
                    )
                if (originalId == null) tr.createGuardrailProfile(dto).getOrThrow() else tr.updateGuardrailProfile(originalId, dto).getOrThrow()
            }
            "channel_routing" -> {
                val rule =
                    ChannelRoutingRuleDto(
                        channelPattern = v("channel_pattern"),
                        peerName = v("peer_name"),
                        automataType = v("automata_type"),
                    )
                val cur: List<ChannelRoutingRuleDto> = tr.getChannelRouting().getOrThrow().rules
                val idx: Int = originalId?.toIntOrNull() ?: -1
                val next: List<ChannelRoutingRuleDto> =
                    if (idx in cur.indices) cur.mapIndexed { i, r -> if (i == idx) rule else r } else cur + rule
                tr.putChannelRouting(next).getOrThrow()
            }
            "council_personas" -> {
                val dto = CouncilPersonaCreateDto(name = originalId ?: v("name"), prompt = values["prompt"].orEmpty().trim(), description = v("description"))
                if (originalId == null) tr.createCouncilPersona(dto).getOrThrow() else tr.updateCouncilPersona(originalId, dto).getOrThrow()
            }
            "skill_registries" -> {
                val name: String = originalId ?: throw UnsupportedOperationException("Use Add for new registries")
                tr.updateSkillRegistry(name, SkillRegistryUpdateDto(url = v("url"), branch = v("branch").ifEmpty { "main" })).getOrThrow()
            }
            "discussions" -> {
                require(v("id").isNotEmpty()) { "Scope ID is required" }
                tr.createDiscussionScope(v("id")).getOrThrow()
            }
            else -> throw UnsupportedOperationException("Not supported: $kind")
        }
    }

    // ---- Project / cluster profiles as JSON ----

    /** Profile [name] as pretty JSON with credential values masked as "•••" (empty name → template). */
    public fun profileJson(
        profile: ServerProfile,
        kind: String,
        name: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val k: String = kindOf(kind)
            runCatching {
                if (name.isEmpty()) {
                    "{\n  \"name\": \"\",\n  \"description\": \"\"\n}"
                } else {
                    val o: JsonObject = t(profile).listKindProfiles(k).getOrThrow().first { it.s("name") == name }
                    pretty.encodeToString(JsonElement.serializer(), mask(o))
                }
            }.fold(onSuccess = { onSuccess(it) }, onFailure = { onError(it.message ?: "Failed to load.") })
        }
    }

    /** Saves edited JSON; masked "•••" values are restored from the stored profile. */
    public fun saveProfileJson(
        profile: ServerProfile,
        kind: String,
        originalName: String?,
        json: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val k: String = kindOf(kind)
            val tr = t(profile)
            val r: Result<Unit> =
                runCatching {
                    val edited: JsonObject =
                        Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("Profile must be a JSON object")
                    if (originalName == null) {
                        tr.createKindProfile(k, edited).getOrThrow()
                    } else {
                        val stored: JsonObject = tr.listKindProfiles(k).getOrThrow().firstOrNull { it.s("name") == originalName } ?: JsonObject(emptyMap())
                        val merged: JsonElement = unmask(edited, stored)
                        tr.putKindProfile(k, originalName, merged as JsonObject).getOrThrow()
                    }
                }
            onDone(msg(r.exceptionOrNull(), "Save failed."))
        }
    }

    private fun kindOf(listKind: String): String = if (listKind == "cluster_profiles") "cluster" else "project"

    private fun mask(el: JsonElement): JsonElement =
        when (el) {
            is JsonObject ->
                JsonObject(
                    el.mapValues { (k, v) ->
                        if (sensitive.containsMatchIn(k) && v is JsonPrimitive && v !is JsonNull && v.content.isNotEmpty() && !v.content.startsWith("\${secret:")) {
                            JsonPrimitive(MASK)
                        } else {
                            mask(v)
                        }
                    },
                )
            is JsonArray -> JsonArray(el.map { mask(it) })
            else -> el
        }

    private fun unmask(
        edited: JsonElement,
        stored: JsonElement?,
    ): JsonElement =
        when {
            edited is JsonPrimitive && edited.content == MASK && stored != null -> stored
            edited is JsonObject ->
                JsonObject(edited.mapValues { (k, v) -> unmask(v, (stored as? JsonObject)?.get(k)) })
            edited is JsonArray ->
                JsonArray(edited.mapIndexed { i, v -> unmask(v, (stored as? JsonArray)?.getOrNull(i)) })
            else -> edited
        }

    // ---- Federation peer ----

    public fun testFedPeer(
        profile: ServerProfile,
        name: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).testFederationPeer(name).fold(
                onSuccess = { o ->
                    val ok: Boolean = o.s("ok") == "true"
                    val text: String =
                        if (ok) {
                            "OK — ${o.s("latency_ms")}ms (${o.s("version").ifEmpty { "unknown version" }})"
                        } else {
                            "FAIL: ${o.s("error").ifEmpty { "no error" }}"
                        }
                    onSuccess(text)
                },
                onFailure = { onError(it.message ?: "Test failed.") },
            )
        }
    }

    public fun setFedPeerEnabled(
        profile: ServerProfile,
        name: String,
        enabled: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val body = JsonObject(mapOf("enabled" to JsonPrimitive(enabled)))
            onDone(msg(t(profile).updateFederationPeer(name, body).exceptionOrNull(), "Update failed."))
        }
    }

    // ---- Exit hooks (BL356) ----

    public fun exitHooks(
        profile: ServerProfile,
        onSuccess: (List<IosExitHook>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listExitHooksJson().fold(
                onSuccess = { arr ->
                    val list: List<IosExitHook> =
                        arr.mapNotNull { it as? JsonObject }.map { o ->
                            IosExitHook(
                                id = o.s("id"),
                                name = o.s("name"),
                                action = o.s("action"),
                                notifySession = o.s("notify_session"),
                                cooldownSeconds = o.s("cooldown_seconds").toIntOrNull() ?: 0,
                                enabled = o.s("enabled") == "true",
                                lastFiredAt = o.s("last_fired_at").takeUnless { it.startsWith("0001-01-01") }.orEmpty(),
                            )
                        }
                    onSuccess(list)
                },
                onFailure = { onError(it.message ?: "Failed to load exit hooks.") },
            )
        }
    }

    /** PWA `createExitHook`: notify fields only for action=notify; cooldown defaults to 300. */
    public fun createExitHook(
        profile: ServerProfile,
        name: String,
        action: String,
        notifySession: String,
        notifyMessage: String,
        cooldown: String,
        onDone: (String?) -> Unit,
    ) {
        val fields = LinkedHashMap<String, JsonElement>()
        fields["name"] = JsonPrimitive(name.trim())
        fields["action"] = JsonPrimitive(action)
        fields["cooldown_seconds"] = JsonPrimitive(cooldown.trim().toIntOrNull() ?: 300)
        if (action == "notify" && notifySession.isNotBlank()) {
            fields["notify_session"] = JsonPrimitive(notifySession.trim())
            if (notifyMessage.isNotBlank()) fields["notify_message"] = JsonPrimitive(notifyMessage)
        }
        scope.launch {
            onDone(msg(t(profile).createExitHook(JsonObject(fields)).exceptionOrNull(), "Failed to add exit hook."))
        }
    }

    public fun setExitHookEnabled(
        profile: ServerProfile,
        id: String,
        enabled: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val body = JsonObject(mapOf("enabled" to JsonPrimitive(enabled)))
            onDone(msg(t(profile).updateExitHook(id, body).exceptionOrNull(), "Update failed."))
        }
    }

    public fun deleteExitHook(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch { onDone(msg(t(profile).deleteExitHook(id).exceptionOrNull(), "Delete failed.")) }
    }

    // ---- Work queue (BL357) ----

    public fun queue(
        profile: ServerProfile,
        role: String,
        state: String,
        onSuccess: (List<IosQueueItem>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listQueueJson(role.trim().ifEmpty { null }, state.ifEmpty { null }).fold(
                onSuccess = { arr ->
                    val list: List<IosQueueItem> =
                        arr.mapNotNull { it as? JsonObject }.map { o ->
                            IosQueueItem(
                                id = o.s("id"),
                                role = o.s("role"),
                                state = o.s("state"),
                                claimedBy = o.s("claimed_by"),
                                payload = o["payload"]?.let { p -> Json.encodeToString(JsonElement.serializer(), p) }.orEmpty(),
                            )
                        }
                    onSuccess(list)
                },
                onFailure = { onError(it.message ?: "Failed to load queue items.") },
            )
        }
    }

    /** PWA `pushQueueItem`: blank payload = `{}`; otherwise it must parse as a JSON object. */
    public fun pushQueue(
        profile: ServerProfile,
        role: String,
        payload: String,
        onDone: (String?) -> Unit,
    ) {
        val parsed: JsonObject? =
            if (payload.isBlank()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        if (parsed == null) {
            onDone("Invalid payload JSON: payload must be a JSON object")
            return
        }
        scope.launch {
            onDone(msg(t(profile).pushQueueItem(role.trim(), parsed).exceptionOrNull(), "Failed to push queue item."))
        }
    }

    public fun deleteQueue(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch { onDone(msg(t(profile).deleteQueueItem(id).exceptionOrNull(), "Failed to delete queue item $id")) }
    }

    // ---- Tailscale auth key ----

    /** Generates a pre-auth key; [onSuccess] gets (key, expiresAt). Callers must not display or log the key. */
    public fun tailscaleAuthKey(
        profile: ServerProfile,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).generateTailscaleAuthKey().fold(
                onSuccess = { d ->
                    val err: String = d.error.orEmpty()
                    if (err.isNotEmpty() || d.key.isEmpty()) onError(err.ifEmpty { "No key returned." }) else onSuccess(d.key, d.expiresAt.orEmpty())
                },
                onFailure = { onError(it.message ?: "Failed to generate auth key.") },
            )
        }
    }

    // ---- Discussion scopes ----

    public fun writeDiscussion(
        profile: ServerProfile,
        id: String,
        content: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            onDone(msg(t(profile).writeDiscussionMessage(id, content.trim()).exceptionOrNull(), "Write failed."))
        }
    }

    // ---- Ollama marketplace ----

    public fun ollamaCatalog(
        profile: ServerProfile,
        onSuccess: (List<IosOllamaModel>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getOllamaCatalog().fold(
                onSuccess = { c ->
                    onSuccess(
                        c.models.map { m ->
                            IosOllamaModel(
                                name = m.name,
                                description = m.description,
                                tags = m.tags.map { tg -> listOf(tg.tag, tg.size).filter { it.isNotEmpty() }.joinToString(" · ") + if (tg.fits) "" else " ⚠" },
                                tagNames = m.tags.map { it.tag },
                            )
                        },
                    )
                },
                onFailure = { onError(it.message ?: "Failed to load catalog.") },
            )
        }
    }

    /** Names of enabled Ollama compute nodes (pull targets). */
    public fun ollamaNodes(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listComputeNodes().fold(
                onSuccess = { nodes -> onSuccess(nodes.filter { it.enabled && it.kind == "ollama" }.map { it.name }) },
                onFailure = { onError(it.message ?: "Failed to load compute nodes.") },
            )
        }
    }

    /** Starts a pull and polls the task every 2 s until it finishes; [onProgress] fires per poll. */
    public fun pullOllamaModel(
        profile: ServerProfile,
        node: String,
        model: String,
        onProgress: (IosPullTask) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val start = tr.pullOllamaModel(node, model)
            val first = start.getOrNull()
            if (first == null) {
                onError(start.exceptionOrNull()?.message ?: "Pull failed.")
                return@launch
            }
            var task: com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto = first
            onProgress(IosPullTask(id = task.id, model = task.model.ifEmpty { model }, progress = task.progress, status = task.status))
            var polls: Int = 0
            while (task.status != "done" && task.status != "complete" && task.status != "error" && task.status != "failed" && polls < 900) {
                delay(2000)
                polls += 1
                val next = tr.getPullTask(task.id).getOrNull() ?: continue
                task = next
                onProgress(IosPullTask(id = task.id, model = task.model.ifEmpty { model }, progress = task.progress, status = task.status))
            }
        }
    }

    // ---- Council runs ----

    public fun councilRuns(
        profile: ServerProfile,
        onSuccess: (List<IosCouncilRun>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).councilListRuns().fold(
                onSuccess = { runs ->
                    onSuccess(
                        runs.map { r ->
                            IosCouncilRun(
                                id = r.id,
                                proposal = r.proposal,
                                mode = r.mode,
                                status = r.status,
                                personas = r.personas,
                                consensus = r.consensus.orEmpty(),
                                startedAt = r.startedAt.orEmpty(),
                            )
                        },
                    )
                },
                onFailure = { onError(it.message ?: "Failed to load runs.") },
            )
        }
    }

    public fun startCouncilRun(
        profile: ServerProfile,
        proposal: String,
        mode: String,
        personas: List<String>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val req = StartCouncilRunRequest(proposal = proposal.trim(), mode = mode, personas = personas)
            onDone(msg(t(profile).councilStartRun(req).exceptionOrNull(), "Failed to start run."))
        }
    }

    public fun stopCouncilRun(
        profile: ServerProfile,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch { onDone(msg(t(profile).councilStopRun(id).exceptionOrNull(), "Failed to stop run.")) }
    }

    // ---- Compute kind migration ----

    public fun kindMigration(
        profile: ServerProfile,
        onSuccess: (IosKindMigration) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val kinds = tr.getMigrationComputeKinds()
            val k = kinds.getOrNull()
            if (k == null) {
                onError(kinds.exceptionOrNull()?.message ?: "Failed to load migration status.")
                return@launch
            }
            val status = tr.getMigrationStatus().getOrNull()
            onSuccess(
                IosKindMigration(
                    notice = status?.notice.orEmpty(),
                    show = status?.show ?: false,
                    nodes = k.nodes.map { it.name },
                    nodeKinds = k.nodes.map { it.currentKind },
                    supportedKinds = k.supportedKinds,
                ),
            )
        }
    }

    public fun migrateKind(
        profile: ServerProfile,
        node: String,
        kind: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch { onDone(msg(t(profile).migrateComputeNodeKind(node, kind).exceptionOrNull(), "Migration failed.")) }
    }

    public fun dismissMigration(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch { onDone(msg(t(profile).dismissMigration().exceptionOrNull(), "Dismiss failed.")) }
    }

    // ---- helpers ----

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

    private fun JsonObject.list(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
}
