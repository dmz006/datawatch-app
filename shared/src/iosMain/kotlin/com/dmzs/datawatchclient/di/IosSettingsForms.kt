package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.SignalLinkEvent
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDto
import com.dmzs.datawatchclient.transport.dto.FreeObserverPeerDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One ComputeNode reference for pickers (name + LLM-API kind). */
public data class IosNodeRef(
    val name: String,
    val kind: String,
)

/** One enabled model row of an LLM (`models[]` — node empty for SaaS kinds). */
public data class IosLlmModel(
    val node: String,
    val model: String,
)

/**
 * LLM edit-form snapshot. [values] holds the scalar fields as strings (bools
 * "true"/"false", lists comma-joined). A literal API key is never returned:
 * `api_key_ref` is only echoed when it is a `${secret:…}` reference, and
 * [apiKeyConfigured] says a literal value exists server-side.
 */
public data class IosLlmForm(
    val values: Map<String, String>,
    val models: List<IosLlmModel>,
    val computeNodes: List<String>,
    val apiKeyConfigured: Boolean,
)

/** One session in Algorithm Mode (PWA _renderAlgorithmRow). */
public data class IosAlgorithmRow(
    val sessionId: String,
    val current: String,
    val phaseIndex: Int,
    val historyCount: Int,
    val aborted: Boolean,
)

/** Signal device-link status (GET /api/link/status). */
public data class IosSignalStatus(
    val linked: Boolean,
    val account: String,
    val deviceCount: Int,
    val error: String,
)

/** Cancels an in-flight Signal link stream (closing the sheet). */
public class IosLinkHandle internal constructor() {
    internal var job: Job? = null

    public fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Settings forms (iOS parity): ComputeNode + LLM add/edit (PWA
 * openComputeAddPanel / _renderLLMEditPanel), Algorithm Mode card, Autonomous
 * scan defaults, Signal device linking. Callbacks fire off the main thread;
 * mutations report null on success or an error message.
 */
public object IosSettingsForms {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** PWA ALGO_PHASES. */
    public val phases: List<String> = listOf("observe", "orient", "decide", "act", "measure", "learn", "improve")

    /** PWA openComputeAddPanel kind options. */
    public val computeKinds: List<String> = listOf("ollama", "openai-compat", "gemini-api", "opencode-api")

    /** PWA _renderLLMEditPanel allKinds. */
    public val llmKinds: List<String> =
        listOf(
            "ollama", "openwebui", "opencode", "claude-code", "opencode-acp", "opencode-prompt",
            "aider", "goose", "gemini", "council", "shell",
        )

    /** PWA _llmSaasKinds — no ComputeNodes / node column / auto-add. */
    public val llmSaasKinds: List<String> = listOf("claude-code", "aider", "goose", "gemini")

    /** PWA _llmSessionBackendKinds — shows the session-backend section. */
    public val llmSessionKinds: List<String> =
        listOf("claude-code", "aider", "goose", "gemini", "opencode", "opencode-acp", "opencode-prompt", "shell")

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun err(
        e: Throwable?,
        fallback: String,
    ): String? = e?.let { it.message ?: fallback }

    // ---- ComputeNode form ----

    /** Existing node → form values (see [saveComputeNode] for the key set). */
    public fun loadComputeNode(
        profile: ServerProfile,
        name: String,
        onSuccess: (Map<String, String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchComputeNodeJson(name).fold(
                onSuccess = { onSuccess(computeValues(it)) },
                onFailure = { onError(it.message ?: "Load failed.") },
            )
        }
    }

