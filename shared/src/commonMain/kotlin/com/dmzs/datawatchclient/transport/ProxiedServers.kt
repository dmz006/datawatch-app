package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Remote servers reached THROUGH a connected datawatch server (#234) — the
 * PWA's `/api/proxy/<name>/…` path. Each remote configured on a real profile
 * (`GET /api/servers`) becomes a *virtual* [ServerProfile]:
 *
 *  - id `"<parentId>::proxy::<name>"`
 *  - displayName `"<parent> › <label or name>"`
 *  - baseUrl `parent.baseUrl + "/api/proxy/" + <name, path-encoded>` — every
 *    REST call becomes `/api/proxy/<name>/api/…` and the WebSocket
 *    `/api/proxy/<name>/ws`, exactly the PWA's `apiFetch` / `connect()` shapes
 *  - the parent's bearer-token *reference* and TLS trust anchor (no token is
 *    copied anywhere)
 *
 * Virtual profiles are never written to the server_profile table, and the
 * real-profile surfaces (push registration, widgets, Wear, Android Auto,
 * all-servers fan-out) never see them.
 */
public object ProxiedServers {
    public const val SEPARATOR: String = "::proxy::"

    /** Display joiner between parent and remote name — matches the PWA picker. */
    public const val NAME_JOINER: String = " › "

    /**
     * Names the parent server reserves under `/api/proxy/` (its own "local"
     * entry and the agent-worker / comm / LLM proxy namespaces) — never a
     * remote server.
     */
    private val RESERVED_NAMES: Set<String> = setOf("local", "agent", "comm", "llm")

    public fun isProxied(id: String?): Boolean = id != null && id.contains(SEPARATOR)

    /** The real parent profile id of a virtual id, or [id] itself when it's real. */
    public fun parentIdOf(id: String): String = if (isProxied(id)) id.substringBefore(SEPARATOR) else id

    /** The remote server name of a virtual id, or null for a real id. */
    public fun remoteNameOf(id: String): String? = if (isProxied(id)) id.substringAfter(SEPARATOR) else null

    public fun idFor(
        parentId: String,
        name: String,
    ): String = "$parentId$SEPARATOR$name"

    /** Builds the virtual profile for remote [name] (optional [label]) on [parent]. */
    public fun make(
        parent: ServerProfile,
        name: String,
        label: String?,
    ): ServerProfile =
        ServerProfile(
            id = idFor(parent.id, name),
            displayName = parent.displayName + NAME_JOINER + (label?.takeIf { it.isNotBlank() } ?: name),
            baseUrl = parent.baseUrl.trimEnd('/') + "/api/proxy/" + name.encodeURLPathPart(),
            bearerTokenRef = parent.bearerTokenRef,
            trustAnchorSha256 = parent.trustAnchorSha256,
            reachabilityProfileId = parent.reachabilityProfileId,
            enabled = true,
            createdTs = parent.createdTs,
        )

    /** Builds the virtual profile from one `/api/servers` entry, or null when it isn't proxiable. */
    public fun make(
        parent: ServerProfile,
        remote: JsonObject,
    ): ServerProfile? {
        val name = remote.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (name.lowercase() in RESERVED_NAMES) return null
        if (name.contains('/') || name.contains(SEPARATOR)) return null
        if (remote.bool("enabled") == false) return null
        // The entry pointing back at the parent itself would just loop.
        val url = remote.string("url")?.trim()?.trimEnd('/')
        if (url != null && url.equals(parent.baseUrl.trim().trimEnd('/'), ignoreCase = true)) return null
        return make(parent, name, remote.string("label"))
    }

    /**
     * Parses a `/api/servers` listing (already unwrapped from `{"servers":[…]}`)
     * into the virtual profiles of [parent], in server order, de-duplicated by
     * name. Disabled entries, the implicit "local" entry, reserved proxy
     * namespaces and an entry pointing back at the parent are skipped. YAML
     * seeded ("builtin") entries ARE remotes and are kept — the PWA lists them.
     */
    public fun parse(
        parent: ServerProfile,
        servers: List<JsonObject>,
    ): List<ServerProfile> = servers.mapNotNull { make(parent, it) }.distinctBy { it.id }

    /**
     * Real profiles followed by every enabled parent's virtual profiles
     * (grouped under their parent). When [activeId] is a virtual id whose
     * parent's list hasn't been fetched yet (cold start), a placeholder virtual
     * profile is synthesized from the id so the active selection survives an
     * app restart.
     */
    public fun withProxied(
        real: List<ServerProfile>,
        byParent: Map<String, List<ServerProfile>>,
        activeId: String?,
    ): List<ServerProfile> {
        val out = ArrayList<ServerProfile>(real.size + byParent.values.sumOf { it.size })
        out += real
        real.filter { it.enabled }.forEach { parent ->
            byParent[parent.id]?.let { out += it }
        }
        if (activeId != null && isProxied(activeId) && out.none { it.id == activeId }) {
            val parent = real.firstOrNull { it.enabled && it.id == parentIdOf(activeId) }
            if (parent != null && !byParent.containsKey(parent.id)) {
                out += make(parent, remoteNameOf(activeId)!!, null)
            }
        }
        return out
    }

