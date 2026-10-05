package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AddSecretDto
import com.dmzs.datawatchclient.transport.dto.CostRateDto
import com.dmzs.datawatchclient.transport.dto.RemoteServerDto
import com.dmzs.datawatchclient.transport.dto.RoutingRuleDto
import com.dmzs.datawatchclient.transport.dto.SessionTemplateDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One row of a generic Settings list card (Swift renders every registry-style
 * card — LLMs, compute nodes, secrets, plugins, … — from these).
 *
 * [detail] is a pretty-printed JSON view with credential-looking keys
 * redacted; [actions] are per-row action labels handled by
 * [IosSettingsLists.action] by index.
 */
public data class IosSettingsRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val badges: List<String>,
    val hasToggle: Boolean,
    val enabled: Boolean,
    val canDelete: Boolean,
    val actions: List<String>,
    val detail: String,
)

/** Operator identity (PWA Settings › Automata › Identity); list fields one per line. */
public data class IosIdentity(
    val role: String,
    val goals: String,
    val projects: String,
    val values: String,
    val focus: String,
    val notes: String,
    val updatedAt: String,
)

/**
 * Settings parity (B27–B32) list/registry cards. `kind` selects the
 * endpoint family; see [load] for the supported set. Mutations report null on
 * success or an error message. Callbacks fire off the main thread.
 */
public object IosSettingsLists {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pretty = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
    private val sensitive = Regex("token|secret|password|api_key|apikey|auth", RegexOption.IGNORE_CASE)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    private fun msg(e: Throwable?, fallback: String): String? = e?.let { it.message ?: fallback }

