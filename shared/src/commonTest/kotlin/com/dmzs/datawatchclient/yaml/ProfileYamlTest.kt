package com.dmzs.datawatchclient.yaml

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileYamlTest {
    private fun roundTrip(el: JsonElement) {
        val yaml = ProfileYaml.stringify(el)
        assertEquals(el, ProfileYaml.parse(yaml), "round trip failed for YAML:\n$yaml")
    }

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test
    fun realisticProjectProfileRoundTrips() {
        roundTrip(
            json(
                """
                {"name":"my-proj","description":"Main repo: backend","git":{"provider":"github",
                "url":"https://github.com/user/repo","branch":"main","auto_pr":true},
                "image_pair":{"agent":"agent-claude","sidecar":"lang-go"},
                "env":{"FEATURE_X":"on","MODEL":"qwen3:8b","EMPTY":""},
                "memory":{"mode":"sync-back","namespace":"project-my-proj","shared_with":["a","b"]},
                "idle_timeout":0,"allow_spawn_children":false,"spawn_budget_total":10,
                "post_task_hooks":["make fmt","go test ./..."],
                "agent_settings":{"opencode_ollama_url":"http://ollama.local:11434","opencode_models":[]},
                "skills":[],"created_at":"2026-10-05T12:00:00Z"}
                """,
            ),
        )
    }

    @Test
    fun clusterProfileWithListOfMapsRoundTrips() {
        roundTrip(
            json(
                """
                {"name":"test-k8s","kind":"k8s","context":"testing","namespace":"default",
                "default_resources":{},"trusted_cas":["-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n"],
                "creds_ref":{"provider":"file","key":"/etc/creds"},
                "shared_volumes":[{"name":"cache","mount_path":"/cache","read_only":true,
                  "nfs":{"server":"nfs.local","path":"/export"}},{"name":"data","mount_path":"/data","pvc":"data-pvc"}]}
                """,
            ),
        )
    }

    @Test
    fun scalarsAndQuotingEdgeCasesRoundTrip() {
        val strings =
            listOf(
                "", " leading", "trailing ", "a: b", "key:", "x #y", "#hash", "- dash", "?q", "[x]", "{y}",
                "true", "False", "yes", "no", "null", "~", "123", "-4.5", "1e3", "0x1F", "\"quoted\"",
                "it's", "back\\slash", "tab\there", "colon:inside", "http://h:1/p?q=1#f", "ünïcødé ✓",
                "*star", "&amp", "!bang", "%pct", "@at", "`tick`", "|pipe", ">gt", "---", "...", "a,b",
                "line1\nline2", "ends with newline\n", "two trailing\n\n", "\n starts with newline",
                "  indented\nsecond", "crlf\r\nline", "café: au lait",
            )
        strings.forEach { roundTrip(JsonObject(mapOf("v" to JsonPrimitive(it)))) }
        roundTrip(JsonArray(strings.map { JsonPrimitive(it) }))
    }

    @Test
    fun numbersBooleansNullRoundTrip() {
        roundTrip(
            json("""{"i":0,"n":-42,"big":9007199254740993,"f":1.5,"t":true,"fa":false,"z":null}"""),
        )
    }

    @Test
    fun emptyCollectionsAndNestingRoundTrip() {
        roundTrip(json("""{"a":{},"b":[],"c":{"d":{"e":[]}},"f":[[],{},[1,[2,3]],{"g":[{"h":null}]}]}"""))
        roundTrip(json("""[]"""))
        roundTrip(json("""{}"""))
        roundTrip(json("""[[1,2],[3]]"""))
        roundTrip(json("""[{"a":"x\ny","b":[1]},"plain"]"""))
    }

    @Test
    fun oddKeysRoundTrip() {
        roundTrip(json("""{"with space":1,"a:b":2,"#c":3,"":4,"ANTHROPIC_API_KEY":"x","dotted.key":5,"-dash":6}"""))
    }

    @Test
    fun topLevelScalarsRoundTrip() {
        listOf<JsonElement>(JsonPrimitive("hi"), JsonPrimitive(3), JsonNull, JsonPrimitive("multi\nline")).forEach { roundTrip(it) }
    }

    @Test
    fun stringifyIsReadableBlockYaml() {
        val yaml =
            ProfileYaml.stringify(
                json("""{"name":"p","git":{"url":"https://x/y"},"tags":["a","b"],"vols":[{"name":"c","ro":true}]}"""),
            )
        assertEquals(
            """
            name: p
            git:
              url: https://x/y
            tags:
              - a
              - b
            vols:
              - name: c
                ro: true

            """.trimIndent(),
            yaml,
        )
    }

    @Test
    fun parsesHandWrittenYamlWithCommentsAndStyles() {
        val text =
            """
            # Project profile
            ---
            name: my-proj   # trailing comment
            description: 'It''s "fine"'
            git:
                url: https://github.com/user/repo
                branch: "main"
            image_pair: {agent: agent-claude, sidecar: lang-go}
            memory:
              shared_with: [a, "b c", 'd']
            skills:
            - test-first
            - go-style
            env:
              EMPTY:
              TILDE: ~
              NUM: 007
              SCRIPT: |
                echo hi
                # not a comment
                echo bye
              FOLDED: >-
                one
                two

                three
            """.trimIndent()
        val expected =
            json(
                """
                {"name":"my-proj","description":"It's \"fine\"",
                "git":{"url":"https://github.com/user/repo","branch":"main"},
                "image_pair":{"agent":"agent-claude","sidecar":"lang-go"},
                "memory":{"shared_with":["a","b c","d"]},
                "skills":["test-first","go-style"],
                "env":{"EMPTY":null,"TILDE":null,"NUM":7,"SCRIPT":"echo hi\n# not a comment\necho bye\n","FOLDED":"one two\nthree"}}
                """,
            )
        assertEquals(expected, ProfileYaml.parse(text))
    }

    @Test
    fun blockScalarChompingVariants() {
        val text = "a: |-\n  x\n\nb: |+\n  y\n\nc: |\n  z\n\n\nd: x\n"
        val o = ProfileYaml.parse(text) as JsonObject
        assertEquals("x", (o["a"] as JsonPrimitive).content)
        assertEquals("y\n\n", (o["b"] as JsonPrimitive).content)
        assertEquals("z\n", (o["c"] as JsonPrimitive).content)
        assertEquals("x", (o["d"] as JsonPrimitive).content)
    }

    @Test
    fun jsonInputIsAccepted() {
        val text = "{\n  \"name\": \"p\",\n  \"git\": {\"url\": \"u\"}\n}"
        assertEquals(json("""{"name":"p","git":{"url":"u"}}"""), ProfileYaml.parse(text))
    }

    @Test
    fun emptyDocumentIsNull() {
        assertEquals(JsonNull, ProfileYaml.parse("# only a comment\n\n"))
        assertEquals(JsonNull, ProfileYaml.parse(""))
    }

    @Test
    fun crlfInputParses() {
        assertEquals(json("""{"a":"b","c":{"d":1}}"""), ProfileYaml.parse("a: b\r\nc:\r\n  d: 1\r\n"))
    }

    @Test
    fun errorsReportTheLine() {
        fun lineOf(text: String): Int = assertFailsWith<YamlParseException> { ProfileYaml.parse(text) }.line
        assertEquals(4, lineOf("a: 1\nb:\n   c: 2\n  d: 3\n"))
        assertEquals(2, lineOf("a: 1\na: 2\n"))
        assertEquals(1, lineOf("a: \"unterminated\n"))
        assertEquals(2, lineOf("a: 1\n\tb: 2\n"))
        assertEquals(1, lineOf("a: [1, 2\n"))
        assertEquals(2, lineOf("a: 1\njust text\n"))
        assertEquals(1, lineOf("a: *ref\n"))
        val e = assertFailsWith<YamlParseException> { ProfileYaml.parse("x: 1\ny: \"bad\" trailing\n") }
        assertTrue(e.message!!.startsWith("Line 2:"), e.message)
    }
}
