package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-task / per-story / per-Automaton verification (judge) LLM setters (server
 * v9.0.73, internal/server/autonomous.go `set_task_verification_llm` /
 * `set_story_verification_llm` / `set_verification_llm`). Bodies are presence-aware:
 * a null argument is omitted (server leaves the field unchanged), "" clears it.
 * Also covers the matching DTO fields on GET.
 */
class RestTransportVerificationLlmTest {
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

    private fun bodyOf(req: RecordedRequest): JsonObject = RestTransport.DefaultJson.parseToJsonElement(req.body.readUtf8()).jsonObject

    @Test
    fun `setTaskVerificationLlm posts task_id and all three fields`() =
        runTest {
            server.enqueue(json("""{"id":"a1"}"""))
            assertTrue(transport.setTaskVerificationLlm("a1", "t1", "ollama", "qwen3:8b", "low").isSuccess)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/autonomous/prds/a1/set_task_verification_llm", req.path)
            assertEquals("Bearer secret-token", req.getHeader("Authorization"))
            val body = bodyOf(req)
            assertEquals(JsonPrimitive("t1"), body["task_id"])
            assertEquals(JsonPrimitive("ollama"), body["verification_backend"])
            assertEquals(JsonPrimitive("qwen3:8b"), body["verification_model"])
            assertEquals(JsonPrimitive("low"), body["verification_effort"])
            assertEquals(JsonPrimitive("operator"), body["actor"])
            assertFalse("story_id" in body)
        }

    @Test
    fun `setStoryVerificationLlm posts story_id and empty strings clear`() =
        runTest {
            server.enqueue(json("""{"id":"a1"}"""))
            assertTrue(transport.setStoryVerificationLlm("a1", "s1", "", "", "").isSuccess)
            val req = server.takeRequest()
            assertEquals("/api/autonomous/prds/a1/set_story_verification_llm", req.path)
            val body = bodyOf(req)
            assertEquals(JsonPrimitive("s1"), body["story_id"])
            assertEquals(JsonPrimitive(""), body["verification_backend"])
            assertEquals(JsonPrimitive(""), body["verification_model"])
            assertEquals(JsonPrimitive(""), body["verification_effort"])
            assertFalse("task_id" in body)
        }

    @Test
    fun `setPrdVerificationLlm omits null fields so the server leaves them unchanged`() =
        runTest {
            server.enqueue(json("""{"id":"a 1"}"""))
            assertTrue(transport.setPrdVerificationLlm("a 1", "claude-code", null, null, actor = null).isSuccess)
            val req = server.takeRequest()
            assertEquals("/api/autonomous/prds/a%201/set_verification_llm", req.path)
            val body = bodyOf(req)
            assertEquals(setOf("verification_backend"), body.keys)
            assertEquals(JsonPrimitive("claude-code"), body["verification_backend"])
        }

    @Test
    fun `setter surfaces the server 400 for an unknown backend`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("unknown LLM \"nope\" — check /api/llms for valid names"))
            assertTrue(transport.setTaskVerificationLlm("a1", "t1", "nope", "", "").isFailure)
        }

    @Test
    fun `getPrd decodes verification overrides, verifier record and file drift`() =
        runTest {
            server.enqueue(
                json(
                    """{"id":"a1","status":"running","verification_backend":"claude-code","verification_model":"opus",""" +
                        """"verification_effort":"high","block_on_file_drift":true,"stories":[{"id":"s1","title":"S",""" +
                        """"verification_backend":"ollama","tasks":[{"id":"t1","title":"T","verification_effort":"low",""" +
                        """"foreign_file_drift":["a.go","b.go"],"verification":{"ok":true,"summary":"fine",""" +
                        """"verifier_backend":"ollama","verifier_model":"qwen3:8b","verifier_effort":"low","verifier_source":"story"}}]}]}""",
                ),
            )
            val prd = transport.getPrd("a1").getOrThrow()
            assertEquals("claude-code", prd.verificationBackend)
            assertEquals("opus", prd.verificationModel)
            assertEquals("high", prd.verificationEffort)
            assertEquals(true, prd.blockOnFileDrift)
            val story = prd.stories.single()
            assertEquals("ollama", story.verificationBackend)
            val task = story.tasks.single()
            assertEquals("low", task.verificationEffort)
            assertEquals(listOf("a.go", "b.go"), task.foreignFileDrift)
            val v = task.verification!!
            assertEquals("ollama", v.verifierBackend)
            assertEquals("qwen3:8b", v.verifierModel)
            assertEquals("low", v.verifierEffort)
            assertEquals("story", v.verifierSource)
        }
}
