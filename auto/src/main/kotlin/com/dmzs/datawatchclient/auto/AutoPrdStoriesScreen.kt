@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto

/**
 * Stories list for a PRD in Android Auto.
 *
 * One row per story: status marker + title as the row title; task summary
 * (count done/total, running task description) as the body text. Read-only —
 * all actions are on the parent [AutoPrdDetailScreen].
 *
 * Reached from AutoPrdDetailScreen via the "Stories" action strip button.
 */
public class AutoPrdStoriesScreen(
    carContext: CarContext,
    private val prd: PrdDto,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()

        if (prd.stories.isEmpty()) {
            items.addItem(
                Row.Builder()
                    .setTitle("No stories")
                    .addText("This plan has no stories yet.")
                    .build(),
            )
        } else {
            prd.stories.forEachIndexed { idx, story ->
                items.addItem(buildStoryRow(idx + 1, story))
            }
        }

        val prdTitle = prd.title?.takeIf { it.isNotBlank() } ?: prd.name.takeIf { it.isNotBlank() } ?: prd.id
        return ListTemplate.Builder()
            .setTitle("${prdTitle.take(28)} — Stories")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }

    private companion object {
        private val DONE_STATUSES = setOf("complete", "completed", "done")
        const val MAX_TITLE_CHARS = 50
        const val MAX_TASK_CHARS = 55
        const val MAX_DESC_CHARS = 70

        fun buildStoryRow(
            position: Int,
            story: PrdStoryDto,
        ): Row {
            val marker = when (story.status) {
                "complete", "completed", "done" -> "✓"
                "in_progress" -> "◉"
                "awaiting_approval" -> "⚠"
                "rejected" -> "✗"
                else -> "○"
            }
            val titleText = "$position. $marker ${story.title.take(MAX_TITLE_CHARS)}"
            val statusColor = when (story.status) {
                "awaiting_approval" -> CarColor.RED
                "in_progress" -> CarColor.GREEN
                else -> CarColor.DEFAULT
            }

            val detailLine = buildStoryDetail(story)
            val taskLine = buildTasksLine(story)

            val rowBuilder = Row.Builder()
                .setTitle(colored(titleText, statusColor))
            if (detailLine.isNotBlank()) rowBuilder.addText(detailLine)
            if (taskLine.isNotBlank() && taskLine != detailLine) rowBuilder.addText(taskLine)
            return rowBuilder.build()
        }

        fun buildStoryDetail(story: PrdStoryDto): String =
            when (story.status) {
                "awaiting_approval" -> "⚠ Awaiting your approval"
                else -> story.description?.take(MAX_DESC_CHARS)?.takeIf { it.isNotBlank() } ?: ""
            }

        fun buildTasksLine(story: PrdStoryDto): String {
            if (story.tasks.isEmpty()) return story.status.ifBlank { "" }
            val done = story.tasks.count { it.status in DONE_STATUSES }
            val failed = story.tasks.count { it.status == "failed" }
            val running = story.tasks.firstOrNull { it.status == "in_progress" }
            return buildString {
                append("$done/${story.tasks.size} tasks")
                if (failed > 0) append(" · $failed failed")
                running?.let { append(" · ▶ ${it.task.take(MAX_TASK_CHARS)}") }
            }
        }
    }
}
