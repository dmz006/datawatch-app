package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.CouncilLivePhase
import com.dmzs.datawatchclient.transport.CouncilLiveState
import com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest
import com.dmzs.datawatchclient.transport.watchCouncilRun
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MockWebServer tests for the council live-run transport block + watcher. */
class RestTransportCouncilLiveTest {
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

    private fun sse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)

    private val runJson =
        """{"id":"r1","proposal":"Ship it?","personas":["a","b"],"mode":"quick",
           "rounds":[{"index":1,"responses":{"a":"A1","b":"B1"}}],
           "consensus":"Yes","dissent":"b worries","started_at":"2026-10-05T10:00:00Z",
           "finished_at":"2026-10-05T10:01:00Z"}"""

    @Test
    fun `start run decodes the async ack and lists bare arrays`() =
        runTest {
            server.enqueue(json("""{"id":"r1","status":"running","events_path":"/api/council/runs/r1/events"}"""))
            server.enqueue(json("[$runJson]"))
            server.enqueue(json("""[{"name":"a","role":"r","system_prompt":"x"}]"""))
            server.enqueue(json("""{"personas":[{"name":"b"}]}"""))
            val ack = transport.councilStartRun(StartCouncilRunRequest(proposal = "Ship it?", mode = "quick", personas = listOf("a"))).getOrThrow()
            assertEquals("r1", ack.id)
            assertEquals("running", ack.status)
            val runs = transport.councilListRuns().getOrThrow()
            assertEquals(1, runs.size)
            assertEquals("completed", runs[0].effectiveStatus)
            assertEquals("A1", runs[0].rounds[0].responses["a"])
            assertEquals(listOf("a"), transport.councilListPersonas().getOrThrow().map { it.name })
            assertEquals(listOf("b"), transport.councilListPersonas().getOrThrow().map { it.name })
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/api/council/run", post.path)
            assertTrue(post.body.readUtf8().contains("\"proposal\":\"Ship it?\""))
        }

    @Test
    fun `cancel posts to the cancel route and get run hits detail`() =
        runTest {
            server.enqueue(json("""{"id":"r1","cancelled":true}"""))
            server.enqueue(json(runJson))
            assertTrue(transport.councilStopRun("r1").isSuccess)
            assertEquals("Yes", transport.councilGetRun("r1").getOrThrow().consensus)
            val cancel = server.takeRequest()
            assertEquals("POST", cancel.method)
            assertEquals("/api/council/runs/r1/cancel", cancel.path)
            val get = server.takeRequest()
            assertEquals("/api/council/runs/r1", get.path)
            assertEquals("Bearer secret-token", get.getHeader("Authorization"))
        }

    @Test
    fun `run events stream parses every frame`() =
        runTest {
            server.enqueue(
                sse(
                    "event: hello\ndata: {}\nid: 0\n\n" +
                        "event: run_started\ndata: {\"run_id\":\"r1\",\"mode\":\"quick\",\"personas\":[\"a\"],\"rounds_total\":1}\nid: 1\n\n" +
                        "event: persona_response\ndata: {\"run_id\":\"r1\",\"round\":1,\"persona\":\"a\",\"text\":\"A1\"}\nid: 2\n\n",
                ),
            )
            val evs = transport.councilRunEvents("r1").toList()
            assertEquals(listOf("hello", "run_started", "persona_response"), evs.map { it.type })
            assertEquals("A1", evs[2].text)
            val req = server.takeRequest()
            assertEquals("/api/council/runs/r1/events", req.path)
            assertTrue(req.getHeader("Accept").orEmpty().startsWith("text/event-stream"))
        }

    @Test
    fun `watcher streams live state then reconciles with the run detail`() =
        runTest {
            server.enqueue(
                sse(
                    "event: hello\ndata: {}\nid: 0\n\n" +
                        "event: round_started\ndata: {\"run_id\":\"r1\",\"round\":1}\nid: 1\n\n" +
                        "event: persona_response\ndata: {\"run_id\":\"r1\",\"round\":1,\"persona\":\"a\",\"text\":\"A1\"}\nid: 2\n\n" +
                        "event: run_completed\ndata: {\"run_id\":\"r1\",\"consensus\":\"Yes\",\"dissent\":\"\"}\nid: 3\n\n",
                ),
            )
            server.enqueue(json("""{"error":"run not found"}""", code = 404)) // on hello: not persisted yet
            server.enqueue(json(runJson)) // after run_completed
            val states =
                transport.watchCouncilRun(CouncilLiveState(runId = "r1", proposal = "Ship it?", mode = "quick", personas = listOf("a", "b")))
                    .toList()
            val last = states.last()
            assertEquals(CouncilLivePhase.COMPLETED, last.phase)
            assertEquals("Yes", last.consensus)
            assertEquals(listOf("A1", "B1"), last.rounds.single().replies.map { it.text })
            assertTrue(states.any { s -> s.rounds.any { r -> r.replies.any { it.text == "A1" } } && !s.isTerminal })
        }

    @Test
    fun `watcher ends on hello when the run already finished`() =
        runTest {
            server.enqueue(sse("event: hello\ndata: {}\nid: 0\n\n"))
            server.enqueue(json(runJson))
            val last = transport.watchCouncilRun(CouncilLiveState(runId = "r1")).toList().last()
            assertEquals(CouncilLivePhase.COMPLETED, last.phase)
            assertEquals("Ship it?", last.proposal)
        }

    @Test
    fun `watcher polls the detail when the stream drops`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(json("""{"error":"x"}""", code = 404))
            server.enqueue(json(runJson))
            val last = transport.watchCouncilRun(CouncilLiveState(runId = "r1"), pollIntervalMs = 10L).toList().last()
            assertEquals(CouncilLivePhase.COMPLETED, last.phase)
        }
}
