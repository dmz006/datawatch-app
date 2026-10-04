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
}
