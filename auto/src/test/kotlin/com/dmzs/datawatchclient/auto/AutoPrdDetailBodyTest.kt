package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.DecisionDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPrdDetailBodyTest {

    private fun makeTask(id: String, task: String, status: String, error: String? = null) =
        PrdTaskDto(id = id, task = task, status = status, error = error)

    private fun makeStory(
        id: String,
        title: String,
        status: String,
        tasks: List<PrdTaskDto> = emptyList(),
        description: String? = null,
    ) = PrdStoryDto(id = id, title = title, status = status, tasks = tasks, description = description)

    private fun makePrd(
        status: String = "running",
        stories: List<PrdStoryDto> = emptyList(),
        spec: String? = null,
        decisions: List<DecisionDto>? = null,
        title: String? = null,
    ) = PrdDto(
        id = "prd-1",
        name = "test-prd",
        title = title,
        status = status,
        stories = stories,
        spec = spec,
        decisions = decisions,
    )

    @Test
    fun `body includes status line`() {
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(status = "running"))
        assertContains(body, "Status: running")
    }

    @Test
    fun `progress bar shown when stories present`() {
        val stories = listOf(
            makeStory("s1", "Story 1", "complete"),
            makeStory("s2", "Story 2", "in_progress"),
            makeStory("s3", "Story 3", "pending"),
        )
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "1/3 stories done")
        assertTrue(body.contains("33%"), "Expected 33% in: $body")
    }

    @Test
    fun `active story shown with running task`() {
        val tasks = listOf(
            makeTask("t1", "Set up scaffolding", "complete"),
            makeTask("t2", "Write auth middleware", "in_progress"),
        )
        val stories = listOf(
            makeStory("s1", "Implement auth", "in_progress", tasks = tasks),
        )
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "◉ Active: Implement auth")
        assertContains(body, "▶ Write auth middleware")
        assertContains(body, "1/2 done")
    }

    @Test
    fun `awaiting approval story shows approval prompt`() {
        val stories = listOf(makeStory("s1", "Deploy to production", "awaiting_approval"))
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "⚠ Active: Deploy to production")
        assertContains(body, "Awaiting your approval")
    }

    @Test
    fun `pending stories listed under up next`() {
        val stories = listOf(
            makeStory("s1", "Done story", "complete"),
            makeStory("s2", "Next story", "pending"),
            makeStory("s3", "Another story", "pending"),
        )
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "Up next:")
        assertContains(body, "○ Next story")
        assertContains(body, "○ Another story")
    }

    @Test
    fun `pending overflow shows count`() {
        val stories = (1..6).map { i ->
            makeStory("s$i", "Story $i", if (i == 1) "complete" else "pending")
        }
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "… 1 more")
    }

    @Test
    fun `last decision appended`() {
        val decisions = listOf(
            DecisionDto(kind = "approve", actor = "user", note = "Looks good"),
        )
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(decisions = decisions))
        assertContains(body, "Last approve (user): Looks good")
    }

    @Test
    fun `spec snippet included`() {
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(spec = "Build a REST API"))
        assertContains(body, "Spec:")
        assertContains(body, "Build a REST API")
    }

    @Test
    fun `long spec is truncated`() {
        val longSpec = "x".repeat(300)
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(spec = longSpec))
        assertContains(body, "…")
        assertFalse(body.contains(longSpec), "Full spec should be truncated")
    }

    @Test
    fun `failed tasks show count`() {
        val tasks = listOf(
            makeTask("t1", "Write tests", "failed"),
            makeTask("t2", "Deploy", "pending"),
        )
        val stories = listOf(makeStory("s1", "Test story", "in_progress", tasks = tasks))
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = stories))
        assertContains(body, "1 failed")
    }

    @Test
    fun `no stories produces no progress bar`() {
        val body = AutoPrdDetailScreen.buildDetailBody(makePrd(stories = emptyList()))
        assertFalse(body.contains("stories done"), "No progress line without stories")
    }
}
