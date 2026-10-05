package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MockWebServer coverage for the `// ---- iOS-H ----` transport block + schedule / guardrail wire fixes. */
class RestTransportIosHTest {
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

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun docsTrustPendingEntriesParsesServerShape() =
        runTest {
            server.enqueue(
                json("""{"pending":[{"source":"plugin:foo","noticed_at":"2026-10-01T00:00:00Z","detail":"3 files"}],"count":1}"""),
            )
            val r = transport.docsTrustPendingEntries()
            assertTrue(r.isSuccess, "got ${r.exceptionOrNull()}")
            val list = r.getOrThrow()
            assertEquals(1, list.size)
            assertEquals("plugin:foo", list[0].source)
            assertEquals("3 files", list[0].detail)
            assertEquals("/api/docs/trust/pending", server.takeRequest().path)
        }

    @Test
    fun docsTrustedEntriesUsesGrantedByAsDetail() =
        runTest {
            server.enqueue(
                json("""{"trusted":[{"source":"core","granted_at":"2026-10-01T00:00:00Z"},{"source":"skill:x","granted_by":"operator"}]}"""),
            )
            val list = transport.docsTrustedEntries().getOrThrow()
            assertEquals(listOf("core", "skill:x"), list.map { it.source })
            assertEquals("", list[0].detail)
            assertEquals("operator", list[1].detail)
            assertEquals("/api/docs/trust", server.takeRequest().path)
        }

    @Test
    fun docsTrustDecidePostsSourcesArray() =
        runTest {
            server.enqueue(json("""{"added":1}"""))
            server.enqueue(json("""{"dismissed":1}"""))
            assertTrue(transport.docsTrustDecide(listOf("plugin:a", "skill:b"), accept = true).isSuccess)
            assertTrue(transport.docsTrustDecide(listOf("plugin:a"), accept = false).isSuccess)
            val accept = server.takeRequest()
            assertEquals("POST", accept.method)
            assertEquals("/api/docs/trust/accept", accept.path)
            val body = Json.parseToJsonElement(accept.body.readUtf8()) as JsonObject
            assertEquals(listOf("plugin:a", "skill:b"), body["sources"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("/api/docs/trust/dismiss", server.takeRequest().path)
        }

    @Test
    fun scheduleMapsCronExprAndSessionName() =
        runTest {
            server.enqueue(
                json(
                    """
                    [
                      {"id":"a","command":"status","cron_expr":"0 9 * * *","session_id":"h-1","session_name":"builder",
                       "schedule_name":"morning","state":"pending","created_at":"2026-10-01T00:00:00Z"},
                      {"id":"b","command":"go","type":"new_session","deferred_session":{"name":"nightly"},
                       "state":"pending","created_at":"2026-10-01T00:00:00Z"}
                    ]
                    """.trimIndent(),
                ),
            )
            val list = transport.listSchedules().getOrThrow()
            assertEquals("0 9 * * *", list[0].cron)
            assertEquals("builder", list[0].sessionName)
            assertEquals("morning", list[0].scheduleName)
            assertNull(list[0].deferredSessionName)
            assertEquals("new_session", list[1].type)
            assertEquals("nightly", list[1].deferredSessionName)
        }

    @Test
    fun guardrailLibraryReadsTypeAsKind() =
        runTest {
            server.enqueue(json("""[{"name":"sast","description":"Static analysis","type":"scan","scan_type":"sast"}]"""))
            val list = transport.listGuardrailLibrary().getOrThrow()
            assertEquals("scan", list[0].kind)
        }
}
