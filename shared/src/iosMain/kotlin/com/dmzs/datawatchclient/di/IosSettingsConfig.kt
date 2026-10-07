package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** About-card snapshot (PWA Settings › About: version, sessions, uptime, orphaned tmux). */
public data class IosAboutInfo(
    val serverVersion: String,
    val hostname: String,
    val sessionCount: Int,
    val uptimeSeconds: Long,
    val orphanedTmux: List<String>,
)

/** One MCP tool row (Settings › About › MCP tools). */
public data class IosMcpTool(
    val name: String,
    val description: String,
)

/**
 * Settings parity (B26–B32): schema-driven config cards, Config Viewer + raw
 * editor, and daemon ops for the About group. Mirrors PWA
 * `GENERAL/COMMS/LLM_CONFIG_FIELDS` + `saveGeneralField` (flat dot-path PUT
 * /api/config) and Android `ConfigFieldsPanel`.
 *
 * All callbacks fire on a background thread — Swift must hop to main.
 * Never logs or returns server tokens: masked config values arrive from the
 * server as "***" and are passed through as-is.
 */
public object IosSettingsConfig {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pretty = Json { prettyPrint = true }

    private fun err(t: Throwable, fallback: String): String = t.message ?: fallback

    // ---- Config read / write ----

    /**
     * GET /api/config flattened to dotted keys → display strings. Arrays of
     * primitives join with "\n" (Swift renders csv fields with ", ").
     */
    public fun load(
        profile: ServerProfile,
        onSuccess: (Map<String, String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchConfig().fold(
                onSuccess = { cfg ->
                    val out = LinkedHashMap<String, String>()
                    flattenToStrings("", JsonObject(cfg.raw), out)
                    onSuccess(out)
                },
                onFailure = { onError(err(it, "Config load failed.")) },
            )
        }
    }

