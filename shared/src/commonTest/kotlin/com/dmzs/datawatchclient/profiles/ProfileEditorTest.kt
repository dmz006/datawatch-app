package com.dmzs.datawatchclient.profiles

import com.dmzs.datawatchclient.transport.SecretMask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileEditorTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val stored =
        obj(
            """
            {"name":"my-proj","description":"d","git":{"provider":"github","url":"https://x/y","auto_pr":true},
            "image_pair":{"agent":"agent-opencode","sidecar":"lang-go"},
            "env":{"ANTHROPIC_API_KEY":"sk-live-123","GITHUB_TOKEN":"${'$'}{secret:gh}","MODEL":"qwen3:8b"},
            "memory":{"mode":"shared","shared_with":["a","b"]},"idle_timeout":0,
            "allow_spawn_children":true,"spawn_budget_total":10,
            "agent_settings":{"claude_auth_key_secret":"anthropic","opencode_models":["m1","m2"]},
            "skills":["s1"],"post_task_hooks":["make fmt"],"created_at":"2026-10-05T00:00:00Z"}
            """,
        )

    @Test
    fun workingDocMasksLiteralSecretsOnly() {
        val doc = ProfileEditor.workingDoc("project", stored)
        val env = doc["env"]!!.jsonObject
        assertEquals(SecretMask.PLACEHOLDER, (env["ANTHROPIC_API_KEY"] as JsonPrimitive).content)
        assertEquals("\${secret:gh}", (env["GITHUB_TOKEN"] as JsonPrimitive).content)
        assertEquals("qwen3:8b", (env["MODEL"] as JsonPrimitive).content)
        // Secret *names* stay visible.
        assertEquals("anthropic", ProfileForm.valuesFrom("project", doc)["agent_settings.claude_auth_key_secret"])
        val yaml = ProfileEditor.toYaml("project", doc, ProfileForm.valuesFrom("project", doc))
        assertFalse(yaml.contains("sk-live-123"))
    }

    @Test
    fun formValuesReflectPwaFields() {
        val v = ProfileForm.valuesFrom("project", stored)
        assertEquals("my-proj", v["name"])
        assertEquals("https://x/y", v["git.url"])
        assertEquals("agent-opencode", v["image_pair.agent"])
        assertEquals("shared", v["memory.mode"])
        assertEquals("a, b", v["memory.shared_with"])
        assertEquals("true", v["allow_spawn_children"])
        assertEquals("10", v["spawn_budget_total"])
        assertEquals("", v["spawn_budget_per_minute"])
        assertEquals("m1, m2", v["agent_settings.opencode_models"])
        assertEquals("s1", v["skills"])
    }

    @Test
    fun untouchedFormIsLossless() {
        val doc = ProfileEditor.workingDoc("project", stored)
        val applied = ProfileForm.apply("project", doc, ProfileForm.valuesFrom("project", doc))
        assertEquals(doc, applied)
        val c = obj("""{"name":"c","kind":"docker","shared_volumes":[{"name":"v","mount_path":"/v"}],"creds_ref":{"provider":"file","key":"k"}}""")
        assertEquals(c, ProfileForm.apply("cluster", c, ProfileForm.valuesFrom("cluster", c)))
    }

    @Test
    fun formEditsOverlayAndPreserveUnknownKeys() {
        val doc = ProfileEditor.workingDoc("project", stored)
        val values =
            ProfileForm.valuesFrom("project", doc) +
                mapOf(
                    "git.branch" to "dev",
                    "memory.shared_with" to "a, c ,, d",
                    "spawn_budget_per_minute" to "2",
                    "skills" to "",
                    "description" to "",
                )
        val out = ProfileForm.apply("project", doc, values)
        assertEquals("dev", ProfileForm.get(out, "git.branch")!!.let { (it as JsonPrimitive).content })
        assertEquals(true, ProfileForm.get(out, "git.auto_pr")!!.let { (it as JsonPrimitive).content == "true" })
        assertEquals("[\"a\",\"c\",\"d\"]", ProfileForm.get(out, "memory.shared_with").toString())
        assertEquals("2", ProfileForm.get(out, "spawn_budget_per_minute").toString())
        assertEquals("[]", out["skills"].toString())
        assertEquals("\"\"", out["description"].toString())
        assertEquals(stored["post_task_hooks"], out["post_task_hooks"])
        assertEquals(doc["env"], out["env"])
    }

    @Test
    fun newProfileDefaultsMatchPwa() {
        val doc = ProfileEditor.workingDoc("project", null)
        val v = ProfileForm.valuesFrom("project", doc)
        assertEquals("agent-claude", v["image_pair.agent"])
        assertEquals("", v["image_pair.sidecar"])
        assertEquals("sync-back", v["memory.mode"])
        assertEquals("", v["git.provider"])
        assertEquals("false", v["allow_spawn_children"])
        assertEquals("k8s", ProfileForm.valuesFrom("cluster", ProfileEditor.workingDoc("cluster", null))["kind"])
        val errs = ProfileForm.validate("project", ProfileForm.apply("project", doc, v))
        assertTrue("name is required" in errs)
        assertTrue("git.url is required" in errs)
    }

    @Test
    fun validationMirrorsServer() {
        fun errs(
            kind: String,
            json: String,
        ) = ProfileForm.validate(kind, obj(json))
        assertEquals(emptyList(), errs("project", """{"name":"ok-1","git":{"url":"u"},"image_pair":{"agent":"agent-claude"}}"""))
        assertTrue(errs("project", """{"name":"Bad_Name","git":{"url":"u"},"image_pair":{"agent":"agent-claude"}}""").single().contains("dns label"))
        assertTrue(errs("project", """{"name":"p","git":{"url":"u"},"image_pair":{"agent":"agent-x","sidecar":"lang-cobol"}}""").size == 2)
        assertTrue(
            errs("project", """{"name":"p","git":{"url":"u"},"image_pair":{"agent":"agent-claude"},"spawn_budget_total":3}""")
                .single().contains("allow_spawn_children"),
        )
        assertTrue(
            errs("project", """{"name":"p","git":{"url":"u"},"image_pair":{"agent":"agent-claude"},"spawn_budget_total":"x"}""")
                .single().contains("non-negative"),
        )
        assertEquals(emptyList(), errs("cluster", """{"name":"c","kind":"docker"}"""))
        assertTrue(errs("cluster", """{"name":"c","kind":"k8s"}""").single().contains("context or endpoint"))
        assertTrue(errs("cluster", """{"name":"c","kind":"nomad"}""").single().contains("docker|k8s|cf"))
    }

    @Test
    fun buildBodyRestoresSecretsAndKeepsName() {
        val doc = ProfileEditor.workingDoc("project", stored)
        val values = ProfileForm.valuesFrom("project", doc) + ("description" to "new")
        val body = ProfileEditor.buildBody("project", doc, values, false, "", "my-proj", stored)
        assertEquals("sk-live-123", (body["env"]!!.jsonObject["ANTHROPIC_API_KEY"] as JsonPrimitive).content)
        assertEquals("new", (body["description"] as JsonPrimitive).content)

        // YAML view: same document, round-tripped through text, renamed name ignored on edit.
        val yaml = ProfileEditor.toYaml("project", doc, values).replace("name: my-proj", "name: other")
        val fromYaml = ProfileEditor.buildBody("project", doc, values, true, yaml, "my-proj", stored)
        assertEquals(body, fromYaml)
    }

    @Test
    fun yamlErrorsAreInlineMessages() {
        assertEquals(null, ProfileEditor.yamlError("name: p\n"))
        assertTrue(ProfileEditor.yamlError("name: p\nname: q\n")!!.contains("Line 2"))
        assertEquals("YAML body is empty", ProfileEditor.yamlError("# nothing\n"))
        assertTrue(ProfileEditor.yamlError("- a\n- b\n")!!.contains("mapping"))
    }

    @Test
    fun placeholderWithoutStoredValueIsRejected() {
        val doc = obj("""{"name":"p","git":{"url":"u"},"image_pair":{"agent":"agent-claude"},"env":{"NEW_TOKEN":"${SecretMask.PLACEHOLDER}"}}""")
        val e =
            assertFailsWith<IllegalArgumentException> {
                ProfileEditor.buildBody("project", doc, emptyMap(), false, "", null, null)
            }
        assertTrue(e.message!!.contains("env.NEW_TOKEN"))
    }

    @Test
    fun unknownSelectValuesAreOffered() {
        val f = ProfileForm.fields("project").first { it.key == "image_pair.sidecar" }
        assertEquals(f.options, ProfileForm.optionsFor(f, "lang-go"))
        assertEquals(f.options + "custom", ProfileForm.optionsFor(f, "custom"))
    }
}
