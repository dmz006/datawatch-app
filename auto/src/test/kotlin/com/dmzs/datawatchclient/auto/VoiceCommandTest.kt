package com.dmzs.datawatchclient.auto

import com.dmzs.datawatchclient.auto.voice.VoiceCommand
import com.dmzs.datawatchclient.auto.voice.levenshtein
import com.dmzs.datawatchclient.auto.voice.parseVoiceCommand
import com.dmzs.datawatchclient.auto.voice.parseVoiceCommandFull
import com.dmzs.datawatchclient.auto.voice.resolveServerName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceCommandTest {

    // ---- Original commands still work ----

    @Test fun `status phrase maps to STATUS`() = assertEquals(VoiceCommand.STATUS, parseVoiceCommand("show status"))
    @Test fun `report phrase maps to REPORT`() = assertEquals(VoiceCommand.REPORT, parseVoiceCommand("give me a summary"))
    @Test fun `refresh phrase maps to REFRESH`() = assertEquals(VoiceCommand.REFRESH, parseVoiceCommand("sync now"))
    @Test fun `cancel phrase maps to CANCEL`() = assertEquals(VoiceCommand.CANCEL, parseVoiceCommand("abort"))

    // ---- New commands ----

    @Test fun `create session phrase`() = assertEquals(VoiceCommand.CREATE_SESSION, parseVoiceCommand("new session"))
    @Test fun `create session alt phrase`() = assertEquals(VoiceCommand.CREATE_SESSION, parseVoiceCommand("start session"))
    @Test fun `approve gate phrase`() = assertEquals(VoiceCommand.APPROVE_GATE, parseVoiceCommand("approve gate"))
    @Test fun `approve guardrail phrase`() = assertEquals(VoiceCommand.APPROVE_GATE, parseVoiceCommand("approve guardrail"))
    @Test fun `what failed phrase`() = assertEquals(VoiceCommand.WHAT_FAILED, parseVoiceCommand("what failed"))
    @Test fun `what broke phrase`() = assertEquals(VoiceCommand.WHAT_FAILED, parseVoiceCommand("what broke"))
    @Test fun `show errors phrase`() = assertEquals(VoiceCommand.WHAT_FAILED, parseVoiceCommand("show errors"))
    @Test fun `list automata phrase`() = assertEquals(VoiceCommand.LIST_AUTOMATA, parseVoiceCommand("list automata"))
    @Test fun `automata status phrase`() = assertEquals(VoiceCommand.LIST_AUTOMATA, parseVoiceCommand("automata status"))
    @Test fun `pause session phrase`() = assertEquals(VoiceCommand.PAUSE_SESSION, parseVoiceCommand("pause session"))
    @Test fun `kill session phrase`() = assertEquals(VoiceCommand.KILL_SESSION, parseVoiceCommand("kill session"))
    @Test fun `stop session phrase`() = assertEquals(VoiceCommand.KILL_SESSION, parseVoiceCommand("stop session"))
    @Test fun `cost report phrase`() = assertEquals(VoiceCommand.COST_REPORT, parseVoiceCommand("cost report"))
    @Test fun `how much phrase`() = assertEquals(VoiceCommand.COST_REPORT, parseVoiceCommand("how much"))
    @Test fun `spending phrase`() = assertEquals(VoiceCommand.COST_REPORT, parseVoiceCommand("spending"))
    @Test fun `memory recall phrase`() = assertEquals(VoiceCommand.MEMORY_RECALL, parseVoiceCommand("recall the auth module"))
    @Test fun `unknown phrase`() = assertEquals(VoiceCommand.UNKNOWN, parseVoiceCommand("play some music"))

    // ---- Server-name resolution ----

    @Test
    fun `exact server name match`() {
        val result = resolveServerName("trent status", listOf("Trent", "Datawatch"))
        assertEquals("Trent", result)
    }

    @Test
    fun `fuzzy server name match within distance 2`() {
        // "Trent" vs "trent" (case-folded) = 0; "trnt" vs "trent" = 1
        val result = resolveServerName("trnt status", listOf("Trent", "Other"))
        assertEquals("Trent", result)
    }

    @Test
    fun `no match beyond distance 2`() {
        val result = resolveServerName("completely unrelated", listOf("Trent"))
        assertNull(result)
    }

    @Test
    fun `first profile name wins on tie`() {
        val result = resolveServerName("trent", listOf("Trent", "Trent2"))
        assertEquals("Trent", result)
    }

    // ---- Levenshtein ----

    @Test fun `identical strings distance 0`() = assertEquals(0, levenshtein("trent", "trent"))
    @Test fun `one char different = 1`() = assertEquals(1, levenshtein("trent", "trend"))
    @Test fun `one insertion = 1`() = assertEquals(1, levenshtein("trent", "trents"))
    @Test fun `deletion gives distance 1`() = assertEquals(1, levenshtein("trnt", "trent"))
    @Test fun `two substitutions = 2`() = assertEquals(2, levenshtein("trobt", "trent"))
    @Test fun `empty vs string = string length`() = assertEquals(5, levenshtein("", "trent"))
    @Test fun `both empty = 0`() = assertEquals(0, levenshtein("", ""))

    // ---- ParsedVoiceCommand topic extraction ----

    @Test
    fun `memory recall extracts topic after recall keyword`() {
        val parsed = parseVoiceCommandFull("recall the auth module")
        assertEquals(VoiceCommand.MEMORY_RECALL, parsed.command)
        assertTrue(parsed.topic?.contains("auth") == true, "topic: ${parsed.topic}")
    }
}
