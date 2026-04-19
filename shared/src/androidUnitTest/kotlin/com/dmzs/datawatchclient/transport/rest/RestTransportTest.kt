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
 * Exercises [RestTransport] against a local [MockWebServer]. Every failure mode
 * in [TransportError] gets a dedicated test so UI banner selection stays
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

    /** Helper — MockResponse has no default Content-Type, so Ktor's
     *  ContentNegotiation skips JSON deserialization without this. */
    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    @Test
    fun noTokenProviderOmitsAuthorizationHeader() = runTest {
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(RestTransport.DefaultJson) }
            expectSuccess = true
        }
        val authless = RestTransport(transport.profile, client, tokenProvider = null)
        server.enqueue(jsonResponse("""{"ok":true}"""))
        val res = authless.ping()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals(null, sent.getHeader("Authorization"))
    }

    @Test
    fun pingSucceedsOn200() = runTest {
        server.enqueue(jsonResponse("""{"ok":true}"""))
        val res = transport.ping()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("/api/health", sent.path)
        assertEquals("Bearer secret-token", sent.getHeader("Authorization"))
    }

    @Test
    fun listSessionsDeserializesHappyPath() = runTest {
        // Wire format matches the parent datawatch openapi.yaml Session schema:
        // bare JSON array, RFC3339 string timestamps, fields task / hostname /
        // created_at / updated_at (NOT task_summary / hostname_prefix / *_ts).
        server.enqueue(
            jsonResponse(
                """
                [
                  {"id":"a3f2","state":"running","task":"fix bug",
                   "hostname":"laptop","created_at":"2024-11-14T22:13:20Z",
                   "updated_at":"2024-11-14T22:14:20Z"}
                ]
                """.trimIndent(),
            ),
        )
        val res = transport.listSessions()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
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
        server.enqueue(jsonResponse("""{"ok":true}"""))
        val res = transport.replyToSession("a3f2", "continue")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/sessions/reply", sent.path)
        val body = sent.body.readUtf8()
        assertTrue(body.contains("\"session_id\":\"a3f2\""), body)
        assertTrue(body.contains("\"text\":\"continue\""), body)
    }

    @Test
    fun startSessionReturnsIdFromResponse() = runTest {
        server.enqueue(jsonResponse("""{"session_id":"b4c9","state":"new"}"""))
        val res = transport.startSession("upgrade deps")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        assertEquals("b4c9", res.getOrThrow())
    }

    @Test
    fun registerDeviceSendsCorrectPayloadAndParsesId() = runTest {
        server.enqueue(jsonResponse("""{"device_id":"dev-9f2"}"""))
        val res = transport.registerDevice(
            deviceToken = "fcm-tok-abc",
            kind = com.dmzs.datawatchclient.transport.DeviceKind.Fcm,
            appVersion = "0.3.0",
            platform = com.dmzs.datawatchclient.transport.DevicePlatform.Android,
            profileHint = "primary",
        )
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        assertEquals("dev-9f2", res.getOrThrow())
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/devices/register", sent.path)
        val body = sent.body.readUtf8()
        // Wire format must match parent v3.0.0 internal/server/devices.go
        assertTrue(body.contains("\"device_token\":\"fcm-tok-abc\""), body)
        assertTrue(body.contains("\"kind\":\"fcm\""), body)
        assertTrue(body.contains("\"app_version\":\"0.3.0\""), body)
        assertTrue(body.contains("\"platform\":\"android\""), body)
        assertTrue(body.contains("\"profile_hint\":\"primary\""), body)
    }

    @Test
    fun unregisterDeviceCallsDeleteWithId() = runTest {
        server.enqueue(jsonResponse("""{"status":"deleted"}"""))
        val res = transport.unregisterDevice("dev-9f2")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("DELETE", sent.method)
        assertEquals("/api/devices/dev-9f2", sent.path)
    }

    @Test
    fun statsDeserializesAllFields() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"cpu_pct":23.5,"mem_pct":41.2,"sessions_total":4,"sessions_running":2,
                 "sessions_waiting":1,"uptime_seconds":90123}
                """.trimIndent(),
            ),
        )
        val res = transport.stats()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val stats = res.getOrThrow()
        assertEquals(23.5, stats.cpuPct!!, 0.001)
        assertEquals(4, stats.sessionsTotal)
        assertEquals(2, stats.sessionsRunning)
        assertEquals(1, stats.sessionsWaiting)
        assertEquals(90123L, stats.uptimeSeconds)
    }
}
