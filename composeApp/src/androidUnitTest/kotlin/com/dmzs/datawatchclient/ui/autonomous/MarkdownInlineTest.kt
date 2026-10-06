package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.ui.text.font.FontStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** e2e flow 08: council replies showed a literal `_(via: node)_`. */
class MarkdownInlineTest {
    private fun italics(s: String): List<String> {
        val a = buildInline(s)
        return a.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { a.text.substring(it.start, it.end) }
    }

    @Test
    fun `underscore italics render without markers`() {
        val a = buildInline("Answer.\n\n_(via: node-a)_")
        assertEquals("Answer.\n\n(via: node-a)", a.text)
        assertEquals(listOf("(via: node-a)"), italics("Answer.\n\n_(via: node-a)_"))
    }

    @Test
    fun `snake_case stays literal`() {
        assertEquals("use max_concurrent_tasks here", buildInline("use max_concurrent_tasks here").text)
        assertTrue(italics("use max_concurrent_tasks here").isEmpty())
    }

    @Test
    fun `asterisk italics still work`() {
        assertEquals(listOf("x"), italics("a *x* b"))
    }
}