    /**
     * Add (editName empty) or edit a ComputeNode. Keys: name, kind, address,
     * routing, dn_image, dn_network, dn_port, dn_container, dn_endpoint, dn_env
     * (one KEY=VALUE per line), dn_auto_start, dn_auto_pull, dp_peer,
     * dp_remote_llm, dp_timeout, observer_peer, hw_os, hw_arch, hw_gpu_vendor,
     * hw_gpu_model, hw_gpu_count, hw_vram (GB per GPU), hw_memory_gb,
     * hw_cpu_cores, max_models. Edit overlays the GET record so fields the form
     * doesn't expose survive.
     */
    public fun saveComputeNode(
        profile: ServerProfile,
        editName: String,
        values: Map<String, String>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val isEdit: Boolean = editName.isNotBlank()
            val name: String = if (isEdit) editName else values["name"]?.trim().orEmpty()
            val problem: String? = validateCompute(name, values)
            if (problem != null) {
                onDone(problem)
                return@launch
            }
            val base: JsonObject =
                if (isEdit) tr.fetchComputeNodeJson(editName).getOrNull() ?: JsonObject(emptyMap()) else JsonObject(emptyMap())
            val body: JsonObject = buildComputeBody(base, name, isEdit, values)
            val r: Result<Unit> = tr.saveComputeNodeJson(if (isEdit) editName else null, body)
            onDone(err(r.exceptionOrNull(), if (isEdit) "Update failed." else "Add failed."))
        }
    }

