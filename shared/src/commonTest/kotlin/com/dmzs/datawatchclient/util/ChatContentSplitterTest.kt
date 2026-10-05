package com.dmzs.datawatchclient.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatContentSplitterTest {
    @Test
    fun plainMarkdownIsOneSegment() {
        assertEquals(listOf<ChatSegment>(ChatMarkdownSegment("**hi** there")), ChatContentSplitter.split("**hi** there"))
        assertTrue(ChatContentSplitter.split("   ").isEmpty())
    }

    @Test
    fun thinkAndThinkingTagsBecomeCollapsibleSegments() {
        val segs = ChatContentSplitter.split("<think>\nplan it\n</think>\nAnswer\n<thinking>more</thinking>tail")
        assertEquals(
            listOf(
                ChatThinkingSegment("plan it"),
                ChatMarkdownSegment("Answer"),
                ChatThinkingSegment("more"),
                ChatMarkdownSegment("tail"),
            ),
            segs,
        )
    }

    @Test
    fun mismatchedCloseTagMatchesLikePwaRegex() {
        // PWA regex allows <think> … </thinking>.
        val segs = ChatContentSplitter.split("<think>x</thinking>")
        assertEquals(listOf<ChatSegment>(ChatThinkingSegment("x")), segs)
    }

    @Test
    fun unclosedThinkStaysLiteral() {
        val segs = ChatContentSplitter.split("<think>still going")
        assertEquals(listOf<ChatSegment>(ChatMarkdownSegment("<think>still going")), segs)
    }

    @Test
    fun imagesSplitOutWithAltAndUrl() {
        val segs = ChatContentSplitter.split("Here:\n![chart](https://example.test/a.png \"t\") after\nend")
        assertEquals(
            listOf(
                ChatMarkdownSegment("Here:"),
                ChatImageSegment(alt = "chart", url = "https://example.test/a.png", loadable = true),
                ChatMarkdownSegment(" after\nend"),
            ),
            segs,
        )
    }

    @Test
    fun imagesInsideCodeFencesAreLeftAlone() {
        val src = "```md\n![x](https://example.test/x.png)\n```"
        assertEquals(listOf<ChatSegment>(ChatMarkdownSegment(src)), ChatContentSplitter.split(src))
    }

    @Test
    fun onlyAbsoluteHttpImagesAreLoadable() {
        assertTrue(ChatContentSplitter.isLoadableImageUrl("https://example.test/a.png"))
        assertTrue(ChatContentSplitter.isLoadableImageUrl("HTTP://example.test/a.png"))
        assertFalse(ChatContentSplitter.isLoadableImageUrl("javascript:alert(1)"))
        assertFalse(ChatContentSplitter.isLoadableImageUrl("data:image/png;base64,AAAA"))
        assertFalse(ChatContentSplitter.isLoadableImageUrl("/api/files/a.png"))
        assertFalse(ChatContentSplitter.isLoadableImageUrl("file:///etc/passwd"))
        assertFalse(ChatContentSplitter.isLoadableImageUrl("https://"))
        val seg = ChatContentSplitter.split("![a](file:///x.png)").single() as ChatImageSegment
        assertFalse(seg.loadable)
    }
}
