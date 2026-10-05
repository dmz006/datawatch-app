package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MockWebServer coverage for the `// ---- iOS-E ----` transport block. */
class RestTransportIosETest {
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
    fun testRemoteServerPostsAndParses() =
        runTest {
            server.enqueue(json("""{"ok":true,"latency_ms":12,"version":"8.40.0"}"""))
            val res = transport.testRemoteServer("peer one").getOrThrow()
            assertTrue(res["ok"]!!.jsonPrimitive.boolean)
            assertEquals("8.40.0", res["version"]!!.jsonPrimitive.content)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/servers/peer%20one/test", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }

    @Test
    fun testRemoteServerFailureBody() =
        runTest {
            server.enqueue(json("""{"ok":false,"latency_ms":0,"error":"dial tcp: refused"}"""))
            val res = transport.testRemoteServer("p").getOrThrow()
            assertFalse(res["ok"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun testRemoteServerNotFoundIsFailure() =
        runTest {
            server.enqueue(json("""server not found""", code = 404))
            assertTrue(transport.testRemoteServer("missing").isFailure)
        }

    @Test
    fun putRemoteServerJsonSendsWholeEntry() =
        runTest {
            server.enqueue(json("""{"name":"p","ok":true}"""))
            val body =
                JsonObject(
                    mapOf(
                        "name" to JsonPrimitive("p"),
                        "url" to JsonPrimitive("https://peer.invalid"),
                        "enabled" to JsonPrimitive(false),
                        "label" to JsonPrimitive("Peer"),
                    ),
                )
            assertTrue(transport.putRemoteServerJson("p", body).isSuccess)
            val req = server.takeRequest()
            assertEquals("PUT", req.method)
            assertEquals("/api/servers/p", req.path)
            val sent = RestTransport.DefaultJson.parseToJsonElement(req.body.readUtf8()).jsonObject
            assertFalse(sent["enabled"]!!.jsonPrimitive.boolean)
            assertEquals("Peer", sent["label"]!!.jsonPrimitive.content)
        }

    @Test
    fun putRemoteServerJsonForbiddenIsFailure() =
        runTest {
            server.enqueue(json("""builtin""", code = 403))
            assertTrue(transport.putRemoteServerJson("b", JsonObject(emptyMap())).isFailure)
        }
}
