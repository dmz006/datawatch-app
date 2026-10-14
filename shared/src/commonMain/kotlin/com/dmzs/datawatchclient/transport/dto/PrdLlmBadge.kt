package com.dmzs.datawatchclient.transport.dto

/**
 * Shared text for the per-story / per-task LLM override pills and the story
 * profile pill (PWA renderStory `llmPill` / `profPill`, renderTask `llmBadge`,
 * app.js ~10344 / ~10593). Both apps render these strings verbatim so the
 * format can't drift between Android and iOS.
 */
public object PrdLlmBadge {
    /**
     * `LLM: <backend|inherit>[ / effort][ / model]`, or null when no part of the
     * override is set (PWA shows nothing then).
     */
    public fun label(
        backend: String?,
        effort: String?,
        model: String?,
    ): String? {
        val b = backend?.trim().orEmpty()
        val e = effort?.trim().orEmpty()
        val m = model?.trim().orEmpty()
        if (b.isEmpty() && e.isEmpty() && m.isEmpty()) return null
        val sb = StringBuilder("LLM: ")
        sb.append(if (b.isEmpty()) "inherit" else b)
        if (e.isNotEmpty()) sb.append(" / ").append(e)
        if (m.isNotEmpty()) sb.append(" / ").append(m)
        return sb.toString()
    }

    public fun storyLabel(story: PrdStoryDto): String? = label(story.backend, story.effort, story.model)

    public fun taskLabel(task: PrdTaskDto): String? = label(task.backend, task.effort, task.model)

    /**
     * `prof: <name>` — PWA only shows this read-only pill when the story is NOT
     * editable (while editable the ⚙ button carries the value in its tooltip).
     */
    public fun storyProfileLabel(
        story: PrdStoryDto,
        editable: Boolean,
    ): String? {
        val p = story.executionProfile?.trim().orEmpty()
        return if (editable || p.isEmpty()) null else "prof: $p"
    }

    /**
     * PWA "Judged by" line value under a task's verification result (renderTask,
     * server v9.0.73+): `<backend>[ / <model>][ / <effort>][ (<source>)]`, or null when
     * the server didn't record a verifier backend (PWA shows nothing then). Source
     * `prd` is shown as [automatonWord] (PWA `prd_verif_source_automaton`); other
     * sources (`task`, `story`, `global`, `pool`) are shown as sent.
     */
    public fun verifierLabel(
        verification: PrdTaskVerificationDto?,
        automatonWord: String,
    ): String? {
        val b = verification?.verifierBackend?.trim().orEmpty()
        if (b.isEmpty()) return null
        val m = verification?.verifierModel?.trim().orEmpty()
        val e = verification?.verifierEffort?.trim().orEmpty()
        val src = verification?.verifierSource?.trim().orEmpty()
        val sb = StringBuilder(b)
        if (m.isNotEmpty()) sb.append(" / ").append(m)
        if (e.isNotEmpty()) sb.append(" / ").append(e)
        if (src.isNotEmpty()) sb.append(" (").append(if (src == "prd") automatonWord else src).append(')')
        return sb.toString()
    }

    /** PWA `editable` gate for story/task structural + LLM edits (renderStory). */
    public fun isEditable(prdStatus: String): Boolean =
        prdStatus.lowercase() in setOf("needs_review", "revisions_asked", "cancelled")
}
