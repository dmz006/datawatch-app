package com.dmzs.datawatchclient.ui.common

import com.dmzs.datawatchclient.docs.DocsLinks
import com.dmzs.datawatchclient.ui.configfields.ChannelBackendSchemas
import com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas
import com.dmzs.datawatchclient.ui.configfields.LlmBackendSchemas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BL414 — every "?" in the Android app must resolve to a docs page. PwaCard /
 * Section look their target up by card id, so a card whose id is missing from
 * DocsLinks silently loses its link; these tests catch that.
 */
class DocsLinksCoverageTest {
    private val sourceRoot = File("src/androidMain/kotlin")

    private fun sources(): Sequence<Pair<File, String>> {
        assertTrue(sourceRoot.isDirectory, "run from the composeApp module dir: ${sourceRoot.absolutePath}")
        return sourceRoot.walkTopDown().filter { it.extension == "kt" }.map { it to it.readText() }
    }

    @Test
    fun everySchemaConfigCardHasADocsTarget() {
        listOf(
            ConfigFieldSchemas.Datawatch, ConfigFieldSchemas.AutoUpdate, ConfigFieldSchemas.Session,
            ConfigFieldSchemas.Pipelines, ConfigFieldSchemas.Autonomous, ConfigFieldSchemas.Agents,
            ConfigFieldSchemas.Plugins, ConfigFieldSchemas.Orchestrator, ConfigFieldSchemas.Whisper,
            ConfigFieldSchemas.Memory, ConfigFieldSchemas.LlmRtk, ConfigFieldSchemas.Goose,
            ConfigFieldSchemas.OpenCode, ConfigFieldSchemas.WebSearch, ConfigFieldSchemas.Vision,
            ConfigFieldSchemas.WebServer, ConfigFieldSchemas.McpServer, ConfigFieldSchemas.CommsAuth,
            ConfigFieldSchemas.Proxy,
        ).forEach { s ->
            assertNotNull(s.docsPath ?: DocsLinks.forKey(s.id), "config card ${s.id} has no docs target")
        }
    }

    @Test
    fun backendDialogsLinkTheirOwnBackendSection() {
        assertEquals("messaging-backends.md#telegram", ChannelBackendSchemas.instanceSectionFor("tg-main", "telegram").docsPath)
        assertEquals("messaging-backends.md#email-smtp", ChannelBackendSchemas.globalSectionFor("email").docsPath)
        assertEquals("llm-backends.md#ollama-local-models", LlmBackendSchemas.sectionFor("ollama").docsPath)
        assertEquals("llm-backends.md#claude-code-default", LlmBackendSchemas.sectionFor("claude-code").docsPath)
    }

    @Test
    fun everyLiteralCardIdHasADocsTarget() {
        // PwaCard / Section / SectionWithAction calls with literal ids (incl. `if (…) "a" else "b"`).
        val call = Regex("""\b(?:PwaCard|Section|SectionWithAction)\((?:[^()]|\([^()]*\))*?\bid = ([^,\n]+)""")
        val literal = Regex(""""([^"$]+)"""")
        val missing = mutableListOf<String>()
        var seen = 0
        sources().forEach { (file, text) ->
            call.findAll(text).forEach { m ->
                literal.findAll(m.groupValues[1]).forEach { id ->
                    seen++
                    if (DocsLinks.forKey(id.groupValues[1]) == null) missing += "${file.name}: ${id.groupValues[1]}"
                }
            }
        }
        assertTrue(seen > 50, "card scan found only $seen ids — regex out of date?")
        assertTrue(missing.isEmpty(), "cards without a docs target: $missing")
    }

    @Test
    fun templatedCardIdsResolve() {
        // KindProfilesCard `gc_${kind}profiles`, StatsScreen `stats_$id`.
        assertNotNull(DocsLinks.forKey("gc_projectprofiles"))
        assertNotNull(DocsLinks.forKey("gc_clusterprofiles"))
        assertEquals(DocsLinks.forKey("stats"), DocsLinks.forKey("stats_cpu"))
    }

    @Test
    fun everyLiteralDocsKeyResolves() {
        // DocsLinks.forKey("…") call sites and SettingsTab / screen `view_*` keys.
        val forKey = Regex("""DocsLinks\.forKey\(\s*"([^"]+)"""")
        val viewKey = Regex(""""(view_[a-z_]+)"""")
        val missing = mutableListOf<String>()
        sources().forEach { (file, text) ->
            (forKey.findAll(text) + viewKey.findAll(text)).forEach { m ->
                if (DocsLinks.forKey(m.groupValues[1]) == null) missing += "${file.name}: ${m.groupValues[1]}"
            }
        }
        assertTrue(missing.isEmpty(), "unknown docs keys: $missing")
    }

    @Test
    fun noHardCodedDocsPathsOutsideTheTable() {
        // Help links must go through DocsLinks so the table stays the single source of truth.
        val hardCoded = Regex("""DocsLinkAction\(\s*"""")
        val offenders = sources().filter { (_, text) -> hardCoded.containsMatchIn(text) }.map { it.first.name }.toList()
        assertTrue(offenders.isEmpty(), "DocsLinkAction with a literal path in: $offenders")
    }
}
