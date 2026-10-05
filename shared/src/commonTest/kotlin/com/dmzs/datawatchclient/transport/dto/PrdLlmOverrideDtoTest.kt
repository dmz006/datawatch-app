package com.dmzs.datawatchclient.transport.dto

import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Per-story / per-task LLM override + spawn fields (server autonomous/models.go
 * Story.Backend/Effort/Model, Task.Backend/Effort/Model/SpawnPRD/ChildPRDID) and
 * template built-in / use-count (Template.IsBuiltin / UseCount). Old servers omit
 * them, so every field must default.
 */
class PrdLlmOverrideDtoTest {
    private val json = RestTransport.DefaultJson

    @Test
    fun `story and task parse llm override and spawn fields`() {
        val prd =
            json.decodeFromString(
                PrdDto.serializer(),
                """
                {"id":"p1","status":"needs_review","stories":[{
                  "id":"s1","title":"S","status":"pending","execution_profile":"fast",
                  "backend":"claude-code","effort":"high","model":"claude-sonnet",
                  "tasks":[{"id":"t1","title":"T","spec":"do","status":"pending",
                    "backend":"ollama","effort":"low","model":"qwen","spawn_prd":true,"child_prd_id":"p2"}]
                }]}
                """.trimIndent(),
            )
        val story = prd.stories.single()
        assertEquals("claude-code", story.backend)
        assertEquals("high", story.effort)
        assertEquals("claude-sonnet", story.model)
        assertEquals("fast", story.executionProfile)
        val task = story.tasks.single()
        assertEquals("ollama", task.backend)
        assertEquals("low", task.effort)
        assertEquals("qwen", task.model)
        assertTrue(task.spawnPrd)
        assertEquals("p2", task.childPrdId)
    }

    @Test
    fun `old server payload without the new fields still parses`() {
        val prd =
            json.decodeFromString(
                PrdDto.serializer(),
                """{"id":"p1","stories":[{"id":"s1","title":"S","tasks":[{"id":"t1","title":"T"}]}]}""",
            )
        val story = prd.stories.single()
        assertNull(story.backend)
        assertNull(PrdLlmBadge.storyLabel(story))
        val task = story.tasks.single()
        assertFalse(task.spawnPrd)
        assertNull(task.childPrdId)
        assertNull(PrdLlmBadge.taskLabel(task))
    }

    @Test
    fun `template parses built-in and use count with defaults`() {
        val list =
            json.decodeFromString(
                TemplateListDto.serializer(),
                """{"templates":[{"id":"a","title":"A","spec":"x","is_builtin":true,"use_count":4},{"id":"b","title":"B","spec":"y"}]}""",
            )
        assertTrue(list.templates[0].isBuiltin)
        assertEquals(4, list.templates[0].useCount)
        assertFalse(list.templates[1].isBuiltin)
        assertEquals(0, list.templates[1].useCount)
    }

    @Test
    fun `llm badge matches the PWA pill format`() {
        assertEquals("LLM: claude-code / high / opus", PrdLlmBadge.label("claude-code", "high", "opus"))
        assertEquals("LLM: inherit / low", PrdLlmBadge.label("", "low", null))
        assertEquals("LLM: ollama", PrdLlmBadge.label("ollama", null, ""))
        assertNull(PrdLlmBadge.label(null, " ", ""))
    }

    @Test
    fun `profile pill only shows read-only and editable gate matches the PWA`() {
        val story = PrdStoryDto(id = "s", executionProfile = "fast")
        assertEquals("prof: fast", PrdLlmBadge.storyProfileLabel(story, editable = false))
        assertNull(PrdLlmBadge.storyProfileLabel(story, editable = true))
        assertNull(PrdLlmBadge.storyProfileLabel(PrdStoryDto(id = "s"), editable = false))
        assertTrue(PrdLlmBadge.isEditable("needs_review"))
        assertTrue(PrdLlmBadge.isEditable("revisions_asked"))
        assertTrue(PrdLlmBadge.isEditable("cancelled"))
        assertFalse(PrdLlmBadge.isEditable("running"))
        assertFalse(PrdLlmBadge.isEditable("draft"))
    }
}
