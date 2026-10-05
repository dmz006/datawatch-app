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

/** MockWebServer coverage for [RestTransport.runCommand] (scroll-mode exit). */
class RestTransportRunCommandTest {
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

    @Test
    fun `runCommand posts text and returns the router result`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                    .setBody("""{"result":"[h-1a2b] Key sent: Escape"}"""),
            )
            val r = transport.runCommand("sendkey h-1a2b: Escape")
            assertEquals("[h-1a2b] Key sent: Escape", r.getOrThrow())
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/command", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
            assertTrue(req.body.readUtf8().contains("\"text\":\"sendkey h-1a2b: Escape\""))
        }

    @Test
    fun `runCommand surfaces server errors as failure`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            assertTrue(transport.runCommand("sendkey x: Escape").isFailure)
        }

    @Test
    fun `autonomous gating reads the live autonomous config`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                    .setBody("""{"enabled":true,"poll_interval_seconds":30}"""),
            )
            assertEquals(true, transport.fetchAutonomousEnabled().getOrThrow())
            assertEquals("/api/autonomous/config", server.takeRequest().path)
        }
}