    /**
     * Resolves the active profile from [activeId] over real + virtual
     * profiles. A virtual id whose remote vanished from its parent's list
     * falls back to the parent (not the first profile); an unknown real id
     * (or a parent that's gone) falls back to the first enabled real profile.
     */
    public fun resolveActive(
        real: List<ServerProfile>,
        byParent: Map<String, List<ServerProfile>>,
        activeId: String?,
    ): ServerProfile? {
        val enabled = real.filter { it.enabled }
        if (enabled.isEmpty()) return null
        if (activeId == null) return enabled.first()
        if (isProxied(activeId)) {
            val parent = enabled.firstOrNull { it.id == parentIdOf(activeId) } ?: return enabled.first()
            return withProxied(real, byParent, activeId).firstOrNull { it.id == activeId } ?: parent
        }
        return enabled.firstOrNull { it.id == activeId } ?: enabled.first()
    }

    /**
     * The real profile a stored selection belongs to — the parent for a
     * virtual id. Used by surfaces keyed to real profiles (widgets, Wear,
     * push), which publish the parent when a proxied remote is selected.
     */
    public fun realIdOf(id: String?): String? = id?.let { parentIdOf(it) }

    /**
     * Where a stored active id should move after a refresh, or null when it's
     * fine as is: a virtual id whose parent is gone/disabled → `Repair(null)`
     * (clear the selection); whose remote vanished from a fetched list →
     * `Repair(parentId)`.
     */
    public fun repairActiveId(
        real: List<ServerProfile>,
        byParent: Map<String, List<ServerProfile>>,
        activeId: String?,
    ): Repair? {
        if (activeId == null || !isProxied(activeId)) return null
        val parentId = parentIdOf(activeId)
        if (real.none { it.enabled && it.id == parentId }) return Repair(null)
        val fetched = byParent[parentId] ?: return null
        return if (fetched.any { it.id == activeId }) null else Repair(parentId)
    }

    /** New stored active id (null = clear) produced by [repairActiveId]. */
    public data class Repair(val newId: String?)

    /**
     * Picker ordering: each real profile followed by its own remotes. Used by
     * the per-tab chip bar and the server picker so a remote sits right
     * after (indented under) its parent.
     */
    public fun groupedForPicker(
        real: List<ServerProfile>,
        virtual: List<ServerProfile>,
    ): List<ServerProfile> =
        buildList {
            real.forEach { parent ->
                add(parent)
                if (parent.enabled) addAll(virtual.filter { parentIdOf(it.id) == parent.id })
            }
        }

    /**
     * Short chip label: the remote's own name (after the joiner) when there's
     * only one real server — the PWA's "Local · remote" feel — else the full
     * "parent › remote" display name.
     */
    public fun chipLabel(
        profile: ServerProfile,
        realEnabledCount: Int,
    ): String =
        if (isProxied(profile.id) && realEnabledCount <= 1) {
            profile.displayName.substringAfter(NAME_JOINER)
        } else {
            profile.displayName
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}

/**
 * Per-parent cache of virtual (proxied) profiles (#234). Platform service
 * locators own one instance, call [refresh] when the real profile list
 * changes, when a picker opens and on a modest foreground interval. A failed
 * fetch keeps the parent's last good list.
 */
public class ProxiedServersRegistry(
    private val fetch: suspend (ServerProfile) -> Result<List<JsonObject>>,
) {
    private val _byParent = MutableStateFlow<Map<String, List<ServerProfile>>>(emptyMap())

    /** parentId → that parent's virtual profiles, only for parents fetched at least once. */
    public val byParent: StateFlow<Map<String, List<ServerProfile>>> = _byParent.asStateFlow()

    /** Flattened virtual profiles, current snapshot. */
    public fun virtualProfiles(): List<ServerProfile> = _byParent.value.values.flatten()

    /** Looks a virtual profile up by id in the current snapshot. */
    public fun find(id: String): ServerProfile? = _byParent.value[ProxiedServers.parentIdOf(id)]?.firstOrNull { it.id == id }

    /**
     * Re-fetches `/api/servers` for every enabled real profile in [profiles]
     * (virtual ones are ignored), in parallel. Parents no longer present are
     * dropped; a parent whose fetch fails keeps its previous list.
     */
    public suspend fun refresh(profiles: List<ServerProfile>) {
        val parents = profiles.filter { it.enabled && !ProxiedServers.isProxied(it.id) }
        val results: List<Pair<String, List<ServerProfile>?>> =
            coroutineScope {
                parents.map { p ->
                    async {
                        p.id to
                            runCatching { fetch(p).getOrThrow() }
                                .map { ProxiedServers.parse(p, it) }
                                .getOrNull()
                    }
                }.awaitAll()
            }
        val keep = parents.map { it.id }.toSet()
        _byParent.update { old ->
            buildMap {
                results.forEach { (id, list) ->
                    val value = list ?: old[id]
                    if (value != null) put(id, value)
                }
            }.filterKeys { it in keep }
        }
    }
}
