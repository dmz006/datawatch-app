package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ProxiedServers
import com.dmzs.datawatchclient.transport.ProxiedServersRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of [IosProxiedServers.refresh]: the current virtual profiles, and
 * whether the stored active selection must move ([repair]) to [newActiveId]
 * (null = clear it). [retryDelayMs] > 0 means some server failed: refresh
 * again after that many ms (capped backoff, #236.1); 0 = all answered.
 */
public class IosProxiedRefresh(
    public val virtualProfiles: List<ServerProfile>,
    public val repair: Boolean,
    public val newActiveId: String?,
    public val retryDelayMs: Long,
)

/**
 * #234 — Swift bridge for remote servers reached through a connected server's
 * `/api/proxy/<name>` (see [ProxiedServers]). `ServerProfileStore` calls
 * [refresh] at launch and when profiles change, every 60 s in the foreground,
 * when a picker opens and after [IosProxiedRefresh.retryDelayMs] on failure,
 * and resolves the active profile with [resolveActive]. Every function takes all arguments explicitly (Swift doesn't see Kotlin defaults).
 */
public object IosProxiedServers {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val registry: ProxiedServersRegistry by lazy {
        ProxiedServersRegistry { parent -> IosServiceLocator.transportFor(parent).listRemoteServers() }
    }

    public fun isProxied(id: String?): Boolean = ProxiedServers.isProxied(id)

    /** The parent id for a proxied id, the id itself otherwise. */
    public fun realIdOf(id: String?): String? = ProxiedServers.realIdOf(id)

    /** Server-local pages (docs) of a proxied remote open on its parent. */
    public fun docsBaseUrl(profile: ServerProfile): String = ProxiedServers.docsBaseUrl(profile)

    /**
     * PWA `server_picker_loading`: true when some enabled real server in
     * [real] has never had its remote list fetched yet (Swift shows "Loading
     * servers…" while a refresh for it runs).
     */
    public fun hasUnlistedParents(real: List<ServerProfile>): Boolean =
        registry.firstLoadPending(real, true, registry.byParent.value)

    /** Snapshot of every discovered virtual profile. */
    public fun virtualProfiles(): List<ServerProfile> = registry.virtualProfiles()

    /** Active profile over real + virtual; a vanished remote falls back to its parent. */
    public fun resolveActive(
        real: List<ServerProfile>,
        activeId: String?,
    ): ServerProfile? = ProxiedServers.resolveActive(real, registry.byParent.value, activeId)

    /** Picker order: each real profile followed by its remotes. */
    public fun pickerRows(real: List<ServerProfile>): List<ServerProfile> =
        ProxiedServers.groupedForPicker(
            real.filterNot { ProxiedServers.isProxied(it.id) },
            registry.virtualProfiles(),
        )

    /** Chip label: just the remote name when there's a single real server. */
    public fun chipLabel(
        profile: ServerProfile,
        realEnabledCount: Int,
    ): String = ProxiedServers.chipLabel(profile, realEnabledCount)

    /** Parent display name for a proxied remote ("via …" subtitle), else null. */
    public fun parentName(
        profile: ServerProfile,
        real: List<ServerProfile>,
    ): String? {
        if (!ProxiedServers.isProxied(profile.id)) return null
        val parentId = ProxiedServers.parentIdOf(profile.id)
        return real.firstOrNull { it.id == parentId }?.displayName
            ?: profile.displayName.substringBefore(ProxiedServers.NAME_JOINER)
    }

    /**
     * Re-fetches `/api/servers` on every enabled real profile, then reports the
     * virtual list and any repair of [activeId]; also prunes cached sessions of
     * remotes that are gone. Callback runs on a background thread.
     */
    public fun refresh(
        real: List<ServerProfile>,
        activeId: String?,
        onDone: (IosProxiedRefresh) -> Unit,
    ) {
        scope.launch {
            val result =
                mutex.withLock {
                    runCatching { registry.refresh(real) }
                    val retry = registry.retryDelayMs() ?: 0L
                    val repair = ProxiedServers.repairActiveId(real, registry.byParent.value, activeId)
                    val keepActive = if (repair != null) repair.newId else activeId
                    runCatching {
                        IosServiceLocator.sessionRepository.pruneProxied(
                            registry.virtualProfiles().map { it.id }.toSet() + listOfNotNull(keepActive),
                        )
                    }
                    IosProxiedRefresh(registry.virtualProfiles(), repair != null, repair?.newId, retry)
                }
            onDone(result)
        }
    }
}