    /**
     * PWA computeTestConnectionDraft: creates a transient `smoke-test-*` node
     * with the entered kind/address, probes /health, then deletes it.
     */
    public fun testComputeDraft(
        profile: ServerProfile,
        kind: String,
        address: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val addr: String = normalizeAddress(address)
        if (addr.isEmpty()) {
            onError("Address required")
            return
        }
        scope.launch {
            val tr = t(profile)
            val probeName = "smoke-test-" + kotlin.random.Random.nextLong(100_000_000L, 999_999_999L).toString()
            val body =
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(probeName),
                        "kind" to JsonPrimitive(kind.ifBlank { "ollama" }),
                        "address" to JsonPrimitive(addr),
                        "declared_capacity" to JsonObject(mapOf("max_concurrent_models" to JsonPrimitive(1))),
                    ),
                )
            val created: Result<Unit> = tr.saveComputeNodeJson(null, body)
            if (created.isFailure) {
                onError(created.exceptionOrNull()?.message ?: "Test failed.")
                return@launch
            }
            val health: Result<JsonObject> = tr.computeNodeHealthJson(probeName)
            tr.deleteComputeNode(probeName)
            health.fold(
                onSuccess = { h ->
                    val summary: String = h.s("status").ifEmpty { h.s("ok") }.ifEmpty { h.toString().take(80) }
                    onSuccess(summary.ifEmpty { "OK" })
                },
                onFailure = { onError(it.message ?: "Test failed.") },
            )
        }
    }

    /** Observer-peer picker: free peers plus [current] (kept even when bound). */
    public fun observerChoices(
        profile: ServerProfile,
        current: String,
        onSuccess: (List<String>) -> Unit,
    ) {
        scope.launch {
            val free: List<FreeObserverPeerDto> = t(profile).getFreePeers().getOrNull().orEmpty()
            val names: MutableList<String> = free.map { it.name }.filter { it.isNotEmpty() }.toMutableList()
            if (current.isNotBlank() && !names.contains(current)) names.add(0, current)
            onSuccess(names)
        }
    }

    /** All ComputeNodes (name + kind) for the LLM form pickers. */
    public fun computeNodes(
        profile: ServerProfile,
        onSuccess: (List<IosNodeRef>) -> Unit,
    ) {
        scope.launch {
            val nodes: List<ComputeNodeDto> = t(profile).listComputeNodes().getOrNull().orEmpty()
            onSuccess(nodes.map { n -> IosNodeRef(name = n.name, kind = n.kind) })
        }
    }

    /** Models available on [node] for the LLM kind (PWA _llmProbeNodeModels); empty on failure. */
    public fun nodeModels(
        profile: ServerProfile,
        node: String,
        kind: String,
        onSuccess: (List<String>) -> Unit,
    ) {
        scope.launch {
            onSuccess(t(profile).computeNodeModelNames(node, kind.ifBlank { "ollama" }).getOrNull().orEmpty())
        }
    }

    // ---- LLM form ----

    public fun loadLlm(
        profile: ServerProfile,
        name: String,
        onSuccess: (IosLlmForm) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchLlmJson(name).fold(
                onSuccess = { onSuccess(llmForm(it)) },
                onFailure = { onError(it.message ?: "Load failed.") },
            )
        }
    }

    /**
     * Add (editName empty) or edit an LLM (PWA _llmSaveDraft field set). A
     * blank `api_key_ref` on edit keeps a configured literal key untouched.
     */
    public fun saveLlm(
        profile: ServerProfile,
        editName: String,
        values: Map<String, String>,
        models: List<IosLlmModel>,
        computeNodes: List<String>,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val isEdit: Boolean = editName.isNotBlank()
            val name: String = if (isEdit) editName else values["name"]?.trim().orEmpty()
            if (name.isEmpty()) {
                onDone("LLM name required")
                return@launch
            }
            val base: JsonObject =
                if (isEdit) tr.fetchLlmJson(editName).getOrNull() ?: JsonObject(emptyMap()) else JsonObject(emptyMap())
            val body: JsonObject = buildLlmBody(base, name, values, models, computeNodes)
            val r: Result<Unit> = tr.saveLlmJson(if (isEdit) editName else null, body)
            onDone(err(r.exceptionOrNull(), if (isEdit) "Update failed." else "Add failed."))
        }
    }

    /** POST /api/llms/{name}/test — success text is the reply (truncated). */
    public fun testLlm(
        profile: ServerProfile,
        name: String,
        model: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).testLlmJson(name, model.ifBlank { null }).fold(
                onSuccess = { o ->
                    val text: String = o.s("text").ifEmpty { o.toString() }
                    onSuccess(text.take(160).ifEmpty { "OK" })
                },
                onFailure = { onError((it.message ?: "Test failed.").take(240)) },
            )
        }
    }

    // ---- Algorithm Mode ----

    public fun algorithmSessions(
        profile: ServerProfile,
        onSuccess: (List<IosAlgorithmRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).algorithmList().fold(
                onSuccess = { list -> onSuccess(list.map { s -> algorithmRow(s) }) },
                onFailure = { onError(it.message ?: "Failed to load.") },
            )
        }
    }

    /** action: advance (optional output) · edit (output required) · abort · reset. */
    public fun algorithmAction(
        profile: ServerProfile,
        sessionId: String,
        action: String,
        output: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val out: String = output.trim()
            val r: Result<*> =
                when (action) {
                    "advance" -> tr.algorithmAdvanceWithOutput(sessionId, out)
                    "edit" ->
                        if (out.isEmpty()) {
                            Result.failure<Unit>(IllegalArgumentException("Edit requires output text in the field above"))
                        } else {
                            tr.algorithmEdit(sessionId, out)
                        }
                    "abort" -> tr.algorithmAbort(sessionId)
                    "reset" -> tr.algorithmReset(sessionId)
                    else -> Result.failure<Unit>(UnsupportedOperationException("Unknown action"))
                }
            onDone(err(r.exceptionOrNull(), "Action failed."))
        }
    }

    // ---- Autonomous scan defaults (PWA loadAutomataSettingsPanel) ----

    /** GET /api/autonomous/scan/config flattened to strings. */
    public fun loadScanConfig(
        profile: ServerProfile,
        onSuccess: (Map<String, String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchScanConfigJson().fold(
                onSuccess = { o ->
                    val m: Map<String, String> =
                        o.entries.mapNotNull { (k, v) ->
                            val p: JsonPrimitive? = v as? JsonPrimitive
                            if (p == null || p is JsonNull) null else k to p.content
                        }.toMap()
                    onSuccess(m)
                },
                onFailure = { onError(it.message ?: "not available") },
            )
        }
    }

    /** PUT one scan field; kind = toggle | number | text. */
    public fun saveScanField(
        profile: ServerProfile,
        key: String,
        kind: String,
        value: String,
        onDone: (String?) -> Unit,
    ) {
        val prim: JsonPrimitive =
            when (kind) {
                "toggle" -> JsonPrimitive(value.equals("true", ignoreCase = true))
                "number" -> JsonPrimitive(value.trim().toIntOrNull() ?: 0)
                else -> JsonPrimitive(value)
            }
        scope.launch {
            val r: Result<Unit> = t(profile).patchScanConfig(JsonObject(mapOf(key to prim)))
            onDone(err(r.exceptionOrNull(), "Save failed."))
        }
    }

    // ---- Signal device linking (PWA startLinking / streamLinkEvents) ----

    public fun signalStatus(
        profile: ServerProfile,
        onSuccess: (IosSignalStatus) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).fetchSignalLinkStatusJson().fold(
                onSuccess = { o ->
                    val devices: Int = (o["devices"] as? JsonArray)?.size ?: 0
                    onSuccess(
                        IosSignalStatus(
                            linked = o.b("linked"),
                            account = o.s("account_number"),
                            deviceCount = devices,
                            error = o.s("error"),
                        ),
                    )
                },
                onFailure = { onError(it.message ?: "Unknown") },
            )
        }
    }

    /**
     * POST /api/link/start then follow the SSE stream. [onQr] receives the
     * `sgnl://` URI to render as a QR code; [onLinked] / [onError] are terminal.
     */
    public fun startSignalLink(
        profile: ServerProfile,
        onQr: (String) -> Unit,
        onLinked: () -> Unit,
        onError: (String) -> Unit,
    ): IosLinkHandle {
        val handle = IosLinkHandle()
        handle.job =
            scope.launch {
                val tr = t(profile)
                val started: Result<String> = tr.startSignalLink("")
                val streamId: String? = started.getOrNull()
                if (streamId == null) {
                    onError(started.exceptionOrNull()?.message ?: "Failed to start linking")
                    return@launch
                }
                var finished = false
                try {
                    tr.signalLinkEvents(streamId).collect { ev: SignalLinkEvent ->
                        when (ev.event) {
                            "qr" -> onQr(ev.data.trim())
                            "linked" -> {
                                finished = true
                                onLinked()
                            }
                            "error" -> {
                                finished = true
                                onError(ev.data.ifBlank { "unknown error" })
                            }
                            else -> Unit
                        }
                    }
                    if (!finished) onError("Linking stream closed.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (!finished) onError(e.message ?: "Linking error")
                }
            }
        return handle
    }

    // ---- builders ----

    private fun validateCompute(
        name: String,
        v: Map<String, String>,
    ): String? {
        if (name.isEmpty()) return "ComputeNode name required"
        val routing: String = v["routing"].orEmpty().ifBlank { "direct" }
        if (routing == "docker-network" && v["dn_image"].orEmpty().isBlank()) {
            return "Docker image is required for docker-network routing"
        }
        if (routing == "datawatch-proxy") {
            if (v["dp_peer"].orEmpty().isBlank()) return "Peer is required for datawatch-proxy routing"
            if (v["dp_remote_llm"].orEmpty().isBlank()) return "Remote LLM name is required for datawatch-proxy routing"
        }
        return null
    }

    private fun normalizeAddress(address: String): String {
        val a: String = address.trim()
        if (a.isEmpty()) return ""
        val lower: String = a.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) a else "http://$a"
    }

    private fun int(
        v: Map<String, String>,
        k: String,
    ): Int = v[k]?.trim()?.toIntOrNull() ?: 0

    private fun flag(
        v: Map<String, String>,
        k: String,
    ): Boolean = v[k]?.equals("true", ignoreCase = true) ?: false

    private fun lines(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun csv(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun strArray(list: List<String>): JsonArray = JsonArray(list.map { JsonPrimitive(it) })

    private fun buildComputeBody(
        base: JsonObject,
        name: String,
        isEdit: Boolean,
        v: Map<String, String>,
    ): JsonObject {
        val out: MutableMap<String, JsonElement> = base.toMutableMap()
        val gpuCount: Int = int(v, "hw_gpu_count")
        val vram: Int = int(v, "hw_vram")
        val memoryGb: Int = int(v, "hw_memory_gb")
        var maxModels: Int = int(v, "max_models")
        if (maxModels == 0 && vram > 0 && gpuCount > 0) maxModels = maxOf(1, (vram * gpuCount) / 8)
        val routing: String = v["routing"].orEmpty().ifBlank { "direct" }
        out["name"] = JsonPrimitive(name)
        out["kind"] = JsonPrimitive(v["kind"].orEmpty().ifBlank { "ollama" })
        out["address"] = JsonPrimitive(normalizeAddress(v["address"].orEmpty()))
        if (!isEdit) out["monitoring_endpoint"] = JsonPrimitive("")
        out["routing"] = JsonPrimitive(routing)
        out["routing_docker_network"] =
            if (routing == "docker-network") {
                JsonObject(
                    mapOf(
                        "docker_endpoint" to JsonPrimitive(v["dn_endpoint"].orEmpty().trim()),
                        "network_name" to JsonPrimitive(v["dn_network"].orEmpty().trim()),
                        "image" to JsonPrimitive(v["dn_image"].orEmpty().trim()),
                        "container_name" to JsonPrimitive(v["dn_container"].orEmpty().trim()),
                        "port" to JsonPrimitive(int(v, "dn_port")),
                        "env" to strArray(lines(v["dn_env"].orEmpty())),
                        "auto_start" to JsonPrimitive(flag(v, "dn_auto_start")),
                        "auto_pull" to JsonPrimitive(flag(v, "dn_auto_pull")),
                    ),
                )
            } else {
                JsonNull
            }
        out["routing_datawatch_proxy"] =
            if (routing == "datawatch-proxy") {
                JsonObject(
                    mapOf(
                        "peer" to JsonPrimitive(v["dp_peer"].orEmpty().trim()),
                        "remote_llm_name" to JsonPrimitive(v["dp_remote_llm"].orEmpty().trim()),
                        "timeout_seconds" to JsonPrimitive(int(v, "dp_timeout")),
                    ),
                )
            } else {
                JsonNull
            }
        val cap: MutableMap<String, JsonElement> = (base["declared_capacity"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        cap["max_concurrent_models"] = JsonPrimitive(maxModels)
        cap["gpus"] = JsonPrimitive(gpuCount)
        cap["gpu_mem_gb"] = JsonPrimitive(vram * gpuCount)
        cap["ram_gb"] = JsonPrimitive(memoryGb)
        cap["gpu_vendor"] = JsonPrimitive(v["hw_gpu_vendor"].orEmpty().trim())
        cap["gpu_model"] = JsonPrimitive(v["hw_gpu_model"].orEmpty().trim())
        out["declared_capacity"] = JsonObject(cap)
        val hw: MutableMap<String, JsonElement> = (base["hardware"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        hw["os"] = JsonPrimitive(v["hw_os"].orEmpty().trim())
        hw["arch"] = JsonPrimitive(v["hw_arch"].orEmpty().trim())
        hw["gpu_vendor"] = JsonPrimitive(v["hw_gpu_vendor"].orEmpty().trim())
        hw["gpu_model"] = JsonPrimitive(v["hw_gpu_model"].orEmpty().trim())
        hw["gpu_count"] = JsonPrimitive(gpuCount)
        hw["memory_gb"] = JsonPrimitive(memoryGb)
        hw["cpu_cores"] = JsonPrimitive(int(v, "hw_cpu_cores"))
        out["hardware"] = JsonObject(hw)
        if (!out.containsKey("scheduling_priority")) out["scheduling_priority"] = JsonPrimitive(50)
        out["observer_peer"] = JsonPrimitive(v["observer_peer"].orEmpty().trim())
        return JsonObject(out)
    }

    private fun computeValues(o: JsonObject): Map<String, String> {
        val cap: JsonObject = o["declared_capacity"] as? JsonObject ?: JsonObject(emptyMap())
        val hw: JsonObject =
            o["hardware"] as? JsonObject ?: o["hardware_spec"] as? JsonObject ?: JsonObject(emptyMap())
        val dn: JsonObject = o["routing_docker_network"] as? JsonObject ?: JsonObject(emptyMap())
        val dp: JsonObject = o["routing_datawatch_proxy"] as? JsonObject ?: JsonObject(emptyMap())
        val gpuCount: Int = hw.i("gpu_count")
        val gpuMem: Int = cap.i("gpu_mem_gb")
        val vramPer: Int = if (gpuMem > 0 && gpuCount > 0) gpuMem / maxOf(1, gpuCount) else 0
        fun nz(n: Int): String = if (n > 0) n.toString() else ""
        return mapOf(
            "name" to o.s("name"),
            "kind" to o.s("kind"),
            "address" to o.s("address"),
            "routing" to o.s("routing").ifEmpty { "direct" },
            "dn_image" to dn.s("image"),
            "dn_network" to dn.s("network_name"),
            "dn_port" to nz(dn.i("port")),
            "dn_container" to dn.s("container_name"),
            "dn_endpoint" to dn.s("docker_endpoint"),
            "dn_env" to dn.list("env").joinToString("\n"),
            "dn_auto_start" to dn.b("auto_start").toString(),
            "dn_auto_pull" to dn.b("auto_pull").toString(),
            "dp_peer" to dp.s("peer"),
            "dp_remote_llm" to dp.s("remote_llm_name"),
            "dp_timeout" to nz(dp.i("timeout_seconds")),
            "observer_peer" to o.s("observer_peer"),
            "hw_os" to hw.s("os"),
            "hw_arch" to hw.s("arch"),
            "hw_gpu_vendor" to hw.s("gpu_vendor"),
            "hw_gpu_model" to hw.s("gpu_model"),
            "hw_gpu_count" to nz(gpuCount),
            "hw_vram" to nz(vramPer),
            "hw_memory_gb" to nz(hw.i("memory_gb")),
            "hw_cpu_cores" to nz(hw.i("cpu_cores")),
            "max_models" to nz(cap.i("max_concurrent_models")),
        )
    }

    private fun llmForm(o: JsonObject): IosLlmForm {
        val key: String = o.s("api_key_ref")
        val isRef: Boolean = key.startsWith("\${secret:")
        val modelsArr: JsonArray? = o["models"] as? JsonArray
        val nodes: List<String> = o.list("compute_nodes")
        val models: List<IosLlmModel> =
            if (modelsArr != null && modelsArr.isNotEmpty()) {
                modelsArr.mapNotNull { el ->
                    val m: JsonObject? = el as? JsonObject
                    if (m == null) {
                        null
                    } else {
                        IosLlmModel(node = m.s("node").ifEmpty { m.s("compute_node") }, model = m.s("model"))
                    }
                }.filter { it.model.isNotEmpty() }
            } else if (o.s("model").isNotEmpty()) {
                listOf(IosLlmModel(node = nodes.firstOrNull().orEmpty(), model = o.s("model")))
            } else {
                emptyList()
            }
        fun nz(n: Int): String = if (n > 0) n.toString() else ""
        val values: Map<String, String> =
            mapOf(
                "name" to o.s("name"),
                "kind" to o.s("kind"),
                "api_key_ref" to (if (isRef) key else ""),
                "timeout_seconds" to nz(o.i("timeout_seconds")),
                "max_inflight" to nz(o.i("max_inflight")),
                "tags" to o.list("tags").joinToString(", "),
                "auto_add_models" to o.b("auto_add_models").toString(),
                "binary" to o.s("binary"),
                "console_cols" to nz(o.i("console_cols")),
                "console_rows" to nz(o.i("console_rows")),
                "output_mode" to o.s("output_mode"),
                "input_mode" to o.s("input_mode"),
                "auto_git_init" to o.b("auto_git_init").toString(),
                "auto_git_commit" to o.b("auto_git_commit").toString(),
                "skip_permissions" to o.b("skip_permissions").toString(),
                "channel_enabled" to o.b("channel_enabled").toString(),
                "auto_accept_disclaimer" to o.b("auto_accept_disclaimer").toString(),
                "permission_mode" to o.s("permission_mode"),
                "default_effort" to o.s("default_effort"),
                "fallback_chain" to o.list("fallback_chain").joinToString(", "),
            )
        return IosLlmForm(
            values = values,
            models = models,
            computeNodes = nodes,
            // Servers ≥ v8.63.1 redact a literal key: blank `api_key_ref` +
            // `api_key_ref_present: true` (datawatch GH#179).
            apiKeyConfigured = (key.isNotEmpty() && !isRef) || (key.isEmpty() && o.b("api_key_ref_present")),
        )
    }

    private fun buildLlmBody(
        base: JsonObject,
        name: String,
        v: Map<String, String>,
        models: List<IosLlmModel>,
        computeNodes: List<String>,
    ): JsonObject {
        val out: MutableMap<String, JsonElement> = base.toMutableMap()
        val kind: String = v["kind"].orEmpty().ifBlank { "ollama" }
        val saas: Boolean = llmSaasKinds.contains(kind)
        val cleanModels: List<IosLlmModel> = models.filter { it.model.isNotBlank() }
        out["name"] = JsonPrimitive(name)
        out["kind"] = JsonPrimitive(kind)
        out["models"] =
            JsonArray(
                cleanModels.map { m ->
                    JsonObject(mapOf("node" to JsonPrimitive(if (saas) "" else m.node), "model" to JsonPrimitive(m.model)))
                },
            )
        out["model"] = JsonPrimitive(cleanModels.firstOrNull()?.model.orEmpty())
        out["auto_add_models"] = JsonPrimitive(if (saas) false else flag(v, "auto_add_models"))
        out["compute_nodes"] = strArray(if (saas) emptyList() else computeNodes)
        val key: String = v["api_key_ref"].orEmpty().trim()
        val existingKey: String = base.s("api_key_ref")
        val keepLiteral: Boolean = key.isEmpty() && existingKey.isNotEmpty() && !existingKey.startsWith("\${secret:")
        // v8.63.1+ returns a literal key redacted (blank + api_key_ref_present) and
        // merges a PUT onto the stored entry: omit the field so the key is kept,
        // rather than echoing the blank back.
        val keepRedacted: Boolean = key.isEmpty() && existingKey.isEmpty() && base.b("api_key_ref_present")
        out.remove("api_key_ref_present")
        out.remove("api_key_ref_prefix")
        if (keepRedacted) {
            out.remove("api_key_ref")
        } else if (!keepLiteral) {
            out["api_key_ref"] = JsonPrimitive(key)
        }
        out["timeout_seconds"] = JsonPrimitive(int(v, "timeout_seconds"))
        out["max_inflight"] = JsonPrimitive(int(v, "max_inflight"))
        out["tags"] = strArray(csv(v["tags"].orEmpty()))
        out["binary"] = JsonPrimitive(v["binary"].orEmpty().trim())
        out["console_cols"] = JsonPrimitive(int(v, "console_cols"))
        out["console_rows"] = JsonPrimitive(int(v, "console_rows"))
        out["output_mode"] = JsonPrimitive(v["output_mode"].orEmpty())
        out["input_mode"] = JsonPrimitive(v["input_mode"].orEmpty())
        out["auto_git_init"] = JsonPrimitive(flag(v, "auto_git_init"))
        out["auto_git_commit"] = JsonPrimitive(flag(v, "auto_git_commit"))
        out["skip_permissions"] = JsonPrimitive(flag(v, "skip_permissions"))
        out["channel_enabled"] = JsonPrimitive(flag(v, "channel_enabled"))
        out["auto_accept_disclaimer"] = JsonPrimitive(flag(v, "auto_accept_disclaimer"))
        out["permission_mode"] = JsonPrimitive(v["permission_mode"].orEmpty())
        out["default_effort"] = JsonPrimitive(v["default_effort"].orEmpty())
        out["fallback_chain"] = strArray(csv(v["fallback_chain"].orEmpty()))
        return JsonObject(out)
    }

    private fun algorithmRow(s: AlgorithmStateDto): IosAlgorithmRow {
        val cur: String = s.current.ifEmpty { "observe" }
        return IosAlgorithmRow(
            sessionId = s.sessionId,
            current = cur,
            phaseIndex = phases.indexOf(cur),
            historyCount = s.history.size,
            aborted = s.aborted,
        )
    }

    // ---- JSON accessors ----

    private fun JsonObject.s(k: String): String {
        val p: JsonPrimitive? = this[k] as? JsonPrimitive
        return if (p == null || p is JsonNull) "" else p.content
    }

    private fun JsonObject.i(k: String): Int {
        val p: JsonPrimitive? = this[k] as? JsonPrimitive
        if (p == null || p is JsonNull) return 0
        return p.content.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun JsonObject.b(k: String): Boolean {
        val p: JsonPrimitive? = this[k] as? JsonPrimitive
        if (p == null || p is JsonNull) return false
        return p.content.equals("true", ignoreCase = true)
    }

    private fun JsonObject.list(k: String): List<String> {
        val arr: JsonArray = this[k] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val p: JsonPrimitive? = el as? JsonPrimitive
            if (p == null || p is JsonNull) null else p.content
        }
    }
}
