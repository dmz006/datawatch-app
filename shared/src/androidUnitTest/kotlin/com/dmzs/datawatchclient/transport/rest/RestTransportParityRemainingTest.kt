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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MockWebServer coverage for the `// ---- parity-android-remaining ----` transport block. */
class RestTransportParityRemainingTest {
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
    fun getSessionResponseReadsResponseField() =
        runTest {
            server.enqueue(json("""{"response":"## Done\nall good"}"""))
            val res = transport.getSessionResponse("host-ab12")
            assertEquals("## Done\nall good", res.getOrThrow())
            val req = server.takeRequest()
            assertEquals("GET", req.method)
            assertEquals("/api/sessions/response?id=host-ab12", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
        }

    @Test
    fun getSessionResponseMissingFieldIsEmpty() =
        runTest {
            server.enqueue(json("""{}"""))
            assertEquals("", transport.getSessionResponse("x").getOrThrow())
        }

    @Test
    fun getSessionResponseSurfacesErrors() =
        runTest {
            server.enqueue(json("""{"error":"nope"}""", code = 404))
            assertTrue(transport.getSessionResponse("x").isFailure)
        }

    @Test
    fun runNamedSessionGuardrailPostsName() =
        runTest {
            server.enqueue(json("""{"guardrail":"secrets-scan","outcome":"pass","summary":"clean"}"""))
            val res = transport.runNamedSessionGuardrail("host-ab12", "secrets-scan")
            val obj = res.getOrThrow()
            assertEquals("\"pass\"", obj["outcome"].toString())
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/sessions/host-ab12/guardrail", req.path)
            assertTrue(req.body.readUtf8().contains("\"name\":\"secrets-scan\""))
        }

    @Test
    fun approveGuardrailVerdictReturnsUnblockedFlag() =
        runTest {
            server.enqueue(json("""{"session_unblocked":true}"""))
            assertTrue(transport.approveGuardrailVerdict("host-ab12", "sast scan").getOrThrow())
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/sessions/host-ab12/guardrail/sast%20scan/approve", req.path)
            assertEquals("{}", req.body.readUtf8())
        }

    @Test
    fun approveGuardrailVerdictWithoutFlagIsFalse() =
        runTest {
            server.enqueue(json("""{"ok":true}"""))
            assertFalse(transport.approveGuardrailVerdict("s", "g").getOrThrow())
        }

    @Test
    fun runPrdRulesCheckPostsAndParsesObject() =
        runTest {
            server.enqueue(json("""{"verdict":"pass","violations":[]}"""))
            val obj = transport.runPrdRulesCheck("prd-1").getOrThrow()
            assertEquals("\"pass\"", obj["verdict"].toString())
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/autonomous/prds/prd-1/scan/rules", req.path)
        }

    @Test
    fun runPrdRulesCheckEmptyBodyIsEmptyObject() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200))
            assertTrue(transport.runPrdRulesCheck("p").getOrThrow().isEmpty())
        }
}
