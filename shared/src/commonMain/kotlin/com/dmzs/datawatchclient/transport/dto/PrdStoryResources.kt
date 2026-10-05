package com.dmzs.datawatchclient.transport.dto

/**
 * One row of the Automata detail "Progress" card: task completion for a story
 * plus the CPU / RSS of the worker sessions running its tasks (observer
 * envelopes keyed by `session_id`). Mirrors Android PrdDetailDialog's progress
 * card so iOS renders the same numbers.
 */
public data class PrdStoryResourceRow(
    val storyId: String,
    val title: String,
    val done: Int,
    val total: Int,
    /** Some task is verifying / running_tests / in_progress / running. */
    val active: Boolean,
    val failed: Boolean,
    /** Average CPU % over the story's sessions; meaningful only when [hasCpu]. */
    val cpuPct: Double,
    val hasCpu: Boolean,
    /** Summed RSS of the story's sessions, MiB (0 = none). */
    val rssMb: Double,
) {
    val fraction: Double get() = if (total > 0) done.toDouble() / total else 0.0
    val complete: Boolean get() = total > 0 && done == total

    /** "CPU 12% · 340 MB", or empty when no envelope matched. */
    val resourceLabel: String
        get() {
            val parts = mutableListOf<String>()
            if (hasCpu) parts.add("CPU ${cpuPct.toInt()}%")
            if (rssMb > 0) parts.add("${rssMb.toInt()} MB")
            return parts.joinToString(" · ")
        }
}

public object PrdStoryResources {
    private val terminal: Set<String> = setOf("completed", "done", "failed", "cancelled", "skipped")
    private val activeStates: Set<String> = setOf("verifying", "running_tests", "in_progress", "running")

    public fun rows(
        prd: PrdDto,
        envelopes: List<StatEnvelopeDto>,
    ): List<PrdStoryResourceRow> {
        val bySession: Map<String, StatEnvelopeDto> =
            envelopes.filter { !it.sessionId.isNullOrBlank() }.associateBy { it.sessionId!! }
        return prd.stories.map { story ->
            val envs: List<StatEnvelopeDto> =
                story.tasks.mapNotNull { it.sessionId }.toSet().mapNotNull { bySession[it] }
            PrdStoryResourceRow(
                storyId = story.id,
                title = story.title.ifBlank { story.id },
                done = story.tasks.count { it.status in terminal },
                total = story.tasks.size,
                active = story.tasks.any { it.status in activeStates },
                failed = story.status == "failed",
                cpuPct = if (envs.isEmpty()) 0.0 else envs.sumOf { it.cpuPct } / envs.size,
                hasCpu = envs.isNotEmpty(),
                rssMb = envs.sumOf { it.rssBytes } / 1_048_576.0,
            )
        }
    }
}