    public fun load(
        profile: ServerProfile,
        kind: String,
        onSuccess: (List<IosSettingsRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { rows(t(profile), kind) }.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load.") },
            )
        }
    }

    public fun setEnabled(
        profile: ServerProfile,
        kind: String,
        id: String,
        enabled: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val r: Result<*> =
                when (kind) {
                    "llms" -> tr.enableLlm(id, enabled)
                    "compute_nodes" -> tr.toggleComputeNodeEnabled(id, enabled)
                    "web_search_providers" -> tr.enableWebSearchProvider(id, enabled)
                    "plugins" -> tr.pluginAction(id, if (enabled) "enable" else "disable")
                    "fed_peers" -> tr.updateFederationPeer(id, JsonObject(mapOf("enabled" to JsonPrimitive(enabled))))
                    "remote_servers" -> IosRemoteServerOps.setEnabled(tr, id, enabled)
                    else -> Result.failure<Unit>(UnsupportedOperationException("Not supported"))
                }
            onDone(msg(r.exceptionOrNull(), "Update failed."))
        }
    }

    public fun delete(
        profile: ServerProfile,
        kind: String,
        id: String,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val r: Result<*> =
                when (kind) {
                    "llms" -> tr.deleteLlm(id)
                    "compute_nodes" -> tr.deleteComputeNode(id)
                    "secrets" -> tr.deleteSecret(id)
                    "remote_servers" -> tr.deleteRemoteServer(id)
                    "fed_peers" -> tr.deleteFederationPeer(id)
                    "session_templates" -> tr.deleteSessionTemplate(id)
                    "device_aliases" -> tr.deleteDeviceAlias(id)
                    "skill_registries" -> tr.deleteSkillRegistry(id)
                    "guardrail_profiles" -> tr.deleteGuardrailProfile(id)
                    "web_search_providers" -> tr.deleteWebSearchProvider(id)
                    "cluster_profiles" -> tr.deleteKindProfile("cluster", id)
                    "project_profiles" -> tr.deleteKindProfile("project", id)
                    "council_personas" -> tr.deleteCouncilPersona(id)
                    "cost_rates" ->
                        tr.getCostRates().mapCatching { cur ->
                            tr.saveCostRates(cur.rates - id).getOrThrow()
                        }
                    "routing_rules" ->
                        tr.getRoutingRules().mapCatching { cur ->
                            val idx = id.toIntOrNull() ?: -1
                            tr.setRoutingRules(cur.rules.filterIndexed { i, _ -> i != idx }).getOrThrow()
                        }
                    "channel_routing" ->
                        tr.getChannelRouting().mapCatching { cur ->
                            val idx: Int = id.toIntOrNull() ?: -1
                            tr.putChannelRouting(cur.rules.filterIndexed { i, _ -> i != idx }).getOrThrow()
                        }
                    else -> Result.failure<Unit>(UnsupportedOperationException("Not supported"))
                }
            onDone(msg(r.exceptionOrNull(), "Delete failed."))
        }
    }

    /** Runs row action [index] (labels from [IosSettingsRow.actions]); success message on [onSuccess]. */
    public fun action(
        profile: ServerProfile,
        kind: String,
        id: String,
        index: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val r: Result<String> =
                when (kind) {
                    "evals" -> tr.evalsRun(id).map { "Score ${fmt(it.score)} · ${it.passed} passed · ${it.failed} failed" }
                    // Index 1 ("Browse") opens the Swift browse sheet and never reaches here.
                    "skill_registries" -> tr.connectSkillRegistry(id).map { "Status: ${it.status}" }
                    "remote_servers" -> IosRemoteServerOps.test(tr, id)
                    "tooling" ->
                        if (index == 0) tr.toolingGitignore(id).map { "Added to .gitignore" }
                        else tr.toolingCleanup(id).map { "Cleaned up" }
                    "web_search_providers" ->
                        tr.testWebSearchProvider(id).map { if (it.ok) "OK · ${it.resultCount} results" else "Failed: ${it.error.orEmpty()}" }
                    "project_profiles" -> tr.smokeKindProfile("project", id).map { "Smoke test started" }
                    "fed_peers" ->
                        tr.testFederationPeer(id).map { o ->
                            if (o.s("ok") == "true") {
                                "OK — ${o.s("latency_ms")}ms (${o.s("version").ifEmpty { "unknown version" }})"
                            } else {
                                "FAIL: ${o.s("error").ifEmpty { "no error" }}"
                            }
                        }
                    else -> Result.failure<String>(UnsupportedOperationException("Not supported"))
                }
            r.fold(onSuccess = { onSuccess(it) }, onFailure = { onError(it.message ?: "Action failed.") })
        }
    }

    /** Card-level actions (no row): tailscale 0 = generate ACL, 1 = generate & push ACL; plugins 0 = reload. */
    public fun cardAction(
        profile: ServerProfile,
        kind: String,
        index: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val tr = t(profile)
            val r: Result<String> =
                when (kind) {
                    "tailscale" ->
                        if (index == 0) tr.generateTailscaleAcl().map { "ACL generated" }
                        else tr.pushTailscaleAcl().map { "ACL generated and pushed" }
                    "plugins" -> tr.reloadPlugins().map { "Reloaded: $it plugin(s)" }
                    else -> Result.failure<String>(UnsupportedOperationException("Not supported"))
                }
            r.fold(onSuccess = { onSuccess(it) }, onFailure = { onError(it.message ?: "Action failed.") })
        }
    }

    /**
     * Create an entry from string fields. Supported kinds + keys:
     * secrets(name, value, description, tags) · device_aliases(alias, server) ·
     * session_templates(name, backend, project_dir, effort, description) ·
     * skill_registries(name, url, branch) · cost_rates(model, in_per_k, out_per_k) ·
     * routing_rules(pattern, backend, description) · remote_servers(name, url, token).
     */
    public fun create(
        profile: ServerProfile,
        kind: String,
        values: Map<String, String>,
        onDone: (String?) -> Unit,
    ) {
        fun v(k: String) = values[k]?.trim().orEmpty()
        fun csv(k: String) = v(k).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        scope.launch {
            val tr = t(profile)
            val r: Result<*> =
                when (kind) {
                    "secrets" -> tr.addSecret(AddSecretDto(name = v("name"), value = values["value"].orEmpty(), description = v("description"), tags = csv("tags")))
                    "device_aliases" -> tr.createDeviceAlias(v("alias"), v("server"))
                    "session_templates" ->
                        tr.createSessionTemplate(
                            SessionTemplateDto(name = v("name"), backend = v("backend"), projectDir = v("project_dir"), effort = v("effort"), description = v("description")),
                        )
                    "skill_registries" -> tr.createSkillRegistry(SkillRegistryRequestDto(name = v("name"), url = v("url"), branch = v("branch").ifEmpty { "main" }))
                    "cost_rates" ->
                        tr.getCostRates().mapCatching { cur ->
                            val rate = CostRateDto(inPerK = v("in_per_k").toDoubleOrNull(), outPerK = v("out_per_k").toDoubleOrNull())
                            tr.saveCostRates(cur.rates + (v("model") to rate)).getOrThrow()
                        }
                    "routing_rules" ->
                        tr.getRoutingRules().mapCatching { cur ->
                            tr.setRoutingRules(cur.rules + RoutingRuleDto(v("pattern"), v("backend"), v("description"))).getOrThrow()
                        }
                    "remote_servers" ->
                        tr.addRemoteServer(RemoteServerDto(name = v("name"), url = v("url"), token = v("token").ifEmpty { null }))
                    else -> Result.failure<Unit>(UnsupportedOperationException("Not supported"))
                }
            onDone(msg(r.exceptionOrNull(), "Save failed."))
        }
    }

    // ---- Identity (Settings › Automata › Identity) ----

    /** GET /api/identity — list fields joined with "\n". */
    public fun identity(
        profile: ServerProfile,
        onSuccess: (IosIdentity) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).getIdentity().fold(
                onSuccess = { d ->
                    onSuccess(
                        IosIdentity(
                            role = d.role,
                            goals = d.northStarGoals.joinToString("\n"),
                            projects = d.currentProjects.joinToString("\n"),
                            values = d.values.joinToString("\n"),
                            focus = d.currentFocus,
                            notes = d.contextNotes,
                            updatedAt = d.updatedAt.orEmpty(),
                        ),
                    )
                },
                onFailure = { onError(it.message ?: "Failed to load identity.") },
            )
        }
    }

    /** PUT /api/identity — list fields are one entry per line. */
    public fun saveIdentity(
        profile: ServerProfile,
        identity: IosIdentity,
        onDone: (String?) -> Unit,
    ) {
        fun lines(s: String) = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        scope.launch {
            val dto =
                com.dmzs.datawatchclient.transport.dto.IdentityDto(
                    role = identity.role.trim(),
                    northStarGoals = lines(identity.goals),
                    currentProjects = lines(identity.projects),
                    values = lines(identity.values),
                    currentFocus = identity.focus.trim(),
                    contextNotes = identity.notes.trim(),
                )
            onDone(msg(t(profile).setIdentity(dto).exceptionOrNull(), "Save failed."))
        }
    }

    // ---- Docs search (Settings › General › Docs Search) ----

    public fun docsSearch(
        profile: ServerProfile,
        query: String,
        onSuccess: (List<IosSettingsRow>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).docsSearch(query.trim(), 20).fold(
                onSuccess = { hits -> onSuccess(hits.map { h -> row(h.path, h.title.ifEmpty { h.path }, h.excerpt, badges = listOf(h.path)) }) },
                onFailure = { onError(it.message ?: "Search failed.") },
            )
        }
    }

    // ---- row builders ----

    private suspend fun rows(
        tr: TransportClient,
        kind: String,
    ): List<IosSettingsRow> =
        when (kind) {
            "llms" ->
                tr.listLlms().getOrThrow().map { e ->
                    val model = e.models.joinToString(", ") { it.model }.ifEmpty { e.model }
                    row(
                        e.name, e.name, listOf(e.kind, model).filter { it.isNotEmpty() }.joinToString(" · "),
                        badges = e.tags.orEmpty(), hasToggle = true, enabled = e.enabled, canDelete = true,
                        // PWA "▾ In use…" — handled in Swift (LlmInUseSheet), not by [action].
                        actions = listOf("In use…"),
                        detail = enc(com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto.serializer(), e),
                    )
                }
            "compute_nodes" ->
                tr.listComputeNodes().getOrThrow().map { n ->
                    val sub = listOf(n.kind, n.address, n.disabledReason.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
                    row(
                        n.name, n.name, sub,
                        badges = (if (n.autoCreated) listOf("auto") else emptyList()) + n.tags,
                        hasToggle = true, enabled = n.enabled, canDelete = true,
                        detail = enc(com.dmzs.datawatchclient.transport.dto.ComputeNodeDto.serializer(), n),
                    )
                }
            "secrets" -> {
                val status = tr.getSecretsStatus().getOrNull()
                val head =
                    status?.let {
                        listOf(
                            row(
                                "_vault", "Vault", listOf(it.activeBackend, if (it.reachable) "reachable" else "unreachable", it.lastError.orEmpty())
                                    .filter { s -> s.isNotEmpty() }.joinToString(" · "),
                            ),
                        )
                    }.orEmpty()
                head +
                    tr.getSecrets().getOrThrow().secrets.map { s ->
                        row(s.name, s.name, s.description, badges = s.tags + s.scopes.map { "scope:$it" }, canDelete = true)
                    }
            }
            "remote_servers" ->
                tr.listRemoteServers().getOrThrow().map { o ->
                    val name = o.s("name")
                    val builtin: Boolean = o.b("builtin")
                    // PWA loadServersList: on/off pill (switch here), Test, built-ins read-only.
                    row(
                        name, o.s("label").ifEmpty { name }, o.s("url"),
                        badges = listOfNotNull(if (o.b("federated")) "federated" else null, if (builtin) "built-in" else null),
                        hasToggle = !builtin, enabled = o.b("enabled", true),
                        canDelete = !builtin, actions = listOf("Test"), detail = redact(o),
                    )
                }
            "fed_peers" ->
                tr.listFederationPeers().getOrThrow().map { o ->
                    val name = o.s("name")
                    row(
                        name, name, o.s("url"),
                        badges = o.list("capabilities").ifEmpty { listOf("none") },
                        hasToggle = true, enabled = o.b("enabled", true),
                        canDelete = true, actions = listOf("Test"), detail = redact(o),
                    )
                }
            "session_templates" ->
                tr.getSessionTemplates().getOrThrow().map { s ->
                    // "Use" is handled in Swift (opens New Session prefilled), never reaches action().
                    row(
                        s.name, s.name, listOf(s.backend, s.projectDir, s.effort, s.description).filter { it.isNotEmpty() }.joinToString(" · "),
                        canDelete = true, actions = listOf("Use"),
                    )
                }
            "device_aliases" ->
                tr.getDeviceAliases().getOrThrow().map { a -> row(a.alias, a.alias, a.server, canDelete = true) }
            "plugins" -> {
                val p = tr.listPlugins().getOrThrow()
                p.native.map { pl ->
                    row(pl.name, pl.name, listOfNotNull(pl.version?.let { "v$it" }, pl.description).joinToString(" · "), badges = listOf("native"), enabled = pl.enabled)
                } +
                    p.plugins.map { pl ->
                        row(
                            pl.name, pl.name, listOfNotNull(pl.version?.let { "v$it" }, pl.description, pl.message).joinToString(" · "),
                            hasToggle = true, enabled = pl.enabled,
                        )
                    }
            }
            "skill_registries" ->
                tr.listSkillRegistries().getOrThrow().map { r ->
                    row(
                        r.name, r.name, "${r.url} @ ${r.branch}",
                        badges = listOfNotNull(r.status, if (r.builtin) "built-in" else null),
                        canDelete = !r.builtin, actions = listOf("Connect", "Browse"),
                    )
                }
            "guardrail_profiles" ->
                tr.listGuardrailProfiles().getOrThrow().map { g ->
                    row(g.id.ifEmpty { g.name }, g.name.ifEmpty { g.id }, g.guardrails.joinToString(", "), badges = g.blockOn.map { "block:$it" }, canDelete = true)
                }
            "guardrail_library" ->
                tr.listGuardrailLibrary().getOrThrow().map { g -> row(g.name, g.name, g.description, badges = listOf(g.kind.ifEmpty { "scan" })) }
            "evals" ->
                tr.evalsList().getOrThrow().map { e ->
                    row(
                        e.effectiveId, e.name, listOf(e.description, "${e.cases} cases").filter { it.isNotEmpty() }.joinToString(" · "),
                        badges = listOfNotNull(e.lastScore?.let { "last ${fmt(it)}" }), actions = listOf("Run"),
                    )
                }
            "tooling" ->
                tr.getToolingStatus().getOrThrow().backends.map { b ->
                    row(
                        b.backend, b.backend, b.present.joinToString(", ").ifEmpty { "no artifacts" },
                        badges = if (b.ignored) listOf("gitignored") else emptyList(),
                        actions = listOf("Add to .gitignore", "Clean up"),
                    )
                }
            "routing_rules" ->
                tr.getRoutingRules().getOrThrow().rules.mapIndexed { i, r ->
                    row(i.toString(), r.pattern, listOf("→ ${r.backend}", r.description).filter { it.isNotEmpty() }.joinToString(" · "), canDelete = true)
                }
            "channel_routing" ->
                tr.getChannelRouting().getOrThrow().rules.mapIndexed { i, r ->
                    row(i.toString(), r.channelPattern, listOf(r.peerName, r.automataType).filter { it.isNotEmpty() }.joinToString(" · "), canDelete = true)
                }
            "web_search_providers" ->
                tr.listWebSearchProviders().getOrThrow().map { w ->
                    row(
                        w.name, w.name, listOf(w.type, w.url).filter { it.isNotEmpty() }.joinToString(" · "),
                        badges = listOf("priority ${w.priority}"), hasToggle = true, enabled = w.enabled, canDelete = true, actions = listOf("Test"),
                    )
                }
            "cost_rates" ->
                tr.getCostRates().getOrThrow().rates.map { (model, r) ->
                    row(model, model, "in ${r.inPerK?.let { fmt(it) } ?: "—"} · out ${r.outPerK?.let { fmt(it) } ?: "—"} USD / 1K", canDelete = true)
                }
            "tailscale" -> {
                val s = tr.getTailscaleStatus().getOrThrow()
                listOf(
                    row("_status", "Status", listOf(s.backend, if (s.enabled) "enabled" else "disabled", s.error).filter { it.isNotEmpty() }.joinToString(" · ")),
                ) + s.nodes.map { n -> row(n.name, n.name, n.ip, badges = listOf(if (n.online) "online" else "offline") + n.tags) }
            }
            "cluster_profiles", "project_profiles" -> {
                val k = if (kind == "cluster_profiles") "cluster" else "project"
                tr.listKindProfiles(k).getOrThrow().map { o ->
                    val name = o.s("name")
                    row(
                        name, name, o.s("description").ifEmpty { o.s("kind") },
                        canDelete = true, actions = if (k == "project") listOf("Smoke test") else emptyList(), detail = redact(o),
                    )
                }
            }
            "council_personas" ->
                tr.councilListPersonas().getOrThrow().map { p ->
                    row(p.name, p.name, p.description, enabled = p.enabled, canDelete = true)
                }
            "discussions" ->
                tr.listDiscussions().getOrThrow().discussions.map { d -> row(d, d, "") }
            "file_service" -> {
                val m = tr.getFileServiceMeta().getOrThrow()
                listOf(row("_root", "Root", m.root)) +
                    m.peers.map { p -> row("peer:$p", p, "peer") } +
                    m.discussions.map { d -> row("disc:$d", d, "discussion") }
            }
            else -> throw UnsupportedOperationException("Unknown list kind: $kind")
        }

    private fun row(
        id: String,
        title: String,
        subtitle: String,
        badges: List<String> = emptyList(),
        hasToggle: Boolean = false,
        enabled: Boolean = true,
        canDelete: Boolean = false,
        actions: List<String> = emptyList(),
        detail: String = "",
    ): IosSettingsRow = IosSettingsRow(id, title, subtitle, badges.filter { it.isNotEmpty() }, hasToggle, enabled, canDelete, actions, detail)

    private fun <T> enc(
        ser: KSerializer<T>,
        v: T,
    ): String = redact(pretty.encodeToJsonElement(ser, v) as? JsonObject ?: JsonObject(emptyMap()))

    /** Pretty JSON with any credential-looking key's value replaced by "•••". */
    private fun redact(o: JsonObject): String = pretty.encodeToString(JsonElement.serializer(), redactEl(o))

    private fun redactEl(el: JsonElement): JsonElement =
        when (el) {
            is JsonObject ->
                JsonObject(
                    el.mapValues { (k, v) ->
                        if (sensitive.containsMatchIn(k) && v is JsonPrimitive && v !is JsonNull && v.content.isNotEmpty() && !v.content.startsWith("\${secret:")) {
                            JsonPrimitive("•••")
                        } else {
                            redactEl(v)
                        }
                    },
                )
            is JsonArray -> JsonArray(el.map { redactEl(it) })
            else -> el
        }

    private fun fmt(d: Double): String {
        val r = kotlin.math.round(d * 1000.0) / 1000.0
        return r.toString()
    }

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

    private fun JsonObject.b(
        k: String,
        default: Boolean = false,
    ): Boolean = (this[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: default

    private fun JsonObject.list(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
}
