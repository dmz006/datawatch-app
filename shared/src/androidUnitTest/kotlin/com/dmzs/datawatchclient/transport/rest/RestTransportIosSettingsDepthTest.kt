package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MockWebServer coverage for the `// ---- iOS settings depth parity ----` transport block. */
class RestTransportIosSettingsDepthTest {
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
    ): MockResponse = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun addFederationPeerPostsBody() =
        runTest {
            server.enqueue(json("""{"name":"alpha"}""", code = 201))
            val body = buildJsonObject {
                put("name", JsonPrimitive("alpha"))
                put("url", JsonPrimitive("http://peer.invalid"))
                put("enabled", JsonPrimitive(true))
            }
            assertTrue(transport.addFederationPeer(body).isSuccess)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/federation/peers", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
            val sent = req.body.readUtf8()
            assertTrue(sent.contains("\"name\":\"alpha\""), sent)
            assertTrue(sent.contains("\"enabled\":true"), sent)
        }

    @Test
    fun updateFederationPeerPutsEncodedName() =
        runTest {
            server.enqueue(json("""{"name":"a b"}"""))
            val body = buildJsonObject { put("url", JsonPrimitive("http://x.invalid")) }
            assertTrue(transport.updateFederationPeer("a b", body).isSuccess)
            val req = server.takeRequest()
            assertEquals("PUT", req.method)
            assertEquals("/api/federation/peers/a%20b", req.path)
        }

    @Test
    fun addFederationPeerSurfacesConflict() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(409).setBody("conflict"))
            assertTrue(transport.addFederationPeer(buildJsonObject { }).isFailure)
        }

    @Test
    fun testFederationPeerReturnsResult() =
        runTest {
            server.enqueue(json("""{"ok":true,"latency_ms":12,"version":"8.40.0"}"""))
            val obj = transport.testFederationPeer("alpha").getOrThrow()
            assertEquals("true", (obj["ok"] as JsonPrimitive).content)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/federation/peers/alpha/test", req.path)
        }

    @Test
    fun patchWebSearchProviderSendsOnlyGivenKeys() =
        runTest {
            server.enqueue(json("""{"name":"sx"}"""))
            val body = buildJsonObject { put("priority", JsonPrimitive(2)) }
            assertTrue(transport.patchWebSearchProviderJson("sx", body).isSuccess)
            val req = server.takeRequest()
            assertEquals("PATCH", req.method)
            assertEquals("/api/websearch/providers/sx", req.path)
            val sent = req.body.readUtf8()
            assertTrue(sent.contains("\"priority\":2"), sent)
            assertTrue(!sent.contains("api_key"), sent)
        }

    @Test
    fun createKindProfilePostsToCollection() =
        runTest {
            server.enqueue(json("""{"name":"p1"}""", code = 201))
            val body = buildJsonObject { put("name", JsonPrimitive("p1")) }
            assertTrue(transport.createKindProfile("project", body).isSuccess)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/profiles/projects", req.path)
            assertTrue(req.body.readUtf8().contains("\"name\":\"p1\""))
        }
}
