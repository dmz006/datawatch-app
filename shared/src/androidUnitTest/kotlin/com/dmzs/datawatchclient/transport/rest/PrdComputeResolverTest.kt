package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.PrdComputeResolver
import com.dmzs.datawatchclient.transport.dto.PrdDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Automata detail compute stats while planning (operator 2026-10-06: "prd running
 * never shows compute node stats"): session ref > backend LLM node > local stats,
 * with planning resolved from decomposition_profile / global planning_backend.
 */
class PrdComputeResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: RestTransport
    private val routes = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        server =
            MockWebServer().apply {
                dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            val body = routes[request.path?.substringBefore('?')]
                            return if (body == null) {
                                MockResponse().setResponseCode(404).setBody("not found")
                            } else {
                                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
                            }
                        }
                    }
                start()
            }
        val profile =
            ServerProfile(
                id = "srv-test",
                displayName = "test",
                baseUrl = server.url("/").toString().trimEnd('/'),
                bearerTokenRef = "dw.profile.srv-test",
                trustAnchorSha256 = null,
                reachabilityProfileId = "lan",
                createdTs = 0L,
            )
        val client =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(RestTransport.DefaultJson) }
                expectSuccess = true
            }
        transport = RestTransport(profile, client) { "secret-token" }
    }

    @AfterTest
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private val nodeDetail = """{"cpu":{"pct":42.0,"cores":8},"mem":{"used_bytes":1024,"total_bytes":4096},"gpu":[]}"""

    @Test
    fun `planning uses global planning_backend node when no decomposition_profile`() =
        runTest {
            routes["/api/autonomous/config"] = """{"enabled":true,"planning_backend":"planner"}"""
            routes["/api/llms"] = """{"llms":[{"name":"planner","kind":"ollama","compute_nodes":["node-a","node-b"]}]}"""
            routes["/api/compute/nodes/node-a/detail"] = nodeDetail
            val r = PrdComputeResolver.resolve(transport, PrdDto(id = "p1", status = "planning", backend = "claude-code"), null)
            assertEquals("node-a", r.ref)
            assertEquals(42.0, r.detail?.cpu?.pct)
        }

    @Test
    fun `planning prefers the automaton decomposition_profile`() =
        runTest {
            routes["/api/autonomous/config"] = """{"planning_backend":"planner"}"""
            routes["/api/llms"] =
                """{"llms":[{"name":"planner","kind":"ollama","compute_nodes":["node-a"]},{"name":"mine","kind":"ollama","compute_node":"node-b"}]}"""
            routes["/api/compute/nodes/node-b/detail"] = nodeDetail
            val r = PrdComputeResolver.resolve(transport, PrdDto(id = "p1", status = "planning", decompositionProfile = "mine"), null)
            assertEquals("node-b", r.ref)
        }

    @Test
    fun `session compute_node_ref wins`() =
        runTest {
            routes["/api/compute/nodes/sess-node/detail"] = nodeDetail
            val r = PrdComputeResolver.resolve(transport, PrdDto(id = "p1", status = "running"), "sess-node")
            assertEquals("sess-node", r.ref)
        }

    @Test
    fun `falls back to local stats when the backend has no node`() =
        runTest {
            routes["/api/autonomous/config"] = """{"enabled":true}"""
            routes["/api/stats"] =
                """{"cpu_load_avg_1":2.0,"cpu_cores":4,"mem_used":100,"mem_total":400,"gpu_name":"RTX","gpu_util_pct":10.0,"gpu_mem_used_mb":1,"gpu_mem_total_mb":2}"""
            val r = PrdComputeResolver.resolve(transport, PrdDto(id = "p1", status = "planning"), null)
            assertNull(r.ref)
            val d = assertNotNull(r.detail)
            assertEquals(50.0, d.cpu?.pct)
            assertEquals(400L, d.mem?.totalBytes)
            assertEquals(1_048_576L, d.gpu.single().memUsedBytes)
            assertTrue(d.gpu.single().name == "RTX")
        }
}
