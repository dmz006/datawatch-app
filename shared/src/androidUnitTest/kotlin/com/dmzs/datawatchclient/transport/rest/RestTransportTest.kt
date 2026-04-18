package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.TransportError
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

/**
 * Exercises [RestTransport] against a local [MockWebServer]. Every failure
 * mode in [TransportError] gets a dedicated test so UI banner selection stays
 * deterministic across server-behavior changes.
 *
 * Runs as an Android JVM unit test (`./gradlew :shared:testDebugUnitTest`).
 */
class RestTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: RestTransport

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        val profile = ServerProfile(
            id = "srv-test",
            displayName = "test",
            baseUrl = server.url("/").toString().trimEnd('/'),
            bearerTokenRef = "dw.profile.srv-test",
            trustAnchorSha256 = null,
            reachabilityProfileId = "lan",
            createdTs = 0L,
        )
        val client = HttpClient(OkHttp) {
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
    fun pingSucceedsOn200() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val res = transport.ping()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("/api/health", sent.path)
        assertEquals("Bearer secret-token", sent.getHeader("Authorization"))
    }

    @Test
    fun listSessionsDeserializesHappyPath() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "sessions": [
                    {"id":"a3f2","state":"running","task_summary":"fix bug",
                     "hostname_prefix":"laptop","created_ts":1700000000000,
                     "last_activity_ts":1700000060000}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val res = transport.listSessions()
        assertTrue(res.isSuccess)
        val sessions = res.getOrThrow()
        assertEquals(1, sessions.size)
        val s = sessions.first()
        assertEquals("a3f2", s.id)
        assertEquals(SessionState.Running, s.state)
        assertEquals("laptop", s.hostnamePrefix)
        assertEquals("fix bug", s.taskSummary)
    }

    @Test
    fun unauthorizedMapsTo401Type() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val res = transport.listSessions()
        assertTrue(res.isFailure)
        assertTrue(
            res.exceptionOrNull() is TransportError.Unauthorized,
            "expected Unauthorized, got ${res.exceptionOrNull()}",
        )
    }

    @Test
    fun serverErrorMapsTo5xxType() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val res = transport.listSessions()
        assertTrue(res.isFailure)
        val err = res.exceptionOrNull()
        assertTrue(err is TransportError.ServerError, "expected ServerError, got $err")
        assertEquals(500, (err as TransportError.ServerError).status)
    }

    @Test
    fun rateLimitedMapsTo429Type() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val res = transport.listSessions()
        assertTrue(res.isFailure)
        assertTrue(
            res.exceptionOrNull() is TransportError.RateLimited,
            "expected RateLimited, got ${res.exceptionOrNull()}",
        )
    }

    @Test
    fun networkUnreachableMapsToUnreachable() = runTest {
        server.shutdown()
        val res = transport.listSessions()
        assertTrue(res.isFailure)
        assertTrue(
            res.exceptionOrNull() is TransportError.Unreachable,
            "expected Unreachable, got ${res.exceptionOrNull()}",
        )
    }

    @Test
    fun replyPostsExpectedBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val res = transport.replyToSession("a3f2", "continue")
        assertTrue(res.isSuccess)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/sessions/reply", sent.path)
        val body = sent.body.readUtf8()
        assertTrue(body.contains("\"session_id\":\"a3f2\""), body)
        assertTrue(body.contains("\"text\":\"continue\""), body)
    }

    @Test
    fun startSessionReturnsIdFromResponse() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"b4c9","state":"new"}""",
            ),
        )
        val res = transport.startSession("upgrade deps")
        assertTrue(res.isSuccess)
        assertEquals("b4c9", res.getOrThrow())
    }

    @Test
    fun statsDeserializesAllFields() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"cpu_pct":23.5,"mem_pct":41.2,"sessions_total":4,"sessions_running":2,
                 "sessions_waiting":1,"uptime_seconds":90123}
                """.trimIndent(),
            ),
        )
        val res = transport.stats()
        assertTrue(res.isSuccess)
        val stats = res.getOrThrow()
        assertEquals(23.5, stats.cpuPct!!, 0.001)
        assertEquals(4, stats.sessionsTotal)
        assertEquals(2, stats.sessionsRunning)
        assertEquals(1, stats.sessionsWaiting)
        assertEquals(90123L, stats.uptimeSeconds)
    }
}
