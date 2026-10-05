package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.AvailableSkillDto
import com.dmzs.datawatchclient.transport.dto.SyncSkillsRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Remote-server (PWA Settings › Comms › Remote Servers) row operations used by
 * [IosSettingsLists]: Test (`testServerEntry`) and the enable switch. The
 * server's PUT replaces the entry wholesale, so the listed object (including
 * its token, which never leaves this layer) is sent back with only
 * `enabled` changed.
 */
internal object IosRemoteServerOps {
    suspend fun setEnabled(
        tr: TransportClient,
        name: String,
        enabled: Boolean,
    ): Result<Unit> {
        val listed: Result<List<JsonObject>> = tr.listRemoteServers()
        val entries: List<JsonObject> = listed.getOrElse { return Result.failure<Unit>(it) }
        val entry: JsonObject =
            entries.firstOrNull { str(it, "name") == name }
                ?: return Result.failure<Unit>(IllegalStateException("Server \"$name\" not found."))
        val body: MutableMap<String, JsonElement> = entry.toMutableMap()
        body["enabled"] = JsonPrimitive(enabled)
        // Server-managed timestamps; the store re-stamps them.
        body.remove("created_at")
        body.remove("updated_at")
        return tr.putRemoteServerJson(name, JsonObject(body))
    }

    suspend fun test(
        tr: TransportClient,
        name: String,
    ): Result<String> =
        tr.testRemoteServer(name).map { o ->
            if (str(o, "ok") == "true") {
                val version: String = str(o, "version").ifEmpty { "?" }
                "${str(o, "latency_ms")}ms v$version"
            } else {
                "fail: " + str(o, "error").ifEmpty { "err" }
            }
        }

    private fun str(
        o: JsonObject,
        k: String,
    ): String = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
}

/** One skill offered by a registry (`/api/skills/registries/{name}/available`). */
public data class IosAvailableSkill(
    val name: String,
    val summary: String,
    val tags: List<String>,
    val synced: Boolean,
)

/**
 * Skill registry browse / sync (PWA Settings › Automata › Skill Registries:
 * "Browse" → available list with per-skill sync, Sync all). Callbacks run on
 * a background thread.
 */
public object IosSkillBrowse {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun t(profile: ServerProfile): TransportClient = IosServiceLocator.transportFor(profile)

    public fun available(
        profile: ServerProfile,
        registry: String,
        onSuccess: (List<IosAvailableSkill>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            t(profile).listAvailableSkills(registry).fold(
                onSuccess = { list: List<AvailableSkillDto> ->
                    onSuccess(
                        list.map { s ->
                            IosAvailableSkill(
                                name = s.name,
                                summary = s.description.orEmpty(),
                                tags = s.tags,
                                synced = s.synced,
                            )
                        },
                    )
                },
                onFailure = { e -> onError(e.message ?: "Failed to load skills.") },
            )
        }
    }

    /** Sync (or with [unsync] remove) the named skills; `null` on success. */
    public fun sync(
        profile: ServerProfile,
        registry: String,
        names: List<String>,
        unsync: Boolean,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val req = SyncSkillsRequestDto(skills = names)
            val r: Result<Unit> =
                if (unsync) t(profile).unsyncSkills(registry, req) else t(profile).syncSkills(registry, req)
            onDone(r.exceptionOrNull()?.let { it.message ?: "Sync failed." })
        }
    }
}
