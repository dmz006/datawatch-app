package com.dmzs.datawatchclient.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.dmzs.datawatchclient.transport.ProxiedServers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * #234 — keeps [ServiceLocator.proxiedServers] fresh: re-fetches each real
 * profile's `/api/servers` when the profile list changes, every
 * [INTERVAL_MS] while the app is in the foreground, and on demand when a
 * server picker opens ([refreshNow]). After each refresh it repairs a stored
 * selection pointing at a vanished remote (→ its parent) and prunes cached
 * sessions of remotes that are gone.
 */
public object ProxiedServersCoordinator {
    private const val INTERVAL_MS: Long = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var started: Boolean = false

    public fun start() {
        if (started) return
        started = true
        scope.launch {
            ServiceLocator.profileRepository.observeAll()
                .distinctUntilChanged { a, b ->
                    a.map { Triple(it.id, it.baseUrl, it.enabled) } == b.map { Triple(it.id, it.baseUrl, it.enabled) }
                }
                .collect { refresh() }
        }
        scope.launch {
            while (true) {
                delay(INTERVAL_MS)
                if (isForeground()) refresh()
            }
        }
    }

    /** Fire-and-forget refresh, e.g. when a server picker opens. */
    public fun refreshNow() {
        scope.launch { refresh() }
    }

    private suspend fun isForeground(): Boolean =
        withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }

    private suspend fun refresh() {
        mutex.withLock {
            runCatching {
                val real = ServiceLocator.profileRepository.observeAll().first()
                val registry = ServiceLocator.proxiedServers
                registry.refresh(real)
                val byParent = registry.byParent.value
                val store = ServiceLocator.activeServerStore
                ProxiedServers.repairActiveId(real, byParent, store.get())?.let { store.set(it.newId) }
                val keep = registry.virtualProfiles().map { it.id }.toSet() + listOfNotNull(store.get())
                ServiceLocator.sessionRepository.pruneProxied(keep)
            }
        }
    }
}
