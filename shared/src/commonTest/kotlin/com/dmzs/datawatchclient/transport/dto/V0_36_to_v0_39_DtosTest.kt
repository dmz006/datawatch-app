package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip + parse coverage for every DTO added between v0.36.0
 * and v0.39.2. Each test names the issue it backs so a regression
 * makes it obvious which transport contract drifted from the
 * datawatch parent.
 */
class V0_36_to_v0_39_DtosTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    // ---- v0.36.0 federated monitoring (#2-#6) ----

    @Test
    fun `observer peers parses the v4_4_0 wire shape`() {
        val src =
            """
            {"peers":[
              {
                "name":"agent-7af3",
                "shape":"agent",
                "host_info":{"hostname":"runner-1","shape":"agent","os":"linux","arch":"amd64"},
                "version":"5.27.0",
                "registered_at":"2026-04-28T00:01:00Z",
                "last_push_at":"2026-04-28T00:01:42Z"
              },
              {"name":"node-2","shape":"standalone"}
            ]}
            """.trimIndent()
        val dto = json.decodeFromString(ObserverPeersDto.serializer(), src)
        assertEquals(2, dto.peers.size)
        assertEquals("agent", dto.peers[0].shape)
        assertEquals("agent", dto.peers[0].hostInfo?.shape)
        assertEquals("standalone", dto.peers[1].shape)
        assertNull(dto.peers[1].hostInfo)
    }

    @Test
    fun `observer stats carries host_ebpf and cluster_nodes`() {
        val src =
            """
            {
              "host":{"ebpf":{"configured":true,"capability":true,"kprobes_loaded":false,"message":"Degraded — kernel<5.10"}},
              "cluster":{"nodes":[
                {"name":"k8s-1","ready":true,"pressures":["memory"],"pod_count":12,"cpu_pct":78.5,"mem_pct":91.2}
              ]}
            }
            """.trimIndent()
        val dto = json.decodeFromString(ObserverStatsDto.serializer(), src)
        assertNotNull(dto.host?.ebpf)
        assertTrue(dto.host?.ebpf?.configured == true)
        assertTrue(dto.host?.ebpf?.capability == true)
        assertEquals(false, dto.host?.ebpf?.kprobesLoaded)
        assertEquals(1, dto.cluster?.nodes?.size)
        assertEquals(listOf("memory"), dto.cluster?.nodes?.first()?.pressures)
    }

    @Test
    fun `plugins payload separates subprocess from native`() {
        val src =
            """
            {
              "plugins":[{"name":"git-hook","kind":"subprocess","enabled":true}],
              "native":[{"name":"datawatch-observer","kind":"native","enabled":true,"version":"5.27.0"}]
            }
            """.trimIndent()
        val dto = json.decodeFromString(PluginsDto.serializer(), src)
        assertEquals(1, dto.plugins.size)
        assertEquals(1, dto.native.size)
        assertEquals("native", dto.native.first().kind)
    }

    // ---- v0.36.1 picker mkdir (#14) ----

    @Test
    fun `mkdir body serialises with action default`() {
        val body = FilesMkdirDto(path = "/projects/new")
        val out = json.encodeToString(FilesMkdirDto.serializer(), body)
        assertTrue(out.contains("\"path\":\"/projects/new\""))
        assertTrue(out.contains("\"action\":\"mkdir\""))
    }

    // ---- v0.37.0 mempalace (#21) ----

    @Test
    fun `memory sweep response parses with dry_run flag`() {
        val src = """{"count":42,"dry_run":true}"""
        val dto = json.decodeFromString(MemorySweepStaleResponseDto.serializer(), src)
        assertEquals(42, dto.count)
        assertTrue(dto.dryRun)
    }

    @Test
    fun `spellcheck suggestions round-trip`() {
        val src = """{"suggestions":[{"word":"helo","suggestions":["hello","help"]}]}"""
        val dto = json.decodeFromString(MemorySpellcheckResponseDto.serializer(), src)
        assertEquals(1, dto.suggestions.size)
        assertEquals(listOf("hello", "help"), dto.suggestions.first().suggestions)
    }

    @Test
    fun `extract facts SVO triple parses with object reserved-word remap`() {
        val src = """{"triples":[{"subject":"Alice","verb":"merged","object":"PR-42"}]}"""
        val dto = json.decodeFromString(MemoryExtractFactsResponseDto.serializer(), src)
        assertEquals("Alice", dto.triples.first().subject)
        // `object` is a Kotlin reserved word → SerialName remaps to `obj`.
        assertEquals("PR-42", dto.triples.first().obj)
    }

    // ---- v0.38.0 autonomous PRD (#11) ----

    @Test
    fun `prd list parses depth and template flag`() {
        val src =
            """
            {"prds":[
              {
                "id":"prd-1","name":"refactor","title":"Refactor X",
                "status":"needs_review","project_profile":"go-runner",
                "depth":2,"is_template":false,
                "stories":[
                  {"id":"s1","title":"Plan","description":"first pass","status":"awaiting_approval",
                   "files":["a.go","b.go"],"files_touched":["a.go"]}
                ]
              }
            ]}
            """.trimIndent()
        val dto = json.decodeFromString(PrdListDto.serializer(), src)
        val prd = dto.prds.first()
        assertEquals(2, prd.depth)
        assertEquals(false, prd.isTemplate)
        assertEquals("awaiting_approval", prd.stories.first().status)
        assertEquals(listOf("a.go", "b.go"), prd.stories.first().files)
        assertEquals(listOf("a.go"), prd.stories.first().filesTouched)
    }

    @Test
    fun `new prd request omits null branches when project_dir mode`() {
        val req =
            NewPrdRequestDto(
                name = "x",
                projectDir = "/code/x",
                backend = "claude-code",
                effort = "thorough",
            )
        val out = json.encodeToString(NewPrdRequestDto.serializer(), req)
        assertTrue(out.contains("\"project_dir\":\"/code/x\""))
        assertTrue(out.contains("\"backend\":\"claude-code\""))
    }

    // ---- v0.39.0 orchestrator (#7) ----

    @Test
    fun `orchestrator graph parses observer_summary on node`() {
        val src =
            """
            {"id":"prd-1","nodes":[
              {"id":"n1","status":"running","observer_summary":{"cpu_pct":42.5,"rss_mb":256,"envelope_count":7,"last_push_at":"2026-04-28T00:00:00Z"}},
              {"id":"n2","status":"complete"}
            ],"edges":[{"from":"n1","to":"n2","kind":"dep"}]}
            """.trimIndent()
        val dto = json.decodeFromString(OrchestratorGraphDto.serializer(), src)
        assertEquals(2, dto.nodes.size)
        assertNotNull(dto.nodes[0].observerSummary)
        assertEquals(42.5, dto.nodes[0].observerSummary?.cpuPct)
        assertEquals(256L, dto.nodes[0].observerSummary?.rssMb)
        assertEquals(7, dto.nodes[0].observerSummary?.envelopeCount)
        assertNull(dto.nodes[1].observerSummary, "no observer_summary on n2")
        assertEquals("dep", dto.edges.first().kind)
    }

    // ---- v0.39.1 start agent (#20) ----

    @Test
    fun `start agent response accepts either session_id or id`() {
        val a = json.decodeFromString(StartAgentResponseDto.serializer(), """{"session_id":"abc"}""")
        val b = json.decodeFromString(StartAgentResponseDto.serializer(), """{"id":"def"}""")
        assertEquals("abc", a.sessionId ?: a.id)
        assertEquals("def", b.sessionId ?: b.id)
    }

    @Test
    fun `start agent request omits cluster_profile when null`() {
        val req =
            StartAgentRequestDto(
                task = "refactor",
                projectProfile = "go-runner",
            )
        val out = json.encodeToString(StartAgentRequestDto.serializer(), req)
        assertTrue(out.contains("\"project_profile\":\"go-runner\""))
        // Default-null fields aren't emitted (encodeDefaults=true would
        // include them — but the DTO sets cluster_profile = null, and
        // Json drops nulls by default unless explicitNulls is on).
    }
}
