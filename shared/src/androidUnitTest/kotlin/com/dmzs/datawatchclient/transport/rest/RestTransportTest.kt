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

    // ---- v0.11 session power-user parity (see docs/plans/2026-04-20-v0.11-session-power-user.md) ----

    @Test
    fun renameSessionPostsIdAndName() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.renameSession("a3f2", "nightly build")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/sessions/rename", sent.path)
        val body = sent.body.readUtf8()
        assertTrue(body.contains("\"id\":\"a3f2\""), body)
        assertTrue(body.contains("\"name\":\"nightly build\""), body)
    }

    @Test
    fun restartSessionReturnsSessionDomain() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"id":"a3f2","state":"running","task":"resume","hostname":"laptop",
                 "created_at":"2024-11-14T22:13:20Z","updated_at":"2024-11-14T22:30:00Z"}
                """.trimIndent(),
            ),
        )
        val res = transport.restartSession("a3f2")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val s = res.getOrThrow()
        assertEquals("a3f2", s.id)
        assertEquals(SessionState.Running, s.state)
        assertEquals("resume", s.taskSummary)
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/sessions/restart", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"id\":\"a3f2\""))
    }

    @Test
    fun deleteSessionPostsSingleId() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.deleteSession("a3f2")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/sessions/delete", sent.path)
        val body = sent.body.readUtf8()
        assertTrue(body.contains("\"id\":\"a3f2\""), body)
        // Single-id variant must NOT emit the ids array (server might reject the mixed shape).
        assertTrue(!body.contains("\"ids\""), body)
    }

    @Test
    fun deleteSessionsBulkPostsIdArray() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.deleteSessions(listOf("a3f2", "b4c9"))
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"ids\":[\"a3f2\",\"b4c9\"]"), body)
        assertTrue(!body.contains("\"id\":"), body)
    }

    @Test
    fun deleteSession404MapsToNotFound() = runTest {
        // Parent-confirmation gate: if the server predates the /api/sessions/delete
        // endpoint, transport must surface NotFound so the UI can grey out the button.
        server.enqueue(MockResponse().setResponseCode(404).setBody("unknown route"))
        val res = transport.deleteSession("a3f2")
        assertTrue(res.isFailure)
        assertTrue(
            res.exceptionOrNull() is TransportError.NotFound,
            "expected NotFound, got ${res.exceptionOrNull()}",
        )
    }

    @Test
    fun fetchCertReturnsRawBytes() = runTest {
        val pem = "-----BEGIN CERTIFICATE-----\nMIID...fake...=\n-----END CERTIFICATE-----\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/x-pem-file")
                .setBody(pem),
        )
        val res = transport.fetchCert()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        assertEquals(pem, res.getOrThrow().decodeToString())
        assertEquals("/api/cert", server.takeRequest().path)
    }

    @Test
    fun fetchCert404MapsToNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val res = transport.fetchCert()
        assertTrue(res.exceptionOrNull() is TransportError.NotFound, "${res.exceptionOrNull()}")
    }

    @Test
    fun setActiveBackendPostsName() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.setActiveBackend("ollama")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/api/backends/active", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"name\":\"ollama\""))
    }

    @Test
    fun setActiveBackend404MapsToNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val res = transport.setActiveBackend("ollama")
        assertTrue(res.exceptionOrNull() is TransportError.NotFound, "${res.exceptionOrNull()}")
    }

    @Test
    fun listAlertsReturnsListPlusUnreadCount() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "alerts": [
                    {"id":"al-1","type":"input_needed","severity":"warn",
                     "message":"Claude needs a choice","session_id":"a3f2",
                     "created_at":"2024-11-14T22:15:00Z","read":false},
                    {"id":"al-2","type":"error","severity":"error",
                     "message":"rate limit","created_at":"2024-11-14T22:18:00Z","read":true}
                  ],
                  "unread_count": 1
                }
                """.trimIndent(),
            ),
        )
        val res = transport.listAlerts()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val view = res.getOrThrow()
        assertEquals(2, view.alerts.size)
        assertEquals(1, view.unreadCount)
        val first = view.alerts.first()
        assertEquals("al-1", first.id)
        assertEquals(
            com.dmzs.datawatchclient.domain.AlertSeverity.Warning,
            first.severity,
        )
        assertEquals("a3f2", first.sessionId)
        assertEquals(false, first.read)
        // Second alert's severity maps "error" → AlertSeverity.Error
        assertEquals(
            com.dmzs.datawatchclient.domain.AlertSeverity.Error,
            view.alerts[1].severity,
        )
    }

    @Test
    fun markAlertReadSingleEmitsOnlyId() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.markAlertRead(alertId = "al-1")
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"id\":\"al-1\""), body)
        // `all` must be omitted when dismissing a single alert.
        assertTrue(!body.contains("\"all\""), body)
    }

    @Test
    fun markAlertReadAllEmitsAllTrue() = runTest {
        server.enqueue(jsonResponse("""{"status":"ok"}"""))
        val res = transport.markAlertRead(all = true)
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"all\":true"), body)
        assertTrue(!body.contains("\"id\""), body)
    }

    @Test
    fun markAlertReadRequiresIdOrAll() = runTest {
        // Callers must pass one or the other — guard is an IllegalArgumentException
        // inside the transport lambda; `request { }` wraps all throwables into
        // TransportError.Unreachable as the catch-all.
        val res = transport.markAlertRead()
        assertTrue(res.isFailure)
        val err = res.exceptionOrNull()
        assertTrue(
            err is TransportError.Unreachable,
            "expected Unreachable wrapper around IllegalArgumentException, got $err",
        )
    }

    @Test
    fun fetchInfoParsesAllFields() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {"hostname":"laptop","version":"3.0.0","llm_backend":"ollama",
                 "messaging_backend":"signal","session_count":4,
                 "server":{"host":"0.0.0.0","port":8443}}
                """.trimIndent(),
            ),
        )
        val res = transport.fetchInfo()
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        val info = res.getOrThrow()
        assertEquals("laptop", info.hostname)
        assertEquals("3.0.0", info.version)
        assertEquals("ollama", info.llmBackend)
        assertEquals("signal", info.messagingBackend)
        assertEquals(4, info.sessionCount)
        assertEquals("0.0.0.0", info.serverHost)
        assertEquals(8443, info.serverPort)
        assertEquals("/api/info", server.takeRequest().path)
    }

    @Test
    fun fetchOutputReturnsPlainTextAndPassesQueryParams() = runTest {
        val plain = "line 1\nline 2\nline 3\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/plain; charset=utf-8")
                .setBody(plain),
        )
        val res = transport.fetchOutput("a3f2", lines = 200)
        assertTrue(res.isSuccess, "expected success, got ${res.exceptionOrNull()}")
        assertEquals(plain, res.getOrThrow())
        val sent = server.takeRequest()
        assertEquals("/api/output?id=a3f2&n=200", sent.path)
    }
}
