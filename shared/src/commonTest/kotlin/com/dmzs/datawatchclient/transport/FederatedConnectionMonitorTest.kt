package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FederatedConnectionMonitorTest {
    private val parent =
        ServerProfile(
            id = "p",
            displayName = "workstation",
            baseUrl = "https://p.example:8443",
            bearerTokenRef = "alias-p",
            reachabilityProfileId = "lan",
            createdTs = 1L,
        )
    private val demo = ProxiedServers.make(parent, "demo", "Demo box")
    private val other = ProxiedServers.make(parent, "other", null)

    @Test
    fun `real profile and all-servers never show a status`() =
        runTest {
            var probes = 0
            val m = FederatedConnectionMonitor(backgroundScope, probe = { probes++; Result.success(emptyList()) })
            m.onActiveProfile(parent)
            runCurrent()
            assertNull(m.status.value)
            m.onActiveProfile(null)
            runCurrent()
            assertNull(m.status.value)
            assertEquals(0, probes)
        }

    @Test
    fun `connecting then loading sessions then cleared, driven by the probe`() =
        runTest {
            val answer = CompletableDeferred<Result<List<Session>>>()
            val storeGate = CompletableDeferred<Unit>()
            val seen = mutableListOf<FedConnPhase?>()
            val m =
                FederatedConnectionMonitor(
                    backgroundScope,
                    probe = { answer.await() },
                    onSessions = { _, _ -> storeGate.await() },
                )
            m.onActiveProfile(demo)
            runCurrent()
            val connecting = m.status.value!!
            assertEquals(FedConnPhase.CONNECTING, connecting.phase)
            assertEquals("Demo box", connecting.serverName)
            assertEquals("p", connecting.parentId)
            assertEquals("workstation", connecting.parentName)
            seen += connecting.phase
            answer.complete(Result.success(emptyList()))
            runCurrent()
            seen += m.status.value?.phase
            storeGate.complete(Unit)
            runCurrent()
            seen += m.status.value?.phase
            assertEquals(listOf(FedConnPhase.CONNECTING, FedConnPhase.LOADING_SESSIONS, null), seen)
        }

    @Test
    fun `401 shows the auth error and retries with backoff until it works`() =
        runTest {
            var calls = 0
            var authorized = false
            val m =
                FederatedConnectionMonitor(
                    backgroundScope,
                    probe = {
                        calls++
                        if (authorized) Result.success(emptyList()) else Result.failure(TransportError.Unauthorized())
                    },
                )
            m.onActiveProfile(demo)
            runCurrent()
            val err = m.status.value!!
            assertEquals(FedConnPhase.ERROR, err.phase)
            assertTrue(err.authFailed)
            assertEquals(1, calls)
            advanceTimeBy(4_999)
            assertEquals(1, calls, "first retry after 5 s")
            advanceTimeBy(2)
            assertEquals(2, calls)
            assertEquals(FedConnPhase.ERROR, m.status.value?.phase, "error stays up while retrying")
            authorized = true
            advanceTimeBy(10_001)
            assertEquals(3, calls)
            assertNull(m.status.value)
        }

    @Test
    fun `real data reported elsewhere clears the error and stops retrying`() =
        runTest {
            var calls = 0
            val m =
                FederatedConnectionMonitor(
                    backgroundScope,
                    probe = {
                        calls++
                        Result.failure(TransportError.Unreachable(IllegalStateException("Connection refused")))
                    },
                )
            m.onActiveProfile(demo)
            runCurrent()
            assertEquals("Connection refused", m.status.value?.reason)
            assertFalse(m.status.value!!.authFailed)
            m.reportData(other.id) // a different server's data changes nothing
            assertEquals(FedConnPhase.ERROR, m.status.value?.phase)
            m.reportData(demo.id)
            assertNull(m.status.value)
            advanceTimeBy(600_000)
            assertEquals(1, calls)
            assertNull(m.status.value)
        }

    @Test
    fun `switching between two remotes of the same parent re-probes, same id does not`() =
        runTest {
            val probed = mutableListOf<String>()
            val m =
                FederatedConnectionMonitor(
                    backgroundScope,
                    probe = {
                        probed += it.id
                        Result.failure(TransportError.Unauthorized())
                    },
                    retryDelayMs = { 1_000_000L },
                )
            m.onActiveProfile(demo)
            runCurrent()
            m.onActiveProfile(demo.copy(displayName = "workstation › renamed"))
            runCurrent()
            assertEquals(listOf(demo.id), probed)
            m.onActiveProfile(other)
            runCurrent()
            assertEquals(listOf(demo.id, other.id), probed)
            assertEquals(other.id, m.status.value?.profileId)
            assertEquals("other", m.status.value?.serverName)
        }

    @Test
    fun `reason text - proxy 502 body, http status, auth`() {
        val bad =
            TransportError.ServerError(
                502,
                "Server error(GET https://p.example:8443/api/proxy/demo/api/sessions: 502 Bad Gateway. " +
                    "Text: \"proxy error: dial tcp: connection refused\"",
            )
        assertEquals("proxy error: dial tcp: connection refused", FederatedConnectionMonitor.reasonOf(bad))
        assertTrue(FederatedConnectionMonitor.isAuthFailure(TransportError.Unauthorized()))
        assertFalse(FederatedConnectionMonitor.isAuthFailure(bad))
        assertEquals("unreachable", FederatedConnectionMonitor.reasonOf(null))
        assertEquals("Server unreachable", FederatedConnectionMonitor.reasonOf(TransportError.Unreachable()))
    }
}
