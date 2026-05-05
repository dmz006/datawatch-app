package com.dmzs.datawatchclient.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuickCommandItemTest {
    @Test fun `label and value stored correctly`() {
        val item = QuickCommandItem(label = "Enter", value = "\n")
        assertEquals("Enter", item.label)
        assertEquals("\n", item.value)
    }

    @Test fun `equality based on both fields`() {
        val a = QuickCommandItem("ESC", "")
        val b = QuickCommandItem("ESC", "")
        val c = QuickCommandItem("ESC", "[A")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test fun `fallback list has expected size and key entries`() {
        // Mirror of the hard-coded fallback in QuickCommandsSheet.
        // Guards against accidental truncation of the list.
        val fallback = listOf(
            QuickCommandItem("approve", "yes"),
            QuickCommandItem("reject", "no"),
            QuickCommandItem("continue", "continue"),
            QuickCommandItem("skip", "skip"),
            QuickCommandItem("quit", "/exit"),
            QuickCommandItem("Enter", "\n"),
            QuickCommandItem("ESC", ""),
            QuickCommandItem("Ctrl-b", ""),
            QuickCommandItem("↑", "[A"),
            QuickCommandItem("↓", "[B"),
            QuickCommandItem("→", "[C"),
            QuickCommandItem("←", "[D"),
            QuickCommandItem("PgUp", "[5~"),
            QuickCommandItem("PgDn", "[6~"),
            QuickCommandItem("Tab", "\t"),
        )
        assertEquals(15, fallback.size)
        assertTrue(fallback.any { it.label == "ESC" && it.value == "" })
        assertTrue(fallback.any { it.label == "↑" && it.value == "[A" })
    }
}
