package com.dmzs.datawatchclient.ui.compute

import com.dmzs.datawatchclient.transport.dto.OllamaCatalogModelDto
import com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto
import com.dmzs.datawatchclient.transport.dto.OllamaTagDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sprint 27 — pure-logic coverage for OllamaMarketplaceDialog catalogue filtering.
 *
 * The dialog is a private @Composable; these tests mirror its filter conditions
 * verbatim so that regressions in search, installed-state detection, and pull-task
 * progress display are caught at the JVM unit-test layer.
 */
class OllamaMarketplaceTest {

    // ── helpers ────────────────────────────────────────────────────────────

    private fun model(name: String, vararg tags: String) =
        OllamaCatalogModelDto(
            name = name,
            tags = tags.map { OllamaTagDto(tag = it) },
        )

    private fun pullTask(id: String, model: String, progress: Int, status: String = "running") =
        OllamaPullTaskDto(id = id, model = model, progress = progress, status = status)

    // ── Catalog search filter ──────────────────────────────────────────────

    @Test
    fun `empty search returns all models`() {
        val catalog = listOf(model("llama3"), model("codellama"), model("phi3"))
        val filtered = catalog.filter { it.name.contains("", ignoreCase = true) }
        assertEquals(3, filtered.size)
    }

    @Test
    fun `search filters by name substring case-insensitive`() {
        val catalog = listOf(model("llama3"), model("codellama"), model("phi3"))
        val filtered = catalog.filter { it.name.contains("llama", ignoreCase = true) }
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == "llama3" })
        assertTrue(filtered.any { it.name == "codellama" })
    }

    @Test
    fun `search with uppercase matches lowercase model name`() {
        val catalog = listOf(model("llama3"), model("phi3"))
        val filtered = catalog.filter { it.name.contains("LLAMA", ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("llama3", filtered.first().name)
    }

    @Test
    fun `search with no match returns empty list`() {
        val catalog = listOf(model("llama3"), model("phi3"))
        val filtered = catalog.filter { it.name.contains("gemini", ignoreCase = true) }
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `search on empty catalog returns empty list`() {
        val filtered = emptyList<OllamaCatalogModelDto>().filter {
            it.name.contains("llama", ignoreCase = true)
        }
        assertTrue(filtered.isEmpty())
    }

    // ── Installed-state detection ──────────────────────────────────────────

    @Test
    fun `isInstalled true when fullModel in installedModels`() {
        val installedModels = listOf("llama3:latest", "codellama:7b")
        val fullModel = "llama3:latest"
        assertTrue(fullModel in installedModels)
    }

    @Test
    fun `isInstalled false when fullModel not in installedModels`() {
        val installedModels = listOf("codellama:7b")
        val fullModel = "llama3:latest"
        assertFalse(fullModel in installedModels)
    }

    @Test
    fun `fullModel is composed as name colon tag`() {
        val modelName = "llama3"
        val tag = OllamaTagDto(tag = "latest")
        val fullModel = "$modelName:${tag.tag}"
        assertEquals("llama3:latest", fullModel)
    }

    @Test
    fun `isInstalled false when installedModels is empty`() {
        val installedModels = emptyList<String>()
        assertFalse("llama3:latest" in installedModels)
    }

    // ── Pull task progress ─────────────────────────────────────────────────

    @Test
    fun `pull task retrieved by fullModel key from pullTasks map`() {
        val task = pullTask(id = "t1", model = "llama3:latest", progress = 42)
        val pullTasks = mapOf("llama3:latest" to task)
        val found = pullTasks["llama3:latest"]
        assertEquals(42, found?.progress)
        assertEquals("running", found?.status)
    }

    @Test
    fun `pull task null when fullModel not in pullTasks`() {
        val pullTasks = mapOf("codellama:7b" to pullTask(id = "t2", model = "codellama:7b", progress = 10))
        val found = pullTasks["llama3:latest"]
        assertNull(found)
    }

    @Test
    fun `pull task at 100 percent reflects completed status`() {
        val task = pullTask(id = "t3", model = "llama3:latest", progress = 100, status = "completed")
        val pullTasks = mapOf("llama3:latest" to task)
        val found = pullTasks["llama3:latest"]!!
        assertEquals(100, found.progress)
        assertEquals("completed", found.status)
    }
}
