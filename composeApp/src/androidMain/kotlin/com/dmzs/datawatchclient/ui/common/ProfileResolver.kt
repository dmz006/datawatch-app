package com.dmzs.datawatchclient.ui.common

import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.flow.first

/**
 * Tiny DI shim so the v0.36+ monitoring + autonomous + mempalace
 * ViewModels can be unit-tested without standing up the full
 * `ServiceLocator` (which owns SQLCipher, transports, the active-server
 * store, and a handful of other singletons).
 *
 * Every VM that previously read directly from `ServiceLocator` now
 * accepts a `ProfileResolver` (defaulted to [Default]) and delegates
 * its `activeProfile()` + `transportFor(profile)` calls through here.
 * Tests pass a fake implementation that returns a stub
 * [TransportClient]; production code keeps the same behaviour as
 * before since `Default` is wired in at the constructor default.
 *
 * Introduced 2026-04-28 in v0.41.0 alongside the first VM unit tests.
 */
public fun interface ProfileResolver {
    /**
     * Returns the active enabled profile + a transport bound to it,
     * or null when no profile is configured / enabled. The pair is
     * resolved together because every VM call site needs both — and
     * the active-server resolution is the authoritative gate for
     * whether a request can even be issued.
     */
    public suspend fun resolve(): Pair<ServerProfile, TransportClient>?

    public companion object {
        /**
         * Production resolver — reads `ServiceLocator.activeServerStore`
         * + `profileRepository` and binds the resulting profile to a
         * fresh `transportFor(profile)`. Idempotent on repeated
         * calls; the transport is stateless from the VM's POV.
         */
        public val Default: ProfileResolver =
            ProfileResolver {
                val activeId = ServiceLocator.activeServerStore.get() ?: return@ProfileResolver null
                val profile =
                    ServiceLocator.profileRepository.observeAll().first()
                        .firstOrNull { it.id == activeId && it.enabled }
                        ?: return@ProfileResolver null
                profile to ServiceLocator.transportFor(profile)
            }
    }
}
