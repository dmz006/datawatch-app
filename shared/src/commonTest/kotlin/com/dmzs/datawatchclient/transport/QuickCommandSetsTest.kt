package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.SavedCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickCommandSetsTest {
    @Test
    fun setsMatchTheWebUi() {
        assertEquals(
            listOf("approve", "reject", "continue", "skip", "ESC", "tmux prefix (Ctrl-b)", "quit"),
            QuickCommandSets.CARD_SYSTEM.map { it.label },
        )
        assertEquals(
            listOf("approve", "reject", "enter", "continue", "skip", "abort", "ESC", "tmux prefix (Ctrl-b)", "quit"),
            QuickCommandSets.DETAIL_SYSTEM.map { it.label },
        )
        assertEquals(listOf("sast-scan", "secrets-scan", "deps-scan"), QuickCommandSets.GUARDRAILS)
    }

    @Test
    fun detailHidesSeededCardShowsAll() {
        val saved = listOf(SavedCommand("approve", "yes", seeded = true), SavedCommand("deploy", "make deploy"))
        assertEquals(listOf("deploy"), QuickCommandSets.detailSaved(saved).map { it.label })
        assertEquals(listOf("approve", "deploy"), QuickCommandSets.cardSaved(saved).map { it.label })
    }

    @Test
    fun specialValuesMapToTmuxKeys() {
        assertEquals("Escape", QuickCommandSets.sendKeyName("__esc__"))
        assertEquals("C-b", QuickCommandSets.sendKeyName("__ctrlb__"))
        assertEquals(null, QuickCommandSets.sendKeyName("yes"))
    }
}
