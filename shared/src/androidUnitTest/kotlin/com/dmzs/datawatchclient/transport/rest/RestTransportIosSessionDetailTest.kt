package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
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

/** MockWebServer tests for the `// ---- iOS session-detail parity (D43a) ----` block. */
class RestTransportIosSessionDetailTest {
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
    fun `fetch session response reads the response field`() =
        runTest {
            server.enqueue(json("""{"session_id":"h-ab12","response":"done **ok**","extra":1}"""))
            val text = transport.fetchSessionResponse("h-ab12").getOrThrow()
            assertEquals("done **ok**", text)
            val req = server.takeRequest()
            assertEquals("GET", req.method)
            assertEquals("/api/sessions/response?id=h-ab12", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }

    @Test
    fun `fetch session response is empty when none captured`() =
        runTest {
            server.enqueue(json("""{"session_id":"h-ab12"}"""))
            assertEquals("", transport.fetchSessionResponse("h-ab12").getOrThrow())
            server.enqueue(json("""{"response":null}"""))
            assertEquals("", transport.fetchSessionResponse("h-ab12").getOrThrow())
        }

    @Test
    fun `fetch session response fails on 404`() =
        runTest {
            server.enqueue(json("""{"error":"not found"}""", code = 404))
            assertTrue(transport.fetchSessionResponse("nope").isFailure)
        }
}
