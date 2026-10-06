package com.dmzs.datawatchclient.transport.sse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Event parsing for the live planning stream (server autonomous_decompose.go wire shape). */
class DecomposeStreamTest {
    private fun frames(raw: String): List<DecomposeSseFrame> {
        val r = DecomposeSseLineReader()
        val out = raw.split("\n").mapNotNull { r.feed(it) }.toMutableList()
        r.flush()?.let { out.add(it) }
        return out
    }

    @Test
    fun `reader splits frames on blank lines and keeps ids`() {
        val raw =
            "retry: 1000\n\n" +
                "id: 1\ndata: {\"type\":\"story\",\"index\":0,\"title\":\"A\"}\n\n" +
                ": keepalive\n\n" +
                "id: 2\ndata: {\"type\":\"progress\",\"done\":1,\"total\":3}\n\n"
        val f = frames(raw)
        assertEquals(2, f.size)
        assertEquals("1", f[0].id)
        assertEquals("2", f[1].id)
        assertEquals("{\"type\":\"progress\",\"done\":1,\"total\":3}", f[1].data)
    }

    @Test
    fun `reader joins multi-line data - strips CR and flushes a trailing frame`() {
        val f = frames("event: x\r\ndata: line1\r\ndata:line2")
        assertEquals(1, f.size)
        assertEquals("x", f[0].event)
        assertEquals("line1\nline2", f[0].data)
        assertNull(f[0].id)
    }

    @Test
    fun `parses story progress complete and error events`() {
        val story =
            DecomposeStreamParser.parse(
                "{\"type\":\"story\",\"index\":2,\"title\":\"Auth\",\"description\":\"Login flow\",\"id\":\"s3\"}",
                "5",
            )
        assertNotNull(story)
        assertEquals(DecomposeStreamEvent.TYPE_STORY, story.type)
        assertEquals(2, story.index)
        assertEquals("Auth", story.title)
        assertEquals("Login flow", story.description)
        assertEquals("s3", story.storyId)
        assertEquals("5", story.eventId)
        assertFalse(story.isTerminal)

        val progress = DecomposeStreamParser.parse("{\"type\":\"progress\",\"done\":3,\"total\":7}", null)!!
        assertEquals(3, progress.done)
        assertEquals(7, progress.total)

        val complete = DecomposeStreamParser.parse("{\"type\":\"complete\",\"story_count\":7}", null)!!
        assertEquals(7, complete.storyCount)
        assertTrue(complete.isTerminal)

        val error = DecomposeStreamParser.parse("{\"type\":\"error\",\"message\":\"llm timeout\"}", null)!!
        assertEquals("llm timeout", error.message)
        assertTrue(error.isTerminal)
    }

    @Test
    fun `null fields - unknown types and garbage are tolerated`() {
        val story = DecomposeStreamParser.parse("{\"type\":\"story\",\"index\":0,\"title\":null,\"description\":null,\"id\":null}", null)!!
        assertEquals("", story.title)
        assertEquals("", story.storyId)
        assertNull(DecomposeStreamParser.parse("{\"type\":\"heartbeat\"}", null))
        assertNull(DecomposeStreamParser.parse("not json", null))
        assertNull(DecomposeStreamParser.parse("[1,2]", null))
    }

    @Test
    fun `live state dedupes replayed stories and records the outcome`() {
        val s0 = DecomposeStreamEvent(type = "story", index = 0, title = "A")
        val s1 = DecomposeStreamEvent(type = "story", index = 1, title = "B")
        val replay = DecomposeStreamEvent(type = "story", index = 0, title = "A")
        val state =
            DecomposeLiveState.fold(
                listOf(
                    s1,
                    s0,
                    DecomposeStreamEvent(type = "progress", done = 2, total = 2),
                    replay,
                    DecomposeStreamEvent(type = "complete", storyCount = 2),
                ),
            )
        assertEquals(listOf("A", "B"), state.stories.map { it.title })
        assertEquals(2, state.done)
        assertEquals(2, state.total)
        assertTrue(state.finished)
        assertFalse(state.isActive)
        assertEquals(2, state.storyCount)

        val failed = DecomposeLiveState().apply(DecomposeStreamEvent(type = "error", message = "boom"))
        assertEquals("boom", failed.error)
        assertFalse(failed.isActive)
    }
}
