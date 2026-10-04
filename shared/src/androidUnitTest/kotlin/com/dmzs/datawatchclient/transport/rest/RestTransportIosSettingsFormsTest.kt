package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MockWebServer tests for the `// ---- iOS settings forms ----` transport block. */
class RestTransportIosSettingsFormsTest {
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
                install(HttpTimeout)
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
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    @Test
    fun `compute node raw get preserves unknown fields`() =
        runTest {
            server.enqueue(json("""{"name":"gpu 1","kind":"ollama","address":"http://h:11434","routing":"direct","scheduling_priority":50}"""))
            val obj = transport.fetchComputeNodeJson("gpu 1").getOrThrow()
            assertEquals("direct", obj["routing"]?.jsonPrimitive?.content)
            assertEquals("50", obj["scheduling_priority"]?.jsonPrimitive?.content)
            val req = server.takeRequest()
            assertEquals("GET", req.method)
            assertEquals("/api/compute/nodes/gpu%201", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }

    @Test
    fun `save compute node posts when new and puts when editing`() =
        runTest {
            server.enqueue(json("{}"))
            server.enqueue(json("{}"))
            val body = buildJsonObject { put("name", JsonPrimitive("n1")) }
            assertTrue(transport.saveComputeNodeJson(null, body).isSuccess)
            assertTrue(transport.saveComputeNodeJson("n1", body).isSuccess)
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/api/compute/nodes", post.path)
            assertTrue(post.body.readUtf8().contains("\"name\":\"n1\""))
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/api/compute/nodes/n1", put.path)
        }

    @Test
    fun `compute node health and model names accept object or array`() =
        runTest {
            server.enqueue(json("""{"status":"ok"}"""))
            server.enqueue(json("""{"models":["qwen3:8b","llama3"]}"""))
            server.enqueue(json("""["a","b"]"""))
            assertEquals("ok", transport.computeNodeHealthJson("n1").getOrThrow()["status"]?.jsonPrimitive?.content)
            assertEquals(listOf("qwen3:8b", "llama3"), transport.computeNodeModelNames("n1", "ollama").getOrThrow())
            assertEquals(listOf("a", "b"), transport.computeNodeModelNames("n1", "openai-compat").getOrThrow())
            assertEquals("/api/compute/nodes/n1/health", server.takeRequest().path)
            assertEquals("/api/compute/nodes/n1/models?kind=ollama", server.takeRequest().path)
            assertEquals("/api/compute/nodes/n1/models?kind=openai-compat", server.takeRequest().path)
        }

    @Test
    fun `llm raw get save and test hit llm endpoints`() =
        runTest {
            server.enqueue(json("""{"name":"l1","kind":"ollama","max_inflight":2}"""))
            server.enqueue(json("{}"))
            server.enqueue(json("{}"))
            server.enqueue(json("""{"text":"OK"}"""))
            assertEquals("2", transport.fetchLlmJson("l1").getOrThrow()["max_inflight"]?.jsonPrimitive?.content)
            val body = buildJsonObject { put("kind", JsonPrimitive("ollama")) }
            assertTrue(transport.saveLlmJson(null, body).isSuccess)
            assertTrue(transport.saveLlmJson("l1", body).isSuccess)
            assertEquals("OK", transport.testLlmJson("l1", "qwen3:8b").getOrThrow()["text"]?.jsonPrimitive?.content)
            assertEquals("/api/llms/l1", server.takeRequest().path)
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/api/llms", post.path)
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/api/llms/l1", put.path)
            val test = server.takeRequest()
            assertEquals("/api/llms/l1/test", test.path)
            val sent = test.body.readUtf8()
            assertTrue(sent.contains("\"model\":\"qwen3:8b\""), sent)
            assertTrue(sent.contains("\"prompt\""), sent)
        }

    @Test
    fun `algorithm advance sends phase output`() =
        runTest {
            server.enqueue(json("""{"session_id":"s1","current":"orient"}"""))
            assertTrue(transport.algorithmAdvanceWithOutput("s1", "observed").isSuccess)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/algorithm/s1/advance", req.path)
            assertEquals("""{"output":"observed"}""", req.body.readUtf8())
        }

    @Test
    fun `scan config raw get and partial put`() =
        runTest {
            server.enqueue(json("""{"enabled":true,"max_findings":0,"fix_loop_enabled":false}"""))
            server.enqueue(json("{}"))
            val cfg = transport.fetchScanConfigJson().getOrThrow()
            assertEquals("true", cfg["enabled"]?.jsonPrimitive?.content)
            assertTrue(transport.patchScanConfig(buildJsonObject { put("fix_loop_enabled", JsonPrimitive(true)) }).isSuccess)
            assertEquals("/api/autonomous/scan/config", server.takeRequest().path)
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("""{"fix_loop_enabled":true}""", put.body.readUtf8())
        }

    @Test
    fun `signal link start returns stream id and stream parses events`() =
        runTest {
            server.enqueue(json("""{"stream_id":"abc123"}"""))
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(": keepalive\n\nevent: qr\ndata: sgnl://linkdevice?uuid=x&pub_key=y\n\nevent: linked\ndata: success\n\n"),
            )
            server.enqueue(json("""{"linked":true,"account_number":"+10000000000","devices":[]}"""))
            assertEquals("abc123", transport.startSignalLink("").getOrThrow())
            val events = transport.signalLinkEvents("abc123").toList()
            assertEquals(2, events.size)
            assertEquals("qr", events[0].event)
            assertEquals("sgnl://linkdevice?uuid=x&pub_key=y", events[0].data)
            assertEquals("linked", events[1].event)
            val status = transport.fetchSignalLinkStatusJson().getOrThrow()
            assertEquals("true", status["linked"]?.jsonPrimitive?.content)
            val start = server.takeRequest()
            assertEquals("/api/link/start", start.path)
            assertEquals("""{"device_name":""}""", start.body.readUtf8())
            assertEquals("/api/link/stream?id=abc123", server.takeRequest().path)
            assertEquals("/api/link/status", server.takeRequest().path)
        }

    @Test
    fun `signal link start without stream id fails`() =
        runTest {
            server.enqueue(json("""{}"""))
            assertTrue(transport.startSignalLink("").isFailure)
        }
}
