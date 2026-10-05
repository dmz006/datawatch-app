package com.dmzs.datawatchclient.transport.dto

import com.dmzs.datawatchclient.transport.rest.RestTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wire names must match the server's council.Persona (`role`, `system_prompt`). */
class CouncilPersonaDtoTest {
    private val json = RestTransport.DefaultJson

    @Test
    fun `persona decodes server role and system_prompt`() {
        val p =
            json.decodeFromString(
                CouncilPersonaDto.serializer(),
                """{"name":"skeptic","role":"Devil's advocate","system_prompt":"Challenge every claim."}""",
            )
        assertEquals("Devil's advocate", p.description)
        assertEquals("Challenge every claim.", p.prompt)
    }

    @Test
    fun `create body sends system_prompt and role`() {
        val body =
            json.encodeToString(
                CouncilPersonaCreateDto.serializer(),
                CouncilPersonaCreateDto(name = "skeptic", prompt = "Challenge.", description = "Advocate"),
            )
        assertTrue(body.contains("\"system_prompt\":\"Challenge.\""), body)
        assertTrue(body.contains("\"role\":\"Advocate\""), body)
    }
}
