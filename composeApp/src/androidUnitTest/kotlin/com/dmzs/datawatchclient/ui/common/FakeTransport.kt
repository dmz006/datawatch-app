package com.dmzs.datawatchclient.ui.common

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportClient
import io.mockk.mockk

/**
 * Test helpers for VM unit tests. v0.41.0 — relies on mockk's
 * `relaxed = true` to stub out the 60-method `TransportClient`
 * interface; tests then `coEvery { transport.foo() } returns …`
 * just the methods they care about. The fake-by-hand approach was
 * too much surface area for marginal value.
 */
internal fun fakeTransport(): TransportClient = mockk(relaxed = true)

/** Convenience: build a [ProfileResolver] backed by a relaxed mockk transport. */
internal fun fakeResolver(
    transport: TransportClient = fakeTransport(),
    profile: ServerProfile = ServerProfile(
        id = "p1",
        displayName = "Test",
        baseUrl = "https://localhost:8443",
        bearerTokenRef = "",
        reachabilityProfileId = "p1",
        createdTs = 0,
    ),
): Pair<TransportClient, ProfileResolver> =
    transport to ProfileResolver { profile to transport }

/** Build a resolver that always returns null (no enabled profile). */
internal val nullResolver: ProfileResolver = ProfileResolver { null }
