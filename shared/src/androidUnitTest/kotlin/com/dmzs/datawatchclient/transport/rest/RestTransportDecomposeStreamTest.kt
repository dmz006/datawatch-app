package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.sse.DecomposeLiveState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MockWebServer coverage for [RestTransport.decomposeEvents] (live planning stream). */
class RestTransportDecomposeStreamTest {
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

    private fun sse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `streams story progress and complete then stops`() =
        runBlocking {
            server.enqueue(
                sse(
                    "retry: 1000\n\n" +
                        "id: 1\ndata: {\"type\":\"story\",\"index\":0,\"title\":\"Auth\",\"id\":\"s1\"}\n\n" +
                        "id: 2\ndata: {\"type\":\"progress\",\"done\":1,\"total\":2}\n\n" +
                        ": keepalive\n\n" +
                        "id: 3\ndata: {\"type\":\"story\",\"index\":1,\"title\":\"API\",\"id\":\"s2\"}\n\n" +
                        "id: 4\ndata: {\"type\":\"progress\",\"done\":2,\"total\":2}\n\n" +
                        "id: 5\ndata: {\"type\":\"complete\",\"story_count\":2}\n\n",
                ),
            )
            val events = transport.decomposeEvents("p1").toList()
            assertEquals(listOf("story", "progress", "story", "progress", "complete"), events.map { it.type })
            val state = DecomposeLiveState.fold(events)
            assertEquals(listOf("Auth", "API"), state.stories.map { it.title })
            assertTrue(state.finished)

            val req = server.takeRequest()
            assertEquals("GET", req.method)
            assertEquals("/api/autonomous/prds/p1/decompose/stream", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
            assertTrue(req.getHeader("Accept").orEmpty().startsWith("text/event-stream"))
            assertNull(req.getHeader("Last-Event-ID"))
        }

    @Test
    fun `reconnects with Last-Event-ID after a drop`() =
        runBlocking {
            server.enqueue(sse("id: 1\ndata: {\"type\":\"story\",\"index\":0,\"title\":\"A\"}\n\n"))
            server.enqueue(sse("id: 2\ndata: {\"type\":\"error\",\"message\":\"llm timeout\"}\n\n"))
            val events = transport.decomposeEvents("p1").toList()
            assertEquals(listOf("story", "error"), events.map { it.type })
            server.takeRequest()
            assertEquals("1", server.takeRequest().getHeader("Last-Event-ID"))
        }

    @Test
    fun `404 means no planning job and ends quietly`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404).setBody("no decompose job found"))
            val events = transport.decomposeEvents("p1").toList()
            assertTrue(events.isEmpty())
            assertEquals(1, server.requestCount)
        }
}
