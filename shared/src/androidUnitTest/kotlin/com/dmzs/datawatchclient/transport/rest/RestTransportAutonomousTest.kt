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

/**
 * MockWebServer tests for PRD lifecycle, channels, and backends endpoints
 * added in v0.38.0–v0.42.13. Complements [RestTransportTest] which covers
 * session + alert + schedule + file endpoints.
 */
class RestTransportAutonomousTest {
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

    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    // ── listPrds ─────────────────────────────────────────────────────────────

    @Test
    fun listPrdsDeserializesHappyPath() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """
                    {"prds":[
                      {"id":"prd-1","name":"refactor","title":"Refactor auth",
                       "status":"needs_review","depth":1,"is_template":false,
                       "stories":[{"id":"s1","title":"Plan","description":"do it",
                         "status":"awaiting_approval","files":["auth.go"],"files_touched":[]}]},
                      {"id":"prd-2","name":"template","status":"draft","depth":0,"is_template":true}
                    ]}
                    """.trimIndent(),
                ),
            )
            val res = transport.listPrds()
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val dto = res.getOrThrow()
            assertEquals(2, dto.prds.size)
            val first = dto.prds[0]
            assertEquals("prd-1", first.id)
            assertEquals("Refactor auth", first.title)
            assertEquals("needs_review", first.status)
            assertEquals(1, first.depth)
            assertEquals(false, first.isTemplate)
            assertEquals(1, first.stories.size)
            assertEquals("awaiting_approval", first.stories[0].status)
            assertEquals(listOf("auth.go"), first.stories[0].files)
            val second = dto.prds[1]
            assertTrue(second.isTemplate)
            val sent = server.takeRequest()
            assertEquals("GET", sent.method)
            assertEquals("/api/autonomous/prds", sent.path)
            assertEquals("Bearer secret-token", sent.getHeader("Authorization"))
        }

    @Test
    fun listPrdsReturnsEmptyListOnEmptyPayload() =
        runTest {
            server.enqueue(jsonResponse("""{"prds":[]}"""))
            val res = transport.listPrds()
            assertTrue(res.isSuccess)
            assertEquals(0, res.getOrThrow().prds.size)
        }

    // ── createPrd ────────────────────────────────────────────────────────────

    @Test
    fun createPrdPostsBodyAndReturnsId() =
        runTest {
            server.enqueue(jsonResponse("""{"id":"prd-new","status":"draft"}"""))
            val req =
                com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto(
                    name = "auth-refactor",
                    title = "Refactor auth module",
                    projectDir = "/code/auth",
                    backend = "claude-code",
                    effort = "thorough",
                )
            val res = transport.createPrd(req)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            assertEquals("prd-new", res.getOrThrow())
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/autonomous/prds", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"name\":\"auth-refactor\""), body)
            assertTrue(body.contains("\"title\":\"Refactor auth module\""), body)
            assertTrue(body.contains("\"project_dir\":\"/code/auth\""), body)
            assertTrue(body.contains("\"backend\":\"claude-code\""), body)
            assertTrue(body.contains("\"effort\":\"thorough\""), body)
        }

    @Test
    fun createPrdWithProjectProfileOmitsProjectDir() =
        runTest {
            server.enqueue(jsonResponse("""{"id":"prd-pp","status":"draft"}"""))
            val req =
                com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto(
                    name = "pp-prd",
                    projectProfile = "go-runner",
                    clusterProfile = "k8s-prod",
                )
            val res = transport.createPrd(req)
            assertTrue(res.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"project_profile\":\"go-runner\""), body)
            assertTrue(body.contains("\"cluster_profile\":\"k8s-prod\""), body)
            assertTrue(!body.contains("\"project_dir\""), body)
        }

    // ── prdAction ────────────────────────────────────────────────────────────

    @Test
    fun prdActionApprovePostsCorrectPath() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.prdAction("prd-1", "approve")
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/autonomous/prds/prd-1/approve", sent.path)
            assertEquals(0L, sent.bodySize)
        }

    @Test
    fun prdActionRejectSendsReasonBody() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    put("reason", kotlinx.serialization.json.JsonPrimitive("scope too broad"))
                }
            val res = transport.prdAction("prd-1", "reject", body)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("/api/autonomous/prds/prd-1/reject", sent.path)
            val sentBody = sent.body.readUtf8()
            assertTrue(sentBody.contains("\"reason\":\"scope too broad\""), sentBody)
        }

    @Test
    fun prdActionDecomposePostsNoBody() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.prdAction("prd-1", "decompose")
            assertTrue(res.isSuccess)
            val sent = server.takeRequest()
            assertEquals("/api/autonomous/prds/prd-1/decompose", sent.path)
            assertEquals(0L, sent.bodySize)
        }

    @Test
    fun prdActionSetLlmSendsBackendEffortModel() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    put("backend", kotlinx.serialization.json.JsonPrimitive("openai"))
                    put("effort", kotlinx.serialization.json.JsonPrimitive("high"))
                    put("model", kotlinx.serialization.json.JsonPrimitive("gpt-4o"))
                }
            val res = transport.prdAction("prd-1", "set_llm", body)
            assertTrue(res.isSuccess)
            val sentBody = server.takeRequest().body.readUtf8()
            assertTrue(sentBody.contains("\"backend\":\"openai\""), sentBody)
            assertTrue(sentBody.contains("\"effort\":\"high\""), sentBody)
            assertTrue(sentBody.contains("\"model\":\"gpt-4o\""), sentBody)
        }

    // ── editStory ────────────────────────────────────────────────────────────

    @Test
    fun editStoryPostsStoryIdAndNewFields() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res =
                transport.editStory(
                    prdId = "prd-1",
                    storyId = "s1",
                    newTitle = "Updated plan",
                    newDescription = "More detail here",
                )
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/autonomous/prds/prd-1/edit_story", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"story_id\":\"s1\""), body)
            assertTrue(body.contains("\"new_title\":\"Updated plan\""), body)
            assertTrue(body.contains("\"new_description\":\"More detail here\""), body)
        }

    @Test
    fun editStoryOmitsNullFields() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res =
                transport.editStory(prdId = "prd-1", storyId = "s1", newTitle = null, newDescription = null)
            assertTrue(res.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"story_id\":\"s1\""), body)
            assertTrue(!body.contains("\"new_title\""), body)
            assertTrue(!body.contains("\"new_description\""), body)
        }

    // ── editFiles ────────────────────────────────────────────────────────────

    @Test
    fun editFilesPostsStoryIdAndFileList() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res =
                transport.editFiles(
                    prdId = "prd-1",
                    storyId = "s1",
                    files = listOf("auth.go", "middleware.go"),
                )
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/autonomous/prds/prd-1/edit_files", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"story_id\":\"s1\""), body)
            assertTrue(body.contains("\"files\""), body)
            assertTrue(body.contains("auth.go"), body)
            assertTrue(body.contains("middleware.go"), body)
        }

    @Test
    fun editFilesWithEmptyListClearsAssociation() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.editFiles(prdId = "prd-1", storyId = "s1", files = emptyList())
            assertTrue(res.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"files\":[]"), body)
        }

    // ── patchPrd ─────────────────────────────────────────────────────────────

    @Test
    fun patchPrdSendsPatchWithProvidedFields() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.patchPrd(prdId = "prd-1", title = "New title", spec = "new spec")
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("PATCH", sent.method)
            assertEquals("/api/autonomous/prds/prd-1", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"title\":\"New title\""), body)
            assertTrue(body.contains("\"spec\":\"new spec\""), body)
        }

    @Test
    fun patchPrdWithOnlyTitleOmitsSpec() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.patchPrd(prdId = "prd-1", title = "Renamed", spec = null)
            assertTrue(res.isSuccess)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"title\":\"Renamed\""), body)
            assertTrue(!body.contains("\"spec\""), body)
        }

    // ── deletePrd ────────────────────────────────────────────────────────────

    @Test
    fun deletePrdSoftDeleteOmitsHardParam() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.deletePrd("prd-1", hard = false)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("DELETE", sent.method)
            // hard=false → no query param
            assertEquals("/api/autonomous/prds/prd-1", sent.path)
        }

    @Test
    fun deletePrdHardDeleteAddsQueryParam() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.deletePrd("prd-1", hard = true)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("DELETE", sent.method)
            assertEquals("/api/autonomous/prds/prd-1?hard=true", sent.path)
        }

    // ── listBackends ─────────────────────────────────────────────────────────

    @Test
    fun listBackendsDeserializesObjectArray() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"llm":[{"name":"claude-code","kind":"claude"},{"name":"openai","kind":"openai"}],"active":"claude-code"}""",
                ),
            )
            val res = transport.listBackends()
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val view = res.getOrThrow()
            assertEquals(listOf("claude-code", "openai"), view.llm)
            assertEquals("claude-code", view.active)
            assertEquals("/api/backends", server.takeRequest().path)
        }

    @Test
    fun listBackendsDeserializesLegacyStringArray() =
        runTest {
            server.enqueue(jsonResponse("""{"llm":["ollama","openai"]}"""))
            val res = transport.listBackends()
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val view = res.getOrThrow()
            assertEquals(listOf("ollama", "openai"), view.llm)
            assertEquals(null, view.active)
        }

    // ── listChannels ─────────────────────────────────────────────────────────

    @Test
    fun listChannelsDeserializesWrappedObject() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"channels":[{"id":"signal","type":"signal","enabled":true},{"id":"ntfy","type":"ntfy","enabled":false}]}""",
                ),
            )
            val res = transport.listChannels()
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val channels = res.getOrThrow()
            assertEquals(2, channels.size)
            assertEquals("signal", (channels[0]["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("/api/channels", server.takeRequest().path)
        }

    @Test
    fun listChannelsDeserializesBareArray() =
        runTest {
            server.enqueue(
                jsonResponse("""[{"id":"telegram","type":"telegram","enabled":true}]"""),
            )
            val res = transport.listChannels()
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            assertEquals(1, res.getOrThrow().size)
        }

    // ── createChannel ────────────────────────────────────────────────────────

    @Test
    fun createChannelPostsTypeIdEnabled() =
        runTest {
            server.enqueue(jsonResponse("""{"id":"ntfy","type":"ntfy","enabled":true}"""))
            val res = transport.createChannel(type = "ntfy", id = "ntfy", enabled = true)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/channels", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"type\":\"ntfy\""), body)
            assertTrue(body.contains("\"id\":\"ntfy\""), body)
            assertTrue(body.contains("\"enabled\":true"), body)
        }

    // ── deleteChannel ────────────────────────────────────────────────────────

    @Test
    fun deleteChannelSendsDeleteToChannelId() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.deleteChannel("signal")
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("DELETE", sent.method)
            assertEquals("/api/channels/signal", sent.path)
        }

    // ── setChannelEnabled ────────────────────────────────────────────────────

    @Test
    fun setChannelEnabledPatchesEnabledFlag() =
        runTest {
            server.enqueue(jsonResponse("", 204))
            val res = transport.setChannelEnabled("signal", enabled = false)
            assertTrue(res.isSuccess, "${res.exceptionOrNull()}")
            val sent = server.takeRequest()
            assertEquals("PATCH", sent.method)
            assertEquals("/api/channels/signal", sent.path)
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"enabled\":false"), body)
        }
}
