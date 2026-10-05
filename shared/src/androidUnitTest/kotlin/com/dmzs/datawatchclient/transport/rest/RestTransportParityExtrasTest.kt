package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ComputeNodeLiveDetailLoader
import com.dmzs.datawatchclient.transport.LlmSaveBody
import com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity extras (operator-approved 2026-10-05): LLM `auto_created` is read-only
 * (never in a save body), compute-node 📡 live detail, story verdicts decode.
 */
class RestTransportParityExtrasTest {
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

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `listLlms reads auto_created`() =
        runTest {
            server.enqueue(json("""{"llms":[{"name":"ollama-default","kind":"ollama","auto_created":true},{"name":"mine","kind":"claude"}]}"""))
            val llms = transport.listLlms().getOrThrow()
            assertTrue(llms[0].autoCreated)
            assertFalse(llms[1].autoCreated)
        }

    @Test
    fun `updateLlm never sends auto_created`() =
        runTest {
            server.enqueue(json("""{"name":"ollama-default","kind":"ollama","auto_created":true}"""))
            val dto = LlmRegistryEntryDto(name = "ollama-default", kind = "ollama", model = "llama3", autoCreated = true)
            assertTrue(transport.updateLlm("ollama-default", dto).getOrThrow().autoCreated)
            val req = server.takeRequest()
            assertEquals("PUT", req.method)
            val body = req.body.readUtf8()
            assertFalse(body.contains("auto_created"), body)
            assertTrue(body.contains("\"model\":\"llama3\""), body)
        }

    @Test
    fun `createLlm never sends auto_created`() =
        runTest {
            server.enqueue(json("""{"name":"x","kind":"ollama"}"""))
            transport.createLlm(LlmRegistryEntryDto(name = "x", kind = "ollama", autoCreated = true)).getOrThrow()
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body.contains("auto_created"), body)
        }

    @Test
    fun `saveLlmJson strips auto_created from a round-tripped server object`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val serverCopy =
                JsonObject(mapOf("name" to JsonPrimitive("x"), "kind" to JsonPrimitive("ollama"), "auto_created" to JsonPrimitive(true)))
            transport.saveLlmJson("x", serverCopy).getOrThrow()
            val body = server.takeRequest().body.readUtf8()
            assertFalse(body.contains("auto_created"), body)
            assertTrue(body.contains("\"kind\":\"ollama\""), body)
        }

    @Test
    fun `LlmSaveBody drops only read-only keys`() {
        val obj = LlmSaveBody.of(LlmRegistryEntryDto(name = "n", kind = "ollama", autoCreated = true))
        assertNull(obj["auto_created"])
        assertEquals("n", (obj["name"] as JsonPrimitive).content)
    }

    @Test
    fun `compute node detail returns raw JSON pretty-printed`() =
        runTest {
            server.enqueue(json("""{"cpu":{"pct":12.5},"models":["llama3"],"gpu":[]}"""))
            val d = ComputeNodeLiveDetailLoader.load(transport, "gpu node")
            assertNull(d.error)
            val text = assertNotNull(d.json)
            assertTrue(text.contains("\"models\""), text)
            assertTrue(text.contains("\n"), "pretty-printed")
            val req = server.takeRequest()
            assertEquals("/api/compute/nodes/gpu%20node/detail", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }

    @Test
    fun `compute node detail surfaces the server reason on failure`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(503)
                    .setBody("compute node has no monitoring_endpoint configured and no cached observer snapshot\n"),
            )
            val d = ComputeNodeLiveDetailLoader.load(transport, "n1")
            assertNull(d.json)
            val err = assertNotNull(d.error)
            assertTrue(err.contains("no monitoring_endpoint configured"), err)
            assertTrue(err.startsWith("503"), err)
        }

    @Test
    fun `story verdicts decode with severity and issues`() {
        val prd =
            RestTransport.DefaultJson.decodeFromString(
                PrdDto.serializer(),
                """{"id":"p1","name":"p","depth":2,"created_at":"2026-10-05T10:00:00Z",
                   "stories":[{"id":"s1","title":"t","verdicts":[
                     {"guardrail":"security","outcome":"block","severity":"high","summary":"bad","issues":["a","b"],"verdict_at":"2026-10-05T10:00:00Z"},
                     {"guardrail":"rules","outcome":"pass","summary":"ok"}]},
                     {"id":"s2","title":"u"}]}""",
            )
        assertEquals(2, prd.depth)
        val v = prd.stories[0].verdicts
        assertEquals(2, v.size)
        assertEquals("block", v[0].outcome)
        assertEquals("high", v[0].severity)
        assertEquals(listOf("a", "b"), v[0].issues)
        assertEquals("", v[1].severity)
        assertTrue(prd.stories[1].verdicts.isEmpty())
    }
}
