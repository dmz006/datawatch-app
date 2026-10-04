package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MockWebServer coverage for the `// ---- Android-missing parity ----` transport block. */
class RestTransportAndroidParityTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: RestTransport

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
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

    private fun json(
        body: String,
        code: Int = 200,
    ): MockResponse =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun sendChannelMessagePostsTextAndSessionId() =
        runTest {
            server.enqueue(json("""{"ok":true}"""))
            val res = transport.sendChannelMessage("host-ab12", "hello")
            assertTrue(res.isSuccess, "got ${res.exceptionOrNull()}")
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/channel/send", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
            val body = req.body.readUtf8()
            assertTrue(body.contains("\"text\":\"hello\""), body)
            assertTrue(body.contains("\"session_id\":\"host-ab12\""), body)
        }

    @Test
    fun sendChannelMessageSurfacesServerErrors() =
        runTest {
            server.enqueue(json("""{"error":"no channel"}""", code = 404))
            assertTrue(transport.sendChannelMessage("x", "y").isFailure)
        }

    @Test
    fun getComputeNodeModelsUnwrapsServerEnvelope() =
        runTest {
            server.enqueue(json("""{"models":["llama3:8b","qwen2"],"kind":"ollama"}"""))
            val res = transport.getComputeNodeModels("gpu-1", "ollama")
            assertEquals(listOf("llama3:8b", "qwen2"), res.getOrThrow())
            assertEquals("/api/compute/nodes/gpu-1/models?kind=ollama", server.takeRequest().path)
        }

    @Test
    fun scanConfigUsesServerKeys() =
        runTest {
            server.enqueue(
                json(
                    """{"enabled":true,"sast_enabled":true,"secrets_enabled":false,"deps_enabled":true,
                    |"fail_on_severity":"warning","rules_grader_enabled":true,"fix_loop_enabled":true,
                    |"fix_loop_max_retries":5}""".trimMargin(),
                ),
            )
            val cfg = transport.getScanConfig().getOrThrow()
            assertTrue(cfg.grader)
            assertTrue(cfg.fixLoop)
            assertEquals(5, cfg.maxRetries)
            server.enqueue(json("{}"))
            transport.updateScanConfig(cfg.copy(maxRetries = 2))
            server.takeRequest()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"fix_loop_max_retries\":2"), body)
            assertTrue(body.contains("\"rules_grader_enabled\":true"), body)
        }

    @Test
    fun fetchCrossHostEnvelopesHitsAllPeers() =
        runTest {
            server.enqueue(json("""{"by_peer":{"local":[{"id":"e1","kind":"session"}]}}"""))
            val obj = transport.fetchCrossHostEnvelopesJson().getOrThrow()
            assertTrue(obj.containsKey("by_peer"))
            assertEquals("/api/observer/envelopes/all-peers", server.takeRequest().path)
        }

    @Test
    fun exitHooksCrud() =
        runTest {
            server.enqueue(json("""[{"id":"h1","name":"build","action":"restart","cooldown_seconds":300,"enabled":true}]"""))
            assertEquals(1, transport.listExitHooksJson().getOrThrow().size)
            assertEquals("/api/exit-hooks", server.takeRequest().path)

            server.enqueue(json("""{"id":"h2"}"""))
            val body = kotlinx.serialization.json.buildJsonObject { put("name", kotlinx.serialization.json.JsonPrimitive("x")) }
            assertTrue(transport.createExitHook(body).isSuccess)
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertTrue(post.body.readUtf8().contains("\"name\":\"x\""))

            server.enqueue(json("""{"id":"h1"}"""))
            val upd = kotlinx.serialization.json.buildJsonObject { put("enabled", kotlinx.serialization.json.JsonPrimitive(false)) }
            assertTrue(transport.updateExitHook("h1", upd).isSuccess)
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/api/exit-hooks/h1", put.path)

            server.enqueue(json("""{"ok":true}"""))
            assertTrue(transport.deleteExitHook("h1").isSuccess)
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun workQueueListPushDelete() =
        runTest {
            server.enqueue(json("null"))
            assertEquals(0, transport.listQueueJson("worker", "pending").getOrThrow().size)
            assertEquals("/api/queue?role=worker&state=pending", server.takeRequest().path)

            server.enqueue(json("""{"id":"q1","role":"worker","state":"pending"}"""))
            val payload = kotlinx.serialization.json.buildJsonObject { put("task", kotlinx.serialization.json.JsonPrimitive("t")) }
            assertTrue(transport.pushQueueItem("worker", payload).isSuccess)
            val push = server.takeRequest()
            assertEquals("/api/queue/push", push.path)
            val b = push.body.readUtf8()
            assertTrue(b.contains("\"role\":\"worker\"") && b.contains("\"task\":\"t\""), b)

            server.enqueue(json("""{"ok":true}"""))
            assertTrue(transport.deleteQueueItem("q1").isSuccess)
            val del = server.takeRequest()
            assertEquals("DELETE", del.method)
            assertEquals("/api/queue/q1", del.path)
        }
}
