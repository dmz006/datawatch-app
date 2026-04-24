package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the v0.34.6 P0 fix. Server's
 * `POST /api/sessions/state` decodes its body as
 * `{"id": string, "state": string}`. Pre-v0.34.6 the DTO used
 * `session_id` as the JSON key, producing a 404 on every state
 * override. This test fails if the SerialName regresses.
 */
class StateOverrideDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes sessionId field with JSON key id`() {
        val dto = StateOverrideDto(sessionId = "ring-2db6", state = "killed")
        val encoded = json.encodeToString(StateOverrideDto.serializer(), dto)
        assertTrue(
            "\"id\":\"ring-2db6\"" in encoded,
            "expected JSON to contain id key; was: $encoded",
        )
        // Make sure the wrong key is NOT emitted — if someone
        // reintroduces @SerialName("session_id") this trips.
        assertTrue(
            "session_id" !in encoded,
            "JSON body must not use the legacy session_id key; was: $encoded",
        )
    }

    @Test
    fun `round-trips through decode`() {
        val payload = """{"id":"ring-xyz","state":"running"}"""
        val decoded = json.decodeFromString(StateOverrideDto.serializer(), payload)
        assertEquals("ring-xyz", decoded.sessionId)
        assertEquals("running", decoded.state)
    }
}
