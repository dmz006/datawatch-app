package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskVerificationDto
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AutoStoryDetailBodyTest {

    private fun makeTask(id: String, task: String, status: String, error: String? = null) =
        PrdTaskDto(id = id, task = task, status = status, error = error)

    private fun makeStory(
        status: String = "in_progress",
        tasks: List<PrdTaskDto> = emptyList(),
        description: String? = null,
        files: List<String> = emptyList(),
    ) = PrdStoryDto(
        id = "s-1",
        title = "Story Title",
        status = status,
        tasks = tasks,
        description = description,
        filesTouched = files,
    )

    @Test
    fun `body contains status`() {
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(status = "in_progress"))
        assertContains(body, "Status: in_progress")
    }

    @Test
    fun `description included when present`() {
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(description = "Implement the OAuth flow"))
        assertContains(body, "Implement the OAuth flow")
    }

    @Test
    fun `tasks listed with correct markers`() {
        val tasks = listOf(
            makeTask("t1", "Set up deps", "complete"),
            makeTask("t2", "Build handler", "in_progress"),
            makeTask("t3", "Write tests", "failed", error = "timeout"),
            makeTask("t4", "Deploy", "pending"),
        )
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = tasks))
        assertContains(body, "✓ Set up deps")
        assertContains(body, "◉ Build handler")
        assertContains(body, "✗ Write tests")
        assertContains(body, "○ Deploy")
    }

    @Test
    fun `failed task shows error`() {
        val tasks = listOf(makeTask("t1", "Deploy", "failed", error = "connection refused"))
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = tasks))
        assertContains(body, "Error: connection refused")
    }

    @Test
    fun `task count summary included`() {
        val tasks = listOf(
            makeTask("t1", "A", "complete"),
            makeTask("t2", "B", "complete"),
            makeTask("t3", "C", "failed"),
            makeTask("t4", "D", "pending"),
        )
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = tasks))
        assertContains(body, "2/4 done")
        assertContains(body, "1 failed")
    }

    @Test
    fun `files touched listed`() {
        val body = AutoStoryDetailScreen.buildStoryBody(
            makeStory(files = listOf("src/main/Auth.kt", "src/test/AuthTest.kt")),
        )
        assertContains(body, "Files:")
        assertContains(body, "src/main/Auth.kt")
    }

    @Test
    fun `file overflow shows count`() {
        val manyFiles = (1..7).map { "file$it.kt" }
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(files = manyFiles))
        assertContains(body, "… 3 more")
    }

    @Test
    fun `no tasks produces no tasks section`() {
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = emptyList()))
        assertTrue(!body.contains("Tasks:"), "No tasks section expected without tasks")
    }

    @Test
    fun `story rows marker for awaiting approval`() {
        val story = PrdStoryDto(id = "s1", title = "Gate Story", status = "awaiting_approval")
        val row = AutoPrdStoriesScreen.buildStoryRow(1, story)
        // Title should contain ⚠ marker
        val title = row.title?.toString() ?: ""
        assertTrue(title.contains("⚠"), "Expected ⚠ in title: $title")
    }

    @Test
    fun `story rows marker for complete`() {
        val story = PrdStoryDto(id = "s1", title = "Done Story", status = "complete")
        val row = AutoPrdStoriesScreen.buildStoryRow(1, story)
        val title = row.title?.toString() ?: ""
        assertTrue(title.contains("✓"), "Expected ✓ in title: $title")
    }

    @Test
    fun `tasks line shows running task`() {
        val tasks = listOf(
            makeTask("t1", "Build the thing", "in_progress"),
        )
        val story = PrdStoryDto(id = "s1", title = "Story", status = "in_progress", tasks = tasks)
        val line = AutoPrdStoriesScreen.buildTasksLine(story)
        assertContains(line, "▶ Build the thing")
    }

    @Test
    fun `failed task with retries shows retry count`() {
        val tasks = listOf(
            PrdTaskDto(id = "t1", task = "Run migration", status = "failed", error = "timeout", retryCount = 2),
        )
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = tasks))
        assertContains(body, "Retries: 2")
    }

    @Test
    fun `completed task with verification shows summary`() {
        val verification = PrdTaskVerificationDto(summary = "All assertions passed", severity = "low")
        val tasks = listOf(
            PrdTaskDto(id = "t1", task = "Write tests", status = "complete", verification = verification),
        )
        val body = AutoStoryDetailScreen.buildStoryBody(makeStory(tasks = tasks))
        assertContains(body, "All assertions passed")
    }

    @Test
    fun `AutoTaskDetailScreen body contains status and task spec`() {
        val task = PrdTaskDto(id = "t1", task = "Deploy to staging", status = "failed", error = "connection refused")
        val body = AutoTaskDetailScreen.buildTaskBody(task)
        assertContains(body, "Status: failed")
        assertContains(body, "Deploy to staging")
        assertContains(body, "Error:")
        assertContains(body, "connection refused")
    }

    @Test
    fun `AutoTaskDetailScreen body shows retry count`() {
        val task = PrdTaskDto(id = "t1", task = "Run tests", status = "failed", retryCount = 3)
        val body = AutoTaskDetailScreen.buildTaskBody(task)
        assertContains(body, "Retries: 3")
    }

    @Test
    fun `AutoTaskDetailScreen body shows verification issues`() {
        val v = PrdTaskVerificationDto(
            summary = "Tests pass",
            issues = listOf("No type annotations", "Missing docstring"),
        )
        val task = PrdTaskDto(id = "t1", task = "Write handler", status = "complete", verification = v)
        val body = AutoTaskDetailScreen.buildTaskBody(task)
        assertContains(body, "Verification:")
        assertContains(body, "Tests pass")
        assertContains(body, "No type annotations")
    }
}
