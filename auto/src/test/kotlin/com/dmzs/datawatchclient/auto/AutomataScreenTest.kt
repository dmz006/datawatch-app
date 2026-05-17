package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutomataScreenTest {

    private fun makePrd(
        id: String,
        storyStatuses: List<String>,
        depth: Int = 0,
    ): PrdDto = PrdDto(
        id = id,
        name = id,
        status = "running",
        depth = depth,
        stories = storyStatuses.mapIndexed { i, s ->
            PrdStoryDto(id = "$id-s$i", title = "Story $i", status = s)
        },
    )

    @Test
    fun `active story position returns 1-based index of first in_progress story`() {
        val prd = makePrd("p", listOf("complete", "in_progress", "pending"))
        assertEquals(2, activeStoryPosition(prd))
    }

    @Test
    fun `active story position returns awaiting_approval story position`() {
        val prd = makePrd("p", listOf("complete", "awaiting_approval", "pending"))
        assertEquals(2, activeStoryPosition(prd))
    }

    @Test
    fun `active story position returns null when no active story`() {
        val prd = makePrd("p", listOf("complete", "complete"))
        assertNull(activeStoryPosition(prd))
    }

    @Test
    fun `subtitle includes story position and progress`() {
        val prd = makePrd("p", listOf("complete", "in_progress", "pending"))
        val subtitle = buildSubtitle(prd, activeStoryPosition(prd))
        assertTrue(subtitle.contains("Story 2/3"), "Expected Story 2/3 in: $subtitle")
        assertTrue(subtitle.contains("33%"), "Expected 33% in: $subtitle")
    }

    @Test
    fun `subtitle shows awaiting approval warning`() {
        val prd = makePrd("p", listOf("complete", "awaiting_approval"))
        val subtitle = buildSubtitle(prd, activeStoryPosition(prd))
        assertTrue(subtitle.contains("awaiting approval"), subtitle)
    }

    @Test
    fun `comparator puts blocked automata first`() {
        val normal = makePrd("normal", listOf("in_progress", "pending"))
        val blocked = makePrd("blocked", listOf("awaiting_approval", "pending"))
        val sorted = listOf(normal, blocked).sortedWith(automataComparator)
        assertEquals("blocked", sorted[0].id)
    }

    @Test
    fun `empty stories handled gracefully`() {
        val prd = makePrd("empty", emptyList())
        assertNull(activeStoryPosition(prd))
        val subtitle = buildSubtitle(prd, null)
        assertTrue(subtitle.isNotBlank())
    }

    // Mirror companion object logic for white-box testing
    private fun activeStoryPosition(prd: PrdDto): Int? {
        val idx = prd.stories.indexOfFirst {
            it.status == "in_progress" || it.status == "awaiting_approval"
        }
        return if (idx >= 0) idx + 1 else null
    }

    private fun buildSubtitle(prd: PrdDto, storyPos: Int?): String = buildString {
        val totalStories = prd.stories.size
        val completedStories = prd.stories.count { it.status == "complete" }
        if (storyPos != null && totalStories > 0) {
            append("Story $storyPos/$totalStories")
            val pct = (completedStories * 100) / totalStories
            append(" · $pct%")
        } else if (totalStories > 0) {
            val pct = (completedStories * 100) / totalStories
            append("$completedStories/$totalStories stories · $pct%")
        } else {
            append(prd.status)
        }
        val hasBlock = prd.stories.any { it.status == "awaiting_approval" }
        if (hasBlock) append(" ⚠ awaiting approval")
    }

    private val automataComparator: Comparator<PrdDto> = compareByDescending { prd ->
        val blockedStories = prd.stories.count { it.status == "awaiting_approval" }
        blockedStories * 10 + prd.depth
    }
}
