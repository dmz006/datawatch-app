package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.dto.PrdDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task S (2026-10-05): per-Automaton `max_concurrent_tasks` (PrdDto field with a
 * default + POST set_concurrency, PWA prdSettings "Max concurrent tasks").
 */
class RestTransportTaskSTest {
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
    fun `getPrd decodes max_concurrent_tasks and defaults to 0`() =
        runTest {
            server.enqueue(json("""{"id":"a1","name":"x","status":"running","max_concurrent_tasks":4}"""))
            assertEquals(4, transport.getPrd("a1").getOrThrow().maxConcurrentTasks)
            server.enqueue(json("""{"id":"a2","name":"y","status":"draft"}"""))
            assertEquals(0, transport.getPrd("a2").getOrThrow().maxConcurrentTasks)
            assertEquals(0, PrdDto().maxConcurrentTasks)
        }

    @Test
    fun `setPrdConcurrency posts max_concurrent_tasks`() =
        runTest {
            server.enqueue(json("""{"id":"a1","max_concurrent_tasks":3}"""))
            assertTrue(transport.setPrdConcurrency("a1", 3).isSuccess)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/autonomous/prds/a1/set_concurrency", req.path)
            val body = RestTransport.DefaultJson.parseToJsonElement(req.body.readUtf8()).jsonObject
            assertEquals(JsonPrimitive(3), body["max_concurrent_tasks"])
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }
}
