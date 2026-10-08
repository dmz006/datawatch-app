package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.rest.RestTransport
import com.dmzs.datawatchclient.transport.ws.SessionsHub
import com.dmzs.datawatchclient.transport.ws.WebSocketTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #236.9 — a proxied remote end to end against a real HTTP/WS server
 * (MockWebServer standing in for the parent datawatch): discovery through
 * `/api/servers`, REST through `/api/proxy/<name>/api/…`, the authenticated
 * reachability probe (never `/api/health`), the connection-status monitor on
 * 401 vs 200, and the WebSocket at `/api/proxy/<name>/ws` routing its
 * `sessions` frame under the virtual profile id.
 */
class ProxiedRemoteEndToEndTest {
    private lateinit var server: MockWebServer
    private lateinit var parent: ServerProfile
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** Remote "demo" answers 200 when true, 401 (no valid token on the parent) when false. */
    @Volatile private var remoteAuthorized: Boolean = true

    private val sessionsJson =
        """[{"id":"r1","state":"running","task":"remote task","hostname":"demo",""" +
            """"created_at":"2026-10-08T10:00:00Z","updated_at":"2026-10-08T10:01:00Z"}]"""

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    return when {
                        path == "/api/servers" ->
                            json(
                                """{"servers":[{"name":"local","url":"x","enabled":true},""" +
                                    """{"name":"demo","label":"Demo box","url":"https://demo.invalid","enabled":true}]}""",
                            )
                        path == "/api/proxy/demo/api/sessions" ->
                            if (remoteAuthorized) json(sessionsJson) else MockResponse().setResponseCode(401).setBody("unauthorized")
                        path == "/api/proxy/demo/api/health" -> json("""{"status":"ok"}""")
                        path == "/api/proxy/demo/ws" ->
                            MockResponse().withWebSocketUpgrade(
                                object : WebSocketListener() {
                                    override fun onOpen(
                                        webSocket: WebSocket,
                                        response: Response,
                                    ) {
                                        webSocket.send("""{"type":"sessions","data":{"sessions":$sessionsJson}}""")
                                    }
                                },
                            )
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        server.start()
        parent =
            ServerProfile(
                id = "parent-1",
                displayName = "workstation",
                baseUrl = server.url("/").toString().trimEnd('/'),
                bearerTokenRef = "dw.profile.parent-1",
                reachabilityProfileId = "lan",
                createdTs = 0L,
            )
    }

    @AfterTest
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun restClient(): HttpClient =
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(RestTransport.DefaultJson) }
            expectSuccess = true
        }

    private fun rest(profile: ServerProfile) = RestTransport(profile, restClient()) { "parent-token" }

    private suspend fun discoverDemo(): ServerProfile {
        val registry = ProxiedServersRegistry { p -> rest(p).listRemoteServers() }
        assertTrue(registry.refresh(listOf(parent)))
        val virtual = registry.virtualProfiles()
        assertEquals(listOf("parent-1::proxy::demo"), virtual.map { it.id }, "the reserved 'local' entry is skipped")
        return virtual.single()
    }

    @Test
    fun `REST goes through api proxy name with the parent's token`() =
        runBlocking {
            val demo = discoverDemo()
            assertEquals("workstation › Demo box", demo.displayName)
            val sessions = rest(demo).listSessions().getOrThrow()
            assertEquals(listOf("r1"), sessions.map { it.id })
            assertEquals(demo.id, sessions.single().serverProfileId)
            val req = requests.last { it.path == "/api/proxy/demo/api/sessions" }
            assertEquals("Bearer parent-token", req.getHeader("Authorization"))
        }

    @Test
    fun `reachability probe of a proxied remote is authenticated, never api health`() =
        runBlocking {
            val demo = discoverDemo()
            requests.clear()
            assertTrue(rest(demo).ping().isSuccess)
            assertEquals(listOf("/api/proxy/demo/api/sessions"), requests.map { it.path })

            remoteAuthorized = false
            val failed = rest(demo).ping()
            assertTrue(failed.exceptionOrNull() is TransportError.Unauthorized, "got ${failed.exceptionOrNull()}")
            assertTrue(requests.none { it.path.orEmpty().endsWith("/api/health") })
        }

    @Test
    fun `connection status shows the auth error on 401 and clears once the remote answers`() =
        runBlocking {
            val demo = discoverDemo()
            remoteAuthorized = false
            val stored = CopyOnWriteArrayList<Session>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val monitor =
                    FederatedConnectionMonitor(
                        scope = scope,
                        probe = { rest(it).listSessions() },
                        onSessions = { _, list -> stored += list },
                        retryDelayMs = { 50L },
                    )
                monitor.onActiveProfile(demo)
                val err =
                    withTimeout(5_000) {
                        monitor.status.filter { it?.phase == FedConnPhase.ERROR }.first()
                    }!!
                assertTrue(err.authFailed)
                assertEquals("Demo box", err.serverName)
                assertEquals("parent-1", err.parentId)
                assertEquals("workstation", err.parentName)

                // The token gets fixed on the parent: the next probe clears the status.
                remoteAuthorized = true
                withTimeout(5_000) { monitor.status.filter { it == null }.first() }
                assertEquals(listOf("r1"), stored.map { it.id })
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `WebSocket opens at api proxy name ws and routes sessions under the virtual id`() =
        runBlocking {
            val demo = discoverDemo()
            val ws = WebSocketTransport(demo, createHttpClientWithWebSockets(), tokenProvider = { "parent-token" })
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val update =
                    scope.async {
                        SessionsHub.fullListFlow.filter { it.serverProfileId == demo.id }.first()
                    }
                ws.globalStream().launchIn(scope)
                val got = withTimeout(10_000) { update.await() }
                assertEquals(listOf("r1"), got.sessions.map { it.id })
                val upgrade = requests.first { it.path == "/api/proxy/demo/ws" }
                assertEquals("Bearer parent-token", upgrade.getHeader("Authorization"))
                assertFalse(requests.any { it.path == "/ws" }, "must not open the parent's own /ws")
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `switching back to the parent clears the status`() =
        runBlocking {
            val demo = discoverDemo()
            remoteAuthorized = false
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val monitor =
                    FederatedConnectionMonitor(scope, probe = { rest(it).listSessions() }, retryDelayMs = { 60_000L })
                monitor.onActiveProfile(demo)
                withTimeout(5_000) { monitor.status.filter { it?.phase == FedConnPhase.ERROR }.first() }
                monitor.onActiveProfile(parent)
                withTimeout(5_000) { monitor.status.filter { it == null }.first() }
                assertNull(monitor.status.value)
            } finally {
                scope.cancel()
            }
        }
}
