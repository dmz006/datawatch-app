package com.dmzs.datawatchclient.util

/**
 * One renderable piece of a chat-mode assistant message (PWA
 * `renderChatMarkdown`, app.js). Top-level subclasses so Swift can `as?` them.
 */
public sealed class ChatSegment

/** Ordinary markdown — rendered by the existing markdown views. */
public data class ChatMarkdownSegment(val text: String) : ChatSegment()

/**
 * `<think>…</think>` / `<thinking>…</thinking>` — PWA collapsible
 * `<details class="chat-thinking">` ("🧠 Thinking..."), closed by default.
 */
public data class ChatThinkingSegment(val text: String) : ChatSegment()

/**
 * `![alt](url)` — PWA `.chat-image` (img max-width 100 %, radius 6, alt caption,
 * hidden on load error). [loadable] is true only for absolute http(s) URLs; the
 * apps never fetch other schemes (data:, file:, javascript:, relative paths).
 */
public data class ChatImageSegment(
    val alt: String,
    val url: String,
    val loadable: Boolean,
) : ChatSegment()

/** Splits chat content into markdown / thinking / image segments, PWA order. */
public object ChatContentSplitter {
    // PWA: /<think(?:ing)?>([\s\S]*?)<\/think(?:ing)?>/g
    private val THINK = Regex("<think(?:ing)?>([\\s\\S]*?)</think(?:ing)?>")

    // PWA: /!\[([^\]]*)\]\(([^)]+)\)/g
    private val IMAGE = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")

    public fun split(content: String): List<ChatSegment> {
        if (!content.contains("<think") && !content.contains("![")) {
            return if (content.isBlank()) emptyList() else listOf(ChatMarkdownSegment(content))
        }
        val out = mutableListOf<ChatSegment>()
        var last = 0
        for (m in THINK.findAll(content)) {
            splitImages(content.substring(last, m.range.first), out)
            val inner = m.groupValues[1].trim()
            if (inner.isNotEmpty()) out += ChatThinkingSegment(inner)
            last = m.range.last + 1
        }
        splitImages(content.substring(last), out)
        return out
    }

    /** True for absolute http:// or https:// URLs (case-insensitive scheme). */
    public fun isLoadableImageUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        return (u.startsWith("https://") || u.startsWith("http://")) && u.length > u.indexOf("//") + 2
    }

    /** Images outside fenced code blocks become [ChatImageSegment]s; the rest stays markdown. */
    private fun splitImages(
        text: String,
        out: MutableList<ChatSegment>,
    ) {
        if (text.isEmpty()) return
        val buf = StringBuilder()

        fun flush() {
            val s = buf.toString().trim('\n')
            if (s.isNotBlank()) out += ChatMarkdownSegment(s)
            buf.clear()
        }

        var inFence = false
        for (line in text.split('\n')) {
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                buf.append(line).append('\n')
                continue
            }
            if (inFence || !line.contains("![")) {
                buf.append(line).append('\n')
                continue
            }
            var pos = 0
            var matched = false
            for (m in IMAGE.findAll(line)) {
                matched = true
                val before = line.substring(pos, m.range.first)
                if (before.isNotBlank()) buf.append(before).append('\n')
                flush()
                // `![alt](url "title")` — the URL is the first token.
                val url = m.groupValues[2].trim().substringBefore(' ')
                out += ChatImageSegment(alt = m.groupValues[1], url = url, loadable = isLoadableImageUrl(url))
                pos = m.range.last + 1
            }
            val after = line.substring(pos)
            if (!matched || after.isNotBlank()) buf.append(after).append('\n')
        }
        flush()
    }
}