    /**
     * PUT /api/config `{key: value}` with [kind] deciding the JSON type:
     * `toggle` → bool, `number` → int/double, `csv` → array split on commas,
     * `lines` → array split on newlines (whitespace preserved), anything else
     * → string. [onDone] gets null on success or an error message.
     */
    public fun write(
        profile: ServerProfile,
        key: String,
        kind: String,
        value: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val patch = buildJsonObject { put(key, typed(kind, value)) }
            IosServiceLocator.transportFor(profile).writeConfig(patch).fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(err(it, "Save failed.")) },
            )
        }
    }

    /** Full masked config as pretty JSON (Config Viewer / raw editor seed). */
    public fun rawJson(
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchConfig().fold(
                onSuccess = { onSuccess(pretty.encodeToString(JsonObject.serializer(), JsonObject(it.raw))) },
                onFailure = { onError(err(it, "Config load failed.")) },
            )
        }
    }

    /**
     * Raw config editor save. Parses [json], diffs its leaves against the live
     * config and PUTs only the changed dotted keys (the server's
     * applyConfigPatch accepts flat keys only). Masked "***" values are never
     * written back. [onSuccess] receives the number of keys written.
     */
    public fun applyRaw(
        profile: ServerProfile,
        json: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val edited =
                runCatching { Json.parseToJsonElement(json).jsonObject }.getOrElse {
                    onError("Invalid JSON: ${it.message ?: "parse error"}")
                    return@launch
                }
            val t = IosServiceLocator.transportFor(profile)
            val current =
                t.fetchConfig().getOrElse {
                    onError(err(it, "Config load failed."))
                    return@launch
                }
            val before = LinkedHashMap<String, JsonElement>()
            val after = LinkedHashMap<String, JsonElement>()
            flattenLeaves("", JsonObject(current.raw), before)
            flattenLeaves("", edited, after)
            val changed =
                after.filter { (k, v) ->
                    !(v is JsonPrimitive && v.isString && v.content == "***") && before[k] != v
                }
            if (changed.isEmpty()) {
                onSuccess(0)
                return@launch
            }
            t.writeConfig(JsonObject(changed)).fold(
                onSuccess = { onSuccess(changed.size) },
                onFailure = { onError(err(it, "Save failed.")) },
            )
        }
    }

    /** GET /api/interfaces names + 0.0.0.0 / 127.0.0.1 (PWA interface_select). */
    public fun interfaces(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
    ) {
        scope.launch {
            val names =
                IosServiceLocator.transportFor(profile).listInterfaces().getOrNull().orEmpty().mapNotNull {
                    (it["name"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                }
            onSuccess((listOf("0.0.0.0", "127.0.0.1") + names).distinct())
        }
    }

    /** Enabled LLM registry names (PWA llm_select / llm_backend pickers). */
    public fun llmNames(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val names =
                t.listLlms().getOrNull()?.filter { it.enabled }?.map { it.name }
                    ?: t.listBackends().getOrNull()?.llm.orEmpty()
            onSuccess(names)
        }
    }

    // ---- About / daemon ops ----

    public fun about(
        profile: ServerProfile,
        onSuccess: (IosAboutInfo) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val t = IosServiceLocator.transportFor(profile)
            val info = t.fetchInfo()
            val stats = t.stats().getOrNull()
            info.fold(
                onSuccess = { i ->
                    onSuccess(
                        IosAboutInfo(
                            serverVersion = i.version,
                            hostname = i.hostname,
                            sessionCount = stats?.sessionsTotal?.takeIf { it > 0 } ?: i.sessionCount,
                            uptimeSeconds = stats?.uptimeSeconds ?: 0L,
                            orphanedTmux = stats?.orphanedTmux.orEmpty(),
                        ),
                    )
                },
                onFailure = { onError(err(it, "Server unreachable.")) },
            )
        }
    }

    /** POST /api/restart. */
    public fun restartDaemon(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).restartDaemon().fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(err(it, "Restart failed.")) },
            )
        }
    }

    /**
     * Community Plugins (Android CommunityPluginsCard parity; web UI datawatch#191):
     * GET /api/plugins/browse?registry=<name> → (name, description-or-version) rows.
     */
    public fun browsePlugins(
        profile: ServerProfile,
        registry: String,
        onSuccess: (List<IosCommunityPlugin>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).browsePlugins(registry).fold(
                onSuccess = { b ->
                    onSuccess(
                        b.plugins.map { p ->
                            IosCommunityPlugin(
                                p.name,
                                com.dmzs.datawatchclient.transport.CommunityPlugins.subtitle(p.manifest.description, p.manifest.version),
                            )
                        },
                    )
                },
                onFailure = { onError(com.dmzs.datawatchclient.transport.ErrorText.of(it, "Browse unavailable.")) },
            )
        }
    }

    /** Registry names for the Community Plugins picker (GET /api/skills/registries). */
    public fun pluginRegistries(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).listSkillRegistries().fold(
                onSuccess = { list -> onSuccess(list.map { it.name }) },
                onFailure = { onError(com.dmzs.datawatchclient.transport.ErrorText.of(it, "Registries unavailable.")) },
            )
        }
    }

    /** Connect a registry (same call as Skill Registries › Connect); [onDone] gets null or the error. */
    public fun connectPluginRegistry(
        profile: ServerProfile,
        registry: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).connectSkillRegistry(registry).fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(com.dmzs.datawatchclient.transport.ErrorText.of(it, "Connect failed.")) },
            )
        }
    }

    /** POST /api/plugins/install {registry, name}; [onDone] gets null on success or the error. */
    public fun installPlugin(
        profile: ServerProfile,
        registry: String,
        name: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).installPlugin(registry, name).fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(com.dmzs.datawatchclient.transport.ErrorText.of(it, "Install failed.")) },
            )
        }
    }

    /** BL413 — Settings › Web Server Let's Encrypt status lines (PWA loadAcmeStatus). */
    public fun acmeStatusLines(
        profile: ServerProfile,
        onDone: (List<com.dmzs.datawatchclient.transport.AcmeLine>) -> Unit,
    ) {
        scope.launch {
            onDone(
                IosServiceLocator.transportFor(profile).acmeStatus().fold(
                    onSuccess = { com.dmzs.datawatchclient.transport.AcmeStatusFormat.settingsLines(it) },
                    onFailure = { listOf(com.dmzs.datawatchclient.transport.AcmeLine("Status unavailable.", "muted")) },
                ),
            )
        }
    }

    /** BL413 — POST /api/acme/renew; [onDone] gets null on success or the error. */
    public fun acmeRenew(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).acmeRenew().fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(com.dmzs.datawatchclient.transport.AcmeStatusFormat.errorText(err(it, "Renew failed."))) },
            )
        }
    }

    /** BL413 — GET /api/acme/verify → (message, ok), PWA toast text. */
    public fun acmeVerify(
        profile: ServerProfile,
        onDone: (String, Boolean) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).acmeVerify().fold(
                onSuccess = {
                    val (m, ok) = com.dmzs.datawatchclient.transport.AcmeStatusFormat.verifyMessage(it)
                    onDone(m, ok)
                },
                onFailure = {
                    onDone("Verify failed: " + com.dmzs.datawatchclient.transport.AcmeStatusFormat.errorText(err(it, "unknown error")), false)
                },
            )
        }
    }

    /**
     * GET /api/update/check → (status, version). status is "up_to_date" or
     * "update_available" (PWA checkForUpdate).
     */
    public fun checkUpdate(
        profile: ServerProfile,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).checkUpdate().fold(
                onSuccess = { o -> onSuccess(o.str("status"), o.str("version").ifEmpty { o.str("latest") }) },
                onFailure = { onError(err(it, "Update check failed.")) },
            )
        }
    }

    /** POST /api/update — daemon self-update; returns the status string. */
    public fun runUpdate(
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).updateDaemon().fold(
                onSuccess = { o -> onSuccess(o.str("status")) },
                onFailure = { onError(err(it, "Update failed.")) },
            )
        }
    }

    /** POST /api/stats/kill-orphans; returns a short summary. */
    public fun killOrphans(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).killOrphans().fold(
                onSuccess = { onDone(null) },
                onFailure = { onDone(err(it, "Kill failed.")) },
            )
        }
    }

    /**
     * POST /api/reload?subsystem= (config / filters / memory). Success summary
     * lists applied + requires_restart keys.
     */
    public fun reload(
        profile: ServerProfile,
        subsystem: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).reloadSubsystem(subsystem).fold(
                onSuccess = { o ->
                    val applied = (o["applied"] as? JsonArray)?.size ?: 0
                    val restart = (o["requires_restart"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                    val msg =
                        if (restart.isEmpty()) "Reloaded $subsystem ($applied applied)"
                        else "Reloaded $subsystem ($applied applied) · restart needed: ${restart.joinToString(", ")}"
                    onSuccess(msg)
                },
                onFailure = { onError(err(it, "Reload failed.")) },
            )
        }
    }

    /** GET /api/mcp/docs → tool name + description list. */
    public fun mcpTools(
        profile: ServerProfile,
        onSuccess: (List<IosMcpTool>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchMcpDocs().fold(
                onSuccess = { root ->
                    val arr =
                        when (root) {
                            is JsonArray -> root
                            is JsonObject -> root["tools"] as? JsonArray
                            else -> null
                        }
                    onSuccess(
                        arr.orEmpty().mapNotNull { el ->
                            val o = el as? JsonObject ?: return@mapNotNull null
                            IosMcpTool(o.str("name"), o.str("description"))
                        }.filter { it.name.isNotEmpty() },
                    )
                },
                onFailure = { onError(err(it, "MCP docs unavailable.")) },
            )
        }
    }

    /** GET /api/channel/info pretty-printed (MCP channel card). */
    public fun channelInfo(
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchChannelInfo().fold(
                onSuccess = { onSuccess(pretty.encodeToString(JsonElement.serializer(), it)) },
                onFailure = { onError(err(it, "Channel info unavailable.")) },
            )
        }
    }

    // ---- Push (APNs) ----

    /** True when this install holds an APNs device token (value never exposed). */
    public fun apnsTokenPresent(): Boolean = !IosServiceLocator.pushStore.apnsToken().isNullOrEmpty()

    /** True when [profile]'s server has accepted this device's APNs registration. */
    public fun apnsRegistered(profile: ServerProfile): Boolean =
        !IosServiceLocator.pushStore.deviceIdFor(profile.id).isNullOrEmpty()

    /**
     * Test push to this iPhone: POST /api/push/apns/test for this device's
     * registration (datawatch v8.63+, real APNs delivery). Servers without the
     * endpoint (404) fall back to POST /api/push/notify (PWA pushSendTest).
     */
    public fun sendTestPush(
        profile: ServerProfile,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = IosServiceLocator.transportFor(profile)
            val deviceId = IosServiceLocator.pushStore.deviceIdFor(profile.id)
            if (deviceId.isNullOrEmpty()) {
                onDone("This device is not registered with the server yet. Tap Re-register this device.")
                return@launch
            }
            tr.sendApnsTest(deviceId).fold(
                onSuccess = { body ->
                    val first = (body["results"] as? JsonArray)?.firstOrNull() as? JsonObject
                    val ok = (first?.get("ok") as? JsonPrimitive)?.content == "true"
                    onDone(if (ok) null else (first?.get("error") as? JsonPrimitive)?.content ?: "No APNs device was sent to.")
                },
                onFailure = { e ->
                    val notFound = e is com.dmzs.datawatchclient.transport.TransportError.NotFound
                    when {
                        // Stale registration: the server forgot this device.
                        notFound && e.message.orEmpty().contains("device not found") ->
                            onDone("This device is not registered with the server yet. Tap Re-register this device.")
                        // Server older than v8.63 (no APNs test endpoint).
                        notFound ->
                            tr.sendTestWebPushNotification().fold(
                                onSuccess = { onDone(null) },
                                onFailure = { onDone(err(it, "Test failed.")) },
                            )
                        (e as? com.dmzs.datawatchclient.transport.TransportError.ServerError)?.status == 503 ->
                            onDone("APNs is not set up on this server (push.apns in the server config).")
                        else -> onDone(com.dmzs.datawatchclient.transport.AcmeStatusFormat.errorText(err(e, "Test failed.")))
                    }
                },
            )
        }
    }

    /** Server-side encryption status (`secure_mode`, encrypted file count / total). */
    public fun serverEncryption(
        profile: ServerProfile,
        onSuccess: (Boolean, Int, Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).getEncryptionStatus().fold(
                onSuccess = { s -> onSuccess(s.secureMode, s.files.count { it.encrypted }, s.files.size) },
                onFailure = { onError(err(it, "Encryption status unavailable.")) },
            )
        }
    }

    // ---- helpers ----

    internal fun typed(
        kind: String,
        value: String,
    ): JsonElement =
        when (kind) {
            "toggle" -> JsonPrimitive(value.trim().equals("true", ignoreCase = true))
            "number" -> {
                val v = value.trim()
                v.toLongOrNull()?.let { JsonPrimitive(it) }
                    ?: v.toDoubleOrNull()?.let { JsonPrimitive(it) }
                    ?: JsonPrimitive(0)
            }
            "csv" -> JsonArray(value.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
            "lines" -> JsonArray(value.split('\n').filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
            else -> JsonPrimitive(value.trim())
        }

    internal fun flattenToStrings(
        prefix: String,
        el: JsonElement,
        out: MutableMap<String, String>,
    ) {
        when (el) {
            is JsonObject ->
                if (el.isEmpty() && prefix.isNotEmpty()) {
                    out[prefix] = ""
                } else {
                    el.forEach { (k, v) -> flattenToStrings(if (prefix.isEmpty()) k else "$prefix.$k", v, out) }
                }
            is JsonArray ->
                out[prefix] =
                    el.joinToString("\n") { item ->
                        if (item is JsonPrimitive && item !is JsonNull) item.content else item.toString()
                    }
            is JsonNull -> out[prefix] = ""
            is JsonPrimitive -> out[prefix] = el.content
        }
    }

    internal fun flattenLeaves(
        prefix: String,
        el: JsonElement,
        out: MutableMap<String, JsonElement>,
    ) {
        if (el is JsonObject && el.isNotEmpty()) {
            el.forEach { (k, v) -> flattenLeaves(if (prefix.isEmpty()) k else "$prefix.$k", v, out) }
        } else if (prefix.isNotEmpty()) {
            out[prefix] = el
        }
    }

    private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
}

/** One Community Plugins row: plugin name + manifest description (or version). */
public data class IosCommunityPlugin(val name: String, val detail: String)
